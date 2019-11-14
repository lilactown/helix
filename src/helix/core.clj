(ns helix.core
  (:require [helix.analyzer :as hana]
            [clojure.string :as string]))

(defn- camel-case
  "Returns camel case version of the string, e.g. \"http-equiv\" becomes \"httpEquiv\"."
  [s]
  (if (or (keyword? s)
          (string? s)
          (symbol? s))
    (let [[first-word & words] (string/split s #"-")]
      (if (or (empty? words)
              (= "aria" first-word)
              (= "data" first-word))
        s
        (-> (map string/capitalize words)
            (conj first-word)
            string/join)))
    s))

(defn style
  [x]
  (if (map? x)
    (hana/clj->js-obj x :kv->prop (fn [k v]
                                    [(-> k name camel-case)
                                     (style v)]))
    x))

(defn key->prop
  [k v]
  (case k
    :class ["className" v]
    :for ["htmlFor" v]
    :style ["style"
            (if (map? v)
              (style v)
              `(clj->js ~v))]

    [(camel-case (name k)) v]))


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
  (let [native? (keyword? type)
        type (if native?
               (name type)
               type)]
    (if (map? (first args))
      `(create-element
        ~type
        ~(hana/clj->js-obj (first args) :kv->prop key->prop)
        ~@(rest args))
      ;; bail to runtime detection of props
      `($$ ~type
           ~@args))))


(defmacro <>
  "Creates a new React Fragment Element"
  [& children]
  `($ Fragment ~@children))


(defn- fnc*
  [display-name props-bindings body]
  (let [ret (gensym "return_value")]
    ;; maybe-ref for react/forwardRef support
    `(fn ~display-name
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

  \"component-name\" will now be a React factory function that returns a React
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
        hooks (hana/find-hooks body)
        sig-sym (gensym "sig")
        fully-qualified-name (str *ns* "/" display-name)]
    `(do (def ~sig-sym (signature!))
         (def ~wrapped-name
           (-> ~(fnc* wrapped-name props-bindings
                                   (cons `(when goog/DEBUG
                                            (~sig-sym))
                                         (if opts-map?
                                           (rest body)
                                           body)))
               (cond-> goog/DEBUG
                 (doto (goog.object/set "displayName" ~fully-qualified-name)))
               (wrap-cljs-component)
               ~@(-> opts :wrap)))

         (def ~display-name
           ~@(when-not (nil? docstring)
               (list docstring))
           (factory ~wrapped-name))

         (when goog/DEBUG
           (~sig-sym ~wrapped-name ~(str/join hooks)
            nil ;; forceReset
            nil) ;; getCustomHooks
           (register! ~wrapped-name ~fully-qualified-name))
         ~display-name)))
