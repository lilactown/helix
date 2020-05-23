# Helix

ClojureScript optimized for modern React development.


```clojure
(ns my-app.core
  (:require [helix.core :refer [defnc $]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            ["react-dom" :as rdom]))

;; define components using the `defnc` macro
(defnc greeting
  "A component which greets a user."
  [{:keys [name]}]
  ;; use helix.dom to create DOM elements
  (d/div "Hello, " (d/strong name) "!"))

(defnc app []
  (let [[state set-state] (hooks/use-state {:name "Helix User"})]
    (d/div
     (d/h1 "Welcome!")
      ;; create elements out of components
      ($ greeting {:name (:name state)})
      (d/input {:value (:name state)
                :on-change #(set-state assoc :name (.. % -target -value))}))))

;; start your app with your favorite React renderer
(rdom/render ($ app) (js/document.getElementById "app"))
```

## Installation

[![Clojars Project](https://img.shields.io/clojars/v/lilactown/helix.svg)](https://clojars.org/lilactown/helix)

Install the latest verion from clojars in your project.

A version of "react" and "react-refresh" should be installed automatically;
install the corresponding version of your favorite renderer (e.g. "react-dom").

### shadow-cljs and npm

During development, you'll want to emit ES6 code until [polyfills are handled
differently](https://github.com/thheller/shadow-cljs/issues/709). You can do
this by passing in a dev compiler configuration:

```clojure
;; shadow-cljs.edn
{,,,
 :builds 
 {:app
 {,,,
  :dev {:compiler-options {:output-feature-set :es6}}}}}
```

Release builds should be able to emit all the way back to ES3.

### shadow-cljs and react-native

See [React Native](./docs/react-native.md).

### lein-cljsbuild / figwheel-main / raw CLJS

Use [CLJSJS](https://github.com/cljsjs/packages/tree/master/react) or package
react yourself using webpack, ensuring it is provided as the name `"react"`.

## Documentation

View formatted docs at [![cljdoc badge](https://cljdoc.org/badge/lilactown/helix)](https://cljdoc.org/d/lilactown/helix/CURRENT)


- [Why Helix](./docs/motivation.md)
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
- [Pro-tips](./docs/pro-tips.md)
  - [Memoizing components](./docs/pro-tips.md#memoizing-components)
  - [Don't use deep equals](./docs/pro-tips.md#dont-use-deep-equals)
  - [Create a custom macro](./docs/pro-tips.md#create-a-custom-macro)
- [Frequently Asked Questions](./docs/faq.md)
  - [What about hx?](./docs/faq.md#what-about-hx)
  - [What about hiccup?](./docs/faq.md#what-about-hiccup)
- [React Native](./docs/react-native.md)


Other resources:

- [#helix Slack channel](https://clojurians.slack.com/archives/CRRJBCX7S) ([Sign up for Slack here](http://clojurians.net))
- [Example TodoMVC](https://github.com/Lokeh/helix-todo-mvc)


## License

Copyright © 2020 Will Acton

Distributed under the EPL 2.0
