(ns refresh-example.depended
  (:require [refresh-example.depended2 :refer [greeting]]))

(defn greet
  [name]
  (str greeting name "!"))
