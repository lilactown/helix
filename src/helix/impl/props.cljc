(ns helix.impl.props
  (:require [clojure.string :as string]
            #?@(:cljs [[cljs-bean.core :as b]
                       [goog.object :as gobj]]))
  #?(:cljs (:require-macros [helix.impl.props])))

(def aria-data-special-case-re #"^(aria-|data-).*")

(defn camel-case
  "Returns camel case version of the string, e.g. \"http-equiv\" becomes \"httpEquiv\"."
  [s]
  (if (or (keyword? s)
          (string? s)
          (symbol? s))
    (let [name-str (name s)]
      ; this is hot path so we want to use low-level interop
      #?(:clj  (cond
                 (some? (re-matches aria-data-special-case-re name-str)) name-str
                 (= (subs name-str 0 1) "'") (subs name-str 1)
                 :else (string/replace name-str #"-(\w)" #(string/upper-case (second %))))
         :cljs (cond
                 (some? (.match name-str aria-data-special-case-re)) name-str
                 (= (.substring name-str 0 1) "'") (.substring name-str 1)
                 :else (.replace name-str #"-(\w)" #(.toUpperCase %2)))))
      s))

(defn kw->str [kw]
  (let [kw-ns (namespace kw)
        kw-name (name kw)]
    (if (nil? kw-ns)
      kw-name
      (str kw-ns "/" kw-name))))


(defn set-obj [o k v]
  #?(:clj (conj o k v)
     :cljs (doto o (gobj/set k v))))

#?(:cljs (defn ->js [x]
           (clj->js x :keyword-fn (comp camel-case name))))

(defn primitive-obj
  ([] #?(:clj '[cljs.core/js-obj]
         :cljs #js {}))
  ([m]
   #?(:clj (if (map? m)
             (primitive-obj m (primitive-obj))
             ;; fall back to runtime
             `(primitive-obj ~m))
      :cljs (primitive-obj m (primitive-obj))))
  ([m o]
   (if (seq m)
     (recur (rest m)
            (let [entry (first m)]
              (set-obj o
                       (camel-case (kw->str (key entry)))
                       #?(:clj `(->js ~(val entry))
                          :cljs (->js (val entry))))))
     #?(:clj (list* o)
        :cljs o))))

(defn into-js-array [aseq]
  #?(:clj (list* (into '[cljs.core/array] aseq))
     :cljs (into-array aseq)))


(defn merge-obj [o1 o2]
  #?(:cljs (js/Object.assign o1 o2)))


(defn -native-props
  ([m] (if-let [spread-sym (m '&)]
         `(merge-obj ~(-native-props (dissoc m '&) (primitive-obj))
                     (-native-props ~spread-sym))
         (-native-props m (primitive-obj))))
  ([m o]
   (if (seq m)
     (recur (rest m)
            (let [entry (first m)
                  k (key entry)
                  v (val entry)]
              (case k
                :class (set-obj o "className" v)
                :for (set-obj o "htmlFor" v)
                :style (set-obj o "style"
                                (if (vector? v)
                                  ;; React Native allows arrays of styles
                                  (into-js-array (map primitive-obj v))
                                  (primitive-obj v)))
                (set-obj o (camel-case (kw->str k)) v))))
     #?(:clj (list* o)
        :cljs o))))


(comment
  (-native-props {:asdf "jkl" :style 'foo})
  )

(defmacro native-props [m]
  (-native-props m))


(defn -props
  ([m] (if-let [spread-sym (m '&)]
         `(merge-obj ~(-props (dissoc m '&) (primitive-obj))
                     (-props ~spread-sym))
         (-props m (primitive-obj))))
  ([m o]
   (if (seq m)
     (recur (rest m)
            (let [entry (first m)]
              (set-obj o (kw->str (key entry)) (val entry))))
     #?(:clj (list* o)
        :cljs o))))

(comment
  (-props {:foo-bar "baz"})
  )

(defmacro props [m]
  (-props m))
