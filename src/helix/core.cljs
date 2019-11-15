(ns helix.core
  (:refer-clojure :exclude [type])
  (:require [goog.object :as gobj]
            [helix.utils :as utils]
            ["react" :as react]
            ["react-refresh/runtime" :as react-refresh])
  (:require-macros [helix.core]))


(when (exists? js/Symbol)
  (extend-protocol IPrintWithWriter
    js/Symbol
    (-pr-writer [sym writer _]
      (-write writer (str "\"" (.toString sym) "\"")))))


(def Fragment react/Fragment)


(def props->clj utils/props->clj)


(defn clj->props
  [x native?]
  (utils/clj->props x native?))


(def create-element react/createElement)


(defn $$
  "Dynamically create a new React element from a valid React type.

  `$` can typically be faster, because it will statically process the arguments
  at macro-time if possible.

  Example:
  ```
  ($$ MyComponent
   \"child1\"
   ($$ \"span\"
     {:style {:color \"green\"}}
     \"child2\" ))
  ```"
  [type & args]
  (let [?p (first args)
        ?c (rest args)]
    (if (map? ?p)
      (apply create-element
             type
             (clj->props ?p (string? type))
             ?c)
      (apply create-element
             type
             nil
             args))))


(defprotocol IExtractType
  (-type [factory] "Extracts the underlying type from the factory function."))


(defn type
  [f]
  (-type f))


(defn factory
  "Creates a factory function for an external React component"
  [type]
  (-> (fn factory [& args]
        (if (map? (first args))
          (apply create-element type (clj->props (first args) false) (rest args))
          (apply create-element type nil args)))
      (specify! IExtractType
        (-type [_] type))))


(defn- cljs-factory
  [type]
  (-> (fn factory [& args]
        (if (map? (first args))
          (let [props (first args)
                {:keys [key ref]} props]
            (apply create-element
                   type
                   #js {:cljs-props (dissoc props :key :ref)
                        :key key
                        :ref ref}
                   (rest args)))
          (apply create-element type nil args)))
      (specify! IExtractType
        (-type [_] type))))


(defn- wrap-cljs-component
  [type]
  ;; convert js props to clj props
  (let [wrapper (fn wrap-type-props [p r]
                  (let [cljs-props (gobj/get p "cljs-props" {})
                        props (reduce (fn [cljs-props' k]
                                        (if (= k "cljs-props")
                                          cljs-props'
                                          (assoc cljs-props'
                                                 (keyword k)
                                                 (gobj/get p k))))
                                      cljs-props
                                      (gobj/getKeys p))]
                  (type #js {:cljs-props props} r)))]
    (when js/goog.DEBUG
      (set! (.-displayName wrapper) (str "cljsProps(" (.-displayName type) ")")))
    wrapper))


(defn- extract-cljs-props
  [o]
  (gobj/get o "cljs-props"))


;;
;; React Fast Refresh
;;


(defn register!
  "Registers a component with the React Fresh runtime.
  `type` is the component function, and `id` is the unique ID assigned to it
  (e.g. component name) for cache invalidation."
  [type id]
  (react-refresh/register type id))


(def signature!
  react-refresh/createSignatureFunctionForTransform)
