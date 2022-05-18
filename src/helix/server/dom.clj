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


(defn realize-elements
  [el]
  (cond
    (and (instance? Element el)
         (or (string? (:type el)) ; DOM element type
             (= 'react/Fragment (:type el))))
    (update-in el [:props :children] #(doall (map realize-elements %)))

    (and (instance? Element el)
         (= 'react/Suspense (:type el)))
    (let [suspense-id (gensym)]
      (try
        (-> el
            (update-in [:props :children]
                       #(doall (map realize-elements %))))
        (catch clojure.lang.ExceptionInfo e
          (if-let [p (::core/deferred (ex-data e))]
            (-> el
                (assoc-in [:props ::core/fallback?] true))
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
            (s/put! stream (str "<template id=\"" (gensym) "\"></template>"))
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


(core/defnc item
  [{:keys [i]}]
  (throw (ex-info "dunno" {::core/deferred (d/success-deferred "foo")}))
  (if (zero? (mod i 10))
    ($d "div" "hello" i)
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
                (core/suspense
                 {:fallback "Loading.."}
                 ($d "div"
                     {:style {:display "flex"
                              :flex-direction "column-reverse"}}
                     (for [i (range 0 count)]
                       ;; every 10 wait 100ms
                       (core/$ item {:i i :key i})))))))))


(realize-elements (core/$ page))


(let [stream (s/stream)
      tree (realize-elements (core/$ page))]
  (put-el! stream tree)
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
    (let [stream (s/stream)]
      (s/put! stream "<!doctype html>")
      (d/future
        (->> (core/$ page)
             (realize-elements)
             (put-el! stream))
        (s/close! stream)
        (prn "closed"))
      {:status 200
       :headers {"content-type" "text/html"}
       :body stream}))

  (def server (http/start-server #'handler {:port 8080}))

  (.close server))

