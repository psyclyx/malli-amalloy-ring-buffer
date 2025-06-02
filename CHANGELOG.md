# Changelog

## 0.1.36 (tag v0.1.36) - 2025-06-01

Some fixes to cljdoc generation.

### Changed

- Raised `metosin/malli` and `clj-commons/ring-buffer` deps to root.
  - Should ensure that cljdoc sees them.

### Removed

- Removed explicit git tag instructions
  - Prevents SHA from being at least commit out of date.
  - Tag is still listed in installation instructions.

## 0.1.29 (tag v0.1.29) - 2025-06-01

### Added

- Build tooling
  - `build.clj`, accessible via `:build` alias
    - Standard build tasks
  - Clojars deployment
    - xyz.psyclyx/malli-amalloy-ring-buffer


### Changed

- Git tag naming convention
  - Git tags are now prefixed with `v` to better follow convention

## 0.0.1 (tag 0.0.1) - 2025-06-01

_First release._
