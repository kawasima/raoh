# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Versions `0.1.0`–`0.5.0` are summarized from git history; entries become more
detailed from the current development cycle onward.

## [Unreleased]

The accumulated development line since `0.5.0` (previously tracked as `0.6.0-SNAPSHOT`).
At release time this section is renamed to the chosen version with a date.

### Added

- **jspecify `@NullMarked` nullness contract** across the `raoh` core module, extended to the
  `raoh-json` and `raoh-jooq` sibling modules. Consumers running null analysis (Eclipse JDT / ecj,
  NullAway, IntelliJ) receive precise, declared contracts instead of guessed ones
  ([#43](https://github.com/kawasima/raoh/issues/43)).
- **NullAway build gate** (`mvn -Pnullcheck`) that validates the core module's nullness contract.
  Requires JDK 25 (Error Prone does not yet support JDK 26).
- **`MapEncoders.nullableProperty(...)`** — the nullable counterpart of `property(...)`, for a
  getter that may return `null`. The `null` branch is handled in the property layer: the value
  encoder is not invoked and `null` is written to the map.
- **`MapEncoders.propertyWithDefault(...)`** (value and `Supplier` overloads) — encodes a default
  value when the getter returns `null`, so the map entry is never `null`.
- **`MapEncoders.discriminate(...)`** — tagged-union encoding, mirroring the decoder side; rejects
  duplicate discriminator tags at construction ([#44](https://github.com/kawasima/raoh/issues/44)).
- **`nullableField(...)`** targeting `@Nullable T`, in core `MapDecoders`, `JsonDecoders`, and
  `JooqRecordDecoders` ([#46](https://github.com/kawasima/raoh/issues/46),
  [#53](https://github.com/kawasima/raoh/issues/53)).
- **Message overloads across the numeric decoders**, with `DecimalDecoder` brought to parity
  (`min` / `max` / `range` / `multipleOf` / sign / `scale`)
  ([#54](https://github.com/kawasima/raoh/issues/54)).
- **CI**: GitHub Actions workflow for build/test and the NullAway null-analysis gate.

### Changed

- **Encode API: null handling moved into the property layer.** Value encoders are now encoders of
  *non-null* values — `Encoder<T, O>` carries non-null type parameters, and `ObjectEncoders`
  factory *inputs* are explicitly `@NonNull` (e.g. `Encoder<@NonNull String, Object>`; the output
  stays a plain `Object` on purpose). All `null` / default handling lives in
  `MapEncoders.nullableProperty` / `propertyWithDefault`, mirroring `field` / `optionalField` on
  the decoder side. Keeping `@Nullable` out of `Encoder`'s type parameters lets consumers running
  either NullAway or ecj/JSpecify use the encoder API without null-analysis noise.
- The value-mapping methods now follow the standard PECS variance used by the JDK
  (`Stream.map`, `Comparator.comparing`, …): `Function<? super T, ? extends V>` /
  `BiFunction<...>` instead of the invariant forms. This covers `MapEncoders.property` /
  `nullableProperty` / `propertyWithDefault` getters, `Encoder.contramap`, `Decoder.map` /
  `flatMap` / `flatMapWithPath`, `Result.map` / `flatMap` / `fold` / `map2` / `traverse` /
  `traverseResults`, `Decoders.recover`, and `Validated` / `Valid` / `Invalid.map`. Binary-compatible
  (erasure unchanged) and a source-compatible widening. The N-ary `combine(...)` builders
  (`Combiner2`–`Combiner16`, `CombinerList`) keep the invariant form.
- `Result` and `Decoder` type-parameter bounds were widened to `<T extends @Nullable Object>` so
  that nullable-valued results and decoders (`ObjectDecoders.nullable`, `Presence`, …) are
  expressible. Invisible to the common non-null instantiation (e.g. `Result<Order>.value()` stays
  non-null).
- Decode value inputs are now typed `@Nullable Object`, reflecting that decoders already accept a
  missing/`null` value and return a `required` error rather than throwing.
- **`raoh-json` scopes Jackson as `provided`** (was `compile`), matching `raoh-jooq`. Because
  raoh-json exposes Jackson's `JsonNode` in its public API, consumers already supply Jackson 3 on
  their classpath; `provided` avoids pinning a specific Jackson 3.x version transitively
  ([#60](https://github.com/kawasima/raoh/issues/60)).
- `ListDecoder.toSet()` now preserves insertion order (previously unspecified via `Set.copyOf`)
  ([#69](https://github.com/kawasima/raoh/issues/69)).

### Removed

- **`ObjectEncoders.nullable(Encoder)`** — replace `property("x", getter, nullable(enc))` with
  `nullableProperty("x", getter, enc)`.
- **`ObjectEncoders.withDefault(Encoder, …)`** — replace
  `property("x", getter, withDefault(enc, default))` with
  `propertyWithDefault("x", getter, enc, default)`.
- **`StringDecoder.allowBlank()`** and the two-argument `StringDecoder(inner, base)` constructor —
  a vestige of an earlier design; `string()` accepts blank input by default
  ([#69](https://github.com/kawasima/raoh/issues/69)).
- **`ObjectDecoders.allowBlankString()` / `JsonDecoders.allowBlankString()`** — redundant with
  `string()`; the internal `enumOf` / `literal` / `discriminate` key readers now use `string()`
  ([#71](https://github.com/kawasima/raoh/issues/71)).

### Fixed

- Numeric decoders (`IntDecoder`, `LongDecoder`, `FloatDecoder`, `DoubleDecoder`, `DecimalDecoder`)
  reject a zero divisor and an inverted range at construction instead of failing silently
  ([#66](https://github.com/kawasima/raoh/issues/66)).
- Encode `discriminate` guards against a `null` variant key at tag injection
  ([#44](https://github.com/kawasima/raoh/issues/44)).

### Compatibility

- **Breaking (source):** the removed `ObjectEncoders.nullable` / `withDefault`,
  `StringDecoder.allowBlank()` + two-arg constructor, and `allowBlankString()` no longer compile;
  migrate as noted above.
- **Breaking (packaging):** `raoh-json` no longer brings Jackson transitively — add
  `tools.jackson.core:jackson-databind` (Jackson 3) to your own build.
- Otherwise runtime-unchanged (the full test suite passes). Consumers running null analysis need no
  build-config workarounds.

## [0.5.0] - 2026-03-31

### Added

- Encoder API (`net.unit8.raoh.encode`: `Encoder`, `ObjectEncoders`, `MapEncoders`) for encoding
  domain objects back into boundary representations, living in the core `raoh` module.
- `double_()`, `float_()`, and `bytes()` decoders in `ObjectDecoders`.
- Acceptance of `java.sql` temporal types (`Date`, `Time`, `Timestamp`) in the temporal decoders.
- `ObjectEncoders.withDefault()` for null-to-default encoding.
- Schema-versioning example (REST API with versioned decoders).

### Changed

- **Reorganized packages into `decode` / `encode` for symmetry** (breaking).
- **`StringDecoder.url()` now returns `Decoder<I, URI>`** instead of a string (breaking).
- Completed Javadoc across public methods.

## [0.4.1] - 2026-03-15

### Added

- Core `discriminate()` for tagged-union decoding, plus `discriminate()` in the Map / jOOQ decoders
  and `combine(List)` in the Map / JSON decoders.
- `Result.map3` / `map4` and `Tuple2`–`Tuple8` for lightweight result destructuring.
- Japanese messages (`messages_ja.properties`).

### Fixed

- `discriminate()` reports `NOT_ALLOWED` for unknown tag values and snapshots/sorts the allowed
  values in the error metadata.

## [0.4.0] - 2026-03-10

### Added

- **`raoh-gsh` domain construction guard** (split into runtime and weaver modules).
- String coerce methods on `StringDecoder`: `toInt()` / `toLong()` / `toDecimal()` / `toBool()`.
- `Path.of(String...)` factory.

### Changed

- Renamed `Combiner.apply()` to `map()` for consistency with the `map` / `flatMap` convention.
- Extracted `ObjectDecoders` from `MapDecoders` and `JooqRecordDecoders`.
- `nonBlank()` now reports its own `BLANK` error code, distinct from `REQUIRED`.
- `Err.toString()` renders the root path as `"/"`; `Ok` gets a matching `toString()`.

## [0.3.1] - 2026-03-08

### Added

- `ListDecoder` `contains` / `containsAll` / `unique` constraints.
- `oneOf` constraint on `StringDecoder`, `IntDecoder`, and `LongDecoder`.
- `BoolDecoder` `isTrue()` / `isFalse()` constraints.
- Temporal constraint chain (`before` / `after` / `between`) for the date/time decoders.
- `CombinerList` for combining more than 16 decoders.
- Locale-aware message resolution.

### Fixed

- Split `MISSING_ELEMENT` / `MISSING_ELEMENTS` and included the missing/duplicate values in the
  corresponding error messages; added the missing built-in codes to `MessageResolver.DEFAULT`.

## [0.3.0] - 2026-03-07

### Added

- **`JooqRecordDecoders`** for decoding jOOQ `Record` input.
- Membership REST example (user/group management).
- `Result.fail` root-path helpers and `flatMapWithPath` for path-aware error handling.
- Apache License 2.0; Maven Central / Javadoc / license / Java-version badges.

### Changed

- Use `Decoder.list()` for variable-length lists.

## [0.2.0] - 2026-03-06

### Added

- **JSON decoders for Jackson `JsonNode` input** (`raoh-json`), with comprehensive JSON/Map decoder
  tests and `package-info.java` documentation.

## [0.1.0] - 2026-03-06

### Added

- Initial public release: the core decoder model (`Decoder`, `Result` / `Ok` / `Err`, `Path`,
  `Presence`), `Map<String, Object>` decoders, error model, a Spring Boot example, and a README with
  an Elm-decoder comparison.

[Unreleased]: https://github.com/kawasima/raoh/compare/v0.5.0...HEAD
[0.5.0]: https://github.com/kawasima/raoh/compare/v0.4.1...v0.5.0
[0.4.1]: https://github.com/kawasima/raoh/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/kawasima/raoh/compare/v0.3.1...v0.4.0
[0.3.1]: https://github.com/kawasima/raoh/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/kawasima/raoh/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/kawasima/raoh/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/kawasima/raoh/releases/tag/v0.1.0
