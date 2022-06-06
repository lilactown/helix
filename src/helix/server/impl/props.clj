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
       :value (if (some? v)
                (str attrs " value=\"" v "\"")
                attrs)
       (:async :checked :controls :default :defer :disabled :hidden :loop
               :multiple :muted :open :required :reversed :selected :scoped
               :seamless) (if (true? v) (str attrs " " (name k) "=\"\"") attrs)
       :content-editable (str attrs " contenteditable=\"" (boolean v) "\"")
       :spell-check (str attrs " spellcheck=\"" (boolean v) "\"")
       :draggable (str attrs " draggable=\"" (boolean v) "\"")
       :allow-fullScreen (if (true? v)
                           (str attrs " allowfullscreen=\"\"")
                           attrs)
       :auto-play (if (true? v)
                    (str attrs " autoplay=\"\"")
                    attrs)
       :auto-focus (if (true? v)
                     (str attrs " autofocus=\"\"")
                     attrs)
       :disable-picture-in-picture (if (true? v)
                                     (str attrs " disablepictureinpicture=\"\"")
                                     attrs)
       :disable-remote-playback (if (true? v)
                                  (str attrs " disableremoteplayback=\"\"")
                                  attrs)
       :form-no-validate (if (true? v)
                           (str attrs " formnovalidate=\"\"")
                           attrs)
       :no-module (if (true? v)
                    (str attrs " nomodule=\"\"")
                    attrs)
       :no-validate (if (true? v)
                      (str attrs " novalidate=\"\"")
                      attrs)
       :plays-inline (if (true? v)
                       (str attrs " playsinline=\"\"")
                       attrs)
       :read-only (if (true? v)
                    (str attrs " readonly=\"\"")
                    attrs)
       :item-scope (if (true? v)
                     (str attrs " itemscope=\"\"")
                     attrs)
       :row-span (str attrs " rowspan=\"" v "\"")
       (:capture :download) (cond
                              (true? v) (str attrs " " (name k) "=\"\"")
                              (some? v) (str attrs " " (name k) "=\"" v "\"")
                              :else attrs)

       (str attrs " " (name k) "=\"" v "\"")))
   ""
   (dissoc props
           :children
           :dangerously-set-inner-HTML
           :inner-html
           :suppress-content-editable-warning
           :suppress-hydration-warning
           :default-checked :default-value)))


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
