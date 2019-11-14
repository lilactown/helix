(ns workshop
  (:require [devcards.core :as dc :include-macros true]
            [workshop.core]))

(defn start! []
  (dc/start-devcard-ui!))

(defn init! [] (start!))

(init!)
