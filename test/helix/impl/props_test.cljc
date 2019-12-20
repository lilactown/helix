(ns helix.impl.props-test
  (:require [helix.impl.props :as impl]
            #?@(:clj [[clojure.test :as t]]
                :cljs [[cljs.test :as t :include-macros true]
                       [goog.object :as gobj]])))

(defn eq
  "Deep equality (works for JS objects in CLJS)"
  [o1 o2]
  #?(:clj (= o1 o2)
     :cljs
     (cond
       (not= (goog/typeOf o1)
             (goog/typeOf o2)) false
       ;; scalars
       (not (#{"array" "object"} (goog/typeOf o1))) (= o1 o2)
       ;; cljs data
       (= o1 o2) true
       ;; js array
       (js/Array.isArray o1) (and (= (count o1) (count o2))
                                  (. o1 every #(eq %1 (aget o2 %2))))
       ;; js obj
       :else (let [ks1 (js/Object.keys o1)
                   ks2 (js/Object.keys o2)]
               (and
                 (= (count ks1) (count ks2))
                 (loop [ks ks1]
                   (cond
                     (empty? ks) true
                     (let [k (first ks)
                           v1 (gobj/get o1 k)
                           v2 (gobj/get o2 k)]
                       (eq v1 v2)) (recur (rest ks))
                     :else false)))))))

#?(:cljs (t/deftest eq-test
           (t/are [x y] (eq x y)
             "1" "1"
             1 1
             #js [] #js []
             #js {} #js {}
             #js {:a 1} #js {:a 1}
             #js {:a #js {:b 'c}} #js {:a #js {:b 'c}})))

(t/deftest -native-props
  (t/testing "Literal styles"
    (let [handler #?(:clj '(fn [])
                     :cljs (fn []))]
      (t/is (eq (impl/-native-props {:foo-bar "baz" :style {:color "blue"} :on-click handler})
                #?(:clj `(cljs.core/js-obj "fooBar" "baz"
                                           "style" (cljs.core/js-obj "color" (helix.impl.props/->js "blue"))
                                           "onClick" ~handler)
                   :cljs #js {:fooBar "baz", :style #js {:color "blue"}, :onClick handler}))
            "Native props with nested literal style"))
    (t/is (eq (impl/-native-props {:style [{:color "blue"} {:asdf 'jkl}]})
              #?(:clj `(cljs.core/js-obj
                        "style" (cljs.core/array
                                 (cljs.core/js-obj "color" (helix.impl.props/->js "blue"))
                                 (cljs.core/js-obj "asdf" (helix.impl.props/->js ~'jkl))))
                 :cljs #js {:style #js [#js {:color "blue"} #js {:asdf "jkl"}]}))
          "Native props with nested literal vector style"))
  #?(:clj (t/testing "Spread props"
            (t/is (eq (impl/-native-props '{:a 1 :b 2 & foo})
                      `(impl/merge-obj (cljs.core/js-obj "a" 1 "b" 2)
                                       (impl/-native-props ~'foo)))))))

#?(:cljs
   (t/deftest native-props
     (t/is (eq (impl/native-props {:foo-bar "baz"})
               #js {:fooBar "baz"}))
     (t/is (eq (impl/native-props {:foo-bar "baz" :style {:color "blue" :background-color "red" :display :flex}})
               #js {:fooBar "baz" :style #js {:color "blue" :backgroundColor "red" :display "flex"}})
           "literal styles")
     (t/is (eq (impl/native-props {:foo-bar "baz"
                                   :style [{:color "blue"}
                                           {:background-color "red"}
                                           {:display :flex}]})
               #js {:fooBar "baz"
                    :style #js [#js {:color "blue"}
                                #js {:backgroundColor "red"}
                                #js {:display "flex"}]}))
     (t/is (eq (let [extra-props {:foo-bar :extra-foo-bar
                                  :b :extra-b}]
                 (impl/native-props {:foo-bar :a :b :b :c :c :d :d & extra-props}))
               #js {:fooBar :extra-foo-bar :b :extra-b :c :c :d :d}))
     (t/is (eq (let [dynamic-style {:color "blue"}]
                 (impl/native-props {:style dynamic-style}))
               #js {:style #js {:color "blue"}}))))


#?(:cljs
   (t/deftest props
     (t/is (eq (impl/props {:foo-bar "baz"})
               #js {:foo-bar "baz"}))
     (t/is (eq (impl/props {:foo-bar "baz" :style {:color "blue" :background-color "red" :display :flex}})
               #js {:foo-bar "baz" :style {:color "blue" :background-color "red" :display :flex}})
           "doesn't recurse into style")
     (t/is (eq (impl/props {:foo-bar "baz"
                            :style [{:color "blue"}
                                    {:background-color "red"}
                                    {:display :flex}]})
               #js {:foo-bar "baz"
                    :style [{:color "blue"}
                            {:background-color "red"}
                            {:display :flex}]})
           "doesn't recurse into vector style")
     (t/is (eq (let [extra-props {:foo-bar :extra-foo-bar
                                  :b :extra-b}]
                 (impl/props {:foo-bar :a :b :b :c :c :d :d & extra-props}))
               #js {:foo-bar :extra-foo-bar :b :extra-b :c :c :d :d}))))
