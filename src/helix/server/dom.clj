(ns helix.server.dom
  (:require
   [clojure.java.io :as io]
   [helix.server.core :as core]
   [helix.server.impl.props :as props]
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


(defn- all
  [ds]
  (apply d/zip (doall ds)))


(defmacro ^:private dfor
  {:style/indent 1}
  [& body]
  `(all (for ~@body)))


(defn -render!
  [el]
  (let [>results (s/stream)
        *suspended (atom {})
        *suspense-counter (atom 0)
        result (binding [*suspended* *suspended
                         *suspense-counter* *suspense-counter]
                 (realize-elements el))]
    (s/put! >results [:root result])

    (d/loop [suspended @*suspended]
      (if (seq suspended)
        (let [*suspended2 (atom {})]
          (d/chain
           (dfor [[suspense-id [d el]] suspended]
             (d/chain
              d
              (fn [_]
                (binding [*suspended* *suspended2
                          *suspense-counter* *suspense-counter]
                  (realize-elements el)))
              (fn [next-el]
                ;; in the case of a waterfall within a suspense boundary, we
                ;; don't want to render the result until we're done suspending
                (if (contains? @*suspended2 suspense-id)
                  (d/success-deferred true)
                  ;; back pressure
                  (s/put! >results [suspense-id next-el])))))
           (fn [_]
             (d/recur @*suspended2))))
        (s/close! >results)))
    >results))


(declare put-el!)


(defn- put-children!
  "Calls put-el! on each child. Handles splitting sequential text nodes."
  [html children]
  (loop [prev (doto (first children)
                (->> (put-el! html)))
         cur (second children)
         children (rest (rest children))]
    (when (some? cur)
      (cond
        (or (instance? Element cur)
            (instance? Element prev))
        (put-el! html cur)

        ;; prev and cur is a text node
        :else (do (s/put! html "<!-- -->") ; split them using comment
                  (put-el! html cur)))
      (when (seq children)
        (recur cur (first children) (rest children))))))


(defn put-el!
  [stream el]
  (cond
    (instance? Element el)
    (cond
      (no-close-tag? (:type el))
      (s/put! stream (str "<" (:type el) (props/props->attrs (:props el)) " />"))

      (string? (:type el))
      (do (s/put! stream (str "<" (:type el) (props/props->attrs (:props el)) ">"))
          (put-children! stream (-> el :props :children))
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
          (put-children! stream (-> el :props :children))
          (when-not (-> el :props ::core/suspended?)
            (s/put! stream "<!--/$-->")))))

    (sequential? el)
    (put-children! stream el)

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
