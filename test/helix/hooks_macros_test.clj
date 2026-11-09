(ns helix.hooks-macros-test
  "Tests for the expansion of the macros in helix.hooks."
  (:require
   [cljs.analyzer :as ana]
   [cljs.core] ; the analyzer looks up the macros in this namespace
   [cljs.env :as env]
   [clojure.test :as t]
   [helix.hooks]
   [helix.impl.analyzer :as hana]))

(defn- expand
  "Returns the expansion of `form` and the types of warnings it produced."
  [form]
  (let [warnings (atom [])
        expansion (binding [ana/*cljs-warning-handlers*
                            ;; like the default handler, ignore disabled types
                            [(fn [warning-type _env _extra]
                               (when (warning-type ana/*cljs-warnings*)
                                 (swap! warnings conj warning-type)))]]
                    ;; use-memo infers its type, which needs a compiler env
                    (env/ensure
                      (apply @(resolve (first form)) form (ana/empty-env)
                             (rest form))))]
    [expansion @warnings]))

(t/deftest simple-body
  (t/testing "use-memo accepts a symbol as its body"
    (let [[expansion warnings] (expand '(helix.hooks/use-memo :once initial))]
      (t/is (= 'helix.hooks/raw-use-memo (first expansion)))
      (t/is (= '(clojure.core/fn [] initial) (second expansion)))
      (t/is (empty? warnings))))
  (t/testing "use-imperative-handle accepts a symbol as its body"
    (let [[expansion warnings] (expand '(helix.hooks/use-imperative-handle
                                          my-ref [] handle))]
      (t/is (= 'helix.hooks/raw-use-imperative-handle (first expansion)))
      (t/is (= 'my-ref (second expansion)))
      (t/is (= '(clojure.core/fn [] handle) (nth expansion 2)))
      (t/is (empty? warnings))))
  (t/testing "effects warn about a symbol and expand to the body, not a hook"
    (t/are [form] (let [[expansion warnings] (expand form)]
                    (and (= 'cleanup expansion)
                         (= [hana/warning-simple-body] warnings)))
      '(helix.hooks/use-effect [x] cleanup)
      '(helix.hooks/use-layout-effect [x] cleanup))))
