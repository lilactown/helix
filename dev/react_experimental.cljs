(ns react-experimental
  (:require
   [helix.experimental.refresh :as refresh] ;; side-effecting
   [helix.core :refer [$ suspense]]
   [helix.dom :as d]
   [helix.hooks :as hooks]
   ["react-dom" :as rdom])
  (:require-macros
   [react-experimental :refer [defnc]]))


(declare fetch-data)


(declare read)


;;
;; App stuff
;;


(def data (atom nil))


(defnc data-view
  []
  (let [data (read @data)]
    (d/div
     (pr-str data))))


(defnc app
  []
  (suspense
   {:fallback (d/h1 "Loading data...")}
   ($ data-view)
   ($ data-view)
   ($ data-view)
   ($ data-view)
   ($ data-view)))


(defn start
  []
  (refresh/inject-hook!)
  (-> (js/document.getElementById "app")
      (rdom/createRoot)
      (.render ($ app))))


(defn ^:dev/after-load reload
  []
  (reset! data (fetch-data))
  (refresh/refresh!))



;;
;; Suspense stuff
;;



(defprotocol IResource
  (-read [args]))


(defn read [resource & args]
  (apply -read resource args))


(defn wrap-promise
  [p]
  (let [status (atom :pending)
        result (atom nil)
        suspender (-> p
                      (.then (fn [res]
                               (reset! status :success)
                               (reset! result res))
                             (fn [err]
                               (reset! status :failure)
                               (reset! result err))))]
    ;; Copying the contract from the codebox examples here:
    ;; https://reactjs.org/docs/concurrent-mode-suspense.html
    (reify
      IResource
      (-read [args]
        (case @status
           :pending (throw suspender)
           :error (throw @result)
           :success @result)))))


(defn fetch-data []
  (-> (js/Promise.
       (fn fetch-data-promise [yes _no]
         (prn :fetching)
         (js/setTimeout #(do (prn :fetched)
                             (yes {:result (rand-int 1000)}))
                        4000)))
      (wrap-promise)))
