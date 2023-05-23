(ns helix.core-test
  (:require
    [cljs.test :as t :include-macros true]
    [goog.object :as gobj]
    [helix.core :as helix :refer (defnc defnc- $)]))


(t/deftest metadata-optimization-expansion
  (t/is (->> (macroexpand
              '(helix/defnc metadata-optimization
                 []
                 {:helix/features {:metadata-optimizations true}}
                 (let [foo "foo"
                       bar ^{:memo [foo]} (str foo "bar")]
                   ^:memo ($ "div" "asdf"))))
             (tree-seq #(and (seqable? %) (not (string? %))) seq)
             ;; this take is here because I accidentally blew this up too many
             ;; times with an infinite loop
             (take 100)
             (some #(= % '(helix.hooks/use-memo [foo] (str foo "bar")))))))


(defnc factory-component
  [{:keys [foo bar]}]
  {:helix/features {:define-factory true}}
  (str "foo: " foo " bar: " bar))

(t/deftest cljs-factory
  (let [props {:foo "asdf" :bar "jkl"}
        el (factory-component props)]
    (t/is (= (helix/type factory-component) (gobj/get el "type")))
    (t/is (= props (helix/extract-cljs-props (gobj/get el "props")))))
  (t/testing "key and ref"
    (let [ref #js {:current nil}
          key "foo"
          props {:foo "asdf" :bar "jkl" :key key :ref ref}
          el (factory-component props)]
      (t/is (= ref (gobj/get el "ref")))
      (t/is (= key (gobj/get el "key")))
      (t/is (= (dissoc props :key :ref)
               (helix/extract-cljs-props (gobj/get el "props")))))))


(defn- tree-contains?
  [tree x]
  (->> tree
       (tree-seq coll? seq)
       (some #{x})))


(t/deftest wrap
  (let [o (macroexpand '(defnc comp
                          [props]
                          {:wrap [(helix/memo)]}
                          "hi"))]
    (t/is (tree-contains? o 'helix/memo)))
  (let [o (macroexpand '(defnc comp
                          {:wrap [(helix/memo)]}
                          [props]
                          "hi"))]
    (t/is (tree-contains? o 'helix/memo))))


(defnc- private-comp
  {:foo :bar}
  []
  "can't see me outside of this namespace!")

(t/deftest private-component-definition
  (let [metadata (meta #'private-comp)]
    (t/is (:private metadata))
    (t/is (= (:foo metadata) :bar))))

(t/deftest ref-test
  (let [ref (helix/create-ref 4649)]
    (t/testing "ref"
      (t/testing "can be initialized with value"
        (t/is (= 4649 (.-current ref))))
      (t/testing "can be used with IDeref"
        (t/is (= 4649 @ref)))
      (t/testing "can be used with ISwap"
        (swap! ref (constantly 0)) ; 0
        (t/is (zero? @ref))
        (swap! ref + 1)            ; (+ 0 1)
        (t/is (= 1 @ref))
        (swap! ref + 1 2)          ; (+ 0 1 1 2)
        (t/is (= 4 @ref))
        (swap! ref + 1 2 3)        ; (+ 0 1 1 2 1 2 3)
        (t/is (= 10 @ref)))
      (t/testing "can be used with IReset"
        (reset! ref "well done")
        (t/is (= "well done" @ref))))))

(t/deftest jsx-test
  (t/testing "jsx transform"
    ;; In this test, you will see comments of the equivalent JSX. You can
    ;; compare the output of the JSX transform by copy-pasting the provided JSX
    ;; and running it through @babel/plugin-transform-react-jsx
    (t/testing "with no props or children"
      (let [component-1 (macroexpand '($ :div))          ; <div></div>
            component-2 (macroexpand '($ "div"))
            component-3 (macroexpand '($ "div" nil))
            component-4 (macroexpand '($ "div" nil nil)) ; <div>{null}</div>
            expected-1 '(helix.core/jsx "div" {})
            expected-2 '(helix.core/jsx "div" {"children" nil})]
        (t/are [x y] (= x (js->clj y))
          expected-1 component-1
          expected-1 component-2
          expected-1 component-3
          ;; This ensures we are matching the behavior of react/createElement,
          ;; which is also reflected in the modern JSX transform.
          expected-2 component-4)))
    (t/testing "with no props and a single child"
      (let [component-1 (macroexpand '($ :div "Hello"))      ; <div>Hello</div>
            component-2 (macroexpand '($ "div" nil "Hello"))
            component-3 (macroexpand '($ "div" "Hello"))
            expected '(helix.core/jsx "div" {"children" "Hello"})]
        (t/are [x] (= expected (js->clj x))
          component-1
          component-2
          component-3)))
    (t/testing "with props and children"
      (let [component (macroexpand
                       ;; <Stack direction={row}>
                       ;;   <Item />
                       ;;   <Item />
                       ;; </Stack>
                       '($ Stack {:direction "row"}
                           ($ Item)
                           ($ Item)))
            ;; Ideally, we'd check the full macroexpansion, but this suffices.
            expected '(helix.core/jsxs Stack
                                       (helix.impl.props/props {:direction "row"}
                                                               [($ Item)
                                                                ($ Item)]))]
        (t/is (= expected (js->clj component)))))
    (t/testing "provides keys as expected"
      (let [props {:key "key"}
            component (macroexpand
                       ;; <div key={kee} {...props}></div>
                       '($ :div {:key "kee" :& props}))
            expected '(helix.core/jsx "div"
                                      (helix.impl.props/dom-props {:key "kee"
                                                                   :& props}
                                                                  nil)
                                      "kee")
            element ($ :div {:key "kee" :& props})]
        (t/is (= expected (js->clj component)))
        (t/is (= "key" (gobj/get element "key")))))))
