(ns ueaq.core
  (:require
   [goog.object :as gobj]))


(defprotocol IUnwrappable
  (-unwrap [o]))


(defn unkeywordize
  [k]
  (if-let [ns (namespace k)]
    (str ns "/" (name k))
    (name k)))


(def unwrappable-key (unkeywordize ::unwrap))


(defn unwrappable?
  [p]
  (or (satisfies? IUnwrappable p)
      (some? (gobj/get p unwrappable-key))))


(defn unwrap
  [p]
  (if (satisfies? IUnwrappable p)
    (-unwrap p)
    ((gobj/get p unwrappable-key))))



(defn shallow-clj->js
  [m]
  (loop [entries (seq m)
         o #js {}]
    (if (nil? entries)
      o
      (let [entry (first entries)
            k (key entry)
            v (val entry)]
        (recur
         (next entries)
         (doto o
           (gobj/set (unkeywordize k) v)))))))


(declare ueaq)

;; -- traps


(defn getter
  [o prop]
  (this-as handler
    (let [opts (.-opts ^js handler)
          context (.-context ^js handler)
          {:keys [recursive? prop->key]} opts

          v (get o (prop->key prop)
                 (gobj/get context prop))]
      (if (and recursive? (associative? v))
        (ueaq v opts)
        v))))


(defn has
  [o prop]
  (this-as handler
    (let [{:keys [prop->key]} (.-opts ^js handler)]
      (contains? o (prop->key prop)))))


(defn own-keys
  [o]
  (to-array (map unkeywordize (keys o))))


(defn enumerate
  [o]
  (map unkeywordize (keys o)))


(defn get-own-property-descriptor
  [o prop]
  (this-as handler
    (let [{:keys [prop->key]} (.-opts ^js handler)
          k (prop->key prop)]
      (if (contains? o k)
        #js {:enumerable true :configurable true
             :writable false :value (get o k)}
        js/undefined))))


(defn get-prototype-of
  [_]
  (this-as handler
    (.-context ^js handler)))


(defn setter
  [_ k v]
  (this-as handler
    (let [context (.-context ^js handler)]
      (gobj/set context k v))))


(defn ^js ueaq
  ([o] (ueaq o {}))
  ([o {:keys [recursive? prop->key mutable?] :as opts
       :or {recursive? false
            prop->key keyword}}]
   (let [;; this is an object to hold implementations of various protocols for
         ;; CLJS usage
         context (specify! #js {}
                   ;; Object
                   ;; (toString [this]
                   ;;   (pr-str* this))

                   IUnwrappable
                   (-unwrap [_] o)

                   ;; IPrintWithWriter
                   ;; (-pr-writer [_ writer _]
                   ;;   ;; prn is not a fast path
                   ;;   (-write writer
                   ;;           (if recursive?
                   ;;             (pr-str (clj->js o))
                   ;;             (pr-str (shallow-clj->js o)))))
                   )

         handler #js {:opts (assoc opts
                                   :prop->key prop->key)
                      :context context
                      :get getter
                      :has has
                      :ownKeys own-keys
                      :enumerate enumerate
                      :getOwnPropertyDescriptor get-own-property-descriptor
                      :getPrototypeOf get-prototype-of
                      :set setter}]
     (js/Proxy. o handler))))
