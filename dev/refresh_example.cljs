(ns refresh-example
  (:require
   ["react-dom/client" :as rdom]
   [helix.core :refer [$ <> defnc]]
   [helix.dom :as d]
   [helix.experimental.refresh :as refresh]
   [helix.hooks :as hooks]))

(defnc app
  []
  {:helix/features {:fast-refresh true}}
  (let [_ (hooks/use-callback :auto-deps #(apply str %1 %&))
        [o set-o] (hooks/use-state
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
