(ns helix.experimental.repl
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


(defn child?
  [node]
  (-> (child node) nil? not))


(defn sibling
  [node]
  (gobj/get node "sibling"))


(defn children
  [node]
  (loop [last (child node)
         childs (vector (child node))]
    (if-let [sibl (sibling last)]
      (recur sibl
             (conj childs sibl))
      childs)))


(defn type? [type node]
  (= type (gobj/get node "type")))


(defn as-tree-seq
  []
  (->> (current)
       (tree-seq child? children)))


;;
;; Displaying
;;

(defn- hooks? [node]
  (not (nil? (gobj/get node "_debugHookTypes"))))


(defn hook-info [hook-type hook]
  (let [queue (gobj/get hook "queue")]
    (cond-> {:type (keyword hook-type)
             :current (gobj/get hook "memoizedState")}
      (not (nil? queue))
      (assoc :dispatch (gobj/get queue "dispatch")))))


(defn- accumulate-hooks [node]
  ;; :memoizedState linked list of hooks each w/ :next, :baseState and :memoizedState
  ;; type is in _debugHookTypes
  (let [hook-types (gobj/get node "_debugHookTypes")]
    (loop [current (gobj/get node "memoizedState")
           hooks (-> [(first hook-types) current]
                     (vector))
           hook-types (rest hook-types)]
      (if-let [next (gobj/get current "next")]
        (recur next
               (conj hooks [(first hook-types) next])
               (rest hook-types))
        hooks))))


(defn info [node]
  (let [props (gobj/get node "memoizedProps")]
    {:props props
     :state (if (hooks? node)
              (->> node
                   (accumulate-hooks)
                   (map #(hook-info (first %) (second %)))))}))


#_(->  (tree)
     (as-> tree (take-last 4 tree))
     (first)
     #_(b/bean)
     #_(keys)
     ;;:type
     #_(dissoc :props :pendingProps :memoizedProps)
     #_(cljs.pprint/pprint)
     (js/console.log))
