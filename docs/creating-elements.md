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

($ "div" {:style {:color "red"}} "red text in a div")
;; => #js {:type "div" :props #js {:style #js {:color "red"}}}

($ "div" ($ "div" "first")
         ($ "div" "second"
                  ($ "span" "last")))
;; => #js {:type "div"
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
passed in as-is, with one exception. There are some additional things to know
about props which are documented below.

### Native props

For "native" components (in React DOM this is any string like "div", "span",
etc.), a map literal passed to the `:style` prop will also be converted to a JS
object at compile time.

```clojure
($ "div" {:style {:color "red"
                  :background "green"}})
;; => #js {:type "div"
;;         :props #js {:style #js {:color "red"
;;                                 :background "green"}}
;;         ...}
```

Other special props:
- The `:class` prop is renamed to `:className`
- The `:for` prop is renamed to `:htmlFor`

All prop keys will be converted from kebab-case to camelCase.

### Dynamic props

Props that need to be determined at runtime can be passed in and merged with the
props map using the `&` key. These are colloquially referred to as "spread
props", which is a reference to usage of JS' spread operator with JSX and the
syntax is meant to mirror dynamic arity in function definitions.

```clojure
;; dynamic props
(def extra-props {:prop3 "baz"})

($ MyComponent {:prop1 "foo" :prop2 "bar" & extra-props})
;; => #js {:type MyComponent :props #js {:prop1 "foo" :prop2 "bar" :prop3 "baz"}}
```

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
