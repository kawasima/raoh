# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.6.0]

### Added

- **jspecify `@NullMarked` nullness contract** across the `raoh` core module. Consumers
  running null analysis (Eclipse JDT / ecj, NullAway, IntelliJ) now receive precise, declared
  contracts instead of guessed ones ([#43](https://github.com/kawasima/raoh/issues/43)).
- **NullAway build gate** (`mvn -Pnullcheck`) that validates the core module's nullness
  contract. Requires JDK 25 (Error Prone does not yet support JDK 26).
- **`MapEncoders.nullableProperty(...)`** — the nullable counterpart of `property(...)`, for a
  getter that may return `null`. The `null` branch is handled in the property layer: the value
  encoder is not invoked and `null` is written to the map.
- **`MapEncoders.propertyWithDefault(...)`** (value and `Supplier` overloads) — encodes a default
  value when the getter returns `null`, so the map entry is never `null`.

### Changed

- **Encode API: null handling moved into the property layer.** Value encoders are now encoders of
  *non-null* values — `Encoder<T, O>` carries non-null type parameters, and `ObjectEncoders`
  factory *inputs* are explicitly `@NonNull` (e.g. `Encoder<@NonNull String, Object>`; the output
  stays a plain `Object` on purpose). All `null` / default handling lives in
  `MapEncoders.nullableProperty` / `propertyWithDefault`, mirroring `field` / `optionalField` on
  the decoder side. Keeping `@Nullable` out of `Encoder`'s type parameters lets consumers running
  either NullAway or ecj/JSpecify use the encoder API without null-analysis noise (no
  "Null constraint mismatch", no unchecked-conversion warnings on `property(..., string())`).
- The value-mapping methods now follow the standard PECS variance used by the JDK
  (`Stream.map`, `Comparator.comparing`, …): `Function<? super T, ? extends V>` /
  `BiFunction<...>` instead of the invariant forms. This covers `MapEncoders.property` /
  `nullableProperty` / `propertyWithDefault` getters, `Encoder.contramap`, `Decoder.map` /
  `flatMap` / `flatMapWithPath`, `Result.map` / `flatMap` / `fold` / `map2` / `traverse` /
  `traverseResults`, `Decoders.recover`, and `Validated` / `Valid` / `Invalid.map`. A method
  reference on a supertype, or one returning a subtype, now adapts without an explicit cast.
  Binary-compatible (erasure unchanged) and a source-compatible widening. The N-ary `combine(...)`
  builders (`Combiner2`–`Combiner16`, `CombinerList`) keep the invariant form.
- `Result` and `Decoder` type-parameter bounds were widened to `<T extends @Nullable Object>` so
  that nullable-valued results and decoders (`ObjectDecoders.nullable`, `Presence`, …) are
  expressible. Invisible to the common non-null instantiation (e.g. `Result<Order>.value()` stays
  non-null).
- Decode value inputs are now typed `@Nullable Object`, reflecting that decoders already accept a
  missing/`null` value and return a `required` error rather than throwing.

### Removed

- **`ObjectEncoders.nullable(Encoder)`** — replace `property("x", getter, nullable(enc))` with
  `nullableProperty("x", getter, enc)`.
- **`ObjectEncoders.withDefault(Encoder, …)`** — replace
  `property("x", getter, withDefault(enc, default))` with
  `propertyWithDefault("x", getter, enc, default)`.

### Compatibility

- The removed `ObjectEncoders.nullable` / `withDefault` are a **breaking source change** (they no
  longer compile); migrate to the property-layer forms above.
- Otherwise runtime-unchanged (the full test suite passes). Consumers running null analysis need no
  build-config workarounds: idiomatic `property(..., string())` / `nullableProperty(..., string())`
  with method-reference getters compile clean under both NullAway and ecj/JSpecify.
