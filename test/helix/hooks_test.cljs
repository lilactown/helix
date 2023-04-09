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
