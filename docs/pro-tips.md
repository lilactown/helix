# Pro Tips

This is a grab-bag document for useful hints and tips when building and
maintaining React applications using helix.


## Create a custom macro

Helix's `defnc` macro is the heart of the library. It also is going to be at the
heart of how you write your components. Keeping a consistent set of semantics
across a codebase you build is a key ingredient to maintainability and
onboarding.

Helix exposes [experimental and optional](./experiments.md) features through
feature flags. This is preferred over global state which might be used by
component libraries, but it makes it cumbersome to add all of these feature
flags across your whole app - or have some components which use different
feature flags.

That's why it's slightly recommended to create your own `defnc` macro which you
can use to build on and extend for your application. There, you can add any
feature flags you want, or even add other default functionality on top of helix
like hiccup parsing, certain higher order components, debug statements - 
whatever your heart desires.

Creating a macro that wraps helix's is simple and easy. See below for an example
which turns on the experimental "fast-refresh" feature for all components that
use it.


```clojure
(ns my-app.lib
  (:require
    [helix.core]))


(defmacro defnc [type params & body]
  (let [opts? (map? (first body)) ;; whether an opts map was passed in
        opts (if opts?
               (first body)
               {})
        body (if opts?
               (rest body)
               body)
        ;; feature flags to enable by default
        default-opts {:helix/features {:fast-refresh true}}]
    `(helix.core/defnc ~type ~params
       ;; we use `merge` here to allow indidivual consumers to override feature
       ;; flags in special cases
       ~(merge default-opts opts)
       ~@body)))
```

Components would then use `my-app.lib/defnc` instead of `helix.core/defnc` to
define components. It has the same API as helix, but with the `:fast-refresh`
feature enabled by default. Nice!
