# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Versions `0.1.0`–`0.5.0` are summarized from git history; entries become more
detailed from the current development cycle onward.

## [Unreleased]

## [0.7.0] - 2026-08-05

> **This is a breaking release, not a patch.** `FieldDecoder` is gone, the combiners take `CombinePart`
> values instead of `Decoder`s, `Decoders.strict(Decoder, Set)` is removed, and the builtin decoders
> are `final`.

### Added

- **`CombinePart<I, T>`** — an explicit component of a `combine(...)` schema, replacing `FieldDecoder`.
  A part is **not** a `Decoder`, which is the whole point: `field("age", int_())` declares the field
  it consumes, and an ordinary `Decoder` wrapper cannot take one, so it cannot quietly erase that
  declaration. Wrapping means composing inside the part; converting is deliberate, via `asDecoder()`.
  Build one with `CombinePart.named(name, decoder[, inputFields])` or `CombinePart.flat(decoder)`
  ([#114](https://github.com/kawasima/raoh/issues/114)).

- **`flat(...)`** in `MapDecoders`, `JsonDecoders` and `JooqRecordDecoders` — lifts a decoder that
  reads the same whole input into a combine component, which is how a flat JOIN row gets split
  across several decoders. This was previously the second role of `nested(...)`, documented in the
  tutorial alongside the first; the two need different types now, so `nested(...)` keeps its meaning (adapting a decoder for use as a
  field *value*) and `flat(...)` takes the other one
  ([#114](https://github.com/kawasima/raoh/issues/114)).

- **`InputFields<I>`** — enumerates the field names present in an input, so `strict` works on any
  representation rather than only on `Map`. `MapDecoders.MAP_FIELDS` and `JsonDecoders.JSON_FIELDS`
  are the built-in ones; implement it to bring `strict` to a boundary the library does not cover,
  and pass it to `Decoders.strict(dec, knownFields, inputFields)` or to
  `CombinePart.named(name, decoder, inputFields)`
  ([#113](https://github.com/kawasima/raoh/issues/113)).

- **`StringDecoder.normalize()` / `normalize(Normalizer.Form)`** — a transform that canonicalizes the
  decoded string, so the constraints written after it stop depending on how the client encoded the
  text. The same が is one code point composed and two decomposed, and filenames originating from
  HFS+ and some macOS, IME and clipboard paths deliver the decomposed form, so a `maxLength(20)` on
  a name field otherwise varies with the sender. The default is NFC; pass a `Normalizer.Form` for
  another. How much a form unifies differs — NFC and NFD unify canonically equivalent strings and
  keep compatibility distinctions, while NFKC and NFKD fold compatibility equivalents as well, which
  suits a search key and not a stored name. It is a transform rather than an implicit step inside
  `string()`, which keeps the choice of form with the caller and keeps the order meaningful:
  `maxLength(20).normalize()` checks the input as it arrived. Two things it does not do under any
  form: a variation sequence such as 葛 followed by U+E0101 is normalization-stable and still counts
  as two code points, and the arguments of later constraints are left alone — Java does not normalize
  string literals, so a decomposed literal passed to `oneOf` will not match a value normalized to NFC
  ([#106](https://github.com/kawasima/raoh/issues/106)).

- **Published-API diff in the build.** `japicmp` compares `raoh`, `raoh-json` and `raoh-jooq`
  against the last release during `verify`, and CI puts the per-module report in the job summary
  and the `japicmp-api-diff` artifact. Nothing used to report a change to the API surface, so the
  breaks in this release are in this file only because someone noticed them. Reporting only for
  now: before 1.0 the breaks are deliberate and frequent, and a build that fails on each one turns
  the exclusion list into the thing you edit to get back to green. At 1.0 the `breakBuild*` flags
  go to true and an intentional break needs an explicit exclusion
  ([#117](https://github.com/kawasima/raoh/issues/117)).

### Fixed

- **A schema can no longer lose a field by being wrapped.** `FieldDecoder` was both a `Decoder` and
  a carrier of schema metadata, so any ordinary `Decoder` wrapper erased the metadata and `strict()`
  then rejected a field the schema plainly contained — silently, with no compile error. Combiners now
  take `CombinePart` values, which are not `Decoder`s, so that wrapper is a compile error. The same
  fragility ran the other way: a new combinator on `Decoder` reintroduced the bug unless someone
  remembered to override it on `FieldDecoder`, which is why seven such overrides existed. Composition
  now happens inside a part, so there is nothing to keep in sync
  ([#114](https://github.com/kawasima/raoh/issues/114)).

- **`combine(...).strict(f)` now rejects unknown fields on the JSON boundary.** The explicit
  `JsonDecoders.strict(dec, knownFields)` always worked, but the ergonomic combiner form hardcoded
  the generic `Decoders.strict`, which only ever scanned `Map` input — so the two spellings of the
  same feature disagreed, and the convenient one silently accepted whatever it was given. The scan
  is now a capability of the field decoder rather than something the combiner assumes: `field()`
  binds a decoder to a field *on a particular boundary*, so it carries an `InputFields`, and
  `Combiner#strict()` recovers it from its components. The sixteen `Combiner*` records are
  untouched — their components, canonical constructors and value semantics are unchanged
  ([#113](https://github.com/kawasima/raoh/issues/113)).

- **`strict()` no longer rejects a valid field as `unknown_field`.** `Combiner#strict()` collects
  known field names by testing each sub-decoder with `instanceof FieldDecoder`, and two things
  broke that. Composing a field decoder — `field("age", int_()).refine(...)`, `.map(...)`,
  `.pipe(...)` — returned a plain `Decoder` and dropped the name. And `optionalField`,
  `optionalNullableField` and `nullableField` were never `FieldDecoder` to begin with, so any
  `strict()` schema containing an optional field rejected that field outright. `FieldDecoder` now
  overrides `map`, `flatMap`, `flatMapWithPath`, `pipe` and all three `refine` overloads with a
  covariant return type, and the three optional-field factories return a `FieldDecoder` in both
  `MapDecoders` and `JsonDecoders` — their declared return type stays `Decoder`, so this is binary
  compatible; the combinators reach the `FieldDecoder` overrides through their bridge methods even
  from a `Decoder`-typed reference. `list()` is deliberately not overridden: it changes the input
  type to `List<I>`, so a single field name no longer describes it
  ([#109](https://github.com/kawasima/raoh/issues/109)).

- **A refinement on a field now reports at the field's path.** `field("age", int_())` appends the
  name inside its own `decode`, so a combinator wrapped around it only saw the enclosing path: one
  decoder reported a type mismatch at `/age` but a refinement failure on the enclosing object, and
  one level down the failure landed on `/user` instead of `/user/age`. `FieldDecoder` now threads
  the field's path through `flatMap` (the rebase target), `flatMapWithPath`, `pipe` and all three
  `refine` overloads, so `field("age", int_()).refine(...)` and
  `field("age", int_().refine(...))` agree. Error **paths move** for those four combinators — code
  that keys off the old enclosing path needs updating
  ([#109](https://github.com/kawasima/raoh/issues/109)).

### Changed

- **`FieldDecoder` is removed**, along with the seven combinator overrides it carried. The field
  factories in `MapDecoders`, `JsonDecoders` and `JooqRecordDecoders` return `CombinePart`, and the
  sixteen `Combiner*` records plus `CombinerList` take `CombinePart` components. A part still decodes
  on its own — `field("age", int_()).decode(map)` — and appends its own name, so standalone and
  combined use share one path contract. Passing one where a `Decoder` is wanted needs an explicit
  `asDecoder()` ([#114](https://github.com/kawasima/raoh/issues/114)).

- **`JooqRecordDecoders.nested(...)` is renamed `flat(...)`**, matching the new distinction between
  reading a field's value and reading the same whole input
  ([#114](https://github.com/kawasima/raoh/issues/114)).

- **`Combiner#strict()` reports two failures separately.** A combiner containing a `flat(...)`
  component is refused because its declared field set is unknown; a combiner whose boundary has no
  `InputFields` — jOOQ, whose fields are named but whose `Record` has no scanner — is refused for
  that reason instead. Both happen when the strict decoder is assembled
  ([#114](https://github.com/kawasima/raoh/issues/114)).

- **`Decoders.strict(Decoder, Set)` is removed.** It was generic in the input type but only scanned
  `Map`, and that gap between what the signature promised and what the implementation did is what
  produced the bug above. Core now has only the boundary-agnostic
  `Decoders.strict(Decoder, Set, InputFields)`. Callers on `Map` input should use
  `MapDecoders.strict`, which is unchanged and already typed to `Map<String, Object>`.
  **Removing a published method breaks both compilation and linkage** — code compiled against
  0.6.0 that called it will fail with `NoSuchMethodError` until recompiled against
  `MapDecoders.strict`. Kept as a deliberate break rather than a deprecated bridge, so the
  misleading signature does not survive a deprecation cycle
  ([#113](https://github.com/kawasima/raoh/issues/113)).

- **`Combiner#strict()` and `strictFlatMap()` throw `IllegalStateException`** when no component
  carries an `InputFields` — a combiner built entirely from bare decoders has no way to tell a
  known field from an unknown one. Previously that case silently accepted everything on JSON and
  rejected everything on `Map`, since the known-field set came out empty. This is an assembly error
  rather than a data error, so it surfaces when the decoder is built rather than when it runs
  ([#113](https://github.com/kawasima/raoh/issues/113)).

- **`refine()` on a builtin decoder now returns that decoder's own type**, so a refinement no
  longer has to come last in a chain: `string().refine(...).minLength(3)` compiles where it
  previously did not, because `refine` is declared on `Decoder` and returned `Decoder<I, T>`. All
  three overloads are overridden on `BoolDecoder`, `DecimalDecoder`, `DoubleDecoder`,
  `FloatDecoder`, `IntDecoder`, `ListDecoder`, `LongDecoder`, `RecordDecoder`, `StringDecoder` and
  `TemporalDecoder`. Existing callers stay binary compatible through the compiler-generated bridge
  methods; a subclass that overrode `refine` would need source changes to recompile, which is moot
  now that these classes are `final` (see below)
  ([#110](https://github.com/kawasima/raoh/issues/110)).

- **The builtin decoders are `final`.** `BoolDecoder`, `DecimalDecoder`, `DoubleDecoder`,
  `FloatDecoder`, `IntDecoder`, `ListDecoder`, `LongDecoder`, `RecordDecoder`, `StringDecoder` and
  `TemporalDecoder` no longer permit subclassing. They were open by default rather than by design:
  every constraint runs through a private `chain(...)` helper, so a subclass could neither add a
  constraint in the same style nor intercept the existing ones — overriding `flatMapWithPath` caught
  `refine` and nothing else, not `minLength()`, not `email()`. Composition is the supported route,
  via the public constructor each class already takes an inner decoder through, or
  `StringDecoder.from(Decoder)`. **Breaks any existing subclass**, at both compile time and link
  time ([#115](https://github.com/kawasima/raoh/issues/115)).

- **The `ObjectDecoders` temporal decoders now accept ISO-8601 text**, the representation the
  matching `ObjectEncoders` factory writes, so a codec pair built over the neutral `Object` tree
  round-trips. `date()`, `time()`, `dateTime()`, `iso8601()` and `offsetDateTime()` parse a
  `String` with the same parse and the same failure message as `string().date()` and friends;
  unparseable text is now `invalid_format` where it used to be `type_mismatch`. `dateTime()` also
  accepts `java.sql.Timestamp`, closing the gap against `iso8601()`. `offsetDateTime()` gets no
  `java.sql` conversion on purpose — `Timestamp` carries no offset, so converting one would mean
  picking a zone for the caller ([#104](https://github.com/kawasima/raoh/issues/104)).

- **`StringDecoder.minLength` / `maxLength` / `fixedLength` now count Unicode code points** instead
  of UTF-16 code units, in both the comparison and the `actual` meta value. A supplementary-plane
  character — a kanji such as `𠮷`, an emoji — used to count as two, contradicting the `characters`
  / `文字` wording of the messages. Strings made only of BMP characters are unaffected; for the
  rest, `maxLength` is now more permissive and `minLength` stricter. The length guards inside
  `email()`, `url()` and `ip()` stay in UTF-16 units — they cap the size of the string before it is
  parsed or matched, and express neither a character count nor the length of the value as sent
  ([#105](https://github.com/kawasima/raoh/issues/105)).

## [0.6.0] - 2026-07-15

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

[Unreleased]: https://github.com/kawasima/raoh/compare/v0.7.0...HEAD
[0.7.0]: https://github.com/kawasima/raoh/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/kawasima/raoh/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/kawasima/raoh/compare/v0.4.1...v0.5.0
[0.4.1]: https://github.com/kawasima/raoh/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/kawasima/raoh/compare/v0.3.1...v0.4.0
[0.3.1]: https://github.com/kawasima/raoh/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/kawasima/raoh/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/kawasima/raoh/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/kawasima/raoh/releases/tag/v0.1.0
