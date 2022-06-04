(ns refresh-example
  (:require
    [helix.core :refer [<> defnc $]]
    [helix.dom :as d]
    [helix.hooks :as hooks]
    [helix.experimental.refresh :as refresh]
    ["react-dom/client" :as rdom]))

(defnc app
  []
  {:helix/features {:fast-refresh true}}
  (let [[o set-o] (hooks/use-state
                   #js {:name "Lisa"})]
    (d/div
     {:style {:text-align "center"
              :padding "10px"
              :color "red"
              :font-family "sans-serif"}}
     (d/div (str "hello, " (.-name o) "!"))
     (d/div
      (d/input {:value (.-name o)
                :on-change #(set-o #js {:name  (.. % -target -value)})})))))


(defonce root nil)


(defn ^:dev/after-load reload
  []
  (refresh/refresh!))


(defn ^:export start
  []
  (refresh/inject-hook!)
  (set! root (rdom/createRoot (js/document.getElementById "app")))
  (.render root ($ app)))
