(ns helix.server.dom
  (:require
   [clojure.string :as string]
   [helix.server.core :as core]
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


(defn put-el!
  [stream el]
  (if (instance? Element el)
    (if (string? (:type el))
     (do (s/put! stream (str "<" (:type el) ">"))
         (doseq [child (-> el :props :children)]
           (put-el! stream child))
         (s/put! stream (str "</" (:type el) ">")))
     (put-el! stream (core/-render (:type el) (:props el))))
    (s/put! stream (to-str el))))


(let [stream (s/stream)]
  (put-el! stream  ($d "div" ($d "span" "hello")))
  (s/close! stream)
  (s/stream->seq stream))



(comment
  (require '[aleph.http :as http])

  (def stream (s/stream))

  (s/put! stream "hi\n")

  (dotimes [_ 100]
    (s/put! stream "hi\n"))

  (s/close! stream)

  (defn handler [req]
    {:status 200
     :headers {"content-type" "text/plain"}
     :body stream})

  (defn streaming-numbers-handler
    [{:keys [params]}]
    (let [cnt (Integer/parseInt (get params "count" "1000"))]
      {:status 200
       :headers {"content-type" "text/plain"}
       :body (let [sent (atom 0)]
               (->> (s/periodically 100 #(str (swap! sent inc) "\n"))
                    (s/transform (take cnt))))}))

  (def server (http/start-server streaming-numbers-handler {:port 8080}))

  (def server (http/start-server handler {:port 8080}))

  (.close server))

