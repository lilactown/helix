# Helix

ClojureScript optimized for modern React development.


```clojure
(ns my-app.core
  (:require [helix.core :refer [defnc $]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            ["react-dom" :as rdom]))

;; define components using the `defnc` macro
(defnc Greeting
  "A component which greets a user."
  [{:keys [name]}]
  ;; use helix.dom to create DOM elements
  (d/div "Hello, " (d/strong name) "!"))

(defnc App []
  (let [[state set-state] (hooks/use-state {:name "Helix User"})]
    (d/div (d/h1 "Welcome!")
           ;; create elements out of components
           ($ Greeting {:name (:name state)})
           (d/input {:value name 
                     :on-change #(set-state assoc :name (.. % -target -value))}))))

;; start your app with your favorite React renderer
(rdom/render ($ App) (js/document.getElementById "app"))
```

## Installation

[![Clojars Project](https://img.shields.io/clojars/v/lilactown/helix.svg)](https://clojars.org/lilactown/helix)

You'll want to make sure you have the latest version of `react`, and whatever
renderer you are targeting (e.g. `react-dom`).


### shadow-cljs and npm

```
npm i react react-dom
```

### lein-cljsbuild / figwheel-main / raw CLJS

Use [CLJSJS](https://github.com/cljsjs/packages/tree/master/react) or package
react yourself using webpack, ensuring it is provided as the name `"react"`.

## Documentation

- [Creating Components](./docs/creating-components.md)
  - [Props](./docs/creating-components.md#props)
  - [Interop](./docs/creating-components.md#interop)
  - [Higher-order Components](./docs/creating-components.md#higher-order-components)
  - [Class Components](./docs/creating-components.md#class-components) (WIP)
- [Creating elements](./docs/creating-elements.md)
  - [$ macro](./docs/creating-elements.md#-macro)
    - [Native elements and props](./docs/creating-elements.md#native-elements-and-props)
    - [Dynamic props](./docs/creating-elements.md#dynamic-props)
  - [`helix.dom`](./docs/creating-elements.md#helixdom)
  - [Other helpful tools](./docs/creating-elements.md#other-helpful-tools)
    - [Fragments](./docs/creating-elements.md#fragments)
    - [Context providers](./docs/creating-elements.md#context-providers)
    - [Suspense boundaries](./docs/creating-elements.md#suspense-boundaries)
    - [Creating elements dynamically](./docs/creating-elements.md#creating-elements-dynamically)
    - [Factory functions](./docs/creating-elements.md#factory-functions)
- [Hooks](./docs/hooks.md)
  - [Maintaining state](./docs/hooks.md#maintaining-state)
    - [Reducers](./docs/hooks.md#reducers)
    - [Refs](./docs/hooks.md#refs)
  - [Doing side effects](./docs/hooks.md#doing-side-effects)
  - [Optimizations](./docs/hooks.md#optimizations)
  - [Other miscellaneous](./docs/hooks.md#other-miscellaneous)
- [Experiments](./docs/experiments.md)
  - [Detect invalid hooks usage](./docs/experiments.md#detect-invalid-hooks-usage)
  - [Fast Refresh](./docs/experiments.md#fast-refresh)


Other resources:

- [#helix Slack channel](https://clojurians.slack.com/archives/CRRJBCX7S) ([Sign up for Slack here](http://clojurians.net))
- [Example TodoMVC](https://github.com/Lokeh/helix-todo-mvc)
- [Future looking example and discussion](https://gist.github.com/Lokeh/e93a1a0ab25d40df006d77f405c1e535)

Everything in the forward-looking example and discussion has been implemented except for annotating expressions with metadata like `^:memo` and `^:callback`.


## License

Copyright © 2020 Will Acton

Distributed under the EPL 2.0
