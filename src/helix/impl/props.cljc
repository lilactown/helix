(ns helix.impl.props
  (:require [clojure.string :as string]
            #?@(:cljs [[cljs-bean.core :as b]
                       [goog.object :as gobj]]))
  #?(:cljs (:require-macros [helix.impl.props])))

(def aria-data-css-custom-prop-special-case-re #"^(aria-|data-|--).*")

#?(:cljs (def camel-regexp (js/RegExp "-(\\w)", "g")))

(defn camel-case
  "Returns camel case version of the string, e.g. \"http-equiv\" becomes \"httpEquiv\"."
  [s]
  (if (or (keyword? s)
          (string? s)
          (symbol? s))
    (let [name-str (name s)]
      ; this is hot path so we want to use low-level interop
      #?(:clj  (cond
                 (some? (re-matches aria-data-css-custom-prop-special-case-re name-str)) name-str
                 (= (subs name-str 0 1) "'") (subs name-str 1)
                 :else (string/replace name-str #"-(\w)" #(string/upper-case (second %))))
         :cljs (cond
                 (some? (.match name-str aria-data-css-custom-prop-special-case-re)) name-str
                 (= (.substring name-str 0 1) "'") (.substring name-str 1)
                 :else (.replace name-str camel-regexp #(.toUpperCase %2)))))
      s))

(comment
  (camel-case "get-asdf-aw9e8f"))

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

#?(:cljs
   (defn merge-obj [o1 o2]
     (if (nil? o2)
       o1
       (doto o1
         (gobj/extend o2)))))

(defn seq-to-class [class]
  (->> class
       (remove nil?)
       (map str)
       (string/join " ")))

#?(:clj
   (defn unquote-class
     "Handle the case of (quote '[foo bar])"
     [class]
     (if (sequential? class)
       (seq-to-class class)
       (str class))))

#?(:clj
   (defn normalize-class [class]
     (cond
       (string? class)
       class

       (and (list? class)
            (= (first class) 'quote))
       (unquote-class (second class))

       :default
       `(normalize-class ~class))))

#?(:cljs
   (defn normalize-class [class]
     (cond
       ;; quick path
       (string? class) class

       (sequential? class) (seq-to-class class)

       ;; not a string or sequential, stringify it
       true (str class))))


#?(:cljs
   (defn or-undefined
     [v]
     (if (nil? v)
       js/undefined
       v)))


(defn dom-style
  [style]
  (cond
    ;; when map, convert to an object w/ camel casing
    (map? style) (primitive-obj style)
    ;; React Native allows arrays of styles
    (vector? style) (into-js-array (map #(if (map? %) (primitive-obj %) %) style))
    ;; if anything else, at compile time fall back to runtime
    ;; at runtime just pass it through and assume it's a JS style obj!
    true #?(:clj `(dom-style ~style)
            :cljs style)))


(defn -dom-props
  ([m] #?(:clj (if-let [spread-sym (cond
                                     (contains? m '&) '&
                                     (contains? m :&) :&)]
                 `(merge-obj ~(-dom-props (dissoc m spread-sym) (primitive-obj))
                             (-dom-props ~(get m spread-sym)))
                 (-dom-props m (primitive-obj)))
          :cljs (if (map? m)
                  (-dom-props m (primitive-obj))
                  ;; assume JS obj
                  m)))
  ([m o]
   (if (seq m)
     (recur (rest m)
            (let [entry (first m)
                  k (key entry)
                  v (val entry)]
              (case k
                :class (set-obj o "className" (normalize-class v))
                :for (set-obj o "htmlFor" v)
                :style (set-obj o "style" (dom-style v))
                :value (set-obj o "value" #?(:clj `(or-undefined ~v)
                                             :cljs (or-undefined v)))
                (set-obj o (camel-case (kw->str k)) v))))
     #?(:clj (list* o)
        :cljs o))))


(comment
  (-dom-props {:asdf "jkl" :style 'foo})

  (-dom-props {:style ["fs1"]})
  )

(defmacro dom-props [m]
  (-dom-props m))


(defn -props
  ([m] #?(:clj (if-let [spread-sym (cond
                                     (contains? m '&) '&
                                     (contains? m :&) :&)]
                 `(merge-obj ~(-props (dissoc m spread-sym) (primitive-obj))
                             (-props ~(get m spread-sym)))
                 (-props m (primitive-obj)))
          :cljs (if (map? m)
                  (-props m (primitive-obj))
                  m)))
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
