(ns helix.core
  (:require [helix.impl.analyzer :as hana]
            [helix.impl.props :as impl.props]
            [clojure.string :as string]))


(defmacro $
  "Create a new React element from a valid React type.

  Will try to statically convert props to a JS object. Falls back to `$$` when
  ambiguous.

  Example:
  ```
  ($ MyComponent
   \"child1\"
   ($ \"span\"
     {:style {:color \"green\"}}
     \"child2\" ))
  ```"
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
      (map? (first args)) `^js/React.Element (create-element
                                              ~type
                                              ~(if native?
                                                 `(impl.props/native-props ~(first args))
                                                 `(impl.props/props ~(first args)))
                                              ~@(rest args))

      :else `^js/React.Element (create-element ~type nil ~@args))))


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
  (let [ret (gensym "return_value")]
    ;; maybe-ref for react/forwardRef support
    `(fn ^js/React.Element ~display-name
       [props# maybe-ref#]
       (let [~props-bindings [(extract-cljs-props props#) maybe-ref#]]
         (do ~@body)))))


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

  'body' should return a React Element."
  [display-name & form-body]
  (let [docstring (when (string? (first form-body))
                    (first form-body))
        props-bindings (if (nil? docstring)
                         (first form-body)
                         (second form-body))
        body (if (nil? docstring)
               (rest form-body)
               (rest (rest form-body)))
        wrapped-name (symbol (str display-name "-helix-render"))
        opts-map? (map? (first body))
        opts (if opts-map?
               (first body)
               {})
        body (if opts-map?
               (rest body)
               body)
        hooks (hana/find-hooks body)
        sig-sym (gensym "sig")
        fully-qualified-name (str *ns* "/" display-name)
        feature-flags (:helix/features opts)]
    (when (:check-invalid-hooks-usage feature-flags)
      (when-some [invalid-hooks (->> (map hana/invalid-hooks-usage body)
                                     (flatten)
                                     (filter (comp not nil?))
                                     (seq))]
        (throw (ex-info "Invalid hooks usage"
                        {:invalid-hooks invalid-hooks}))))
    `(do ~(when (:fast-refresh feature-flags)
            `(if ^boolean goog/DEBUG
               (def ~sig-sym (signature!))))
         (def ~wrapped-name
           (-> ~(fnc* wrapped-name props-bindings
                      (cons (when (:fast-refresh feature-flags)
                              `(if ^boolean goog/DEBUG
                                 (when ~sig-sym
                                   (~sig-sym))))
                                         body))
               (cond->
                 (true? ^boolean goog/DEBUG)
                 (doto (goog.object/set "displayName" ~fully-qualified-name)))
               ~@(-> opts :wrap)))

         (def ~display-name
           ~@(when-not (nil? docstring)
               (list docstring))
           ~wrapped-name)

         ~(when (:fast-refresh feature-flags)
            `(if ^boolean goog/DEBUG
               (when ~sig-sym
                 (~sig-sym ~wrapped-name ~(string/join hooks)
                  nil ;; forceReset
                  nil)) ;; getCustomHooks
               (register! ~wrapped-name ~fully-qualified-name)))
         ~display-name)))


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
                                (apply concat (list "displayName" (str display-name)))))
        js-statics `(cljs.core/js-obj ~@(->> statics
                                   (map #(if (method? %)
                                           (->method %)
                                           (->value %)))
                                   (apply concat)))]
    ;; TODO handle render specially
    `(def ~display-name (create-component ~js-spec ~js-statics))))

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
