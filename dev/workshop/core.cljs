(ns workshop.core
  (:require
    [helix.core :as helix :refer [$ <> defnc]]
    [helix.dom :as d]
    [helix.hooks :as hooks]
    ["react" :as r]
    ["react-dom/server" :as rds]
    [devcards.core :as dc :include-macros true]))


(defnc props-test
  [props]
  (d/div
   (d/div "props test")
   (for [[k v] props]
     (d/div
      {:key k}
      (d/div
       {:style {:color "red"}}
       "key: " (str k))
      (d/div "val: " (pr-str v))))))


(dc/defcard props ($ props-test {:class :class
                                 :style :style
                                 :for :for
                                 :kebab-case :kebab-case
                                 :camelCase :camelCase}))


(defnc memoized
  [{:keys [foo bar]}]
  {:wrap [(helix/memo)]}
  (let [count (hooks/use-ref 0)]
    (hooks/use-layout-effect
     :always
     (swap! count inc))
    (pr-str foo bar @count)))

(defnc memoized-key
  [{:keys [foo bar]}]
  {:wrap [(helix/memo :bar)]}
  (let [count (hooks/use-ref 0)]
    (hooks/use-layout-effect
     :always
     (swap! count inc))
    (pr-str foo bar @count)))


(defnc memoized-test
  []
  (let [[render-count force-render] (hooks/use-state 0)]
    (<>
     (d/div
      (d/div "Top Render Count:" render-count)
      (d/button {:on-click #(force-render inc)} "Render"))
     (d/div
      (d/div
       "memoized "
       ($ memoized
          {:foo (str "foo" (quot render-count 5)) :bar "bar"}))
      (d/div
       "memoized-key "
       ($ memoized-key
          {:foo (str "foo" (quot render-count 5)) :bar "bar"}))))))


(dc/defcard memoized-props
  ($ memoized-test))


(defnc subcomponent
  [{:keys [name] :as props}]
  (d/div name))


(defnc state-test
  []
  (let [[{:keys [name]} set-state] (hooks/use-state {:name "asdf"})]
    (d/div
     (d/input {:value name
               :on-change #(set-state assoc :name (.. % -target -value))})
     ($ subcomponent {:name name}))))


(dc/defcard use-state
  ($ state-test))


(comment
  ((->> (helix.repl/as-tree-seq)
       ;; (take 10)
       #_(filter (comp fn? :type))
       (filter #(helix.repl/type? state-test %))
       (first)
       (helix.repl/info)
       :state
       first
       :dispatch
       #_(js/console.log)) {:name "jkl"})

  (->> (helix.repl/as-tree-seq)
       ;; (take 10)
       #_(filter (comp fn? :type))
       (filter #(helix.repl/type? state-test %))
       (first)
       (helix.repl/info)
       (cljs.pprint/pprint))
  )


(defnc display-range
  [{:keys [end color] :as props}]
  (for [n (range end)]
    (d/div {:key n
            :style {:width "10px" :height "10px" :display "inline-block"
                    :background (or color "green") :margin "auto 2px"}})))


(defnc effect-test
  []
  (let [[count set-count] (hooks/use-state 0)
        renders (hooks/use-ref 1)
        [fx-state set-fx-state] (hooks/use-state {:every 0
                                                  :every/auto 0
                                                  :every-third 0
                                                  :every-third/auto 0})
        threes (quot count 3)]
    (hooks/use-effect
     :always
     (swap! renders inc))
    (hooks/use-effect
     [count]
     (set-fx-state assoc :every count))
    (hooks/use-effect
     [threes]
     (set-fx-state assoc :every-third threes))
    (hooks/use-effect
     :auto-deps
     (set-fx-state assoc :every/auto count))
    (hooks/use-effect
     :auto-deps
     (set-fx-state assoc :every-third/auto threes))

    (d/div
     (d/div "Count: " count)
     (d/button {:on-click #(set-count inc)} "inc")
     (d/div
      (d/div "renders:")
      ($ display-range {:end @renders :color "red"}))
     (for [[k v] fx-state]
       (d/div {:key (str k)}
        (d/div (str k))
        ($ display-range {:end v}))))))


(helix/defcomponent error-boundary
  (constructor [this]
    (set! (.-state this) #js {:error nil}))

  ^:static
  (getDerivedStateFromError [this error]
    #js {:error error})

  (render [this]
    (if-not (.. this -state -error)
      (.. this -props -children)
      (d/pre (d/code (pr-str (.. this -state -error)))))))


(dc/defcard use-effect
  ($ error-boundary
     ($ effect-test)))


(defnc lazy-test
  [{:keys [begin end]
    :or {begin 0}}]
  (<>
   (d/div (str "numbers " (or begin 0) "-" (dec end) ":"))
   (d/ul
    (for [n (range begin end)]
      (d/li {:key n} n)))
   (d/div "ur welcome")))


(dc/defcard lazy
  ($ lazy-test {:end 6}))


(defnc dynamic-test
  []
  (let [div "div"
        props {:style {:color "blue"}}
        children '("foo" "bar")]
    (<> "&" ($ div {& props} children "baz")
        ":&" ($ div {:& props} children "baz"))))

(dc/defcard dynamic
  ($ dynamic-test))


(defnc children-test
  [{:keys [children] :as props}]
  (d/div {:style {:display "flex"
                  :justify-content "space-between"}}
   children))

(dc/defcard children
  ($ children-test (d/div "foo") (d/div "bar")))


(defnc use-memo-component
  [{:keys [qworp]}]
  (let [bar "bar"
        foobar (macroexpand '(hooks/use-memo :auto-deps (str qworp bar goog/DEBUG)))]
    (pr-str foobar)))


(dc/defcard use-memo
  ($ use-memo-component {:qworp "foo"}))


(def class-component
  (helix/create-component #js {:displayName "Foo"
                               :constructor
                               (fn [this]
                                 (set! (.-state this) #js {:count 3}))
                               :render
                               (fn [this props state]
                                 (d/div (.-count (.-state this))))}
                          nil))


(defnc use-memo-metadata
  [{:keys [foo]}]
  {:helix/features {:metadata-optimizations true}}
  (let [foobar ^:memo (vector foo "bar")
        first-foobar (hooks/use-ref foobar)
        [count set-count] (hooks/use-state 0)]
    (d/div
     (str (identical? foobar @first-foobar))
     (d/br)
     count
     (d/button {:on-click #(set-count inc)} "render"))))


(dc/defcard use-memo-metadata-card
  ($ use-memo-metadata {:foo "foo"}))


(helix/defcomponent ClassComponent
  (render [this props state]
    (d/div "hi")))

(dc/defcard class-component
  (<> ($ class-component {:foo "bar"})
      ($ ClassComponent {:foo "baz"})))


(helix/defhook use-custom-effect
  [deps f]
  (hooks/use-effect deps (f)))

(defnc ^{:foo "bar"} custom-effect-test
  [{:keys [deps]}]
  {:helix/features {:check-invalid-hooks-usage true}}
  (let [[state set-state] (hooks/use-state nil)
        mounted? (hooks/use-ref false)]
    (use-custom-effect
     deps
     #(do (when-not @mounted? ;; avoid infinite render in `:always`
            (set-state deps))
          (reset! mounted? true)))
    (prn :render)
    (d/div "deps: " (when state
                     (pr-str state)))))

(dc/defcard custom-effect-test-card
  (<> ($ custom-effect-test {:deps :always})))


(defnc factory-fn-component
  [{:keys [foo bar]}]
  {:helix/features {:define-factory true}}
  (d/div "hello " foo bar))


(dc/defcard define-factory
  (<> (factory-fn-component {:foo "baz" :bar 42})
      ($ (helix/type factory-fn-component) {:foo "asdf" :bar "jkl"})))


(def sub-atom (atom {:foo 0}))


(add-watch sub-atom :global prn)


(defn subscribe-atom
  [f]
  (let [k (gensym "subscribe")]
    (add-watch sub-atom k f)
    #(remove-watch sub-atom k)))


(defn get-atom-value
  []
  @sub-atom)


(defnc use-subscription-test
  []
  (let [value (hooks/use-subscription {:get-current-value get-atom-value
                                       :subscribe subscribe-atom})
        [changes set-changes] (hooks/use-state 0)]
    (hooks/use-effect
     [value]
     (set-changes inc))
    (d/div
     "Value: " (:foo value) " "
     (d/button {:on-click #(do
                             (swap! sub-atom update :foo inc)
                             (swap! sub-atom into {}))} "+")
     (d/div
      "Changes: " changes))))


(dc/defcard use-subscription
  ($ use-subscription-test))


;;
;; -- Benchmarking
;;

(defnc helix-children-benchmark
  [{:keys [children] :as props}]
  (d/div {:style {:display "flex"
                   :justify-content "space-between"}}
          children))

(defnc helix-children-interpret-props-benchmark
  [{:keys [children] :as props}]
  (let [props {:style {:display "flex"
                       :justify-content "space-between"}}]
  (d/div {& props}
         children)))


(defn react-children-benchmark
  [props]
  (r/createElement
   "div"
   #js {:style
        #js {:display "flex"
             :justifyContent "space-between"}}
   (.-children ^js props)))


#_(defnc simple-benchmark-component []
  (let [[re-render set-state] (hooks/use-state 0)
        force-render #(set-state inc)
        [iterations set-iterations] (hooks/use-state 10000)

        react-time (hooks/use-memo
                    [re-render]
                    (with-out-str
                      (simple-benchmark
                       []
                       (rds/renderToString
                        (r/createElement
                         react-children-benchmark
                         #js {:foo "bar"}
                         (r/createElement "div" #js {:style #js {:backgroundColor "green"}} "foo")
                         (r/createElement "div" nil "bar")))
                       iterations)))

        helix-time (hooks/use-memo
                    [re-render]
                    (with-out-str
                      (simple-benchmark
                       []
                       (rds/renderToString
                        ($ helix-children-benchmark
                           {:foo "bar"}
                           (d/div {:style {:background-color "green"}} "foo")
                           (d/div "bar")))
                       iterations)))

        helix-interpret-props-time (hooks/use-memo
                                    [re-render]
                                    (with-out-str
                                      (simple-benchmark
                                       []
                                       (rds/renderToString
                                        ($ helix-children-interpret-props-benchmark
                                           {:foo "bar"}
                                           (d/div {:style {:background-color "green"}} "foo")
                                           (d/div "bar")))
                                       iterations)))]
    (<>
     (d/div
      (d/input {:value iterations
                :on-change #(set-iterations
                             (-> %
                                 .-target
                                 .-value
                                 (js/parseInt 10)))
                :type "number"})
      (d/button {:on-click force-render} "Re-run"))
     (d/div
      {:style {:padding "5px"}}
      (d/code
       react-time))
     (d/div
      {:style {:padding "5px"}}
      (d/code
       helix-time))
     (d/div
      {:style {:padding "5px"}}
      (d/code
       helix-interpret-props-time)))))

#_(dc/defcard simple-benchmark
  ($ simple-benchmark-component))
