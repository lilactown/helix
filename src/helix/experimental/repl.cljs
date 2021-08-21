(ns helix.experimental.repl
  (:require [goog.object :as gobj]
            [cljs-bean.core :as b])
  (:refer-clojure :exclude [find]))


(defonce id->root (atom {}))


(defn on-commit-fiber-root
  [id root _maybe-priority-level _did-error?]
  (swap! id->root assoc id root))


(defn inject-hook!
  []
  (let [hook (gobj/get js/window "__REACT_DEVTOOLS_GLOBAL_HOOK__")
        onCommitFiberRoot (gobj/get hook "onCommitFiberRoot")]
    (gobj/set hook "onCommitFiberRoot"
              (fn [& args]
                (apply on-commit-fiber-root args)
                (this-as this
                  (.apply onCommitFiberRoot this (to-array args)))))))


(defn current-fiber
  ([] (current-fiber 1))
  ([id] (gobj/get (get @id->root id) "current")))


(defn child-node
  [node]
  (gobj/get node "child"))


(defn has-child?
  [node]
  (-> (child-node node) nil? not))


(defn sibling-node
  [node]
  (gobj/get node "sibling"))


(defn siblings
  [node]
  (when (some? node)
    (lazy-seq
     (cons node (siblings (sibling-node node))))))


(defn children
  [node]
  (siblings (child-node node)))


(defn all-fibers
  ([]
   (->> (current-fiber)
        (tree-seq has-child? children)))
  ([root]
   (tree-seq has-child? children root)))



;;
;; Displaying
;;

(defn- hooks?
  [node]
  (not (nil? (gobj/get node "_debugHookTypes"))))


(defn hook-info
  [hook-type hook]
  (let [queue (gobj/get hook "queue")]
    (cond-> {:type (keyword hook-type)
             :current (gobj/get hook "memoizedState")}
      (not (nil? queue))
      (assoc :dispatch (gobj/get queue "dispatch")))))


(defn- accumulate-hooks
  [node]
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


(defn fiber->map
  [node]
  (when (some? node)
    (with-meta
      {:props (b/bean (gobj/get node "memoizedProps"))
       :type (gobj/get node "type")
       :dom-el (gobj/get node "stateNode")
       :state (if (hooks? node)
                (->> node
                     (accumulate-hooks)
                     (map #(hook-info (first %) (second %))))
                (-> node
                    (gobj/get "memoizedState")
                    (b/bean)))
       :children (map fiber->map (children node))}
      {:fiber node})))


;;
;; Querying
;;


(defn find-all
  [type]
  (->> (all-fibers)
       (filter #(= (gobj/get % "type") type))
       (map fiber->map)))


(defn find
  [type]
  (first (find-all type)))
