(ns helix.core
  (:refer-clojure :exclude [type])
  (:require [goog.object :as gobj]
            [helix.utils :as utils]
            ["./class.js" :as helix.class]
            [cljs-bean.core :as bean]
            [ueaq.core :as ueaq]
            ["react" :as react]
            ["react-dom/server" :as rds])
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
  (ueaq/ueaq x)
  #_(utils/clj->props x native?))


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
                   ;; #js {:cljs-props (dissoc props :key :ref)
                   ;;      :key key
                   ;;      :ref ref}
                   (ueaq/ueaq props)
                   (rest args)))
          (apply create-element type nil args)))
      (specify! IExtractType
        (-type [_] type))))


(defn- wrap-cljs-component
  [type]
  ;; convert js props to clj props
  (let [wrapper (fn wrap-type-props [p r]
                  ;; (js/console.log p)
                  ;; (let [cljs-props (gobj/get p "cljs-props" {})
                  ;;       gather-keys (fn [cljs-props' k]
                  ;;                     (if (= k "cljs-props")
                  ;;                       cljs-props'
                  ;;                       (assoc cljs-props'
                  ;;                              (keyword k)
                  ;;                              (gobj/get p k))))]
                  ;;   (type #js {:cljs-props (.reduce (gobj/getKeys p)
                  ;;                                   gather-keys
                  ;;                                   cljs-props)}
                  ;;         r))
                  (type p r))]
    (when js/goog.DEBUG
      (set! (.-displayName wrapper) (str "cljsProps(" (.-displayName type) ")")))
    wrapper))


(defn- extract-cljs-props
  [o]
  (bean/bean o))



;;
;; -- class components
;;



(defn create-component [spec statics]
  (helix.class/createComponent spec statics))

(comment
  (def MyComponent
    (create-component #js {:displayName "Foo"
                           :constructor
                           (fn [this]
                             (set! (.-state this) #js {:count 3}))
                           :render
                           (fn [this props state]
                             (prn props state)
                             ($$ "div" (.-count (.-state this))))}
                      nil))

  (js/console.log MyComponent)

  (rds/renderToString ($$ MyComponent {:foo "baz"})))


;;
;; React Fast Refresh
;;


(defn register!
  "Registers a component with the React Fresh runtime.
  `type` is the component function, and `id` is the unique ID assigned to it
  (e.g. component name) for cache invalidation."
  [type id]
  (when (exists? (.-$$Register$$ js/window))
    (.$$Register$$ js/window type id)))


(defn signature! []
  ;; grrr `maybe` bug strikes again
  (and (exists? (.-$$Signature$$ js/window))
      (.$$Signature$$ js/window)))
