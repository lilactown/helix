(ns react-experimental
  (:require
   [helix.core]))

(defmacro defnc [type params & body]
  `(helix.core/defnc ~type ~params
     {:helix/features {:fast-refresh true}}
     ~@body))
