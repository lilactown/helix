(ns helix.demo.ssr
  (:require
   #?@(:clj [[aleph.http :as http]
             [helix.server.core :as hx :refer [defnc $ suspense]]
             [helix.server.dom :as dom :refer [$d]]
             [manifold.deferred :as md]
             [manifold.stream :as s]
             [reitit.ring :as ring]]
       :cljs [[helix.core :as hx :refer [defnc $ suspense]]
              [helix.dom :as d :refer [$d]]
              ["react-dom" :as react-dom]]))
  ;; makes shadow-cljs reload our clj ns
  #?(:cljs (:require-macros [helix.demo.ssr])))


(def *cached? (atom #{}))

(defn fetch!
  [i]
  (when-not (get @*cached? i)
    (throw
     (ex-info
      "dunno"
      {::hx/deferred #?(:clj (md/future
                               (Thread/sleep (* 100 i))
                               (swap! *cached? conj i))
                        :cljs nil)}))))


(defnc item [{:keys [i]}]
  (if (zero? (mod i 10))
    (do (fetch! i)
        ($d "div" "hello" i))
    ($d "div" "hi" i)))


(defnc page [{:keys [count] :or {count 10}}]
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
                    (suspense
                     {:fallback "Loading all..."}
                     (for [i (range 0 count)]
                       #_(core/$ item {:i i :key i})
                       (suspense
                        {:fallback ($d "div" "Loading..")}
                        ($ item {:i i :key i}))))))))))



#?(:clj
   (defn page-handler [req]
     {:status 200
      :headers {"content-type" "text/html"}
      :body (doto (dom/render-to-stream ($ page {:count 40}))
              (s/on-closed #(reset! *cached? #{})))}))


#?(:clj
   (def router
     (ring/router
      [["/" {:get page-handler}]
       ["/assets/*" (ring/create-resource-handler)]])))



(comment
  #?(:clj (def server (http/start-server
                       (ring/ring-handler router)
                       {:port 9090})))

  #?(:clj (.close server)))

