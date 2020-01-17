(ns helix.impl.analyzer-test
  (:require [helix.impl.analyzer :as hana]
            [clojure.test :as t]))

(t/deftest invalid-hooks-usage
  (t/testing "correct usage"
    (t/are [form] (nil? (hana/invalid-hooks-usage form))
      '(foo)
      '(use-foo)
      '(foo (bar (use-baz)))))
  (t/testing "conditionals"
    (t/testing "correct hooks usage in pred"
      (t/are [form] (nil? (hana/invalid-hooks-usage form))
        '(if (use-foo)
           (bar)
           (baz))
        '(if-let [foo (use-foo)]
           (bar)
           (baz))
        '(if-some [foo (use-foo)]
           (bar)
           (baz))
        '(when (bar (use-foo))
           (baz)
           (asdf))
        '(when-let [foo (use-foo)]
           (bar))
        '(when-some [foo (use-foo)]
           (bar))
        '(case (use-foo)
           :one (bar)
           two (baz)
           3 (asdf))
        '(cond-> (use-foo)
           true (bar)
           asdf (baz))
        '(cond-> (foo)
           (use-foo) (bar))
        '(and (use-foo) (bar))
        '(or (use-foo) (bar))
        '(condp = (use-foo)
           bar (baz))
        '(condp = asdf
           (use-foo) (baz))))
    (t/testing "incorrect hooks usage in branch"
      (t/are [form] (seq (hana/invalid-hooks-usage form))
        '(if (foo)
           (use-foo)
           (bar))
        '(if (foo)
           (bar)
           (use-foo))
        '(if (foo)
           (bar (use-foo))
           (baz))
        '(if-let [foo (foo)]
           (use-foo)
           (bar))
        '(if-some [foo (foo)]
           (use-foo)
           (bar))
        '(when (foo)
           (use-foo))
        '(when (foo)
           (bar (use-foo)))
        '(when-let [foo (foo)]
           (use-foo))
        '(when-some [foo (foo)]
           (use-foo))
        '(case (foo)
           :one (use-foo)
           two (bar)
           3 (baz))
        '(case (foo)
           :one (bar)
           two (use-baz)
           3 asdf)
        '(and foo (use-bar))
        '(or foo (use-bar))
        '(if (and (use-foo) baz)
           (use-foo)
           nil)
        '(if (and baz (use-foo))
           1 2)
        '(cond-> (foo)
           true (use-foo))
        '(cond-> (foo)
           true (bar)
           baz (use-foo))
        '(condp = asdf
           true (bar)
           (use-foo) (baz))
        '(condp #(use-foo %) asdf
            123 (baz))))))
