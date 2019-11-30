(ns helix.impl.utils
  (:require [clojure.string :as str]
            [goog.object :as gobj]))

(defn keyword->str [k]
  (let [kw-ns (namespace k)
        kw-name (name k)]
    (if (nil? kw-ns)
      kw-name

      (str kw-ns "/" kw-name))))


(defn props->clj [props]
  (loop [ks (js/Object.keys props)
         m {}]
    (if (nil? ks)
      m
      (let [k (first ks)
            v (gobj/get props k)]
        (recur (next ks)
               (case k
                 ;; backwards compat
                 "class" (assoc m
                                :className v
                                :class v)
                 "className" (assoc m
                                    :className v
                                    :class v)
                 "htmlFor" (assoc m
                                  :htmlFor v
                                  :for v)
                 (assoc m (keyword k) v)))))))

(comment
  (props->clj #js {:class "foo"})
  )


(defn- set-obj [o k v]
  (do (gobj/set o k v)
      o))

(defn- join-classes
  "Join the `classes` with a whitespace."
  [classes]
  (->> classes
       (remove nil?)
       (str/join " ")))

(defn- class-name [x]
  (cond (or (nil? x)
            (keyword? x)
            (string? x))
        x

        (or (sequential? x)
            (set? x))
        (join-classes x)

        :else x))

(defn- camel-case*
  "Returns camel case version of the string, e.g. \"http-equiv\" becomes \"httpEquiv\"."
  [s]
  (if (or (keyword? s)
          (string? s)
          (symbol? s))
    (let [[first-word & words] (str/split (name s) #"-")]
      (if (or (empty? words)
              (= "aria" first-word)
              (= "data" first-word))
        s
        (-> (map str/capitalize words)
            (conj first-word)
            str/join)))
    s))

(defn- map->camel+js [x]
  (cond
    (map? x) (loop [ps (seq x)
                    o #js {}]
               (if (nil? ps)
                 o
                 (let [p (first ps)
                       k (key p)
                       v (val p)]
                   ;; side-effecting
                   (set-obj o (camel-case* (name k)) (map->camel+js v))
                   (recur (next ps)
                          o))))
    true x))

(comment
  (map->camel+js {})

  (map->camel+js {:color "red"})

  (map->camel+js {:color "red" :background "green"})

  (next [1 2])
  )

(defn clj->props
  "Shallowly converts props map to a JS obj. Handles certain special cases:

  1. `:class` -> \"className\", and joins collections together as a string
  2. `:for` -> \"htmlFor\"
  3. `:style` -> deeply converts this prop to a JS obj

  By default, converts kebab-case keys to camelCase strings. pass in `false`
  as a second arg to disable this."
  ([props] (clj->props props true))
  ([props native?]
   (loop [pxs (seq props)
          js-props #js {}]
     (if (nil? pxs)
       js-props
       (let [p (first pxs)
             k (key p)
             v (val p)]
         ;; side-effecting
         (case k
           :style (set-obj js-props "style" (map->camel+js v))
           :class (if native?
                    (set-obj js-props "className" (class-name v))
                    (do (set-obj js-props "class" (class-name v))
                        (set-obj js-props "className" (class-name v))))
           :for (if native?
                  (set-obj js-props "htmlFor" v)
                  (do (set-obj js-props "for" v)
                      (set-obj js-props "htmlFor" v)))

           (set-obj js-props
                    (if native?
                      (camel-case* (keyword->str k))
                      (keyword->str k))
                    v))
         (recur (next pxs)
                js-props))))))

(comment
  (clj->props {:class "foo"
               :style {:color "red"}})

  (clj->props {:class [nil "foo"]})

  (clj->props {:asdf "jkl"})

  (let [case {:x-x? "asdf"}]
    (-> (clj->props case)))

  (let [case {:x-x.x? "asdf"}]
    (-> (clj->props case)))

  (let [case {:xa1-xb2.x? "asdf"}]
    (-> (clj->props case)))


  (let [case {:ns/xb2.x? "asdf"}]
    (-> (clj->props case)))

  (let [case {:ns/-xb2.x? "asdf"}]
    (-> (clj->props case)))
  )
