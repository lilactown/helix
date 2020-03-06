(ns fast-refresh-example
  (:require [helix.core :as hx :refer [<> defnc $]]
            [helix.dom :as d]
            [helix.experimental.refresh :as refresh]
            [helix.hooks :as hooks]
            ["react-dom" :as rdom]))

(defn greet
  [name date]
  (str "Hi! " name "! It is now: " date))


(defn bartify [set-name-fn]
  (js/setTimeout
    (fn []
      (js/console.log "Changing to Bart")
      (set-name-fn "Bart"))
    3000))


(defn change-date [set-date-fn]
  (js/console.log "Setting interval to change date")
  (js/setInterval
    (fn []
      (let [n (Math/trunc (/ (js/Date.now) 1000))]
        (js/console.log "Setting date to: " n "... hoping to see only one message per n")
        (set-date-fn n)))
    1000))


(defn ^:dev/after-load after-load []
  (refresh/refresh!))


(defnc app
  []
  {:helix/features {:fast-refresh true}}
  (let [[name set-name] (hooks/use-state "Lisa")
        [date set-date] (hooks/use-state (js/Date.now))]

    (js/console.log (str "Name is:" name))

    (hooks/use-effect
      :once
      (bartify set-name))

    (hooks/use-effect
      :once
      (change-date set-date))

    (d/div
     {:style {:text-align "center"
              :padding "10px"
              :color "green"
              :font-family "sans-serif"}}
     (d/div (greet name date))

     (d/div
      (d/input {:value name :on-change #(set-name (.. % -target -value))})))))

(defn ^:export start []
  (rdom/render
    ($ app)
    (js/document.getElementById "app"))
  (js/console.log "Injecting Refresh Hook")
  (refresh/inject-hook!))