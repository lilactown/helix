(ns helix.core
  (:require
   [helix.impl.analyzer :as hana]
   [helix.impl.props :as impl.props]
   [cljs.tagged-literals :as tl]
   [clojure.string :as string]
   [clojure.zip :as zip]))

(defn- jsx-children [coll]
  (let [s (seq coll)]
    (if (and s (next s))
      (tl/->JSValue coll)
      (first coll))))

(defmacro $
  "Create a new React element from a valid React type.

  Will try to statically convert props to a JS object.

  To pass in dynamic props, use the special `&` or `:&` key in the props map
  to have the map merged in.

  Simple example:

  ($ my-component
     \"child1\"
     ($ \"span\"
        {:style {:color \"green\"}}
        \"child2\" ))

  Dynamic exmaple:

  (let [dynamic-props {:foo \"bar\"}]
    ($ my-component
       {:static \"prop\"
        & dynamic-props}))
  "
  {:style/indent 0}
  [type & args]
  (when (and (symbol? (first args))
             (= (hana/inferred-type &env (first args))
                'cljs.core/IMap))
    (hana/warn hana/warning-inferred-map-props
               &env
               {:form (cons type args)
                :props-form (first args)}))
  (let [inferred (hana/inferred-type &env type)
        native? (or (keyword? type)
                    (string? type)
                    (= inferred 'string)
                    (= inferred 'cljs.core/Keyword)
                    (:native (meta type)))
        type (if (keyword? type)
               (name type)
               type)
        has-props? (or (map? (first args))
                       (nil? (first args)))
        children (if has-props?
                   (rest args)
                   args)
        props (if (map? (first args))
                (if native?
                  `(impl.props/dom-props ~(first args) ~(jsx-children children))
                  `(impl.props/props     ~(first args) ~(jsx-children children)))
                (tl/->JSValue (cond-> {}
                                (not-empty children)
                                (assoc :children (jsx-children children)))))
        has-key? (when has-props?
                   (contains? (first args) :key))
        the-key (when has-key?
                  (:key (first args)))
        emit-fn (if (next children)
                  `jsxs
                  `jsx)]
    (if has-key?
      `^js/React.Element (~emit-fn ~type ~props ~the-key)
      `^js/React.Element (~emit-fn ~type ~props))))

(defmacro <>
  "Creates a new React Fragment Element"
  [& children]
  `^js/React.Element ($ Fragment ~@children))


(defmacro provider
  "Creates a Provider for a React Context value.

  Example:

    (def my-context (react/createContext))

    (provider {:context my-context :value my-value} child1 child2 ...childN)"
  [{:keys [context value] :as props} & children]
  `(let [^{:tag ~'js} ctx# ~context]
     ^js/React.Element ($ (.-Provider ctx#)
                          ;; use contains to guard against `nil`
                          ~@(when (contains? props :value)
                              `({:value ~value}))
                          ~@children)))


(defmacro suspense
  "Creates a React Suspense boundary."
  [{:keys [fallback]} & children]
  `^js/React.Element ($ Suspense
                        ~@(when fallback
                            `({:fallback ~fallback}))
                        ~@children))


;;
;; -- component definition
;;


(defmacro infer-deps
  [deps-binding & body]
  {:style/indent :defn}
  (let [deps (hana/resolve-local-vars &env body)]
    `(let [~deps-binding ~deps]
       ~@body)))


(defn- fnc*
  [display-name props-bindings body]
  ;; maybe-ref for react/forwardRef support
  `(fn ^js/React.Element ~@(when (some? display-name) [display-name])
     [props# maybe-ref#]
     (let [~props-bindings [(extract-cljs-props props#) maybe-ref#]]
       ~@body)))


(def meta->form
  {:memo (fn [form deps]
           `(helix.hooks/use-memo
             ~(if (coll? deps)
                deps
                :auto-deps)
             ~form))
   :callback (fn [form deps]
               `(helix.hooks/use-callback
                 ~(if (coll? deps)
                    deps
                    :auto-deps)
                 ~form))})


(defmacro fnc
  "Creates a new anonymous function React component. Used like:

  (fnc ?optional-component-name
    [props ?forwarded-ref]
    {,,,opts-map}
    ,,,body)

  Returns a function that can be used just like a component defined with
  `defnc`, i.e. accepts a JS object as props and the body receives them as a
  map, can be used with `$` macro, forwardRef, etc.

  `opts-map` is optional and can be used to pass some configuration options.
  Current options:
   - ':wrap' - ordered sequence of higher-order components to wrap the component in
   - ':helix/features' - a map of feature flags to enable.

  Some feature flags only pertain to named components, i.e. Fast Refresh and
  factory functions, and thus can not be used with `fnc`."
  {:style/indent :defn}
  [& body]
  (let [[_display-name props-bindings body] (if (symbol? (first body))
                                             [(first body) (second body)
                                              (rest (rest body))]
                                             [nil (first body) (rest body)])
        opts-map? (map? (first body))
        opts (if opts-map?
               (first body)
               {})
        feature-flags (:helix/features opts)

        ;; feature flags
        flag-check-invalid-hooks-usage? (:check-invalid-hooks-usage feature-flags true)
        flag-metadata-optimizations (:metadata-optimizations feature-flags)

        body (if opts-map?
               (rest body)
               body)
        body (if flag-metadata-optimizations
               (hana/map-forms-with-meta body meta->form)
               body)

        ;; TODO add fast refresh support somehow?
        _hooks (hana/find-hooks body)]
    (when flag-check-invalid-hooks-usage?
      (when-some [invalid-hooks (->> (map hana/invalid-hooks-usage body)
                                     (flatten)
                                     (filter (comp not nil?))
                                     (seq))]
        (doseq [invalid-hook invalid-hooks]
          (hana/warn hana/warning-invalid-hooks-usage
                     &env
                     invalid-hook))))

    `(-> ~(fnc* nil props-bindings
                body)
         ~@(-> opts :wrap))))


(defn- skip
  "Like clojure.zip/next, but skips delving deeper into the current node, moving
  either right or up to the right or ending."
  [loc]
  (if (= :end (loc 1))
    loc
    (or
     ;; -- skip checking branch and going down
     ;; (and (branch? loc) (down loc))
     ;; --
     (zip/right loc)
     (loop [l loc]
       (if (zip/up l)
         (or (zip/right (zip/up l))
             (recur (zip/up l)))
         [(zip/node l) :end])))))


(defmacro defnc
  "Defines a new functional React component. Used like:

  ```
  (defnc component-name
    \"Optional docstring\"
    {,,,fn-meta}
    [props ?ref]
    {,,,opts-map}
    ,,,body)
  ```

  `component-name` will now be bound in the namespace a React function component
  that returns a React Element.


  Your component should adhere to the following:

  First parameter is 'props', a map of properties passed to the component.

  Second parameter is optional and is used with `React.forwardRef`.

  `fn-meta` is optional and will be merged into the metadata of the `component-name`
  symbol. A special `:wrap` key may contain an ordered sequence of higher-order
  components to wrap the component in.

  `opts-map` is optional and can be used to pass some configuration options to the
  macro. Current options:
   - `:helix/features` - a map of feature flags to enable. See \"Experimental\" docs.

  `body` should return a React Element."
  {:style/indent :defn}
  [display-name & form-body]
  (let [[docstring form-body] (if (string? (first form-body))
                                [(first form-body) (rest form-body)]
                                [nil form-body])
        [fn-meta form-body] (if (map? (first form-body))
                              [(first form-body) (rest form-body)]
                              [nil form-body])
        props-bindings (first form-body)
        body (rest form-body)
        opts-map? (map? (first body))
        opts (cond-> (if opts-map?
                       (first body)
                       {})
               (:wrap fn-meta) (assoc :wrap (:wrap fn-meta)))
        sig-sym (gensym "sig")
        fully-qualified-name (str *ns* "/" display-name)
        feature-flags (:helix/features opts)

        ;; feature flags
        flag-fast-refresh? (:fast-refresh feature-flags)
        flag-check-invalid-hooks-usage? (:check-invalid-hooks-usage feature-flags true)
        flag-define-factory? (:define-factory feature-flags)
        flag-metadata-optimizations (:metadata-optimizations feature-flags)


        body (cond-> body
               opts-map? (rest)
               flag-metadata-optimizations (hana/map-forms-with-meta meta->form))

        hooks (hana/find-hooks body)

        component-var-name (if flag-define-factory?
                             (with-meta (symbol (str display-name "-type"))
                               {:private true})
                             display-name)

        component-fn-name (symbol (str display-name "-render"))]
    (when flag-check-invalid-hooks-usage?
      (when-some [invalid-hooks (->> (map hana/invalid-hooks-usage body)
                                     (flatten)
                                     (filter (comp not nil?))
                                     (seq))]
        (doseq [invalid-hook invalid-hooks]
          (hana/warn hana/warning-invalid-hooks-usage
                     &env
                     invalid-hook))))

    `(do ~(when flag-fast-refresh?
            `(if  ~(with-meta 'goog/DEBUG {:tag 'boolean})
               (def ~sig-sym (signature!))))
         (def ~(vary-meta
                component-var-name
                merge
                {:helix/component? true}
                fn-meta)
           ~@(when-not (nil? docstring)
               (list docstring))
           (-> ~(fnc* component-fn-name props-bindings
                      (cons (when flag-fast-refresh?
                              `(if ^boolean goog/DEBUG
                                 (when ~sig-sym
                                   (~sig-sym))))
                            body))
               (cond->
                 (true? ^boolean goog/DEBUG)
                 (doto (-> (.-displayName) (set! ~fully-qualified-name))))
               ~@(-> opts :wrap)))

         ~(when flag-define-factory?
            `(def ~display-name
               (cljs-factory ~component-var-name)))

         ~(when flag-fast-refresh?
            `(when (with-meta 'goog/DEBUG {:tag 'boolean})
               (when ~sig-sym
                 (~sig-sym ~component-var-name ~(string/join hooks)
                  nil ;; forceReset
                  nil)) ;; getCustomHooks
               (register! ~component-var-name ~fully-qualified-name)))
         ~display-name)))


(defmacro defnc-
  "Same as defnc, yielding a non-public def"
  {:style/indent :defn}
  [display-name & rest]
  (list* `defnc (vary-meta display-name assoc :private true) rest))


;;
;; Custom hooks
;;


(defmacro defhook
  "Defines a new custom hook function.
  Checks for invalid usage of other hooks in the body, and other helix
  features."
  {:style/indent :defn}
  [sym & body]
  (let [[docstring params body] (if (string? (first body))
                                  [(first body) (second body) (drop 2 body)]
                                  [nil (first body) (rest body)])
        [opts body] (if (map? (first body))
                      [(first body) (rest body)]
                      [nil body])
        feature-flags (:helix/features opts)

        ;; feature flags
        #_#_flag-fast-refresh? (:fast-refresh feature-flags)
        flag-check-invalid-hooks-usage? (:check-invalid-hooks-usage
                                         feature-flags
                                         ;; default on
                                         true)
        ;; find the first param marked as ^:deps
        auto-deps-pos (->> (map-indexed vector params)
                           (some (fn [[i param]]
                                   (when (:deps (meta param))
                                     (inc i)))))]
    (when flag-check-invalid-hooks-usage?
      (when-some [invalid-hooks (->> (map hana/invalid-hooks-usage body)
                                     (flatten)
                                     (filter (comp not nil?))
                                     (seq))]
        (doseq [invalid-hook invalid-hooks]
          (hana/warn hana/warning-invalid-hooks-usage
                     &env
                     invalid-hook))))
    (when-not (string/starts-with? (str sym) "use-")
      (hana/warn hana/warning-invalid-hook-name &env {:form &form}))
    `(defn ~(vary-meta sym merge {:helix/hook? true
                                  :helix/auto-deps-pos auto-deps-pos})
      ;; use ~@ here so that we don't emit `nil`
      ~@(when-not (nil? docstring) (list docstring))
      ~params
      ~@body)))



;;
;; Class components
;;


(defn- static? [form]
  (boolean (:static (meta form))))


(defn- method? [form]
  (and (list? form)
       (simple-symbol? (first form))
       (vector? (second form))))


(defn- ->method [[sym-name bindings & form]]
  {:assert [(simple-symbol? sym-name)]}
  (list (str sym-name)
        `(fn ~sym-name ~bindings
           ~@form)))


(defn- ->value [[sym-name value]]
  {:assert [(simple-symbol? sym-name)]}
  (list (str sym-name) value))


(defmacro defcomponent
  "Defines a React class component.
  Like `class display-name extends React.Component { ... }` in JS.

  Methods are defined using (method-name [this ,,,] ,,,) syntax.
  Properties elide the arguments vector (property-name expr)

  Static properties and methods can be added by annotating the method or
  property with metadata containing the :static keyword.

  Some assumptions:
  - To use setState, you must store the state as a JS obj
  - The render method receives three arguments: this, a CLJS map of props,
    and the state object.
  - displayName by default is the symbol passed in, but can be customized
    by manually adding it as a static property

  Example:

  (defcomponent foo
   (constructor
    [this]
    (set! (.-state this) #js {:counter 0})))"
  {:style/indent [1 :form [1]]}
  [display-name & spec]
  {:assert [(simple-symbol? display-name)
            (seq (filter #(= 'render (first %)) spec))]}
  (let [[docstring spec] (if (string? (first spec))
                           [(first spec) (rest spec)]
                           [nil spec])
        {statics true spec false} (group-by static? spec)
        js-spec `(cljs.core/js-obj ~@(->> spec
                                          (map ->method)
                                          (apply concat)))
        js-statics `(cljs.core/js-obj
                     ~@(->> statics
                            (map #(if (method? %)
                                    (->method %)
                                    (->value %)))
                            (apply concat
                                   (list "displayName"
                                         ;; fully qualified name
                                         (str *ns* "/" display-name)))))]
    ;; TODO handle render specially
    `(def ~display-name
       ~@(when docstring [docstring])
       (create-component ~js-spec ~js-statics))))

(comment
  (macroexpand
   '(defcomponent asdf
      (foo [] "bar")
      ^:static (greeting "asdf")
      (bar [this] asdf)
      ^:static (baz [] 123)))

  ;; => (helix.core/create-component
  ;;     (cljs.core/js-obj
  ;;      "foo"
  ;;      (clojure.core/fn foo [] "bar")
  ;;      "bar"
  ;;      (clojure.core/fn bar [this] asdf))
  ;;     (cljs.core/js-obj "greeting" "asdf" "baz" (clojure.core/fn baz [] 123)))

  ($ "arst"
     ($ "oieno")
     ($ "fqfw")
     ($ "123"))
  )


(defmacro use-auto-effect
  [f]
  `(infer-deps
    deps#
    (use-effect
     deps#
     ~f)))


(defmacro use-auto-layout-effect
  [f]
  `(infer-deps
    deps#
    (use-layout-effect
     deps#
     ~f)))


(defmacro use-auto-memo
  [f]
  `(infer-deps
    deps#
    (use-memo
     deps#
     ~f)))


(defmacro use-auto-callback
  [f]
  `(infer-deps
    deps#
    (use-callback
     deps#
     ~f)))
