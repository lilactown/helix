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
           ;:default-value 7
           ;:default-checked true
           :checked true
           :value 7
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
           :draggable true
           :allow-fullScreen true
           :async true
           :auto-focus true
           :auto-play true
           :controls true
           :default true
           :defer true
           :disabled true
           :disable-picture-in-picture true
           :disable-remote-playback true
           :form-no-validate true
           :hidden true
           :loop true
           :no-module true
           :no-validate true
           :open true
           :plays-inline true
           :read-only true
           :required true
           :reversed true
           :scoped true
           :seamless true
           :item-scope true
           :cols 5
           :rows 5
           :size 5
           :span 5
           :row-span 5
           :start 5
           :capture true
           :download "asdf"}))


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
     {:cljs (string/trim-newline (:out (shell/sh "node" "out/main.js")))
      :clj (dom/render-to-string el)}))

#_(make)

#_(let [{:keys [cljs clj]} (make)]
  (= (.length cljs) (.length clj)))

(defn -main []
  #?(:clj (prn (make))
     :cljs (print (rdom/renderToString el))))

#?(:cljs (set! *main-cli-fn* -main))
