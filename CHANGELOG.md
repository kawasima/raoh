# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.6.0]

### Added

- **jspecify `@NullMarked` nullness contract** across the `raoh` core module. Consumers
  running null analysis (Eclipse JDT automatic mode, NullAway, IntelliJ) now receive
  precise, declared contracts instead of guessed ones. This resolves the wall of
  "Null type safety … unchecked conversion to `@Nonnull`" warnings that JDT previously
  emitted on every `MapEncoders.property(...)` getter ([#43](https://github.com/kawasima/raoh/issues/43)).
- **NullAway build gate** (`mvn -Pnullcheck`) that validates the core module's nullness
  contract. Requires JDK 25 (Error Prone does not yet support JDK 26).

### Changed

- `Result`, `Decoder`, and `Encoder` type-parameter bounds were widened to
  `<T extends @Nullable Object>` so that nullable-valued results, decoders, and encoders
  (`ObjectDecoders.nullable`, `ObjectEncoders.nullable`, `Presence`, …) are expressible.
  The bound widening is invisible to the common non-null instantiation
  (e.g. `Result<Order>.value()` stays non-null).
- Decode value inputs are now typed `@Nullable Object`, reflecting that decoders already
  accept a missing/`null` value and return a `required` error rather than throwing.
- Encode nullable paths (`ObjectEncoders.nullable` / `withDefault`, `PropertyEncoder`,
  `MapEncoders.object`) now carry explicit `@Nullable` output type information.

### Compatibility

- Annotation-only change: **binary- and source-compatible** for consumers not running
  null analysis. Method descriptors and erasure are unchanged; runtime behavior is
  unchanged (the full test suite passes). No breaking changes.
