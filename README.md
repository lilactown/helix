# Helix

ClojureScript optimized for modern React development.


```clojure
(ns my-app.core
  (:require [helix.core :refer [defnc $ <>]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            ["react-dom" :as rdom]))

(defnc Greeting
  "A component which greets a user. The user can double click on their name to edit it."
  [{:keys [name on-name-change]}]
  (let [[editing? set-editing?] (hooks/use-state false)
        input-ref (hooks/use-ref nil)]
    (hooks/use-layout-effect
      :auto-deps ;; automatically infer deps array from body; stand in for `[editing?]`
      (when editing?
        (.focus (.-current input-ref)))
    (d/div
     "Hello, " (if editing?
                 (d/input {:ref input-ref
                           :on-change #(on-name-change (.. % -target -value))
                           :value name
                           :on-blur #(set-editing? false)})
                 (d/strong {:on-double-click #(set-editing? true)} name)
                "!")))

(defnc App []
  (let [[state set-state] (hooks/use-state {:name "Helix User"})]
    (<> (d/h1 "Welcome!")
        ($ Greeting {:name (:name state)
                     :on-name-change #(set-name assoc :name %)}))))

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

- [Example TodoMVC](https://github.com/Lokeh/helix-todo-mvc)
- [Future looking example and discussion](https://gist.github.com/Lokeh/e93a1a0ab25d40df006d77f405c1e535)

Everything in the forward-looking example and discussion has been implemented except for annotating expressions with metadata like `^:memo` and `^:callback`.


## License

Copyright © 2019 Will Acton

Distributed under the EPL 2.0
