(ns helix.demo.ssr
  (:require
   #?@(:clj [[aleph.http :as http]
             [helix.server.core :as hx :refer [defnc $ <> suspense]]
             [helix.server.dom-test :as dom :refer [$d]]
             [helix.server.hooks :as hooks]
             [manifold.deferred :as d]
             [manifold.stream :as s]
             [reitit.ring :as ring]]
       :cljs [[helix.core :as hx :refer [defnc $ <> suspense]]
              [helix.dom :as dom :refer [$d]]
              [helix.hooks :as hooks]
              ["react-dom/client" :as rdom]]))
  ;; makes shadow-cljs reload our clj ns
  #?(:cljs (:require-macros [helix.demo.ssr])))


(def *cached? (atom #{}))


(defn fetch! [i]
  (when-not (get @*cached? i)
    (throw
     #?(:clj (ex-info
              "dunno"
              {::hx/deferred (d/future
                               (Thread/sleep (* 100 i))
                               (swap! *cached? conj i))})
        :cljs (js/Promise.
               (fn [res _]
                 (prn :suspending)
                 (js/setTimeout
                  (fn []
                    (swap! *cached? conj i)
                    (res))
                  (* 100 i))))))))


(defnc counter [_]
  (let [[counter set-counter] (hooks/use-state 0)]
    (<> ($d "button" {:on-click #(set-counter inc)} "+") counter)))


(defnc item [{:keys [i]}]
  (if (zero? (mod i 10))
    (do (fetch! i)
        ($d "div"
            "hello" i
            ($ counter)))
    ($d "div" "hi" i)))


(defnc app [{:keys [count] :or {count 10}}]
  ($d "div"
      ($ counter)
      (suspense
       {:fallback ($d "div" "Loading all....")}
       (for [i (range 0 count)]
         #_($ item {:i i :key i})
         (suspense
          {:fallback ($d "div" "Loading..")
           :key i}
          ($ item {:i i}))))))


(defnc page [_]
  ($d "html"
      ($d "head"
          ($d "title" "Streaming test"))
      ($d "body" {:style {:color "blue"}}
          ($d "div" {:id "app"}
              ($ app {:count 30}))
          #?(:clj "<script src=\"/assets/js/main.js\"></script>"
             :cljs ($d "script" {:src "/assets/js/main.js"})))))

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

