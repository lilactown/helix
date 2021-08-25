(ns react-repl.core
  (:require
   [goog.object :as gobj]
   [cljs-bean.core :as b]
   [react-repl.state :as state])
  (:refer-clojure :exclude [find]))


(defn current-fiber
  ([] (current-fiber 1))
  ([id] (gobj/get (get @state/roots id) "current")))


(defn child-fiber
  [fiber]
  (gobj/get fiber "child"))


(defn has-child?
  [fiber]
  (-> (child-fiber fiber) nil? not))


(defn parent
  [fiber]
  (gobj/get fiber "return"))


(defn sibling-fiber
  [fiber]
  (gobj/get fiber "sibling"))


(defn siblings
  [fiber]
  (when (some? fiber)
    (lazy-seq
     (cons fiber (siblings (sibling-fiber fiber))))))


(defn children
  [fiber]
  (siblings (child-fiber fiber)))


(defn all-fibers
  ([]
   (tree-seq has-child? children (current-fiber)))
  ([root]
   (tree-seq has-child? children root)))


;;
;; Displaying
;;


(defn- has-hooks?
  [fiber]
  (not (nil? (gobj/get fiber "_debugHookTypes"))))


(defn- hook->map
  [hook-type hook]
  (let [queue (gobj/get hook "queue")]
    (with-meta
      (cond-> {:type hook-type
               :current (gobj/get hook "memoizedState")}
        (not (nil? queue))
        (assoc :dispatch (gobj/get queue "dispatch")))
      {:hook hook
       :hook/type hook-type})))


(defn props
  "Returns the current props of the fiber."
  [fiber]
  (b/bean (gobj/get fiber "memoizedProps")))


(defn state
  "Returns the current state (hooks and local component state) of the fiber."
  [fiber]
  ;; :memoizedState linked list of hooks each w/ :next, :baseState and :memoizedState
  ;; type is in _debugHookTypes
  (let [hook-types (gobj/get fiber "_debugHookTypes")]
    (loop [current (gobj/get fiber "memoizedState")
           hooks (-> (hook->map (first hook-types) current)
                     (vector))
           hook-types (rest hook-types)]
      (if-let [next (gobj/get current "next")]
        (recur next
               (conj hooks (hook->map (first hook-types) next))
               (rest hook-types))
        hooks))))


(defn fiber->map
  [fiber]
  (when (some? fiber)
    (with-meta
      {:props (b/bean (gobj/get fiber "memoizedProps"))
       :type (gobj/get fiber "type")
       :state-node (gobj/get fiber "stateNode")
       :parent (parent fiber)
       :state (if (has-hooks? fiber)
                (->> fiber
                     (state)
                     (map #(hook->map (first %) (second %))))
                (-> fiber
                    (gobj/get "memoizedState")
                    (b/bean)))
       :children (map fiber->map (children fiber))}
      {:fiber fiber})))


;;
;; Hook operations
;;


(defn hook-deps
  [hook]
  (case (:type hook)
    ("useEffect"
     "useLayoutEffect"
     "useMemo"
     "useCallback") (second (:current hook))))


(defn hook-dispatch
  [{:keys [dispatch] :as _hook} & args]
  (apply dispatch args))


;;
;; Querying
;;


(defn find-all
  [type]
  (->> (all-fibers)
       (filter #(= (gobj/get % "type") type))))


(defn find
  [type]
  (first (find-all type)))
