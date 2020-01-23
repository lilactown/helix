# Experiments

Helix has a number of features that are actively being developed and
experimented with, in production. The way these features are exposed is through
feature flags that can be enabled on a per-component basis.

The `defnc` macro can optionally take an map of options after the parameter list
just like `defn`. In that map, passing a map of feature flags to the
`:helix/features` allows you to enable/disable certain features.

```clojure
(defnc MyComponent [props]
  {:helix/features {:some-flag true}}
  ...)
```

Here is the current list of experimental features and flags associated with them.


## Detect invalid hooks usage

This feature analyzes the body of a component defined with `defnc`, doing a best
effort to detect and throw a warning when hooks are used within a conditional or
loop.

This experimental feature can be enabled with the `:check-invalid-hooks-usage`
flag.

```clojure
(defnc MyComponent [props]
  {:helix/features {:check-invalid-hooks-usage true}}
  (when (:foo props)
    (hooks/use-effect :once (do-foo (:foo props))))
  ...)

;; Compile error! Invalid hooks usage `(hooks/use-effect :once (do-foo (:foo props)))`
```


## Fast refresh

[React Fast Refresh](https://github.com/facebook/react/issues/16604) is an
experimental React feature that enables hot reloading of React components while
preserving the state of components that use Hooks.

It's a first-class feature in React that allows you to reap the developer time
benefits of using a global store (hot reloading without losing state) while
still using local state in your components.

The feature works by analyzing the body of your component defined with `defnc`
and creating a signature based on the hooks used. When you change the hooks
used in the component, it will re-initialize the state; otherwise, it will
preserve the state.

To enable this feature, you will need to do two things. First, add
`helix.experimental.refresh` to your preloads (see shadow-cljs or figwheel docs)
and then in your component, enable the `:fast-refresh` flag.

```clojure
(defnc MyComponent [props]
  {:helix/features {:fast-refresh true}}
  ...)
```

You also will want to ensure that you do not have an `after-load` function that
re-renders the app, as that will re-mount the entire app, thus losing all of the
benefits of Fast Refresh.
