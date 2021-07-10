(ns workshop
  (:require [devcards.core :as dc :include-macros true]
            [helix.experimental.repl :as repl]
            [workshop.core]))

(defn ^:dev/after-load start! []
  (dc/start-devcard-ui!))

(defn init! []
  (start!)
  (repl/inject-hook))

(init!)
