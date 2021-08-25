(ns react-repl.dom
  (:require
   [goog.dom :as gdom]
   [goog.object :as gobj]
   [react-repl.core :as rr]))


(defn parent-el
  [fiber]
  (cond
    (gdom/isElement (gobj/get fiber "stateNode"))
    (gobj/get fiber "stateNode")

    (some? (rr/parent fiber)) (recur (rr/parent fiber))))


(defn child-el
  [fiber]
  (cond
    (gdom/isElement (gobj/get fiber "stateNode"))
    (gobj/get fiber "stateNode")

    (rr/has-child? fiber) (recur (rr/child-fiber fiber))))
