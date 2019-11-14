(ns helix.core
  (:require [clojure.walk]
            [clojure.string :as str]))

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
        (clj->props ~(first args) ~native?)
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


;;
;; React Fast Refresh
;;


(defn- find-all
  "Recursively walks a tree structure and finds all elements
  that match `pred`. Returns a vector of results."
  [pred tree]
  (let [results (atom [])]
    (clojure.walk/postwalk
     (fn walker [x]
       (when (pred x)
         (swap! results conj x))
       x)
     tree)
    @results))


(defn hook?
  [x]
  (when (list? x)
    (let [fst (first x)]
      (and (symbol? fst) (str/starts-with? (name fst)
                                           "use")))))

(defn find-hooks [body]
  (find-all hook? body))

;; TODO:
;; - Detect custom hooks
;; - Handle re-ordering
;;   - Detect hooks used in let-bindings and add left-hand side to signature
;;   - ???



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
        hooks (find-hooks body)
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
