(ns workshop.clj-kondo-hooks-test
  (:require ["react" :as react]
            [helix.core :refer-macros [defnc] :refer [$]]
            [helix.dom :as d]))


(defn comp-printer [comp] (println (str comp)))

(defnc my-comp-kitchen-sink
  "optional docstring"
  {:meta 'data}
  [{:keys [children]} _ref]
  {:helix/features {:fast-refresh true}
   :wrap           [(comp-printer) (react/forwardRef)]}
  (d/div {:style {:display "flex"} & {:dynamic 'prop}} children))

(defnc my-comp-no-doc
  [{:keys [children]} _ref]
  {:helix/features {:fast-refresh true}
   :wrap           [(comp-printer) (react/forwardRef)]}
  ($ my-comp {:style {:display "flex"} & {:dynamic 'prop}} children))

(defnc my-comp-no-opts
  "optional docstring"
  [{:keys [children]} _ref]
  ($ my-comp {:style {:display "flex"}} children))

(defnc my-comp-no-wrap-in-opts
  "optional docstring"
  [{:keys [children]} _ref]
  {:helix/features {:fast-refresh true}}
  ($ my-comp {:style {:display "flex"}} children))

(defnc my-comp
  [{:keys [children]} _ref]
  (d/div children))


(d/div ($ my-comp
          {:some :prop
           &     {:foo :bar}}

          ($ my-comp-kitchen-sink "ba1")
          ($ my-comp-no-doc "baa")
          ($ my-comp-no-opts "bab")
          ($ my-comp-no-wrap-in-opts "bac")
          "baz"
          {:bad :map-children}))

($ my-comp {:some :prop
            &     {:foo :bar}}
  {:bad :map-children})
