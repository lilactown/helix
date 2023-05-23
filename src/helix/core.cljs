(ns helix.core
  (:refer-clojure :exclude [type])
  (:require [goog.object :as gobj]
            [helix.impl.props :as impl.props]
            [helix.impl.classes :as helix.class]
            [cljs-bean.core :as bean]
            ["react" :as react]
            ["react/jsx-runtime" :as jsx-runtime])
  (:require-macros [helix.core]))


(when (exists? js/Symbol)
  (extend-protocol IPrintWithWriter
    js/Symbol
    (-pr-writer [sym writer _]
      (-write writer (str "\"" (.toString sym) "\"")))))


(def Fragment
  "React.Fragment. See `helix.core/<>` for macro version."
  react/Fragment)


(def Suspense
  "React.Suspense. See `helix.core/suspense` for macro version."
  react/Suspense)


(def create-element react/createElement)


(def create-context
  "React.createContext"
  react/createContext)


;; this is to enable calling `(.createElement (get-react))` without doing
;; a dynamic arity dispatch. See https://github.com/Lokeh/helix/issues/20
(defn ^js/React get-react [] react)

(def jsx  jsx-runtime/jsx)
(def jsxs jsx-runtime/jsxs)

(defn $
  "Create a new React element from a valid React type.

  Example:
  ```
  ($ MyComponent
   \"child1\"
   ($ \"span\"
     {:style {:color \"green\"}}
     \"child2\" ))
  ```"
  [type & args]
  (let [?p (first args)
        ?c (rest args)
        native? (or (keyword? type)
                    (string? type)
                    (:native (meta type)))
        has-props? ^boolean (or (map? ?p)
                                (nil? ?p))
        children* ^seq (if has-props?
                         ?c
                         args)
        children (if (next children*)
                   (into-array children*)
                   (first children*))
        props* (cond-> {}
                 has-props?       (conj ?p)
                 (some? children) (assoc :children children))
        props (if native?
                (impl.props/-dom-props props*)
                (impl.props/-props     props*))
        key (:key props*)
        emit-fn (if (and children* (next children*))
                  jsxs
                  jsx)
        type' (if (keyword? type)
                (name type)
                type)]
    (if (some? key)
      (emit-fn type' props key)
      (emit-fn type' props))))


(def ^:deprecated $$
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
 $)


(defprotocol IExtractType
  (-type [factory] "Extracts the underlying type from the factory function."))


(defn type
  "Geven a factory function created by `helix.core/factory` or `cljs-factory`,
  returns the original component that the factory creates elements of."
  [f]
  (-type f))


(defn factory
  "Creates a factory function for a React component, that when called returns an
  element with the props and children passed to the factory.

  Use `helix.core/type` to extract the original React component."
  [type]
  (-> (fn factory [& args]
        (apply $ type args))
      (specify! IExtractType
        (-type [_] type))))


(defn cljs-factory
  "Creates a factory function for a component defined via `defnc`, that when
  called returns an element with the props and children passed to the factory.
  Slightly more performant than `factory` when used with a helix component.

  Use `helix.core/type` to extract the original component this was called with."
  [type]
  (-> (fn factory [& args]
        (if (map? (first args))
          (let [props (first args)]
            (apply react/createElement
                   type
                   #js {"helix/props"
                        (dissoc props
                                :key
                                :ref)
                        "key" (get props :key js/undefined)
                        "ref" (get props :ref js/undefined)}
                   (rest args)))
          (apply react/createElement
                 type
                 #js {}
                 args)))
      (specify! IExtractType
        (-type [_] type))))


(defn- assoc-some [m k x]
  (if (some? x)
    (assoc m k x)
    m))


(defn extract-cljs-props
  "A helper function for turning a props object into a CLJS map. Works with both
  factory functions (which stores a map in a single key, \"helix/props\") and
  normal JS objects.
  Mostly used internally by helix, but can be useful when writing HOC."
  [o]
  (when (and ^boolean goog/DEBUG (or (map? o) (nil? o)))
    (throw (ex-info "Props received were a map. This probably means you're calling your component as a function." {:props o})))
  (if-let [props (gobj/get o "helix/props")]
    (assoc-some props :children (gobj/get o "children"))
    (bean/bean o)))


(defn- props-kvs-identical?
  [prev cur]
  (let [prev-props (extract-cljs-props prev)
        cur-props (extract-cljs-props cur)]
    (and (= (count prev-props) (count cur-props))
         (every?
          #(identical? (get prev-props %) (get cur-props %))
          (keys cur-props)))))


(defn memo
  "Like React.memo, but passes props to `compare` as CLJS map-likes instead of
  JS objects.
  `compare` should return true if props are equal, and false if not."
  ([component] (react/memo component props-kvs-identical?))
  ([component compare]
   (react/memo
    component
    (fn memo-compare
      [o o']
      (compare
       (extract-cljs-props o)
       (extract-cljs-props o'))))))



;;
;; -- class components
;;



(defn create-component
  "Helper function for creating a class component. See `defcomponent`."
  [spec statics]
  (let [render (.-render ^js spec)
        render' (fn [this]
                  (render
                   this
                   (extract-cljs-props (.-props ^js this))
                   (.-state ^js this)))]
    (gobj/set spec "render" render')
    (helix.class/createComponent react/Component spec statics)))

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

(defn create-ref
  "Like react/createRef, but the ref can be swapped, reset, and dereferenced
  like an atom.

  Note: `helix.core/create-ref` is mostly used for class components. Function
  components typically rely on `helix.hooks/use-ref` instead."

  ([]
   (create-ref nil))

  ([initial-value]
   (let [^js ref (react/createRef)]
     (set! (.-current ref)
           (specify! #js {:current initial-value}
             IDeref
             (-deref [^js this]
               (.-current this))

             IReset
             (-reset! [^js this x]
               (set! (.-current this) x))

             ISwap
             (-swap!
               ([^js this f]
                (set! (.-current this) (f (.-current this))))
               ([^js this f a]
                (set! (.-current this) (f (.-current this) a)))
               ([^js this f a b]
                (set! (.-current this) (f (.-current this) a b)))
               ([^js this f a b xs]
                (set! (.-current this) (apply f (.-current this) a b xs))))))
     (.-current ref))))

;;
;; -- React Fast Refresh
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
