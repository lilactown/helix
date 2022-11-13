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
