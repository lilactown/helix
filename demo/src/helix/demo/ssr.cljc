(ns helix.demo.ssr
  (:require
   #?@(:clj [[aleph.http :as http]
             [helix.server.core :as hx :refer [defnc $ <> suspense]]
             [helix.server.dom :as dom :refer [$d]]
             [manifold.deferred :as md]
             [manifold.stream :as s]
             [reitit.ring :as ring]]
       :cljs [[helix.core :as hx :refer [defnc $ <> suspense]]
              [helix.dom :as dom :refer [$d]]
              ["react-dom/client" :as rdom]]))
  ;; makes shadow-cljs reload our clj ns
  #?(:cljs (:require-macros [helix.demo.ssr])))


(def *cached? (atom #{}))

(defn fetch!
  [i]
  #?(:clj (when-not (get @*cached? i)
            (throw
             (ex-info
              "dunno"
              {::hx/deferred #?(:clj (md/future
                                       (Thread/sleep (* 100 i))
                                       (swap! *cached? conj i))
                                :cljs nil)})))))


(defnc item [{:keys [i]}]
  (if (zero? (mod i 10))
    (do #_(fetch! i)
        ($d "div" (str "hello" i)))
    ($d "div" (str "hi" i))))


(defnc app [{:keys [count] :or {count 10}}]
  ($d "div"
      "ooo"
      (for [i (range 0 count)]
        #_($ item {:i i :key i})
        (suspense
         {:fallback ($d "div" "Loading..")
          :key i}
         ($ item {:i i})
         #_"hi"))))


(defnc page [{:keys [count] :or {count 10}}]
  (let [color "blue"]
    ($d "html"
        ($d "head"
            ($d "title" "Streaming test"))
        ($d "body"
            {:style {:color color}}
            ($d "div" {:id "app"} ($ app {:count count}))
            #?(:clj "<script src=\"/assets/js/main.js\"></script>"
               :cljs ($d "script" {:src "/assets/js/main.js"}))))))

(def client-root)

#?(:cljs
   (defn ^:dev/after-load render!
     []
     (.render client-root ($ page))
     #_(.render client-root ($ app))))

#?(:cljs
   (defn start-client!
     []
     (set! client-root (rdom/hydrateRoot js/document
                                           ($ page)))
     #_(set! client-root (rdom/createRoot (js/document.getElementById "app")))
     #_(render!)))



#?(:clj
   (defn page-handler [req]
     {:status 200
      :headers {"content-type" "text/html"}
      :body (doto (dom/render-to-stream ($ page))
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

