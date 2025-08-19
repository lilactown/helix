# Changelog

## UNRELEASED

### Fixed

- clj-kondo hooks for DOM macros lint `&` correctly
- Fixed a regression in 0.2.0 where the new JSX transform broke components that
  spread all props and accepted children. It caused the spread to override any
  static children passed in.

## 0.2.1

### Fixed

* Extend JS Symbol using the primitive `symbol`

## 0.2.0

### Added

* #130 String keys are now supported in props, which when given to a DOM element
  will pass those verbatim (i.e. without camelCasing)
* `helix.hooks/use-id` that wraps new base React hook `useId` (@rome-user)
* #131 `helix.core/creat-ref` that wraps `React.createRef` similar to use-ref
  (@rome-user)
* #146 / #142 `:style/indent` metadata on `$`, `defnc`, `defnc-`, `defhook`, and all DOM macros
* DOM macros are now written literally; this helps move towards self hosted CLJS support

### Fixed

* #136 Fix issue with loading helix in Node.js due to depending on `window` being
  available (@rome-user)

### Breaking

* #127 `helix.hooks/wrap-fx` is now private (@rome-user)

## 0.1.10

### Fixed

* Replace deprecated `goog.object/extend` with `js/Object.assign`

### Added

* `:wrap` can now be passed in the metadata map as well as the options map.
A clj-kondo lint warning will occur when using `:wrap` in the options map.
See https://github.com/lilactown/helix/issues/110
* clj-kondo hooks are now included when using git deps
* Add support for `:define-factory` in clj-kondo hooks
* Add `hgroup` element to `helix.dom`

## 0.1.9

### Fixed

* `defnc-` no longer requires one to refer `defnc` as well
* `provider` no longer emits extern warnings
* clj-kondo hooks no longer remove metadata like line numbers when analyzing

## 0.1.8

### Fixed

* Passing a function to the setter returned by `use-state` caused an infinite loop

## 0.1.7

### Added

* Added docstrings to many functions and macros
* `helix.dom/marker` DOM macro
* `defnc-` which defines a private functional component in the namespace it's called in

### Fixed

* `defcomponent` now adds the `displayName` property as a static, so should show up in devtools now
* Mark `assoc-some` as private
* Fixed an issue with fast-refresh where JS values in hooks would incorrectly
  invalidate on every refresh
* The setter function returned by `use-state`, when passed multiple arguments, will check whether the first argument is callable before calling `apply` on it.
* `defcomponent` now properly accepts docstring

## 0.1.6

### Added

- Allow metadata to be set on vars created with `defnc` as a map after the name and before the args list, just like `defn`
- clj-kondo hooks for `defnc`, `$` et al.

## 0.1.5

### Breaking

- Removed `deps.cljs` which declares dependencies on React. This was causing unnecessary build warnings/errors.
Consumers should install `react` and `react-dom` themselves. This will not break existing projects.

## 0.1.4

### Fixed

- Correctly compare props when using `helix.core/memo` without a custom comparator combined with `:define-factory`
- CLJS 1.10.891 compat: don't assume `goog.object` is globally available

## 0.1.3

### Fixed

- Fix invalid hooks check when hooks appear inside literals, i.e. `(when x [(use-foo)])`
- Fix recursive calls to components wrapped in HOC
- Detect hooks inside of JS literals

## 0.1.2

### Fixed

- Fix invalid hooks check false warnings when a `use-` symbol appears in a
quoted form (@SevereOverfl0w)
- Allow a single var/binding to be passed to `use-callback`

### Added

- Invalid hooks check now happens in `defhook` forms
- `fnc` for constructing anonymous components

## 0.1.1

### Fixed

- Fix performance issue with `use-subscription` due to calling `pr-str` on subscription state every render

## 0.1.0

### Breaking

- Enable `:check-invalid-hooks-usage` by default

### Added

- `helix.dom/$d` helper for rendering components with DOM props
coercion; e.g. `($d material-ui/button {:class ["foo" "bar"]})`

## 0.0.15

### Fixed

- (@jimmyhmiller) Fix `use-subscription` infinite render

## 0.0.14

### Fixed

- (@alidlorenzo) Correctly handle objects when passing a vector into React Native `style` prop
- (@aiba) Don't log result of `performFastRefresh`
- (@stanislas) Don't camel case CSS custom properties
- (@wilkerlucio) Declare DOM macros to allow Cursive's static analysis to detect them
- (@wilkerlucio) Correctly declare deps in deps.edn
- (@jimmyhmiller) Change from js/goog.DEBUG to goog/DEBUG to help with infer-externs
- (@jimmyhmiller) Add style/indent :defn to hooks that have deps
- use-debug-value correctly refers React's useDebugValue

### Added

- `use-subscription` hook that respects Clojure equality

### Breaking

- mark component-fn private when using factory functions

## 0.0.13

### Fixed

- Passing in `:ref` and `:key` props with factory functions
- Docstrings for `defnc` and `$`

## 0.0.12

### Added

- for "native" elements the `:style` prop now accepts an arbitrary JS object as well as a map or vector


## 0.0.11

### Added

- Added `deps.cljs` which specifies react, react-refresh
- `:class` prop for native components now accepts a seq(able) collection, e.g. `:class ["foo" "bar"]` is equivalent to `:class (str foo " " bar)`.
- Spread props can now take a JS object. This can help a lot in certain interop situations where a 3rd party library passes a JS object that are meant to be given as props to an element of your choice.


### Fixed

- Spread props using keyword `:&` works with non-native components
- Fix a bug where passing in non-existent value to a native element would pass in `nil`, when the element expected `js/undefined`
- Helix now works with the vanilla CLJS compiler, including figwheel projects
- `:define-factory` works with `:fast-refresh` feature flag


### Breaking

- Due to https://github.com/thheller/shadow-cljs/issues/709, the fix for vanilla CLJS
now requires that shadow-cljs projects emit ES6 code during development.
