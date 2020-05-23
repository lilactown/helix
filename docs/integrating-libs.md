# Integrating with other libraries

Helix can be paired with other React wrappers, which can be useful if you'd like easy access to hooks or less indirection in the creation of your React components.

## Reagent

Calling Helix components from Reagent components requires nothing special; just use the regular Helix component construction form:

``` clojure
(require '[helix.core :as  h :refer [defnc $ <>]])

(defn reagent-component [a b c]
  [:div
   ($ helix-component {:foo "bar"})])
```

This works because Reagent accepts React components in the hiccup returned by components.

Calling Reagent components from Helix components requires the use of `reagent.core/as-element`:

``` clojure
(require '[reagent.core :as reagent]
         '[helix.dom :as  d])

(defnc helix-component [{:keys [a b c]}]
  (d/div
   (reagent/as-element [reagent-component a b c])))
```

Note that Reagent components can take any number of arguments, whereas Helix components behave like vanilla React components accepting either a single `props` object or a `props` object and a `ref`.

You might want to be able to use Reagent's reactive atoms or Re-frame subscriptions in your Helix components. This is possible with a couple custom hooks:

``` clojure
(require '["react" :as react]
         '[re-frame.core :as rf])

(defn use-deref [a]
  ;; TODO
  )

(defn use-sub
  "Subscribe to a re-frame sub"
  [query]
  (use-deref (rf/subscribe query)))

```

Finally, if you want to rewrite a (form-1) Reagent component as a Helix component, it's generally pretty simple:

- Ensure the parameter signature accepts a single `props` map.
- Change the signature to accept a single `props` map.
- Update any reactive atom and re-frame subscription dereferences to use hooks.
- Optionally, convert hiccup to helix.dom.
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
(defnc foo [{:keys [a b c]}]
  (d/div
   {:class "abc"}
   (d/div
    (reagent/as-element [subcomponent a b c]))))

(defnc bar [{:keys [z y x]}]
  (d/div ($ foo {:a (use-sub [:sub]) :b y :c x})))
```
