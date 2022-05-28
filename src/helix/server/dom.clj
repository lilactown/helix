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


(def ^:dynamic *suspended*) ; atom


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
          (if-let [d (::core/deferred (ex-data e))]
            (do (swap! *suspended* assoc suspense-id [d el])
                (-> el
                    (assoc-in [:props ::core/fallback?] true)
                    (assoc-in [:props ::core/suspense-id] suspense-id)))
            (throw e)))))

    (instance? Element el)
    (realize-elements (core/-render (:type el) (:props el)))

    (sequential? el)
    (doall (map realize-elements el))

    :else (to-str el)))


(defn -render!
  [el]
  (let [>results (s/stream)
        suspended (atom {})
        result (binding [*suspended* suspended]
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
              (s/put! >results [suspense-id (binding [*suspended* suspended2]
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
            (s/put! stream (str "<template id=\""
                                (-> el :props ::core/suspense-id)
                                "\"></template>"))
            (put-el! stream (-> el :props :fallback))
            (s/put! stream "<!--/$-->"))
        (do
          #_(s/put! stream "<!--$-->")
          (doseq [child (-> el :props :children)]
            (put-el! stream child))
          #_(s/put! stream "<!--/$-->"))))

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
           (s/put! html (str "<div id=\"S:" suspense-id "\">"))
           (put-el! html el)
           (s/put! html (str "</div>"))
           (if-not @loaded-boundary-script?
             (do (reset! loaded-boundary-script? true)
                 (s/put! html (str "<script>" complete-boundary-function "; "
                                   "$RC(\"" suspense-id "\", \"S:" suspense-id "\")"
                                   "</script>")))
             (s/put! html (str "<script>"
                               "$RC(\"" suspense-id "\", \"S:" suspense-id "\")"
                               "</script>"))))))
     html)
    html))


;;
;; Example
;;

(def *cached? (atom #{}))


(core/defnc item [{:keys [i]}]
  (if (zero? (mod i 10))
    (do (when-not (get @*cached? i)
          (throw (ex-info "dunno" {::core/deferred (d/future
                                                     (Thread/sleep (* 100 i))
                                                     (swap! *cached? conj i))})))
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
                    (core/suspense
                     {:fallback "Loading all..."}
                     (for [i (range 0 count)]
                       #_(core/$ item {:i i :key i})
                       (core/suspense
                        {:fallback ($d "div" "Loading..")}
                        (core/$ item {:i i :key i}))))))))))



(defn handler [req]
  {:status 200
   :headers {"content-type" "text/html"}
   :body (render-to-stream (core/$ page {:count 40}))})


(comment
  (require '[aleph.http :as http])

  (def server (http/start-server #'handler {:port 9090}))

  (.close server)
  )

