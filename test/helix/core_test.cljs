(ns helix.core-test
  (:require
    [cljs.test :as t :include-macros true]
    [goog.object :as gobj]
    [helix.core :as helix :refer (defnc $)]))


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
