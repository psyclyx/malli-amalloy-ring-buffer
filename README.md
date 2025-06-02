# malli-amalloy-ring-buffer
![Tests](https://github.com/psyclyx/malli-amalloy-ring-buffer/workflows/Tests/badge.svg)
[![Clojars Project](https://img.shields.io/clojars/v/xyz.psyclyx/malli-amalloy-ring-buffer.svg)](https://clojars.org/xyz.psyclyx/malli-amalloy-ring-buffer)
[![cljdoc badge](https://cljdoc.org/badge/xyz.psyclyx/malli-amalloy-ring-buffer)](https://cljdoc.org/d/xyz.psyclyx/malli-amalloy-ring-buffer)

A [metosin/malli](https://github.com/metosin/malli) schema implementation for [clj-commons/ring-buffer](https://github.com/clj-commons/ring-buffer), providing validation, generation, and transformation capabilities for ring buffers in Clojure and ClojureScript.

## Project status

**Alpha**

There is decent test coverage, but this hasn't yet been tested in
production. Use at your own risk. Issues and PRs are welcome!

## Installation

**Latest version**: `0.1.29` (tag `v0.1.29`)

Note: This library doesn't declare `metosin/malli` or `clj-commons/ring-buffer` as dependencies, but does require namespaces from both. Ensure they are on your classpath.

### Clojars

#### deps.edn

```clojure
xyz.psyclyx/malli-amalloy-ring-buffer {:mvn/version "0.1.29"}
```

#### Leiningen/Boot

```clojure
[xyz.psyclyx/malli-amalloy-ring-buffer "0.1.29"]
```

### Git tag

```clojure
xyz.psyclyx/malli-amalloy-ring-buffer {:git/url "https://github.com/psyclyx/malli-amalloy-ring-buffer"
                                       :git/tag "v0.1.29"
                                       :git/sha "94717732454a"}
```

## Usage

Snippets in this section assume the following requires:

```clojure
(require
  '[amalloy.ring-buffer :as rb]
  '[malli.core :as m]
  '[malli.error :as me]
  '[malli.generator :as mg]
  '[malli.registry :as mr]
  '[malli.transform :as mt]
  '[psyclyx.malli.amalloy.ring-buffer :as malli.rb])
```

### Registry

Make the schema accessible as `:amalloy/ring-buffer` by adding it to the default registry:
```clojure
(mr/set-default-registry!
  (mr/composite-registry
    (m/default-schemas)
    malli.rb/registry))
```

### Validation

```clojure
(m/validate [:amalloy/ring-buffer :int]
            (into (rb/ring-buffer 3) [1 2 3])) ; => true
```

```clojure
(m/validate [:amalloy/ring-buffer :int]
            [1 2 3]) ; => false
```

#### Capacity

Ring buffer capacity can be constrained using schema properties:

- `:capacity` - exact capacity requirement
- `:min-capacity` - minimum capacity bound
- `:max-capacity` - maximum capacity bound

Note that `:capacity` cannot be combined with min/max bounds.

##### Exact

```clojure
(m/validate [:amalloy/ring-buffer {:capacity 5} :int]
            (rb/ring-buffer 5)) ; => true
```

```clojure
(m/validate [:amalloy/ring-buffer {:capacity 5} :int]
            (rb/ring-buffer 10)) ; => false
```

##### Minimum bound

```clojure
(m/validate [:amalloy/ring-buffer {:min-capacity 5} :int]
            (rb/ring-buffer 5)) ; => true
```

```clojure
(m/validate [:amalloy/ring-buffer {:min-capacity 5} :int]
            (rb/ring-buffer 3)) ; => false
```

##### Maximum bound

```clojure
(m/validate [:amalloy/ring-buffer {:max-capacity 5} :int]
            (rb/ring-buffer 3)) ; => true
```

```clojure
(m/validate [:amalloy/ring-buffer {:max-capacity 5} :int]
            (rb/ring-buffer 10)) ; => false
```

### Error messages

Explanations can be humanized to meaningful error messages in English. Localization PRs are welcome!

```clojure
(->> [1 2 3]
     (m/explain [:amalloy/ring-buffer :int])
     me/humanize) ; => ["should be a ring buffer"]
```

```clojure
(->> (into (rb/ring-buffer 3) [1 2 3])
     (m/explain [:amalloy/ring-buffer {:capacity 5} :int])
     me/humanize) ; => ["should have capacity 5"]
```

```clojure
(->> (into (rb/ring-buffer 3) [1 2 3])
     (m/explain [:amalloy/ring-buffer {:min-capacity 4} :int])
     me/humanize) ; => ["should have capacity >= 4"]
```

```clojure
(->> (into (rb/ring-buffer 3) [1 2 3])
     (m/explain [:amalloy/ring-buffer {:min-capacity 1 :max-capacity 2} :int])
     me/humanize) ; => ["should have capacity between 1 and 2"]
```

```clojure
(->> (into (rb/ring-buffer 3) [1 2 3])
     (m/explain [:amalloy/ring-buffer :string])
     (me/humanize)) ; => [["should be a string"] ["should be a string"] ["should be a string"]]
```

### Generation
Ring buffers can be generated from schemas.

```clojure
(mg/generate [:amalloy/ring-buffer :int]) ; => #amalloy/ring-buffer [59 (-32326 -19097 1154648 94 -1 -67 -72204790)]
```

Capacity constraints are respected.

```clojure
(mg/generate [:amalloy/ring-buffer {:capacity 3} :int]) ; => #amalloy/ring-buffer [3 (3838072 503345 7160549)]
```

```clojure
(mg/generate [:amalloy/ring-buffer {:min-capacity 3} :int]) ; => #amalloy/ring-buffer [11 (1707179 -1 217 -3 -814 -71 644 -2 -561 0 118606)]
```

### Transformation
`:amalloy/ring-buffer` schemas support encoding/decoding with transformers.

Ships with `ring-buffer-transformer`, which transforms between ring buffers and sequentials.

#### Encoding

```clojure
(m/encode [:amalloy/ring-buffer :int]
          (into (rb/ring-buffer 5) [1 2 3])
          (malli.rb/ring-buffer-transformer)) ; => [1 2 3]
```

#### Decoding

```clojure
(m/decode [:amalloy/ring-buffer :int]
          [1 2 3]
          (malli.rb/ring-buffer-transformer)) ; => #amalloy/ring-buffer [3 (1 2 3)]
```

##### Capacity inference

When decoding collections without an exact `:capacity`, it will be inferred from the sequential being decoded and any capacity bounds.

```clojure
(m/decode [:amalloy/ring-buffer {:min-capacity 3} :int]
          [1 2 3 4 5]
          (malli.rb/ring-buffer-transformer)) ; => #amalloy/ring-buffer [5 (1 2 3 4 5)]
```

###### Underflow

When decoding sequences shorter than the required capacity, the ring
buffer is created with the specified capacity but only partially
filled:

```clojure
(m/decode [:amalloy/ring-buffer {:min-capacity 3} :int]
          [1 2]
          (malli.rb/ring-buffer-transformer)) ; => #amalloy/ring-buffer [3 (1 2)]
```

This applies to both `:capacity` and `:min-capacity` constraints.

###### Overflow

When the sequence being decoded exceeds `:capacity` or
`:max-capacity`, decoding will fail by default. This is to prevent
seemingly successful decodes from unintentionally dropping data.

```clojure
(m/decode [:amalloy/ring-buffer {:capacity 3} :int]
          (range 5)
          (malli.rb/ring-buffer-transformer)) ; => (0 1 2 3 4)
```

```clojure
(m/decode [:amalloy/ring-buffer {:max-capacity 3} :int]
          (range 5)
          (malli.rb/ring-buffer-transformer)) ; => (0 1 2 3 4)
```

This behavior can be disabled with `:overflow`.

```clojure
(m/decode [:amalloy/ring-buffer {:max-capacity 3} :int]
          (range 5)
          (malli.rb/ring-buffer-transformer {:overflow true})) ; => #amalloy/ring-buffer [3 (2 3 4)]
```

#### Nested transformations

Of course, malli transformers become much more useful when composed and used to transform larger structures.

```clojure
(m/encode
  [:map [:foo/xs [:amalloy/ring-buffer :int]]]
  {:foo/xs (into (rb/ring-buffer 5) [1 2 3])}
  (mt/transformer
    (mt/key-transformer {:encode name})
    (malli.rb/ring-buffer-transformer)
    (mt/string-transformer))) ; => {"xs" ["1" "2" "3"]}
```

```clojure
(m/decode
  [:map [:foo/xs [:amalloy/ring-buffer {:capacity 5} :int]]]
  {"xs" ["1" "2" "3"]}
  (mt/transformer
    (mt/key-transformer {:decode {"xs" :foo/xs}})
    (malli.rb/ring-buffer-transformer)
    (mt/string-transformer))) ; => {:foo/xs #amalloy/ring-buffer [5 (1 2 3)]}
```

## Development

### REPL

#### Editor (preferred)

There's a `.dir-locals.el` that should make CLJ and CLJS repls work
OOTB on Emacs + CIDER. Just `cider-jack-in-clj` or
`cider-jack-in-cljs` from a clojure-mode buffer.

For other editors, you'll need to make sure your editor is including
the `:libs` and `:dev` aliases, and is injecting whatever additional
middleware it needs. The `:cider` and `:nrepl` aliases may be useful
here. When in doubt, consult your editor's documentation.

As an alternative, starting an nREPL with cider support from the CLI
is likely sufficient to enable connecting from your editor.

#### nREPL + Cider

```shell
clj -M:libs:dev:nrepl:cider
```

This starts an nREPL that's usable from the CLI and accepts connections
from many editors.

To switch to a CLJS repl, require `repl` and evaluate `(repl/start-cljs)`.

### Run tests

#### NPM dependency

The ClojureScript test runner requires an npm dependency, which can
be installed with

```shell
npm i
```

#### All tests

```shell
clj -M:libs:test
```

#### Clojure

```shell
clj -M:libs:test --focus :clj
```

#### ClojureScript

```shell
clj -M:libs:test --focus :cljs
```

### Builds

#### Clean

```clojure
clj -T:build clean
```

#### Jar

```clojure
clj -T:build jar
```

#### Install

Requires a previously-built jar.

```clojure
clj -T:build install
```

#### CI (clean, test, build jar)

```clojure
clj -T:build ci
```

#### Deploy to Clojars

`CLOJARS_USERNAME` and `CLOJARS_TOKEN` must be set.

```clojure
clj -T:build ci && clj clj -T:build deploy
```

### Miscellaneous tools

#### Antq (identify outdated dependencies)

```clojure
clj -M:outdated
```

```clojure
clj -M:outdated --upgrade
```

#### cljstyle (code formatter)

```clojure
clj -M:cljstyle check
```

```clojure
clj -M:cljstyle fix
```

## Contributing

Issues and pull requests welcome!

### Release checklist

- [ ] Squash merge in to main (if needed)
- [ ] Deploy to Clojars
- [ ] Cut a new tag at commit used to deploy
- [ ] Update changelog
- [ ] Update version/tag/sha in readme

## Copyright

Copyright (c) 2025 Alice Burns

Released under the MIT license.
