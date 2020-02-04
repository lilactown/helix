(ns helix.repl
  (:require [goog.object :as gobj]
            [cljs-bean.core :as b]))

(def id->root (atom {}))

(defn on-commit-fiber-root [id root maybe-priority-level did-error?]
  (swap! id->root assoc id root))

(defn inject-hook []
  (let [hook (gobj/get js/window "__REACT_DEVTOOLS_GLOBAL_HOOK__")
        onCommitFiberRoot (gobj/get hook "onCommitFiberRoot")]
    (gobj/set hook "onCommitFiberRoot"
              (fn [& args]
                (apply on-commit-fiber-root args)
                (this-as this
                  (.apply onCommitFiberRoot this (to-array args)))))))

#_(inject-hook)

#_(add-watch id->root :dev (fn [_ _ _ r]
                             (js/console.log r)))

(defn current
  ([] (current 1))
  ([id] (gobj/get (get @id->root id) "current")))

#_(current)

(defn child
  [node]
  (gobj/get node "child"))

(-> (current)
    (child)
    (b/bean))
