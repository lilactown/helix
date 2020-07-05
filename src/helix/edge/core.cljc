(ns helix.edge.core
  (:require [helix.core :as helix])
  #?@(:clj ()
      :cljs [(:require-macros [helix.edge.core])]))


#?(:clj
   (do
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
       `(helix/$ ~type ~@args))

     (defmacro <>
       [& children]
       `(helix/<> ~@children))

     (defmacro provider
       "Creates a Provider for a React Context value.

  Example:

    (def my-context (react/createContext))

    (provider {:context my-context :value my-value} child1 child2 ...childN)"
       [{:keys [context value] :as props} & children]
       `(helix/provider ~props ~@children))

     (defmacro suspense
       "Creates a new React Fragment Element"
       [{:keys [fallback] :as props} & children]
       `(helix/suspense ~props ~@children))

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
       [type params & body]
       (let [[docstring params body] (if (string? params)
                                       [params (first body) (rest body)]
                                       [nil params body])
             opts? (map? (first body)) ;; whether an opts map was passed in
             opts (if opts?
                    (first body)
                    {})
             body (if opts?
                    (rest body)
                    body)
             ;; feature flags to enable by default
             default-opts {:helix/features {:fast-refresh true
                                            :check-invalid-hooks-usage true
                                            :metadata-optimizations true}}]
         `(helix/defnc ~type ~@(when docstring [docstring]) ~params
            ;; we use `merge` here to allow indidivual consumers to override feature
            ;; flags in special cases
            ~(merge default-opts opts)
            ~@body)))

     (defmacro defhook [sym & body]
       `(helix/defhook ~sym ~@body))

     (defmacro defcomponent
       "Defines a React class component"
       [display-name & spec]
       `(helix/defcomponent ~display-name ~@spec))))
