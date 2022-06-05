(ns fixtures.kitchen-sink
  #?(:cljs
     (:require
      [helix.core :refer [defnc $]]
      [helix.dom :as dom]
      ["react-dom/server" :as rdom])
     :clj
     (:require
      [cljs.build.api :as b]
      [clojure.java.shell :as shell]
      [helix.server.core :refer [defnc $]]
      [helix.server.dom :as dom]
      [manifold.stream :as s]
      [clojure.string :as string])))


(defnc div-props
  [_]
  (dom/$d "div"
          {:style {:color "red"}
           :id "fuzzy"
           :class ["foo" "bar"]
           :for "baz"
           :accept-charset "utf8"
           :http-equiv "content-security-policy"
           :default-value 7
           :default-checked true
           :multiple true
           :muted true
           :selected true
           ;; can't haven children and dangerouslySetInnerHTML
           ;:children '("foo" "bar" "baz")
           :dangerously-set-inner-HTML #?(:clj {:__html "<div></div>"}
                                          :cljs #js {:__html "<div></div>"})
           ;; warning when using innerHtml
           ;:inner-html "<span></span>"
           :suppress-content-editable-warning true
           :suppress-hydration-warning true
           :content-editable true
           :spell-check false
           :draggable true}))


(def el ($ div-props))


#?(:clj
   (defn make []
     (b/build {:output-dir "out"
               :output-to "out/main.js"
               :optimizations :none
               :target :nodejs
               :main 'fixtures.kitchen-sink
               :npm-deps {:react "18.1.0"
                          :react-dom "18.1.0"}})
     [{:cljs (string/trim-newline (:out (shell/sh "node" "out/main.js")))
       :clj (->> el
                 (dom/render-to-stream)
                 (s/stream->seq)
                 (drop 1) ; doctype html
                 (string/join))}]))

#_(make)

(defn -main []
  #?(:clj (prn (make))
     :cljs (print (rdom/renderToString el))))

#?(:cljs (set! *main-cli-fn* -main))
