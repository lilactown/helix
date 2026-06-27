(ns helix.hooks-test
  "Namespace containing tests for hooks wrappers."
  (:require [cljs.test :as t]
            [helix.core :refer [$ defnc]]
            [helix.hooks :as hooks]
            ["react-dom/server" :as rds]))

;;; useId hook test

(defnc use-id-example []
  (let [id (hooks/use-id)]
    (str "Generated identifier: " id)))

(t/deftest use-id-test
  (t/testing "whether components invoking use-id render correctly"
    (t/is (= "Generated identifier: :R0:"
             (rds/renderToString ($ use-id-example))))))


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
