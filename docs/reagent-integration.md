# Reagent Integration

Helix can be easily integrated into a Reagent project, which can be useful if you'd like easy access to hooks or less indirection in the creation of your React components.

Calling Helix components from Reagent components requires nothing special; just use the regular Helix component construction form:

``` clojure
(require '[helix.core :as  h])

(defn reagent-component [a b c]
  [:div
   (h/$ helix-component {:foo "bar"})])
```

This works because Reagent accepts React components in the hiccup returned by components.

Calling Reagent components from Helix components requires the use of `reagent.core/as-element`:

``` clojure
(require '[reagent.core :as reagent]
         '[helix.dom :as  h.dom])

(h/defnc helix-component [{:keys [a b c]}]
  (h.dom/div
   (reagent/as-element [reagent-component a b c])))
```

Note that Reagent components can take any number of arguments, whereas Helix components abide by the React rule of components accepting either a single `props` object or a `props` object and a `ref`.

You might want to be able to use Reagent's reactive atoms or Re-frame subscriptions in your Helix components. This is possible with a couple custom hooks:

``` clojure
(require '["react" :as react]
         '[re-frame.core :as rf])

(defn use-sub
  "Subscribe to a re-frame sub.
  Pass `deps` to make resubscribe when query args change.
  Borrowed from https://github.com/Lokeh/hooks-demo/blob/master/src/hooks_demo/hooks.cljs"
  ([query]
   (use-sub query []))
  ([query deps]
   (let [r (react/useMemo #(rf/subscribe query) (clj->js deps))
         [v u] (react/useState @r)]
     (react/useEffect
      (fn []
        (let [t (reagent/track! #(u @r))]
          #(reagent/dispose! t)))
      (clj->js deps))
     v)))

(let [n (atom 0)]
  (defn- unique-int
    "Returns ever-increasing (per process) integers."
    []
    (swap! n inc)))

(defn use-deref
  "Hook to deref a reactive atom. Updates will cause re-renders."
  ;; if no deps are passed in, we assume we only want to run
  ;; subscrib/unsubscribe on mount/unmount
  ([a] (use-deref a []))
  ([a deps]
   ;; create a react/useState hook to track and trigger renders
   (let [[v u] (react/useState @a)
         ;; Use unique name for the atom's watch.
         watch (react/useMemo (fn [] (keyword (str "use-deref-" (unique-int))))
                            #js[])]
     ;; react/useEffect hook to create and track the subscription to the iref
     (react/useEffect
      (fn []
        (add-watch a watch
                   ;; update the react state on each change
                   (fn [_ _ _ v'] (u v')))
        ;; return a function to tell react hook how to unsubscribe
        #(remove-watch a watch))
      ;; pass in deps vector as an array
      (clj->js deps))
     ;; return value of useState on each run
     v)))

```

Finally, if you want to rewrite a (form-1) Reagent component as a Helix component, it's generally pretty simple:

- Change `defn` to `h/defnc`
- Change the signature to accept a single `props` map.
- Change reactive atoms and re-frame subscriptions to hooks.
- Convert hiccup to helix.dom and helix.core/$ invocations.
- Wrap any Reagent components with `reagent.core/as-element`.

These two examples are equivalent:

``` clojure
;; Reagent:
(defn foo [a b c]
  [:div {:class "abc"}
   [:div [subcomponent a b c]]])

(defn bar [z y x]
  [:div [foo @(rf/subscribe [:sub]) y x]])

;; Helix:
(h/defnc foo [{:keys [a b c]}]
  (h.dom/div
   {:class "abc"}
   (h.dom/div
    (reagent/as-element [subcomponent a b c]))))

(h/defnc bar [{:keys [z y x]}]
  (h.dom/div (h/$ foo {:a (use-sub [:sub]) :b y :c x})))
```
