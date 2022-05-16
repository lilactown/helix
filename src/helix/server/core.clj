(ns helix.server.core)


(defprotocol IRenderable
  (-render [c props]))


(extend-protocol IRenderable
  clojure.lang.IFn
  (-render [f props]
    (f props)))


(defrecord Element [type props key])


(defn $
  [type & args]
  (if (and (map? (first args)) (not (instance? Element (first args))))
    (let [props (first args)
          children (rest args)
          key (:key props)]
      (->Element type (-> props (dissoc :key) (assoc :children children)) key))
    (->Element type {:children args} nil)))


(defn <>
  [& children]
  (->Element 'react/Fragment {:children children} nil))


(defn provider
  [{:keys [context value]} & children]
  (->Element
   'react/Provider
   {:context context :value value :children children}
   nil))


(defn suspense
  [{:keys [fallback]} & children]
  (->Element
   'react/Suspense
   {:fallback fallback :children children}
   nil))


(defmacro fnc
  [& body]
  `(fn ~@body))


(defmacro defnc
  [& body]
  `(defn ~@body))


(defmacro defhook
  [& body]
  `(defn ~@body))
