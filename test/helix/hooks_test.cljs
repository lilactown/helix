(ns helix.hooks-test
  "Namespace containing tests for hooks wrappers."
  (:require
   ["react-dom/server" :as rds]
   [cljs.test :as t]
   [helix.core :refer [$ defnc]]
   [helix.hooks :as hooks]))

;;; useId hook test

(defnc use-id-example []
  (let [id (hooks/use-id)]
    (str "Generated identifier: " id)))

(t/deftest use-id-test
  (t/testing "whether components invoking use-id render correctly"
    (t/is (= "Generated identifier: :R0:"
             (rds/renderToString ($ use-id-example))))))

;;; function variants of hooks

(defnc star-hooks-example [{:keys [deps]}]
  (hooks/use-effect* (fn []) deps)
  (hooks/use-layout-effect* (fn []) deps)
  (hooks/use-imperative-handle* nil (fn [] #js {}) deps)
  (let [memoized (hooks/use-memo* (fn [] "memo") deps)
        callback (hooks/use-callback* (fn [] "callback") deps)]
    ($ "div" (str memoized " " (callback)))))

(t/deftest star-hooks-deps
  (t/are [deps] (= "<div>memo callback</div>"
                   (rds/renderToString ($ star-hooks-example {:deps deps})))
    :once
    :always
    []
    ["a"]))

;;; use-memo

(defnc use-memo-symbol-example [{:keys [value]}]
  (let [captured (hooks/use-memo :once value)]
    ($ "div" captured)))

(t/deftest use-memo-symbol-body
  (t/is (= "<div>hello</div>"
           (rds/renderToString ($ use-memo-symbol-example {:value "hello"})))))

;;; use-imperative-handle

(t/deftest use-imperative-handle-test
  (t/testing "ref is passed through as the first argument"
    (t/testing "with a vector of deps"
      (let [expansion (macroexpand
                        '(hooks/use-imperative-handle my-ref [dep]
                           #js {:focus (fn [])}))]
        ;; (raw-use-imperative-handle my-ref (fn [] ...) (cljs.core/array dep))
        (t/is (= 'helix.hooks/raw-use-imperative-handle (first expansion)))
        (t/is (= 'my-ref (second expansion))
              "ref must be the first argument to useImperativeHandle")))
    (t/testing "with :always"
      (let [expansion (macroexpand
                        '(hooks/use-imperative-handle my-ref :always
                           #js {:focus (fn [])}))]
        (t/is (= 'helix.hooks/raw-use-imperative-handle (first expansion)))
        (t/is (= 'my-ref (second expansion))
              "ref must be the first argument to useImperativeHandle")))))
