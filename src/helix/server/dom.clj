(ns helix.server.dom
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [helix.server.core :as core]
   [manifold.deferred :as d]
   [manifold.stream :as s])
  (:import [helix.server.core Element]))


(def $d core/$)


(defprotocol ToString
  (^String to-str [x] "Convert a value into a string."))


(extend-protocol ToString
  clojure.lang.Keyword (to-str [k] (name k))
  clojure.lang.Ratio (to-str [r] (str (float r)))
  String (to-str [s] s)
  Object (to-str [x] (str x))
  nil (to-str [_] ""))


(def no-close-tag?
  #{"area" "base" "br" "col" "command" "embed" "hr" "img" "input" "keygen" "link"
    "meta" "param" "source" "track" "wbr"})


(defn- entry->style
  [[k v]]
  (str (if (string? k) k (name k)) ":" v))

(defn style-str
  [styles]
  (reduce
   (fn [s e]
     (str s ";" (entry->style e)))
   (entry->style (first styles))
   (rest styles)))


#_(style-str {:color "red"})

#_(style-str {"color" "blue" :flex "grow"})


(defn props->attrs
  [props]
  (reduce-kv
   (fn [attrs k v]
     (case k
       :style (str attrs " style=\"" (style-str v) "\"")
       :id (str attrs " id=\"" v "\"")
       attrs))
   ""
   props))


#_(props->attrs {:style {:color "red"}})


(def ^:dynamic *suspended*) ; atom
(def ^:dynamic ^:private *suspense-counter*) ; atom


(defn- gen-suspense-id
  []
  (dec (swap! *suspense-counter* inc)))


(defn realize-elements
  ([el]
   (cond
     (and (instance? Element el)
          (or (string? (:type el)) ; DOM element type
              (= 'react/Fragment (:type el))))
     (update-in el [:props :children] #(doall (map realize-elements %)))

     (and (instance? Element el)
          (= 'react/Suspense (:type el)))
     (try
       (-> el
           (update-in [:props :children] #(doall (map realize-elements %))))
       (catch clojure.lang.ExceptionInfo e
         (if-let [d (::core/deferred (ex-data e))]
           (let [suspense-id (or (-> el :props ::core/suspense-id)
                                 (gen-suspense-id))
                 el (-> el
                        (assoc-in [:props ::core/suspense-id] suspense-id)
                        (assoc-in [:props ::core/suspended?] true))]
             (swap! *suspended* assoc suspense-id [d el])
             (assoc-in el [:props ::core/fallback?] true))
           (throw e))))

     (instance? Element el)
     (realize-elements (core/-render (:type el) (:props el)))

     (sequential? el)
     (doall (map realize-elements el))

     :else (to-str el))))


(defn -render!
  [el]
  (let [>results (s/stream)
        suspended (atom {})
        suspense-counter (atom 0)
        result (binding [*suspended* suspended
                         *suspense-counter* suspense-counter]
                 (realize-elements el))]
    (s/put! >results [:root result])

    (d/loop [suspended @suspended]
      (let [suspended-results (s/stream)]
        ;; as each deferred resolves, put the id and element onto
        ;; suspended-results so we can process them in the order they
        ;; complete
        (d/chain
         (apply d/zip (for [[suspense-id [d el]] suspended]
                        (d/chain
                         d
                         (fn [_]
                           (s/put! suspended-results [suspense-id el])))))
         (fn [_]
           (s/close! suspended-results)))
        (let [suspended2 (atom {})]
          (d/chain
           (s/consume-async
            (fn [[suspense-id el]]
              (s/put!
               >results
               [suspense-id
                (binding [*suspended* suspended2
                          *suspense-counter* suspense-counter]
                  (realize-elements el))]))
            suspended-results)
           (fn [_]
             (if (seq @suspended2)
               (d/recur @suspended2)
               (s/close! >results)))))))
    >results))


(defn put-el!
  [stream el]
  (cond
    (instance? Element el)
    (cond
      (no-close-tag? (:type el))
      (s/put! stream (str "<" (:type el) (props->attrs (:props el)) " />"))

      (string? (:type el))
      (do (s/put! stream (str "<" (:type el) (props->attrs (:props el)) ">"))
          (doseq [child (-> el :props :children)]
            (put-el! stream child))
          (s/put! stream (str "</" (:type el) ">")))

      (= 'react/Fragment (:type el))
      (doseq [child (-> el :props :children)]
        (put-el! stream child))

      (= 'react/Suspense (:type el))
      (if (::core/fallback? (:props el))
        (do (s/put! stream "<!--$?-->")
            (s/put! stream (str "<template id=\"B:"
                                (-> el :props ::core/suspense-id)
                                "\"></template>"))
            (put-el! stream (-> el :props :fallback))
            (s/put! stream "<!--/$-->"))
        (do
          (when-not (-> el :props ::core/suspended?)
            ;; don't render boundary again if prev suspended
            (s/put! stream "<!--$-->"))
          (doseq [child (-> el :props :children)]
            (put-el! stream child))
          (when-not (-> el :props ::core/suspended?)
            (s/put! stream "<!--/$-->")))))

    (sequential? el)
    (doseq [x el]
      (put-el! stream x))

    :else (s/put! stream (to-str el))))


(def complete-boundary-function
  (slurp (io/resource "helix/server/rc.js")))


(defn render-to-stream
  [el]
  (let [html (s/stream)
        loaded-boundary-script? (atom false)]
    (s/put! html "<!doctype html>")
    (s/connect-via
     (-render! el)
     (fn [[suspense-id el]]
       (if (= :root suspense-id)
         (put-el! html el)
         (do
           (s/put! html (str "<div hidden id=\"S:" suspense-id "\">"))
           (put-el! html el)
           (s/put! html (str "</div>"))
           (if-not @loaded-boundary-script?
             (do (reset! loaded-boundary-script? true)
                 (s/put! html (str "<script>" complete-boundary-function "; "
                                   "$RC(\"B:" suspense-id "\", \"S:" suspense-id "\")"
                                   "</script>")))
             (s/put! html (str "<script>"
                               "$RC(\"B:" suspense-id "\", \"S:" suspense-id "\")"
                               "</script>"))))))
     html)
    html))
