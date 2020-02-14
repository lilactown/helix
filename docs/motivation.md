# Why Helix

Helix is a library which wraps [React](https://reactjs.org/), a JavaScript
library for building user interfaces.

The goals of Helix are:

- Provide an ergonomic, well-documented API for building components in 
ClojureScript
- Have as small of a runtime as possible
- Add as few new semantics on top of React as possible

Helix accomplishes this by providing a collection of macros for creating
components and elements.

Years ago, the only way to build stateful React components was to define a
JavaScript class that extended React’s `Component` class and implement various
lifecycle handlers. Most ClojureScript wrappers interop through this way of
building components. Since the release of [React Hooks](https://reactjs.org/docs/hooks-intro.html),
a modern component is a JavaScript function that takes in a props object, and
returns React Element objects.

```javascript
function MyComponent(props) {
  return React.createElement("div", null, "Hello, React");
}

React.createElement(MyComponent);
```

This is simple enough to translate to CLJS:

```clojure
(defn my-component
  [props]
  (react/createElement “div” nil “Hello, CLJS!”))

(react/createElement my-component)
```


React Hooks are motivating because they vastly simplify both the code required
to build components, and the mental model of how components work. Before,
understanding React’s lifecycle methods and how they mapped to how and when your
code would run was quite confusing. [React Hooks mental model](https://reactjs.org/docs/hooks-faq.html#how-do-lifecycle-methods-correspond-to-hooks) is smaller,
and removes an amount of subtle bugs that hampered many React applications and
libraries.

They also provide a migration path to being “safe” in Concurrent mode (e.g. your
app will have less bugs when trying to migrate to Concurrent mode).
[Concurrent mode](https://reactjs.org/docs/concurrent-mode-intro.html) opens up
a whole slew of fantastic UX and DX improvements that should not be ignored. 

However, two issues appear quickly when attempting to write CLJS applications
using the method above:

1. `props` is always passed in as a native JS object
2. React elements must always be passed a native JS object

So an example of a component that determines whether it’s “on” or “off” and changes its color would look like so:

```clojure
(defn my-component
  [props]
  (let [on? (goog.object/get props “on?”)]
    (react/createElement
      “div”
      #js {:style #js {:background (if on? “red” “black”)}}
      “Hello, CLJS!”)))

(react/createElement my-component #js {:on? true})
```

This is mechanically tiresome, but consistent. Because of this, a wrapping
library can help us easily handle converting to and from JS objects. Here’s how
that component would look in helix:

```clojure
(defnc my-component
  [{:keys [on?]}]
  (d/div
    {:style {:background (if on? “red” “black”)}}
    “Hello, CLJS!”))

($ my-component {:on? true})
```

The defnc macro does the work of coercing the props object into something that
can be destructured easily. The `div` macro (`d` here is an alias of `helix.dom`)
creates the “div” element and `$` macro creates a new `my-component` element,
handling coercion of the map literal we pass in into a JS object in the most
performant way possible.

Semantically, the two are equivalent. Helix only makes reading and writing
components in ClojureScript much easier.

The long term goal of helix is to build on these capabilities with additional
features to help developers write performant, correct code. An example in helix
today is an optional linter which will check your component for Hooks usage
which violate the [rules of hooks](https://reactjs.org/docs/hooks-rules.html).

```clojure
(defnc my-component
  []
  {:helix/features {:check-invalid-hooks-usage true}}
  (for [n (range 10)]
    (let [[count set-count] (hooks/use-state 0)]
      (d/button {:on-click #(set-count inc)} count))))
```

The above component will throw a compiler warning, saying that using a hook
inside of a loop is invalid. It’s nice to find out about that at compile time,
rather than at runtime!

Eventually, some of these features will be turned on by default. However,
[creating your own macro](./pro-tips.md#create-a-custom-macro) is easy, and in
large applications suggested in order to enable/disable these experimental
features, as well as add other compile-time processing of your components.

Some ideas of things you might want to build on top of helix’s component macro:


- Integration with a design library in order to document and test components
- Hiccup parsing
- Integration with a data loading solution that associates GraphQL queries with
components
- Adding your own team’s code style checking for components

The world is vast! I hope helix can help remove some of the complexity from it.
