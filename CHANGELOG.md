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

- **`MapEncoders.lazy(Supplier<Encoder>)`** — the encode counterpart of `Decoders.lazy`, for
  self-referential (recursive) encoders. Closes the last decode/encode asymmetry among the structural
  combinators: a recursive domain type (e.g. a tree) that decodes via `lazy` can now be encoded back
  the same way ([#94](https://github.com/kawasima/raoh/issues/94)).
- **`ObjectEncoders.bytes()` / `uuid()` / `uri()`** — the encode duals of the existing decoders.
  `bytes()` passes a `byte[]` through as-is (for JDBC binary columns); `uuid()` and `uri()` emit the
  canonical string form, round-tripping `StringDecoder.uuid()` / `uri()`. Also documents in
  `comparisons.md` that `object(property(...), ...)` — not a symmetric `combine` — is the intended
  encode idiom, and how to bridge a `Map<String, Object>` to a Jackson `JsonNode`
  ([#94](https://github.com/kawasima/raoh/issues/94)).
- **Schema-reuse guidance** (docs) — `comparisons.md` now documents how to cover Zod's
  `.merge()`/`.extend()`/`.pick()`/`.omit()`/`.partial()` in Raoh's nominal-typed model by extracting
  each field's value decoder into a variable and reusing it across related shapes (subset `combine`
  for pick/omit, extra fragments for merge/extend, `optionalNullableField` + `Presence` for PATCH).
  Raoh has no structural schema operators today; the fragment-reuse pattern is the recommended
  approach ([#95](https://github.com/kawasima/raoh/issues/95)).
- **`Decoder.refine(...)`** — a generic predicate-based refinement combinator (three overloads: a
  `code`/`message` pair, a metadata-carrying variant, and a fully caller-controlled `onFail` variant).
  Keeps the value unchanged on success and produces an `Issue` at the current path on failure, with a
  caller-supplied error code (no new built-in code) whose message survives `MessageResolver`. This is
  the ergonomic form of the common `flatMapWithPath` "keep the value, or fail with a domain rule"
  idiom, and is the direct analogue of Zod's `.refine()`. Refinement failures accumulate with sibling
  errors through `combine`. There is no encoder-side dual: the encode side is a total function with no
  failure channel ([#93](https://github.com/kawasima/raoh/issues/93)).
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
- **`EntryEncoder<T>` abstraction plus `MapEncoders.optionalProperty(...)` / `presenceProperty(...)`.**
  `EntryEncoder` writes zero-or-more keys into the output map; `PropertyEncoder` (always one key) is a
  special case. `optionalProperty` omits the key when the getter returns `null` (the encode dual of
  decode's `optionalField` → `Optional`, distinct from `nullableProperty` which writes `key: null`);
  `presenceProperty` round-trips the tri-state `Presence` (`Absent` → omit, `PresentNull` → write
  `null`, `Present(v)` → write the value) ([#41](https://github.com/kawasima/raoh/issues/41),
  [#61](https://github.com/kawasima/raoh/issues/61)).
- **`MapEncoders.mapOf(...)`** — encodes a homogeneous `Map<String, V>` by applying a value encoder
  to each value, the encode mirror of `ObjectDecoders.map(...)`
  ([#63](https://github.com/kawasima/raoh/issues/63)).
- **`nullableField(...)`** targeting `@Nullable T`, in core `MapDecoders`, `JsonDecoders`, and
  `JooqRecordDecoders` ([#46](https://github.com/kawasima/raoh/issues/46),
  [#53](https://github.com/kawasima/raoh/issues/53)).
- **Message overloads across the numeric decoders**, with `DecimalDecoder` brought to parity
  (`min` / `max` / `range` / `multipleOf` / sign / `scale`)
  ([#54](https://github.com/kawasima/raoh/issues/54)).
- **Custom-message overloads on `ListDecoder` and `RecordDecoder` constraints**
  (`nonempty` / `minSize` / `maxSize` / `fixedSize` / `contains` / `unique`, and the record
  size constraints), matching the string/numeric decoders. `ListDecoder.containsAll(T...)` is
  intentionally left out, mirroring the `StringDecoder.oneOf(String...)` varargs precedent
  ([#87](https://github.com/kawasima/raoh/issues/87)).
- **`JsonDecoders.double_()` / `float_()`** — the JSON boundary reached floating-point parity with
  `ObjectDecoders`, giving a primitive `double`/`float` (and the `DoubleDecoder`/`FloatDecoder`
  constraint API) instead of only `decimal()` → `BigDecimal`. JSON temporals stay on the canonical
  `string().iso8601()` / `date()` / `dateTime()` path (documented, no new primitives)
  ([#86](https://github.com/kawasima/raoh/issues/86)).
- **Typed, cast-free `variant()` / `discriminate(field, Variant...)` on the decode side**, in core
  `Decoders` and re-exported from `MapDecoders` / `JsonDecoders` / `JooqRecordDecoders`. Mirrors the
  encoder's `variant()` / `discriminate()`, removing the per-arm up-cast the `Map`-based form
  requires; rejects duplicate tags at construction. The `Map`-based overload stays for back-compat
  ([#82](https://github.com/kawasima/raoh/issues/82)).
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
- **`MapEncoders.object(...)` now accepts `EntryEncoder<T>...`** (was `PropertyEncoder<T>...`).
  Source-compatible — `PropertyEncoder` implements `EntryEncoder`, so `object(property(...), ...)`
  is unchanged — but binary-incompatible (the erased parameter type changed), so recompile against
  the new version ([#41](https://github.com/kawasima/raoh/issues/41)).

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
- `JsonDecoders.double_()` / `float_()` reject an out-of-range magnitude with `type_mismatch`
  instead of letting Jackson 3's strict `doubleValue()` / `floatValue()` throw out of `decode()`;
  `ObjectDecoders.double_()` / `float_()` were aligned to reject the same rather than silently
  returning `Infinity` (both keep `NaN` flowing through for range constraints to catch)
  ([#86](https://github.com/kawasima/raoh/issues/86)).

### Compatibility

- **Breaking (source):** the removed `ObjectEncoders.nullable` / `withDefault`,
  `StringDecoder.allowBlank()` + two-arg constructor, and `allowBlankString()` no longer compile;
  migrate as noted above.
- **Breaking (binary):** `MapEncoders.object(...)` changed its varargs element type
  (`PropertyEncoder<T>...` → `EntryEncoder<T>...`); source stays compatible but a recompile is
  needed. Otherwise the encoder additions (`optionalProperty` / `presenceProperty` / `mapOf`) are
  purely additive.
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
