# Changelog

## Unreleased

### Added

- Added `deps.cljs` which specifies react, react-refresh
- `:class` prop for native components now accepts a seq(able) collection, e.g. `:class ["foo" "bar"]` is equivalent to `:class (str foo " " bar)`.
- Spread props can now take a JS object. This can help a lot in certain interop situations where a 3rd party library passes a JS object that are meant to be given as props to an element of your choice.


### Fixed

- Spread props using keyword `:&` works with non-native components
- Fix a bug where passing in non-existent value to a native element would pass in `nil`, when the element expected `js/undefined`
- Helix now works with the vanilla CLJS compiler, including figwheel projects


### Breaking

- Due to https://github.com/thheller/shadow-cljs/issues/709, the fix for vanilla CLJS
now requires that shadow-cljs projects emit ES6 code during development.
