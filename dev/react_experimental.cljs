(ns react-experimental
  (:require
   [helix.experimental.refresh :as refresh] ;; side-effecting
   [helix.core :refer [$ suspense]]
   [helix.dom :as d]
   [helix.hooks :as hooks]
   ["react-dom" :as rdom])
  (:require-macros
   [react-experimental :refer [defnc]]))


(defnc app
  []
  "asdfed")


(defn start []
  (refresh/inject-hook!)
  (-> (js/document.getElementById "app")
      (rdom/createRoot)
      (.render ($ app))))


(defn ^:dev/after-load reload []
  (refresh/refresh!))
