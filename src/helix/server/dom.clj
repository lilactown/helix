(ns helix.server.dom
  (:require
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


(defn style-str
  [styles]
  (reduce-kv
   (fn [s k v]
     (str s (if (string? k) k (name k)) ": " v ";"))
   ""
   styles))


#_(style-str {:color "red"})

#_(style-str {"color" "blue"})


(defn props->attrs
  [props]
  (reduce-kv
   (fn [attrs k v]
     (case k
       :style (str attrs " style=\"" (style-str v) "\"")
       attrs))
   ""
   props))


#_(props->attrs {:style {:color "red"}})


(def ^:dynamic *suspended-results*) ; stream


(defn realize-elements
  [el]
  (cond
    (and (instance? Element el)
         (or (string? (:type el)) ; DOM element type
             (= 'react/Fragment (:type el))))
    (update-in el [:props :children] #(doall (map realize-elements %)))

    (and (instance? Element el)
         (= 'react/Suspense (:type el)))
    (let [suspense-id (gensym "suspense")]
      (try
        (-> el
            (assoc-in [:props ::core/suspense-id] suspense-id)
            (update-in [:props :children]
                       #(doall (map realize-elements %))))
        (catch clojure.lang.ExceptionInfo e
          (if-let [p (::core/deferred (ex-data e))]
            (let [sr *suspended-results*] ; lexically bind just in case
              (d/on-realized p (fn [_] (s/put! sr [suspense-id el]))
                             identity)
              (-> el
                  (assoc-in [:props ::core/fallback?] true)
                  (assoc-in [:props ::core/suspense-id] suspense-id)))
            (throw e)))))

    (instance? Element el)
    (realize-elements (core/-render (:type el) (:props el)))

    (sequential? el)
    (doall (map realize-elements el))

    :else (to-str el)))


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
            (s/put! stream (str "<template id=\""
                                (-> el :props ::core/suspense-id)
                                "\"></template>"))
            (put-el! stream (-> el :props :fallback))
            (s/put! stream "<!--/$-->"))
        (do
          (s/put! stream "<!--$-->")
          (doseq [child (-> el :props :children)]
            (put-el! stream child))
          (s/put! stream "<!--/$-->"))))

    (sequential? el)
    (doseq [x el]
      (put-el! stream x))

    :else (s/put! stream (to-str el))))


(def *cached? (atom false))


(core/defnc item
  [{:keys [i]}]
  (if (zero? (mod i 10))
    (do (when-not @*cached?
          (throw (ex-info "dunno" {::core/deferred (d/future (reset! *cached? true))})))
        ($d "div" "hello" i))
    ($d "div" "hi" i)))


(core/defnc page [{:keys [count] :or {count 10}}]
  (let [color (get ["red" "green" "blue" "black"] (rand-int 3))]
    ($d "html"
        ($d "head"
            ($d "title" "Streaming test"))
        ($d "body"
            {:style {:color color}}
            ($d "div"
                ($d "div"
                    ($d "input"))
                ($d "div"
                    {:style {:display "flex"
                             :flex-direction "column-reverse"}}
                    (core/suspense
                     {:fallback "Loading..."}
                     (for [i (range 0 count)]
                       (core/$ item {:i i :key i})))))))))


@*cached?

(reset! *cached? false)

(let [stream (s/stream)
      suspended (s/stream)
      tree (binding [*suspended-results* suspended]
             (realize-elements (core/$ page)))]
  (put-el! stream tree)
  (s/consume
   (fn [[suspense-id el]]
     (binding [*suspended-results* (s/stream)] ; ignore for now
       (s/put! stream (str "<template id=\"S:" suspense-id "\">"))
       (put-el! stream (realize-elements el))
       (s/put! stream (str "</template>"))))
   suspended)
  (s/close! stream)
  (s/stream->seq stream))


(comment
  (require '[aleph.http :as http])

  (def stream (s/stream))

  (s/put! stream "hi\n")

  (dotimes [_ 1000]
    (s/put! stream "hi\n"))

  (s/close! stream)

  (defn handler [_]
    {:status 200
     :headers {"content-type" "text/plain"}
     :body stream})

  (defn handler [req]
    (let [stream (s/stream)]
      (s/put! stream "<!doctype html>")
      (d/future
        (binding [*suspended-results* (s/stream)]
          (->> (core/$ page)
               (realize-elements)
               (put-el! stream))
          (s/consume
           (fn [[suspense-id el]]
             (prn suspense-id)
             (binding [*suspended-results* (s/stream)] ; ignore for now
               (s/put! stream (str "<template id=\"S:" suspense-id "\">"))
               (put-el! stream (realize-elements el))
               (s/put! stream (str "</template>"))))
           *suspended-results*)
          (s/close! *suspended-results*)
          )
        (s/close! stream)
        (prn "closed"))
      {:status 200
       :headers {"content-type" "text/html"}
       :body stream}))

  (def server (http/start-server #'handler {:port 8080}))

  (.close server))

