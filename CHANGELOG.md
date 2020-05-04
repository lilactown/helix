# Changelog

## Unreleased

### Added

- Added `deps.cljs` which specifies react, react-refresh
- `:class` prop for native components now accepts a seq(able) collection, e.g. `:class ["foo" "bar"]` is equivalent to `:class (str foo " " bar)`.


### Fixed

- Spread props using keyword `:&` works with non-native components
- Fix a bug where passing in non-existent value to a native element would pass in `nil`, when the element expected `js/undefined`
