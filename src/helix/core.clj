(ns helix.core
  (:require [helix.impl.analyzer :as hana]
            [helix.impl.props :as impl.props]
            [clojure.string :as string]))


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
               type)]
    (cond
      (map? (first args))
      `^js/React.Element (.createElement
                          (get-react)
                          ~type
                          ~(if native?
                             `(impl.props/dom-props ~(first args))
                             `(impl.props/props ~(first args)))
                          ~@(rest args))

      :else `^js/React.Element (.createElement (get-react) ~type nil ~@args))))


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
  `^js/React.Element ($ (.-Provider ~context)
                        ;; use contains to guard against `nil`
                        ~@(when (contains? props :value)
                            `({:value ~value}))
                        ~@children))


(defmacro suspense
  "Creates a React Suspense boundary."
  [{:keys [fallback]} & children]
  `^js/React.Element ($ Suspense
                        ~@(when fallback
                            `({:fallback ~fallback}))
                        ~@children))


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
  [& body]
  (let [[display-name props-bindings body] (if (symbol? (first body))
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


        body (cond-> body
               opts-map? (rest)
               flag-metadata-optimizations (hana/map-forms-with-meta meta->form))

        hooks (hana/find-hooks body)]
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


(defmacro defnc
  "Creates a new functional React component. Used like:

  (defnc component-name
    \"Optional docstring\"
    [props ?ref]
    {,,,opts-map}
    ,,,body)

  \"component-name\" will now be a React function component that returns a React
  Element.


  Your component should adhere to the following:

  First parameter is 'props', a map of properties passed to the component.

  Second parameter is optional and is used with `React.forwardRef`.

  'opts-map' is optional and can be used to pass some configuration options to the
  macro. Current options:
   - ':wrap' - ordered sequence of higher-order components to wrap the component in
   - ':helix/features' - a map of feature flags to enable. See \"Experimental\" docs.

  'body' should return a React Element."
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
        opts (if opts-map?
               (first body)
               {})
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


;;
;; Custom hooks
;;


(defmacro defhook [sym & body]
  (let [[docstring params body] (if (string? (first body))
                                  [(first body) (second body) (drop 2 body)]
                                  [nil (first body) (rest body)])
        [opts body] (if (map? (first body))
                      [(first body) (rest body)]
                      [nil body])
        feature-flags (:helix/features opts)

        ;; feature flags
        flag-fast-refresh? (:fast-refresh feature-flags)
        flag-check-invalid-hooks-usage? (:check-invalid-hooks-usage feature-flags true)]
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
    `(defn ~(vary-meta sym merge {:helix/hook? true})
      ;; use ~@ here so that we don't emit `nil`
      ~@(when-not (nil? docstring) (list docstring))
      ~params
      ~@body)))



;;
;; Class components
;;


(defn static? [form]
  (boolean (:static (meta form))))


(defn method? [form]
  (and (list? form)
       (simple-symbol? (first form))
       (vector? (second form))))


(defn ->method [[sym-name bindings & form]]
  {:assert [(simple-symbol? sym-name)]}
  (list (str sym-name)
        `(fn ~sym-name ~bindings
           ~@form)))


(defn ->value [[sym-name value]]
  {:assert [(simple-symbol? sym-name)]}
  (list (str sym-name) value))


(defmacro defcomponent
  "Defines a React class component."
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
                            (apply concat (list "displayName"
                                                (str display-name)))))]
    ;; TODO handle render specially
    `(def ~display-name
       ~@(when docstring docstring)
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
  )
