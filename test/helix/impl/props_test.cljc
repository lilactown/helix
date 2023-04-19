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

(t/deftest -dom-props
  (t/testing "Literal styles"
    (let [handler #?(:clj '(fn [])
                     :cljs (fn []))]
      (t/is (eq (impl/-dom-props {:foo-bar "baz"
                                  "baz-qux" 42
                                  :style {:color "blue"
                                          :--custom-property "1px"}
                                  :on-click handler})
                #?(:clj `(cljs.core/js-obj
                          "fooBar" "baz"
                          "baz-qux" 42
                          "style" (cljs.core/js-obj
                                   "color" (helix.impl.props/->js "blue")
                                   "--custom-property" (helix.impl.props/->js "1px"))
                          "onClick" ~handler)
                   :cljs #js {:fooBar "baz"
                              :baz-qux 42
                              :style #js {:color "blue"
                                          :--custom-property "1px"}
                              :onClick handler}))
            "Dom props with nested literal style"))
    (t/is (eq (impl/-dom-props {:style [{:color "blue"} {:asdf 'jkl} {:--custom-property "1px"}]})
              #?(:clj `(cljs.core/js-obj
                        "style" (cljs.core/array
                                 (cljs.core/js-obj
                                  "color" (helix.impl.props/->js "blue"))
                                 (cljs.core/js-obj
                                  "asdf" (helix.impl.props/->js ~'jkl))
                                 (cljs.core/js-obj
                                  "--custom-property" (helix.impl.props/->js "1px"))))
                 :cljs #js {:style #js [#js {:color "blue"} #js {:asdf "jkl"}
                                        #js {:--custom-property "1px"}]}))
          "Dom props with nested literal vector style"))
  #?(:cljs (t/testing "JS object"
             (t/is (let [obj #js {:a 1 :b 2 :fooBar #js {:baz "jkl"}}]
                     (eq (impl/-dom-props obj)
                         obj)))))
  #?(:clj (t/testing "Spread props"
            (t/is (eq (impl/-dom-props '{:a 1 :b 2 "c-d" 3 & foo})
                      `(impl/merge-obj (cljs.core/js-obj "a" 1 "b" 2 "c-d" 3)
                                       (impl/-dom-props ~'foo)))))))

#?(:cljs
   (t/deftest dom-props
     (t/is (eq (impl/dom-props {:foo-bar "baz"
                                :foo-bar-baz "asdf"
                                "foo-bar-baz" "jkl"})
               #js {:fooBar "baz" :fooBarBaz "asdf" :foo-bar-baz "jkl"}))
     (t/is (eq (impl/dom-props {:foo-bar "baz"
                                :style {:color "blue"
                                        :background-color "red"
                                        :display :flex
                                        :--custom-property "1px"}})
               #js {:fooBar "baz"
                    :style #js {:color "blue"
                                :backgroundColor "red"
                                :display "flex"
                                :--custom-property "1px"}})
           "literal styles")
     (t/is (eq (impl/dom-props {:foo-bar "baz"
                                :style [{:color "blue"}
                                        {:background-color "red"}
                                        {:display :flex}
                                        {:--custom-property "1px"}]})
               #js {:fooBar "baz"
                    :style #js [#js {:color "blue"}
                                #js {:backgroundColor "red"}
                                #js {:display "flex"}
                                #js {:--custom-property "1px"}]}))
     (t/is (eq (let [extra-props {:foo-bar :extra-foo-bar
                                  :b :extra-b
                                  "baz-qux" 42}]
                 (impl/dom-props {:foo-bar :a :b :b :c :c :d :d & extra-props}))
               #js {:fooBar :extra-foo-bar
                    :b :extra-b
                    "baz-qux" 42
                    :c :c :d :d}))
     (t/is (eq (let [dynamic-style {:background-color "blue"
                                    :--custom-property "1px"}]
                 (impl/dom-props {:style dynamic-style}))
               #js {:style #js {:backgroundColor "blue"
                                :--custom-property "1px"}}))
     (t/is (eq (impl/dom-props {:style #js {:backgroundColor "blue"
                                               :--custom-property "1px"}})
               #js {:style #js {:backgroundColor "blue"
                                :--custom-property "1px"}}))
     (t/is (eq (let [dynamic-js-style #js {:backgroundColor "blue"
                                           :--custom-property "1px"}]
                 (impl/dom-props {:style dynamic-js-style}))
               #js {:style #js {:backgroundColor "blue"
                                :--custom-property "1px"}}))
     (t/is (eq (impl/dom-props {:foo "bar"
                                   & #js {:baz "asdf"}})
               #js {:foo "bar" :baz "asdf"}))

     (t/is (eq (impl/dom-props {:foo "bar"
                                   & nil})
               #js {:foo "bar"}))

     (t/is (eq (impl/dom-props {:style ["bar"]})
               #js {:style #js ["bar"]}))))


#?(:cljs
   (t/deftest props
     (t/is (eq (impl/props {:foo-bar "baz"
                            "baz-qux" 42})
               #js {:foo-bar "baz"
                    "baz-qux" 42}))
     (t/is (eq (impl/props {:foo-bar "baz"
                            :style {:color "blue"
                                    :background-color "red"
                                    :display :flex
                                    :--custom-property "1px"}})
               #js {:foo-bar "baz" :style {:color "blue"
                                           :background-color "red"
                                           :display :flex
                                           :--custom-property "1px"}})
           "doesn't recurse into style")
     (t/is (eq (impl/props {:foo-bar "baz"
                            :style [{:color "blue"}
                                    {:background-color "red"}
                                    {:display :flex}
                                    {:--custom-property "1px"}]})
               #js {:foo-bar "baz"
                    :style [{:color "blue"}
                            {:background-color "red"}
                            {:display :flex}
                            {:--custom-property "1px"}]})
           "doesn't recurse into vector style")
     (t/is (eq (let [extra-props {:foo-bar :extra-foo-bar
                                  :b :extra-b}]
                 (impl/props {:foo-bar :a :b :b :c :c :d :d & extra-props}))
               #js {:foo-bar :extra-foo-bar :b :extra-b :c :c :d :d}))
     (t/is (eq (impl/props {:foo "bar"
                                   & #js {:baz "asdf"}})
               #js {:foo "bar" :baz "asdf"}))

     (t/is (eq (impl/props {:foo "bar"
                                   & nil})
               #js {:foo "bar"}))))


(t/deftest test-normalize-class
  #?(:clj
     (do
       (t/testing "macro expansion - string value shall be kept as is"
         (t/is (= "foo"
                  (impl/normalize-class "foo"))))
       (t/testing "macro expansion - quoted forms shall be converted to string"
         (t/is (= "foo bar"
                  (impl/normalize-class (quote '[foo bar]))))
         (t/is (= "bar"
                  (impl/normalize-class (quote 'bar)))))
       (t/testing "macro expansion - other value shall be passed to runtime check"
         (t/is (= '(helix.impl.props/normalize-class foo)
                  (impl/normalize-class 'foo)))
         (t/is (= '(helix.impl.props/normalize-class [foo bar])
                  (impl/normalize-class '[foo bar])))
         (t/is (= '(helix.impl.props/normalize-class (vector foo bar))
                  (impl/normalize-class '(vector foo bar)))))))
  #?(:cljs
     (do (t/testing "runtime - all shall be converted to string"
           (t/is (= (impl/normalize-class 'foo)
                    "foo"))
           (t/is (= (impl/normalize-class '[foo bar])
                    "foo bar")))
         (t/testing "runtime - nil shall be filtered out"
           (t/is (= (impl/normalize-class ["foo" nil])
                    "foo"))))))
