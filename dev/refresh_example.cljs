(ns refresh-example
  (:require [helix.core :as hx :refer [<> defnc]]
            [helix.dom :as d]
            [helix.hooks :as hooks]
            [refresh-example.depended :refer [greet]]
            ["react-dom" :as rdom]))

(defnc app
  []
  (let [[name set-name] (hooks/use-state "Lisa")]
    (d/div
     {:style {:text-align "center"
              :padding "10px"
              :color "green"
              :font-family "sans-serif"}}
     (d/div (greet name))
     (d/div
      (d/input {:value name :on-change #(set-name (.. % -target -value))})))))


(defn ^:export start []
  (rdom/render
   (app)
   (js/document.getElementById "app")))
