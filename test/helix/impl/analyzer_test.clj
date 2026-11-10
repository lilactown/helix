(ns helix.impl.analyzer-test
  (:require
   [cljs.analyzer :as ana]
   [cljs.tagged-literals :as tl]
   [clojure.string :as str]
   [clojure.test :as t]
   [helix.core]
   [helix.impl.analyzer :as hana]))

(defn- map-meta [body]
  (hana/map-forms-with-meta body helix.core/meta->form))

(t/deftest map-forms-with-meta
  (t/testing "rewrites let binding"
    (t/is (= '(let [x (helix.hooks/use-memo :auto-deps (+ 1 1))]
                x)
             (map-meta '(let [x ^:memo (+ 1 1)] x))))
    (t/is (= '(let [f (helix.hooks/use-callback
                        :auto-deps
                        (fn [ev] f))])
             (map-meta '(let [f ^:callback (fn [ev] f)])))))
  (t/testing "a map containing a ^:callback value stays a map"
    (let [[m] (map-meta '[{:on-click ^:callback (fn [] 1)}])]
      (t/is (map? m))
      (t/is (= '(helix.hooks/use-callback :auto-deps (fn [] 1))
               (:on-click m)))))
  (t/testing "props map passed to $ stays a map"
    (let [[[_ _ props]] (map-meta '[($ "button" {:on-click ^:callback (fn [] 1)})])]
      (t/is (map? props))))
  (t/testing "a map key can be rewritten"
    (let [[m] (map-meta '[{^:memo (foo) 1}])]
      (t/is (= {'(helix.hooks/use-memo :auto-deps (foo)) 1} m))))
  (t/testing "a set containing a ^:memo value stays a set"
    (let [[s] (map-meta '[#{^:memo (foo)}])]
      (t/is (set? s))
      (t/is (contains? s '(helix.hooks/use-memo :auto-deps (foo))))))
  (t/testing "a vector containing a ^:memo value stays a vector"
    (let [[v] (map-meta '[[^:memo (foo)]])]
      (t/is (vector? v))
      (t/is (= '[(helix.hooks/use-memo :auto-deps (foo))] v))))
  (t/testing "a list whose child was rewritten stays a list"
    (let [[l] (map-meta '[(let [a ^:memo (foo)] a)])]
      (t/is (list? l))))
  (t/testing "explicit deps are used"
    (t/is (= '[(helix.hooks/use-memo [a] (foo a))]
             (map-meta '[^{:memo [a]} (foo a)]))))
  (t/testing "forms nested inside of a rewritten form are rewritten"
    (t/is (= '[(helix.hooks/use-memo
                 :auto-deps
                 (foo (helix.hooks/use-callback :auto-deps (fn [] 1))))]
             (map-meta '[^:memo (foo ^:callback (fn [] 1))]))))
  (t/testing "other metadata is kept"
    (let [[form] (map-meta [(with-meta '(foo ^:memo (bar)) {:line 3})])]
      (t/is (= {:line 3} (meta form)))))
  (t/testing "forms without metadata are unchanged"
    (let [body '[(foo {:a [1 #{2}]}) () [] {} #{}]]
      (t/is (= body (map-meta body)))))
  (t/testing "bodies with more than 10k nodes don't throw"
    (let [body [(vec (range 20000))]]
      (t/is (= body (map-meta body))))))

(defn- printed-warning
  "Returns what the default handler prints for a helix warning when
  *cljs-warnings* is bound to `warnings`."
  [warnings]
  (let [w (java.io.StringWriter.)]
    (binding [*err* w
              ana/*cljs-warnings* warnings
              ana/*cljs-warning-handlers* [ana/default-warning-handler]]
      (hana/warn hana/warning-simple-body {} {:form '(foo)}))
    (str w)))

(t/deftest warnings-visible-with-default-handler
  (t/testing "printed by default"
    (t/is (str/includes? (printed-warning ana/*cljs-warnings*)
                         "Got a single symbol")))
  (t/testing "printed when *cljs-warnings* doesn't include helix's types"
    (t/is (str/includes? (printed-warning {:undeclared-var true})
                         "Got a single symbol")))
  (t/testing "silent inside no-warn, which only knows the cljs warning types"
    (t/is (= "" (printed-warning {:undeclared-var false}))))
  (t/testing "silent when the type is disabled"
    (t/is (= "" (printed-warning {:undeclared-var true
                                  hana/warning-simple-body false})))))

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
           (use-foo) (baz))
        '(when true
           '(use-foo))))
    (t/testing "incorrect hooks usage in branch"
      (t/are [form] (seq (hana/invalid-hooks-usage form))
        '(if (foo)
           (use-foo)
           (bar))
        '(if (foo)
           use-foo
           bar)
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
           123 (baz))
        '(when x
           [(hooks/use-foo)])
        '(when x
           {:a (hooks/use-foo)})
        '{:a (when x (hooks/use-foo))}
        '[(when x
            (hooks/use-foo))]
        '(when x
           #{(hooks/use-foo)})
        '#{(when x
             (hooks/use-foo))}))

    (t/testing "correct hooks usage in loop"
      (t/are [form] (nil? (hana/invalid-hooks-usage form))
        '(for [foo (use-foo)] foo)
        '(loop [foo (use-foo)] foo)
        '(doseq [foo (use-foo)] foo)
        '(dotimes [foo (use-foo)] foo)
        '(reduce foo (use-foo))
        '(reduce foo)
        '(reduce-kv foo (use-foo))
        '(map foo (use-foo))
        '(mapv foo (use-foo))
        '(filter foo (use-foo))
        '(filterv foo (use-foo))
        '(trampoline foo (use-foo))
        '(reductions foo (use-foo))
        '(partition-by foo (use-foo))
        '(group-by foo (use-foo))
        '(map-indexed foo (use-foo))
        '(keep foo (use-foo))
        '(mapcat foo (use-foo))
        '(run! foo (use-foo))
        '(keep-indexed foo (use-foo))
        '(remove foo (use-foo))
        '(some foo (use-foo))
        '(iterate foo (use-foo))
        '(tree-seq foo bar (use-foo))))
    (t/testing "incorrect hooks usage in loop"
      (t/are [form] (seq? (hana/invalid-hooks-usage form))
        '(for [foo bar] (use-foo))
        '(loop [foo bar] (use-foo))
        '(doseq [foo bar] (use-foo))
        '(dotimes [foo bar] (use-foo))
        '(reduce #(use-foo) foo)
        '(reduce #(use-foo))
        '(reduce use-foo foo)
        '(reduce-kv #(use-foo) foo)
        '(map #(use-foo) foo)
        '(mapv #(use-foo) foo)
        '(filter #(use-foo) foo)
        '(filterv #(use-foo) foo)
        '(trampoline #(use-foo) foo)
        '(reductions #(use-foo) foo)
        '(partition-by #(use-foo) foo)
        '(group-by #(use-foo) foo)
        '(map-indexed #(use-foo) foo)
        '(keep #(use-foo) foo)
        '(mapcat #(use-foo) foo)
        '(run! #(use-foo) foo)
        '(keep-indexed #(use-foo) foo)
        '(remove #(use-foo) foo)
        '(some #(use-foo) foo)
        '(iterate #(use-foo) foo)
        '(lazy-seq (use-foo))
        '(tree-seq use-foo bar foo)
        '(tree-seq foo use-bar foo)
        '(tree-seq #(use-foo) bar foo)
        '(tree-seq foo #(use-bar) foo)))))

(t/deftest hook-symbol-check
  (t/testing "hook? true"
    (t/are [s] (hana/hook? s)
      'use-foo
      'foo/use-bar
      'useFoo
      'foo/useBar))
  (t/testing "hook? false"
    (t/are [s] (not (hana/hook? s))
      'foo
      'foo/bar
      'use
      'user
      'foo/use
      'foo/user)))

(t/deftest hook-expr-on-non-list-seqs
  (t/testing "hook-expr? recognizes syntax-quoted / generated seqs"
    (t/is (hana/hook-expr? `(helix.hooks/use-memo [] 1)))
    (t/is (hana/hook-expr? (lazy-seq (list 'use-foo))))
    (t/is (hana/hook-expr? (cons 'use-foo ()))))
  (t/testing "vectors and maps aren't hook expressions"
    (t/is (not (hana/hook-expr? '[use-foo])))
    (t/is (not (hana/hook-expr? '{use-foo 1}))))
  (t/testing "invalid-hooks-usage reports generated hooks in a conditional"
    (t/is (seq (hana/invalid-hooks-usage
                 (list 'when 'x (lazy-seq (list 'use-foo)))))))
  (t/testing "^:memo inside a conditional is reported after metadata expansion"
    (t/is (seq (->> (map-meta '[(when x ^:memo (foo))])
                    (map hana/invalid-hooks-usage)
                    (flatten)
                    (remove nil?)))))
  (t/testing "find-hooks includes generated hooks"
    (t/is (= 1 (count (hana/find-hooks [`(helix.hooks/use-memo [] 1)])))))
  (t/testing "find-hooks ignores generated quote forms"
    (t/is (= [] (hana/find-hooks [(cons 'quote (list '(use-foo)))])))))

(t/deftest resolve-local-vars
  (let [env {:locals {'a {:name 'a} 'b {:name 'b}}}
        resolved #(set (hana/resolve-local-vars env %))]
    (t/testing "only symbols bound as locals are found"
      (t/is (= #{'a 'b} (resolved '(foo a [b c])))))
    (t/testing "locals inside #js [] are found"
      (t/is (= #{'a 'b} (resolved (list (tl/->JSValue ['a]) 'b)))))
    (t/testing "locals inside #js {} are found"
      (t/is (= #{'a} (resolved (list (tl/->JSValue {:x 'a}))))))
    (t/testing "locals inside nested #js literals are found"
      (t/is (= #{'a 'b}
               (resolved (list (tl/->JSValue {:x (tl/->JSValue ['a 'b])}))))))))

(t/deftest find-hooks
  (t/are [hooks body] (= hooks (hana/find-hooks body))
         [] '(do '(use-foo))
         '[(use-foo)] '(use-foo)
         '[(use-foo)] '{:foo (use-foo)}
         '[(use-foo)] '{:foo #{use-bar (use-foo)}}
         '[] '('use-foo bar)
         '[(use-callback (fn* [p1__#] (identity p1__#)))] '(use-callback (fn* [p1__20354#] (identity p1__20354#))))
  (t/testing "normalises bindings"
    (t/are [form1 form2]
           (= (hana/find-hooks (list 'use-callback form1))
              (hana/find-hooks (list 'use-callback form2)))
           '(fn* [p1__20354#] (identity p1__20354#))
           '(fn* [p1__20364#] (identity p1__20364#))

           '(fn* [p1__20356# & rest__20357#] (identity p1__20356# rest__20357#))
           '(fn* [p1__20366# & rest__20367#] (identity p1__20366# rest__20367#))

           '(fn* [p1__20359# p2__20360# p3__20361# & rest__20362#]
                 (identity p1__20359# p2__20360# p3__20361# rest__20362#))
           '(fn* [p1__20369# p2__20370# p3__20371# & rest__20372#]
                 (identity p1__20369# p2__20370# p3__20371# rest__20372#)))))
