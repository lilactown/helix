(ns helix.children
  (:require ["react" :as react]
            [cljs-bean.core :as b]
            [goog.object :as gobj])
  (:refer-clojure :exclude [map count vec]))

(defn children
  "Given a props object, returns children contained in it (if any).

  Handles cases where props could be either a JS object or a CLJS structure."
  [props]
  (if (or (b/bean? props) (map? props))
    (:children props)
    ;; assume it's a raw props object
    (gobj/get props "children")))

(defn map
  "Map children that are typically specified as `props.children`.

  `f` is a function of up to 3 args:
  - `child`: the child element
  - `key`: the React key assigned to this element
  - `index`: the numeric index of the element in the child collection starting at 0

  If children is an array it will be traversed and the function will be called
  for each child in the array.

  If children is null or undefined, this function will return null or undefined
  rather than an array."
  [f children]
  (.map react/Children children f))

(defn count
  "Returns the total number of components in children, equal to the number of
  times that a callback passed to map would be invoked."
  [children]
  (.count react/Children children))

(defn only
  "Verifies that children has only one child (a React element) and returns it.
  Otherwise this method throws an error."
  [children]
  (.only react/Children children))

(defn vec
  "Returns the children opaque data structure as a flat vector with keys
  assigned to each child. Useful if you want to manipulate collections of
  children in render, especially if you want to reorder or slice
  (:children props) before passing it down."
  [children]
  (into [] (.toArray react/Children children)))
