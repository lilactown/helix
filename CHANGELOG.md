# Changelog

## 0.0.15

## Fixed

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
