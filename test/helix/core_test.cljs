(ns helix.core-test
  (:require
    [cljs.test :as t :include-macros true]
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
