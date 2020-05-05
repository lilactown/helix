# Creating Elements

[React Elements](https://reactjs.org/docs/rendering-elements.html) are a way to
represent instances of components as data. [Components](./creating-components.md)
are expected to return React Elements objects. Helix provides a number of
helpful tools to aid in Element creation.

## $ macro

The `$` macro takes a component type (string, keyword, or symbol referring to a
Component), optionally some props, and any children, and returns a React Element
with that same information, like [React.createElement](https://reactjs.org/docs/react-api.html#createelement).

```clojure
($ "div" "hello")
;; => #js {:type "div" :props #js {:children "hello"}}

($ my-component {:data {:foo "bar"}} "red text in a div")
;; => #js {:type my-component :props #js {:data {:foo "bar"}}}

($ my-component ($ "div" "first")
               ($ "div" "second"
                        ($ "span" "last")))
;; => #js {:type my-component
;;         :props #js {:children #js [#js {:type "div"
;;                                         :props #js {:children "first"}}
;;                                    #js {:type "div"
;;                                         :props #js {:children #js ["second"
;;                                                                    #js {:type "span"
;;                                                                         :props #js {:children "last"}}]}}]}}
```

When a map literal is passed to the second argument, it will treat this map as
props to provide the component and will be compiled and output as a JS object.
This is to avoid converting the map to a JS object at runtime, as well as
seamlessly using helix and 3rd party React components together.

This conversion is shallow. If you pass in any CLJS type into a prop, it will be
passed in as-is. All keys are converted to strings without any munging.

A few exceptions are documented below.

### Native elements and props

For "native" elements (in React DOM this is any string like "div", "span",
etc.), all prop keys will be converted from kebab-case to camelCase and several
props will be specially transformed.

An element is determined to be "native" and be subject to these transformations
if:

- It is a string e.g. "div"
- OR it is a keyword e.g. :div
- OR it is _inferred_ to be a string or keyword (experimental)
- OR it has metadata key `:native` set to true

```clojure
($ "div" {:style {:color "red"
                  :background "green"}})
;; => #js {:type "div"
;;         :props #js {:style #js {:color "red"
;;                                 :background "green"}}
;;         ...}


($ ^:native SomeComponent {:on-click do-thing})
;; => #js {:type SomeComponent :props #js {:onClick do-thing}}
```

Other special props:
- The `:class` prop is renamed to `:className`
- The `:for` prop is renamed to `:htmlFor`

### Dynamic props

Props that need to be determined at runtime can be passed in and merged with the
props map using the `&` or `:&` key. This is colloquially referred to as "spread
props", which is a reference to usage of JS' spread operator with JSX. The
syntax is meant to mirror dynamic arity in function definitions.

```clojure
;; dynamic props
(def extra-props {:prop3 "baz"})

($ my-component {:prop1 "foo" :prop2 "bar" & extra-props})
;; => #js {:type my-component :props #js {:prop1 "foo" :prop2 "bar" :prop3 "baz"}}
```

Props in the dynamic map will override props that are defined statically.

```clojure
(def extra-props {:b 3})

($ my-component {:a 1 :b 2 & extra-props})
;; => #js {:type my-component :props #js {:a 1 :b 3}}
```

This syntax is also useful when you want to pass a non-literal map to a component:

```clojure
(let [m {:foo "bar"}]
  ;; This will not work; `m` will be treated as a child:
  ($ my-component m)
  ;; This will work; `my-component` will recieve `m` as props:
  ($ my-component {& m}))
```  

You can use either the symbol `&` or the keyword `:&`, as some tools like
Cursive, joker, etc. use static analysis to find unimported symbols, which
`&` looks like. Try and be consistent with which you use, especiallly on a
team!

## helix.dom

The `helix.dom` namespace contains helper macros for creating React DOM
Elements.

All macros use the same props syntax and rules as `$` for native elements.

```clojure
(ns my-app.feature
  (:require [helix.dom :as d]))


(d/div "hello")

(d/div {:style {:color "red"}} "red text")

;; spread props
(d/input {:type "text" & other-props})
```

## Other helpful tools

### Fragments

`helix.core/<>` is a helper macro to create [React Fragments](https://reactjs.org/docs/react-api.html#reactfragment)

```clojure
(<> ($ "div") ($ "span"))
;; => #js {:type react/Fragment :props #js {:children #js [ ... ]}}
```

### Context providers

`helix.core/provider` is a helper macro to create a Provider element based on
a [React Context](https://reactjs.org/docs/context.html) value.

```clojure
(def my-context (react/createContext "default"))

(helix.core/provider
  {:context my-context
   :value "overrides default value"}
  ($ some-component)
  ($ other-component))
;; => #js {:type (.-Provider my-context)
;;         :props #js {:value "overrides default value"
;;                     :children #js [ ... ]}}
```


### Suspense boundaries

`helix.core/suspense` is a helper macro to create a [React Suspense boundary](https://reactjs.org/docs/react-api.html#reactsuspense).

```clojure
(helix.core/suspense
  {:fallback ($ spinner)}
  ($ "div"
     ($ other-component)))
;; => #js {:type react/Suspense
;;         :props #js {:fallback #js {:type Spinner}}
;;                     :children #js [ ... ]}
```

### Factory functions

Factory functions can be used instead of calls to `$`. Factory functions will
parse their props at runtime from a CLJS map to a JS object, thus being slightly
slower.

```clojure
(ns my-app.feature
  (:require [helix.core :refer [defnc factory]]
            [helix.dom :as d]))

(defnc MyComponent [{:keys [name on-click]}]
  (d/a {:on-click #(on-click name)}
       "Greetings " name "!"))

(def my-component (factory MyComponent))


(my-component {:name "Uma" :on-click #(js/alert (str "hello," %))})
;; => {:type MyComponent
;;     :props #js {:name "Uma" :on-click #function[...]}}
```
