# Pro Tips

This is a grab-bag document for useful hints and tips when building and
maintaining React applications using helix.


## Memoizing components

Performance is critical for a lot of user interfaces. React provides tools to
memoize calculations a number of different ways, including _whole components_.

**NOTE**: Memoization has its own cost! Do not memoize just because; profile, 
then optimize!

With class components, you can use the `componentShouldUpdate` method to
tell React whether to call the `render` method. With function components (as
created by `defnc`), React provides the top-level `memo` higher-order component
that you can wrap your component in.

One thing to note is that React's [memo](https://reactjs.org/docs/react-api.html#reactmemo)
function allows you to pass in a custom comparator, which you can use to
determine whether to re-render based on the old props and new props. _This is
not always required._ By passing in no comparator, React will default to doing a
referential identity check on each prop. Most often, this is what you want!

If you do want to customize the props comparator, the `memo` function will
receive the _JS props objects_, not a props map like we expect to be passed to
our `defnc` component.

Helix provides `helix.core/memo` as a wrapper around React's `memo` function
which handles the coercion to a props map just like `defnc` does. In the cases
where you need to use a custom comparator, use that instead!


## Don't use deep equals

On the topic of **Memoizing components** above, we mentioned custom comparators.
One thing we should avoid doing unless absolutely necessary is a _deep
comparison_ of objects. Here's an example:

```clojure
(defnc my-component
  [{:keys [data]}]
  {:wrap [(helix.core/memo =)]}
  (d/div (pr-str data)))
```

Looks simple and easy! But if our `data` prop isn't identical to the previous,
`=` will do a recursive walk of the structure until it bottoms out in a value
that is equal, or can't possibly be equal (different type, or a primitive like
string/number/etc. that aren't equal).

For a component that we're supposedly trying to optimize, this can end up adding
a ton of extra work each render! Its worst case scenario is if the data _is
different_ but structurally similar, which in my experience tends to be true;
the data we keep in state or returned by our servers doesn't tend to change
drastically in shape render-to-render.

Instead, it's usually better to compare props using `identical?` (which
`React.memo` and `helix.core/memo` do by default) and try and maintain
referential identity when data has not changed. For instance, if you're
computing data during render, wrap it in `use-memo` to only re-compute it when
its dependent data changes. Same thing for event handlers and other functions
you pass in to components.

It can be tempting to add a custom comparator to ignore things like event
handlers and other functions passed in as props, however this can often lead to
subtle bugs later on where the handler needs to close over a new value or
changes in some other way and your component ignores the render triggered by
this change. Instead, `use-callback` is your friend!


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
  (let [[docstring params body] (if (string? params)
                                  [params (first body) (rest body)]
                                  [nil params body])
        opts? (map? (first body)) ;; whether an opts map was passed in
        opts (if opts?
               (first body)
               {})
        body (if opts?
               (rest body)
               body)
        ;; feature flags to enable by default
        default-opts {:helix/features {:fast-refresh true}}]
    `(helix.core/defnc ~type ~@(when docstring [docstring]) ~params
       ;; we use `merge` here to allow indidivual consumers to override feature
       ;; flags in special cases
       ~(merge default-opts opts)
       ~@body)))
```

Components would then use `my-app.lib/defnc` instead of `helix.core/defnc` to
define components. It has the same API as helix, but with the `:fast-refresh`
feature enabled by default. Nice!
