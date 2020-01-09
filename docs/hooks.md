# Hooks

[React Hooks](https://reactjs.org/docs/hooks-intro.html) are a way to manage
state, subscribe to external data sources, read from the DOM, and do other
side-effects in a way that coordinates with React's mount, render, commit
lifecycle.

Components created with the [`defnc`](https://github.com/Lokeh/helix/blob/master/docs/creating-components.md#creating-components) macro are function components, and thus are
able to leverage all of the fundamental Hooks as well as 3rd party libraries
which distribute custom Hooks.

`helix.hooks` is a namespace that exports wrappers for all of the fundamental
Hooks (`useState`, `useEffect`, etc.) that provide a Clojure-friendly syntax and
some additional sugar to aid with repetitious tasks.


## Maintaining state

`helix.hooks/use-state` takes one argument (either an initial value, or a
function that returns an initial value) and returns a vector tuple of the value
and a *setter* function.

The *setter* function will take either a new value, or a function that will
take the current value and return the new one; the same behavior as
`React.useState`.

```clojure
(let [[count set-count] (use-state 0)]
  (prn count) ;; => 0
  ...
  (set-count inc)) ;; Next render `count` will be 1
```

In addition to this, `use-state` also allows you to pass _additional arguments_
to the setter function, similar to `clojure.core/swap!`.

```clojure
(set-count + 2 3 4) ;; => Next render `count` will be 10
```

See the [useState](https://reactjs.org/docs/hooks-state.html) documentation for
additional rationale and documentation.

### Reducers

For more complex state flows, `use-reducer` can often be a clearer way to write
the state transitions based on each action.

```clojure
(defn reducer [state action]
  (case action
    :add (inc state)
    :minus (dec state)
    :double (* 2 state)))

(let [[count dispatch] (use-reducer reducer 0)]
  (prn count) ;; => 0
  ...
  (dispatch :minus)) ;; => Next render `count` will be -1
```

You can also use multi-methods with `use-reducer` to allow your apps actions to
be extended ad-hoc.

```clojure
(defmulti reducer (fn [_ action] action))

(defmethod reducer :add [state _]
  (inc state))

(defmethod reducer :minus [state _]
  (dec state))

(defmethod reducer :double [state _]
  (* 2 state))

(let [[count dispatch] (use-reducer reducer 0)]
  ...)
```

### Refs

For mutable state which shouldn't trigger a re-render, React provides the
`useRef` Hook. Similarly, `helix.hooks` exports a `use-ref` Hook.

The difference is that, while you can access the current value via the
`"current"` property on the object, you can also use the core library's `deref`,
`swap!` and `reset!`.

```clojure
(let [ref (use-ref "foo")]
  (prn ref) ;; => #js {:current "foo"}
  (prn @ref) ;; => "foo"
  ...
  (reset! ref "bar") ;; => #js {:current "bar"}
  (prn @ref)) ;; => "bar"
```

Again, refs will not trigger a re-render on state change.


## Doing side effects

`use-effect` and `use-layout-effect` are both functions which can be used to
coordinate external side effects like subscribing to data sources, reading from
the DOM, etc.

The docs on [Using the Effect Hook](https://reactjs.org/docs/hooks-effect.html)
are an excellent guide on the rationale, use cases, and general concepts.

`use-effect` and `use-layout-effect` have a couple syntactic differences that
will be documented here.

First, they are a macro that does not require passing a 0-arity anonymous
function. Instead, you write just the body of the function inside the macro.

Second, the argument that helps determines whether an effect should be executed
is the _first_, not the second argument.

```clojure
(use-effect
  [foo bar]
  (js/console.log foo bar))
```

The above effect will run on initial render, and anytime `foo` or `bar` change.

In order to have it run _every render_, you may pass a keyword, `:always` to the
first argument.

```clojure
(use-effect
  :always
  (js/console.log foo bar))
```

To only run the effect once on mount, you may pass the `:once` keyword:

```clojure
(use-effect
  :once
  (js/console.log foo bar))
```

Finally, helix can use the ClojureScript analyzer to determine which local
bindings are used in your Effect and automatically add those to your dependency
vector. To do this, use the keyword `:auto-deps`:

```clojure
;; this is equivalent to the first one which explicitly listed `[foo bar]`
(use-effect
  :auto-deps
  (js/console.log foo bar))
```

## Optimizations

To avoid calculating new values every render, `use-memo` and `use-callback` are
provided as wrappers for [useMemo](https://reactjs.org/docs/hooks-reference.html#usememo)
and [useCallback](https://reactjs.org/docs/hooks-reference.html#usecallback).

Similar to `use-effect` and `use-layout-effect`, `use-memo` and `use-callback`
takes the dependency vector as the first argument and accepts keywords `:once`,
`:always` and `:auto-deps`.

```clojure
;; only calculates on mount or if `foo` or `bar` change
(use-memo
  [foo bar]
  (+ foo bar))

;; calculates once on mount
(use-memo
  :once
  (+ foo bar))

;; only calculates on mount or if `foo` or `bar` change
(use-memo
  :auto-deps
  (+ foo bar))

;; same for use-callback
(use-callback
  :auto-deps
  (fn [baz]
    (+ foo bar baz)))
```

## Other miscellaneous

- `use-context` is the same as [useContext](https://reactjs.org/docs/hooks-reference.html#usecontext)
- `use-imperative-handle` is the same as [useImperativeHandle](https://reactjs.org/docs/hooks-reference.html#useimperativehandle)
- `use-debug-value` is the same as [useDebugValue](https://reactjs.org/docs/hooks-reference.html#usedebugvalue)

