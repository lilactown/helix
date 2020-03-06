(ns refresh-example
  (:require
    [helix.core :refer [<> defnc $]]
    [helix.dom :as d]
    [helix.hooks :as hooks]
    [helix.experimental.refresh :as refresh]
    ["react-dom" :as rdom]))

(defnc app
  []
  {:helix/features {:fast-refresh true}}
  (let [[name set-name] (hooks/use-state "Lisa")]
    (d/div
     {:style {:text-align "center"
              :padding "10px"
              :color "red"
              :font-family "sans-serif"}}
     (d/div (str "hello, " name))
     (d/div
      (d/input {:value name :on-change #(set-name (.. % -target -value))})))))


(defn ^:dev/after-load reload
  []
  (refresh/refresh!))


(defn ^:export start
  []
  (refresh/inject-hook!)
  (rdom/render
   ($ app)
   (js/document.getElementById "app")))
