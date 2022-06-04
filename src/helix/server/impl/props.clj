(ns helix.server.impl.props
  (:require
   [clojure.string :as string]))

(defn- entry->style
  [[k v]]
  (str (if (string? k) k (name k)) ":" v))

(defn style-str
  [styles]
  (reduce
   (fn [s e]
     (str s ";" (entry->style e)))
   (entry->style (first styles))
   (rest styles)))

#_(style-str {:color "red"})

#_(style-str {"color" "blue" :flex "grow"})


(defn seq-to-class [class]
  (->> class
       (remove nil?)
       (map str)
       (string/join " ")))


(defn props->attrs
  [props]
  (reduce-kv
   (fn [attrs k v]
     (case k
       :style (str attrs " style=\"" (style-str v) "\"")
       :id (str attrs " id=\"" v "\"")
       :class (str attrs " class=\""
                   (if (seqable? v)
                     (seq-to-class v)
                     v) "\"")
       (:value :default-value) (str attrs " value=\"" v "\"")
       (:checked :default-checked) (if (true? v)
                                     (str attrs " checked")
                                     attrs)
       :multiple (if (true? v)
                   (str attrs " multiple")
                   attrs)
       :selected (if (true? v)
                   (str attrs " selected")
                   attrs)
       :muted (if (true? v)
                (str attrs " muted")
                attrs)
       :content-editable (str attrs " contenteditable=\"" (boolean v) "\"")
       :spell-check (str attrs " spellcheck=\"" (boolean v) "\"")
       :draggable (str attrs " draggable=\"" (boolean v) "\"")
       (str attrs " " (name k) "=\"" v "\"")))
   ""
   (dissoc props
           :children
           :dangerousely-set-inner-html
           :inner-html
           :suppress-content-editable-warning
           :suppress-hydration-warning)))


(comment
  (props->attrs {:style {:color "red"}})

  (props->attrs {:style {:color "red"}
                 :id "foo"
                 :class ["foo" "bar"]
                 :for "baz"
                 :accept-charset "utf8"
                 :http-equiv "content-security-policy"
                 :default-value 7
                 :default-checked true
                 :multiple true
                 :muted true
                 :selected true
                 :children '("foo" "bar" "baz")
                 :dangerousely-set-inner-html "<div></div>"
                 :inner-html "<span></span>"
                 :suppress-content-editable-warning true
                 :suppress-hydration-warning true
                 :content-editable true
                 :spell-check false
                 :draggable true}))
