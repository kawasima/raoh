# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Versions `0.1.0`–`0.5.0` are summarized from git history; entries become more
detailed from the current development cycle onward.

## [Unreleased]

> **Read the Breaking entries under Changed before upgrading.** `pattern()` takes the pattern text
> instead of a `java.util.regex.Pattern`, and refuses a pattern past the limits the Raoh
> Specification 0.9.0 sets. `iso8601()` no longer accepts `24:00:00` with a fraction.

### Added

- **`DecimalText`**, the reader of decimal text that `StringDecoder.toDecimal()` uses: it decides
  the form toDecimal() accepts and gives the value `new BigDecimal(String)` gives, scale
  included, in one pass and without a regular expression. It is public so that an input model,
  such as raoh-json reading a JSON number as written, reads numbers the same way
  ([#161](https://github.com/raoh-project/raoh-java/issues/161)).

### Changed

- **Breaking: `StringDecoder.pattern()` takes a pattern of the specification's language, as text.**
  `pattern(Pattern)`, `pattern(Pattern, String)` and `pattern(Pattern, String, String)` are removed;
  `pattern(String)`, `pattern(String, String)` and `pattern(String, String, String)` replace them.
  The Raoh Specification defines the argument as a pattern of one fixed language, the one Souther
  uses, and `java.util.regex.Pattern` accepts more than that: back references, lookarounds,
  property classes such as `\p{L}` that follow the JDK's Unicode data, and flags carried beside
  the text. Text outside the language is now refused with `IllegalArgumentException` when the
  decoder is built. A value is matched against the set of strings the pattern means, in one pass
  over the value, so matching takes time linear in the length of the value and no pattern
  backtracks. `\d`, `\w` and `\s` are ASCII. The match is still of the whole value, and the
  `pattern` metadata is still the pattern text. To migrate, pass the text that was given to
  `Pattern.compile`; a pattern that relied on a flag or on one of the refused constructs has to be
  rewritten or moved to `refine()`
  ([#171](https://github.com/raoh-project/raoh-java/issues/171)).
- **Breaking: a pattern is admitted within three limits.** The Raoh Specification 0.9.0 sets the
  limits every implementation holds to, counted from the pattern text: a repetition count of at
  most 134217727, groups nested at most 200 deep, and at most 250000 states once the repetitions
  are written out (a set of characters or an anchor is one, a choice of n alternatives is one plus
  one more than each, `A{n,m}` is m times `A` plus one, `A{n,}` is n + 1 times `A` plus one, and
  the pattern is one more). `a{249998}` is admitted and `a{249999}` and `(a{500}){500}` are not. A
  pattern past a limit throws `IllegalArgumentException` when the decoder is built, with a message
  that names the limit and does not call it "not a pattern". Every pattern within the limits is
  admitted; the matcher no longer refuses one for its own size
  ([#171](https://github.com/raoh-project/raoh-java/issues/171)).
- **Breaking: `iso8601()` reads `24:00:00` only with no fraction.** 0.8.0 accepted an all-zero
  fraction (`2024-01-15T24:00:00.0Z`, `24:00:00.000000000+09:00`) as the start of the next day,
  because `DateTimeFormatter.ISO_INSTANT` did. The Raoh Specification 0.9.0 and Souther refuse
  hour 24 with any fraction, and these are now `invalid_format`
  ([#160](https://github.com/raoh-project/raoh-java/issues/160)).
- **`toLowerCase()`, `toUpperCase()` and `normalize()` follow Unicode 18.0.0 on every JDK.** They
  used `String.toLowerCase(Locale.ROOT)`, `String.toUpperCase(Locale.ROOT)` and
  `java.text.Normalizer`, so the result followed the Unicode version of the running JDK (16.0 on
  JDK 25), and a final sigma was decided by the JDK's word boundaries instead of Unicode's
  `Final_Sigma` condition: `Α1Σ` became `α1ς` where Unicode gives `α1σ`. Both now apply the
  Unicode 18.0.0 default case conversion, with no language tailoring, and Unicode 18.0.0
  normalization in all four forms. `normalize(Normalizer.Form)` keeps its signature; the
  argument only names the form
  ([#166](https://github.com/raoh-project/raoh-java/issues/166)).
- **The text rules come from 199x-notation.** `raoh` now depends on
  `net.unit8.199x:199x-notation` 0.1.0, the implementation of the text rules Raoh and Souther share:
  case conversion, normalization, the `White_Space` set, order and length in scalar values, the
  temporal grammar and the pattern language. Raoh's own copies of the `White_Space` set and of
  the temporal regular expressions are gone. `iso8601()` no longer builds its value with
  `DateTimeFormatter.ISO_INSTANT`: the shared grammar decides that `24:00:00` with no fraction is
  the start of the next day and that second `60` is refused, and `offsetDateTime()` reads a
  separate form in which hour `24` is not a time. Apart from the fraction after `24:00:00` above, what the
  temporal decoders accept is unchanged
  ([#160](https://github.com/raoh-project/raoh-java/issues/160)).
- **`CodePointOrder.compare` places an unpaired surrogate above every character of the basic
  multilingual plane.** It compared an unpaired surrogate as the code point of its own value. The
  order of strings that hold no unpaired surrogate is unchanged.

### Fixed

- **`ulid()` accepts either case and refuses a value past 128 bits.** It matched
  `[0-9A-HJKMNP-TV-Z]{26}`, so a lower-case ULID was refused although Crockford's base 32 is
  case-insensitive, and `80000000000000000000000000`, which needs 130 bits, was accepted. It now
  takes Crockford's base 32 in either case with a first character of `0` to `7`, so
  `7ZZZZZZZZZZZZZZZZZZZZZZZZZ` is the largest. The string is given unchanged
  ([#165](https://github.com/raoh-project/raoh-java/issues/165)).
- **`DecimalDecoder.multipleOf()` decides any two scales instead of throwing.** It tested
  `value.remainder(n)`, which aligns the two scales by building a power of ten as large as their
  difference, so `1E+100` against `7E-2147483647`, or `1E+2147483647` against `3` or `0.1`, threw
  `ArithmeticException` at decode time. Divisibility is now decided from the unscaled values and
  the difference of the scales, taken as a `long`: by modular exponentiation of ten when the
  value's exponent is the larger, and by counting the value's trailing zeros when it is the
  smaller. The answer is the same wherever `remainder` gave one
  ([#168](https://github.com/raoh-project/raoh-java/issues/168)).
- **`toDecimal()` reads long digit strings without quadratic time.** `new BigDecimal(String)` folds
  the digits into the magnitude a group at a time, each fold going over the whole magnitude, so
  400,000 digits took over two seconds and four million several minutes. `DecimalText` splits the
  digits in halves and joins them as `high × 10^k + low` with the powers of ten worked out once:
  400,000 digits now take tens of milliseconds and four million about a second. The cost still
  grows faster than linearly, and the decoder still sets no limit of its own: bound the length
  of untrusted input first, as in `string().maxLength(40).toDecimal()`. That is the policy #161
  asked for: a conversion's cost is kept from growing pathologically, and how many digits are
  valid stays the caller's to say
  ([#161](https://github.com/raoh-project/raoh-java/issues/161)).

## [0.8.0] - 2026-09-27

> **Read the two Breaking entries under Changed before upgrading.** The `java.sql` temporal inputs
> and non-JDK `Number` subclasses that `ObjectDecoders` used to accept now report
> `type_mismatch`. Several other decoders also accept a different set of inputs than in 0.7.2
> (`nonBlank()`, `trim()`, `enumOf()`, `toBool()`, `ipv6()`, `iso8601()`), and
> `Path.toJsonPointer()` escapes `~` and `/`. The japicmp report against 0.7.2 lists only
> additions (`CodePointOrder`, new `MessageKeys` constants): these are changes of behaviour, not
> of signatures.

### Changed

- **`nonBlank()` and `trim()` share one whitespace definition.** `nonBlank()` used
  `String.isBlank()` and `trim()` used `String.trim()`, two different JDK sets: NO-BREAK SPACE
  (U+00A0) and U+2007 were not blank, U+001C to U+001F were, and `trim()` stripped every code point
  up to U+0020 (including NUL) but kept U+00A0 and U+3000. Both now use the Unicode `White_Space`
  property (25 code points), written out in Raoh and pinned to Unicode 18.0.0, so a value is blank
  exactly when `trim()` leaves it empty. U+0085, U+00A0, U+2007 and U+202F are now whitespace;
  U+0000 and U+001C to U+001F are not. U+200B stays non-whitespace
  ([#158](https://github.com/raoh-project/raoh-java/issues/158)).
- **`enumOf()` and `toBool()` match ASCII case-insensitively.** They compared with
  `String.toLowerCase(Locale.ROOT)`, so the JDK's Unicode case mapping decided what was accepted;
  on Java 25 `blocKed` (with U+212A KELVIN SIGN as the fifth letter) decoded to `Thread.State.BLOCKED`. Case-insensitive now
  means `A`-`Z` equal `a`-`z` and nothing else; every other character must match exactly, so the
  accepted names no longer depend on the JDK's Unicode version. Constants with non-ASCII letters
  (`enum Wide { Ａ }`) no longer match their lower-case forms, and `allowed` lists them as declared.
  `enumOf()` also throws `IllegalArgumentException` when it is built for an enum with two constants
  that are equal under this matching (`A` and `a`); one of them used to become unreachable
  silently. `StringDecoder.toLowerCase()` still uses the JDK's mapping, as it is an explicit
  text transformation ([#147](https://github.com/raoh-project/raoh-java/issues/147)).

### Fixed

- **`Issue.meta()` iterates its keys in a fixed order and snapshots its top-level mapping.**
  `Issue` kept the map it was given. Built-in constraints build their metadata with `Map.of`,
  whose iteration order changes with each JVM start, so the key order of `meta()` and of
  `Issues.toJsonList()` did too; a caller that passed a mutable map could also add, remove or
  replace entries of an issue already created. `Issue` now copies `meta` into an unmodifiable map ordered by its `String` keys.
  `null` keys are rejected, `null` values are kept, and a value that is itself a collection or map
  is kept as given, so changes made to that value later are still visible. The `net.unit8.raoh` package documentation states the general contract:
  built-in decoders acquire no ambient capability themselves, and a collection that built-in
  decoding exposes keeps the input's order or uses a deterministic one
  ([#142](https://github.com/raoh-project/raoh-java/issues/142)).
- **`list()` and `map()` keep `null` values and the input's key order.** `ObjectDecoders` and
  `JsonDecoders` collected the decoded values and returned `List.copyOf` / `Map.copyOf` of them.
  Both reject `null`, so `map(nullable(string()))` on `{"a": null}` and `list(nullable(string()))`
  on `[null]` threw `NullPointerException` instead of returning `Ok`. `Map.copyOf` also returned
  the keys in an order that changes with each JVM start. The decoders now return an unmodifiable
  view of their own `ArrayList` / `LinkedHashMap`, as `Result.traverse` already did: a `null`
  that the element decoder produced is kept, and the map iterates in the input's key order (the
  `Map`'s iteration order, or the JSON property order). The type parameters of `ListDecoder`,
  `RecordDecoder`, these factories and `Result.traverse` now read `extends @Nullable Object`, so
  the JSpecify signature admits the nullable element type the runtime already accepted.
  `ListDecoder.contains` and `containsAll` mark their elements `@NonNull`, which is what they
  already enforced at runtime ([#143](https://github.com/raoh-project/raoh-java/issues/143)).
- **`iso8601()` rejects second `60` instead of returning the second before it.** It handed the
  text to `Instant.parse`, whose `ISO_INSTANT` parser reads a clock time of `23:59:60` as
  `23:59:59` (at any offset, so `2016-12-31T23:59:60+09:00` became `14:59:59Z`) and reports the
  adjustment only through `DateTimeFormatter.parsedLeapSecond()`, which `Instant.parse` drops. The
  caller got a moment the text did not name. `iso8601()` now checks that report and fails with
  `invalid_format` / `not a valid ISO 8601 instant`, the same issue as any other second `60`. The
  report says the parser replaced the second, not that the text is a UTC leap second, so no
  leap-second message key is added. `24:00:00` is still accepted as the start of the next day,
  which is the same instant. Along with it, the `ObjectDecoders` temporal decoders no longer parse
  a `String` themselves: they hand it to the matching `StringDecoder` conversion, so the two routes
  read text by one set of rules and report identical issues
  ([#130](https://github.com/raoh-project/raoh-java/issues/130)).
- **`ipv6()` and `ip()` no longer depend on the host's network interfaces.** They checked a literal
  with `InetAddress.getByName`, which looks a zone ID up among the interfaces of the running machine,
  so `fe80::1%en0` was accepted on macOS and rejected on Linux, and `fe80::1%eth0` the other way
  round. The zone ID after `%` is now taken as an opaque string (RFC 4007, RFC 9844): any non-empty
  string without `%` or NUL is accepted, and only for an address whose scope is below global
  (link-local unicast `fe80::/10`, or multicast of scope 1 to D per RFC 4291 and RFC 7346), so
  `2001:db8::1%eth0`, `::1%lo0` and the deprecated site-local `fec0::1%eth0` are rejected.
  The same change fixes two neighbours. An IPv4-mapped address such as `::ffff:192.0.2.1` was
  rejected, because the JDK returns an `Inet4Address` for it, although RFC 4291 defines it as IPv6
  text. And the 45-character guard applied to the whole string, zone ID included, so a full-form
  link-local address with an interface name failed; it now applies to the address part only
  ([#127](https://github.com/raoh-project/raoh-java/issues/127)).
- **Decoders no longer read the JVM default locale.** Case mapping and fallback-message
  formatting used the default locale, so under `tr-TR` `enumOf` looked `TITLE` up as `tıtle` and
  rejected the input `title`, `StringDecoder.toUpperCase()` turned `title` into `TİTLE`, and the
  JSON decoders reported a string node as `actual: "strıng"`; under `th-TH-u-nu-thai` or `ar-EG`,
  `Issue.message()` read `must be at least ๑๐๐๐` or `must be at least ١٠٠٠`. They all use
  `Locale.ROOT` now, and so does `MessageResolver.DEFAULT`. `StringDecoder.toLowerCase()` and
  `toUpperCase()` are documented to map case by `Locale.ROOT`; for a locale-specific mapping use
  `map(s -> s.toLowerCase(locale))`. `ResourceBundleMessageResolver.resolve(code, meta)` and
  `resolve(issue)` still use the default locale, since choosing the display locale is their job.
  The build now checks every external call in `raoh`, `raoh-json` and `raoh-jooq` against a
  reviewed catalog of effects, so a new call that reads the default locale, or any other ambient
  state, fails the build (see the effect audit below)
  ([#136](https://github.com/raoh-project/raoh-java/issues/136)).
- **`MapDecoders.nested()` checks every key, not only the first.** It cast the map to
  `Map<String, Object>` when its first key was a `String`, so a later `Integer` key reached the inner
  decoder and surfaced as a `ClassCastException` (under `strict(...)`, for one) instead of an issue,
  and whether a mixed map was rejected at all depended on iteration order. It now reports
  `type_mismatch` at the map's path, before the inner decoder runs
  ([#133](https://github.com/raoh-project/raoh-java/issues/133)).
- **A format check no longer resolves to the generic `invalid format`.** `email()`, `url()`,
  `uuid()`, `startsWith()`, `enumOf()`, the ISO-8601 parsers and the other format checks all
  report `invalid_format`, and until now they also shared `invalid_format` as their message key.
  What told `not a valid email` apart from `not a valid UUID` was only the English sentence written
  into the issue, so a `ResourceBundle` applied `raoh.invalid_format` to every one of them: an email
  failure resolved to `invalid format` in English and `形式が不正です` in Japanese. `startsWith("ab")`
  lost the prefix it asked for, although its metadata carries it. This is the gap
  [#125](https://github.com/raoh-project/raoh-java/pull/125) closed for `out_of_range`, left open for
  `invalid_format`.
- **`Path.toJsonPointer()` escapes `~` and `/` in a segment, as RFC 6901 requires.** It joined the
  raw segments with `/`, so a field named `a/b` and the member `b` of `a` were both reported at
  `/a/b`, and `Issues.flatten()` and `groupByPath()`, which key their output by this string, merged
  the two into one entry. A segment now writes `~` as `~0` and `/` as `~1`: `a/b` is `/a~1b` and
  `~c` is `/~0c`. `segments()`, `equals()` and `format()` work on the raw names and are unchanged.
  Callers that compare path strings for field names containing `/` or `~` see the new form
  ([#126](https://github.com/raoh-project/raoh-java/issues/126)).
- **A partial locale bundle is no longer overridden by the base bundle's refined keys.**
  `ResourceBundleMessageResolver` searched `raoh.<messageKey>` and then `raoh.<code>` over the
  bundle `ResourceBundle.getBundle` returns, and that bundle answers for its parents too. With
  `raoh.out_of_range.minimum` in `messages.properties` and only `raoh.out_of_range` in a user's
  `messages_fr.properties`, a French request got the English refined template. The resolver now
  searches each locale's own file, most specific first, and tries both keys in one file before
  moving to the next. The same change lets a locale template whose placeholders the metadata
  cannot supply fall through to the base bundle's template for the same key, which it used to
  hide. Present since [#125](https://github.com/raoh-project/raoh-java/pull/125).

### Added

- **`MessageKeys.TYPE_MISMATCH_STRING_KEYS`** (`type_mismatch.string_keys`) for a map rejected
  because of a key that is not a `String`, with templates in both bundles:
  `expected object with string keys, found Integer key` in English. Its metadata carries
  `expected` = `object with string keys` and `actual` = the key's type (`Integer`, `null`), so a
  bundle that defines only `raoh.type_mismatch` still resolves it to
  `expected object with string keys` rather than `expected object`
  ([#133](https://github.com/raoh-project/raoh-java/issues/133)).
- **A message key for each format check**, under the unchanged `invalid_format` code, with templates
  in both shipped locales:
  `raoh.invalid_format.{email,url,uri,uuid,ip,ipv4,ipv6,ulid,cuid,starts_with,ends_with,includes,enum,literal}`
  and, for the ISO-8601 parsers on `StringDecoder` and `ObjectDecoders`,
  `raoh.invalid_format.{instant,date,time,date_time,offset_date_time}`. Constants are in
  `MessageKeys`. The key strings match raoh-rust's message catalogue, so a catalogue can be shared
  between the two. `pattern()` keeps the plain `invalid_format` key, since its message is already the
  generic one, and `pattern(p, code)` keeps reporting the caller's code.
- **Named sections in the shipped tutorial.** Every section of `tutorial.md` and `tutorial.ja.md`
  now declares a `<!-- souther-section: name -->`, so Souther's `souther doc` and `doc_read` hand
  out one part as `raoh/tutorial/flat` or `raoh/tutorial.ja/flat` instead of the whole file, and
  `doc_search` answers with the section that holds a term. The two languages use the same names.
  The names are part of what raoh publishes and are not renamed from here on; `ShippedDocsTest`
  checks them against the rules Souther applies when it reads the jar and against the list of
  names already published ([#131](https://github.com/raoh-project/raoh-java/issues/131)).

### Changed

- **The build audits the effect of every external call.** A built-in decoder does not itself
  acquire ambient capabilities (default locale, default time zone, clock, randomness, class path,
  filesystem, network) except those passed in its input or explicit configuration. A build
  plugin internal to this repository, `raoh-effect-audit-maven-plugin`, reads the bytecode of
  `raoh`, `raoh-json` and `raoh-jooq` after compilation and checks each external member they use
  against `effect-audit/catalog.txt`, which files every member under `CLOSED`, `EXPLICIT`,
  `DELEGATED` or `AMBIENT` by its API contract. A member missing from the catalog fails the
  build. A `DELEGATED` member (an overridable method called virtually, or one that converts an
  argument through the argument's own methods) and an `AMBIENT` member need an approval for each
  calling method in `effect-audit/<module>.txt`, filed under the reason it is acceptable there,
  and decoder code must not reach an `AMBIENT` member at all, directly or through Raoh's own
  methods, including the overrides external code calls back on a Raoh object a decoder creates:
  the audit walks Raoh's call graph, across modules, and prints the path. A static field that is
  not final, and a write to static state after class initialization, are refused. JDK classes are read from the `--release`
  API, so the audit gives the same result on any JDK that builds the release. Lambdas,
  method references, record methods, pattern and enum switches and dynamic constants are
  followed to the members they reach, and an unknown bootstrap method fails the build. This replaces
  the test that derived default-locale readers from the JDK call graph, which could not be made
  complete, and the forbidden-apis check with its `@SuppressForbidden` exemptions: the catalog
  and the approvals are the one place each classification and each exception is recorded. The
  plugin is not published
  ([#151](https://github.com/raoh-project/raoh-java/issues/151)).

- **Breaking: the `ObjectDecoders` temporal decoders no longer accept `java.sql` values.**
  `date()`, `time()`, `dateTime()` and `iso8601()` accepted `java.sql.Date`, `java.sql.Time` and
  `java.sql.Timestamp` and converted them with `toLocalDate()`, `toLocalTime()`,
  `toLocalDateTime()` and `toInstant()`. Those conversions read the JVM default time zone, so the
  same input object decoded to a different value after `TimeZone.setDefault`:
  `new java.sql.Date(0L)` was `1970-01-01` in UTC and `1969-12-31` in `America/Los_Angeles`.
  `toInstant()` reads it only for a `Timestamp` changed through its deprecated setters. Under
  JDBC's own convention this usually came out right, since a driver builds these values in the
  same default zone and reading them back in that zone returns what the database held. The
  conversions are removed because a built-in decoder does not read ambient state such as the
  default time zone on its own; what it needs comes from its input or its explicit configuration,
  and a `java.sql` value does not carry the zone it was built in. They now report `type_mismatch`. Convert JDBC values to `java.time` types where they
  are read, for example with `ResultSet.getObject(column, LocalDate.class)`, or with jOOQ
  fields typed as `LocalDate`, `LocalTime` and `LocalDateTime`
  ([#141](https://github.com/raoh-project/raoh-java/issues/141)).
- **Breaking: the numeric decoders convert by Raoh's rules and accept only JDK number types.**
  `ObjectDecoders.int_()`, `long_()`, `double_()`, `float_()` and `decimal()` accepted any `Number`
  and took the value from its own `intValue()`, `longValue()`, `doubleValue()`, `floatValue()` or
  `toString()`, so `int_()` returned `Ok[705032704]` for `5_000_000_000L` and `Ok[1]` for `1.9`,
  and `long_()` wrapped a `BigInteger` beyond the `long` range. `decimal()` threw
  `NumberFormatException` out of `decode()` for `NaN` and infinities, and `JsonDecoders.int_()`
  threw `JsonNodeException` for a JSON integer beyond the `int` range. The decoders now accept
  `Byte`, `Short`, `Integer`, `Long`, `Float`, `Double`, `BigInteger` and `BigDecimal` (the last
  two by exact class, since a subclass can override their conversions) and convert each by a
  rule Raoh documents. `int_()` and `long_()` return only values the input holds: an integer
  outside the target range fails with `type_mismatch` and the new message key
  `type_mismatch.numeric_range` ("value is outside the integer range"), and a `Double`, a
  `Float` or a `BigDecimal` with a fractional part fails with `type_mismatch`. An integral
  `BigDecimal` such as `5.00` is accepted, so JDBC `NUMBER` / `DECIMAL` columns read as before.
  `double_()` and `float_()` round to the nearest value by IEEE 754 (`9007199254740993L` decodes
  to `9007199254740992.0`). A value beyond their range still fails with `type_mismatch`, now
  under the `type_mismatch.numeric_range` key instead of the plain one. `decimal()`
  converts a finite `Double` or `Float` to the decimal its `toString()` prints, as before, and
  rejects `NaN` and infinities. Other `Number` types, such as `AtomicInteger`, `AtomicLong`,
  `LongAdder` or a custom subclass, now report `type_mismatch`; convert them to a JDK number
  before decoding. The `JsonDecoders` numeric decoders decide which JSON numbers they admit, then
  read the node's value with `numberValue()` and hand it to the `ObjectDecoders` decoder, so the
  conversion to the target type follows the same rules on both routes. `JsonDecoders.int_()` and
  `long_()` admit only an integer literal: they reject `1.0` whether or not the mapper keeps it
  as `BigDecimal`, although `ObjectDecoders.int_()` accepts an integral `BigDecimal`. They also
  accept a `BigInteger` node whose value fits. `StringDecoder.toInt()` and `toLong()` report a
  well-formed integer outside the range with the same `type_mismatch.numeric_range` key, so
  `"5000000000"` and `5000000000L` fail alike. The `JooqRecordDecoders` field decoders hand
  jOOQ's unsigned types (`UByte`, `UShort`, `UInteger`, `ULong`, used for MySQL / MariaDB
  `UNSIGNED` columns) to the value decoder as the `BigInteger` they hold, so `int_()` and the
  other numeric decoders keep accepting them, and a `ULong` beyond the `long` range fails
  instead of wrapping. A custom value decoder that matched on those jOOQ types now receives a
  `BigInteger`
  ([#152](https://github.com/raoh-project/raoh-java/issues/152)).
- **String conversions accept a grammar Raoh defines, not whatever the JDK parser accepts.**
  `uuid()`, `toInt()`, `toLong()`, `toDecimal()`, `date()`, `time()`, `dateTime()`,
  `offsetDateTime()` and `iso8601()` passed the text to `UUID.fromString`, `Integer.parseInt`,
  `Long.parseLong`, `new BigDecimal(String)` and the `DateTimeFormatter.ISO_*` parsers, so the
  accepted language was the JDK's and was written down nowhere. Each conversion now checks its own
  grammar, documented in its Javadoc, before the JDK builds the value; no JDK formatter decides
  what is accepted. Everything `ObjectEncoders` writes is still accepted. The following inputs
  were accepted before and are now rejected:
  - `uuid()`: anything other than 32 hexadecimal digits grouped `8-4-4-4-12` (RFC 9562), such as
    `1-1-1-1-1`, which `UUID.fromString` read as `00000001-0001-0001-0001-000000000001`.
  - `toInt()`, `toLong()`, `toDecimal()`: digits other than ASCII `0`–`9`, such as full-width
    `１２３` or Arabic-Indic `٣`. Signs, leading zeros, and (for `toDecimal()`) `5.`, `.5` and
    exponents such as `1e3` are still accepted.
  - The temporal conversions: a lower-case `t` or `z`, as in `2016-12-31t23:59:59z`, and a year
    written other than as `LocalDate.toString()` and `Instant.toString()` write it: leading zeros
    on a signed year (`+00001`, `-00001`, `+0999999999`), which the JDK read as the year without
    them.
  - `offsetDateTime()`: an offset of hours only, such as `2024-01-15T10:30+09`.
  - `time()`, `dateTime()`, `offsetDateTime()`, `iso8601()`: a decimal point with no fraction
    digits after it, such as `10:30:00.`.

  The default messages of `date()` and `time()` changed from `not a valid date (yyyy-MM-dd)` and
  `not a valid time (HH:mm:ss)` to `not a valid ISO-8601 date (e.g., 2024-01-15)` and
  `not a valid ISO-8601 local time (e.g., 10:30 or 10:30:45)`, because a date may have a signed
  expanded year and a time may omit its seconds. The `messages.properties` and
  `messages_ja.properties` entries changed with them
  ([#137](https://github.com/raoh-project/raoh-java/issues/137)).
- **`uri()` and `url()` accept the RFC 3986 `URI` grammar that `java.net.URI` can hold, not whatever it parses.**
  Both passed the text to `URI.create`, which follows RFC 2396 and RFC 2732, and `url()` then
  required `URI.getHost()` to be non-null, which is RFC 2396's hostname rule rather than the RFC
  3986 `host`. Raoh now reads the text by the RFC 3986 `URI` rule itself, in linear time without
  backtracking, and `java.net.URI` only builds the value. The result type is still
  `java.net.URI`, so the RFC 3986 URIs it cannot hold are rejected and listed in the Javadoc: an
  empty scheme-specific part (`a:`, `a:#f`), an empty authority followed by nothing (`a://`), an
  `IPvFuture` host (`http://[v1.abc]/`), and an IPv6 host with a port above `Integer.MAX_VALUE`.
  The following inputs were accepted before and are now rejected:
  - A relative reference, which has no scheme and is not a URI: `foo/bar`, `../x`, `#top`,
    `//example.com/`.
  - A zone ID in an IPv6 host, `http://[fe80::1%25eth0]/`; RFC 9844 obsoleted RFC 6874, which had
    added it.
  - Raw non-ASCII characters, such as `http://exé.com/`; they must be percent-encoded.

  The following were rejected by `url()` and are now accepted:
  - An RFC 3986 `reg-name` that is not an RFC 2396 hostname: `http://my_host/`,
    `http://example.123/`, `http://%41.com/`. `URI.getHost()` returns `null` for these, and some
    JDK networking APIs, such as `HttpRequest.newBuilder`, reject them.
  - An upper-case scheme, `HTTP://example.com/`; RFC 3986 schemes are case-insensitive.
  - Text longer than 2048 UTF-16 code units. The limit was a resource policy, not part of the
    grammar; write `string().maxLength(2048).url()` to keep it
    ([#144](https://github.com/raoh-project/raoh-java/issues/144)).
- **`ipv6()` and `ip()` check the RFC 4291 text form themselves.** A successful
  `Inet6Address.ofLiteral` decided acceptance, and it accepts text outside RFC 4291 section 2.2.
  The address is now read by the RFC 3986 `IPv6address` rule, the same one `uri()` uses for an IPv6
  host, and the zone check reads the scope from the first group of that text, so the JDK is no
  longer called. Now rejected: a group of more than four digits (`::00001`) and an embedded IPv4
  address with a leading zero (`::01.2.3.4`, `::1.2.3.04`), which `ipv4()` already rejected
  on its own ([#146](https://github.com/raoh-project/raoh-java/issues/146)).
- **`ipv6()` and `ip()` reject a bracketed address such as `[::1]`.** The brackets belong to the host
  syntax of a URI, not to the address, and were accepted only because the JDK parser strips them
  ([#127](https://github.com/raoh-project/raoh-java/issues/127)).
- **`ObjectDecoders.map()` requires `String` keys.** It converted each key with `String.valueOf`
  and used the result as the output key, so `1` and `"1"`, or `null` and `"null"`, became one key and
  one of the two values was dropped with no issue. A map with any key that is not a non-null `String`
  now fails with `type_mismatch` at the map's own path, checked before any value is decoded. A caller that relied on the conversion must turn its keys into strings itself,
  where it can decide what a collision means
  ([#133](https://github.com/raoh-project/raoh-java/issues/133)).
- **Format checks carry a refined `messageKey`.** The English message stored on each issue is
  byte-identical to before (except for `date()` and `time()`, whose wording changed with #137), and `MessageResolver.DEFAULT` resolves each key to that same sentence. A
  bundle that defines only `raoh.invalid_format` still resolves these issues, because
  `ResourceBundleMessageResolver` falls back from the message key to the code. Since
  `Issue.equals` compares `messageKey`, an expected `Issue` built with
  `Issue.of(path, "invalid_format", "not a valid email")` no longer equals the one `email()` emits.
- **String `allowed` lists are in code point order.** `StringDecoder.oneOf()` and `discriminate()`
  sorted the values they report with `String.compareTo`, which compares UTF-16 code units. Where a
  character above U+FFFF meets one in U+E000–U+FFFF the two orders disagree: `compareTo` puts
  `"😀"` (U+1F600, a surrogate pair from U+D83D) before `"Ａ"` (U+FF21). `enumOf()` did not sort at
  all and reported its lower-cased constant names in `HashMap` order, which no version of Raoh
  promised. All three now sort with the new `CodePointOrder`, the order Raoh for Rust sorts strings
  in. Every string `allowed` Raoh reports follows this order, and `CodePointOrder.sorted()` lets a
  custom decoder follow it too. The numeric `oneOf()` checks keep reporting theirs in numeric order.

## [0.7.2] - 2026-08-07

> **Read the Changed section before upgrading.** This is a patch release, but `Issue` gained a
> record component. Binary compatibility holds — code compiled against 0.7.1 keeps running, and
> japicmp reports the change as additive. Two things do change: `Issue.equals` now compares
> `messageKey`, so an expected `Issue` built with `Issue.of(...)` no longer matches one a built-in
> constraint emitted, and a five-component record deconstruction pattern over `Issue` no longer
> compiles. Tests that compare whole `Issue` values are where this shows up.

### Fixed

- **A one-sided bound no longer resolves to a message naming a bound it does not have.**
  `min()`, `max()`, `positive()`, `negative()`, `nonNegative()` and `nonPositive()` all report
  `out_of_range`, and a `ResourceBundle` holds one template per key, so `raoh.out_of_range=must be
  between {min} and {max}` was applied to every one of them. `min(0)` resolved to
  `must be between 0 and {max}` — an upper bound that was never declared, shown to whoever sent the
  value, with the placeholder still in it. Only `range()` came out right. `TemporalDecoder.before()`,
  `after()` and `between()` were worse: their metadata carries `before`, `after`, `from` and `to`, so
  both placeholders survived, and `MessageResolver.DEFAULT` reduced all three to `out of range`
  ([#123](https://github.com/raoh-project/raoh-java/issues/123)).
- **`positive()` and `nonNegative()` are described as themselves.** Both bound below and differ only
  on whether zero passes, which their metadata did not record, so a resolver had no way to tell them
  apart ([#124](https://github.com/raoh-project/raoh-java/issues/124)).
- **`nonempty()` keeps saying `must not be empty`.** It and `minSize(1)` emit the same code and
  byte-identical metadata; resolving through a bundle rewrote the first into
  `must have at least 1 elements`.
- **`MessageResolver.interpolate` no longer re-scans values it has substituted.** It iterated over
  the metadata and ran a replace per entry, so a value containing `{max}` was substituted again by a
  later entry. It now scans the template once.

### Added

- **`Issue.messageKey()`** — the key naming which constraint failed, alongside `code`, which keeps
  classifying the failure for programs to branch on. One code covers several constraints that need
  different wording and carry different metadata; the key is what tells them apart. Constants are in
  the new **`MessageKeys`** class, and a key is always its error code, a dot, and a qualifier
  (`out_of_range.positive`). Issues built without one use the code as their key.
- **`MessageResolver.resolve(Issue)` and `resolve(Issue, Locale)`** — `default` methods that see the
  message key and the message stored at decode time. `Issue.resolve` now calls these. A resolver
  written as a two-argument lambda keeps working through the default implementation.
- **`MessageResolver.interpolateFully(String, Map)`** — fills a template only when the metadata
  supplies every placeholder it asks for, returning `null` otherwise, so a resolver can decline
  instead of emitting a half-filled sentence. The check has to run before substitution: afterwards a
  brace that came from a value cannot be told apart from one the template wrote.
- **`Result.fail` and `Result.failWith` overloads taking a message key**, for decoders that emit a
  shared code.
- **Bundle templates for each constraint** in both shipped locales:
  `raoh.out_of_range.{minimum,maximum,range,positive,negative,non_negative,non_positive,before,after,between}`
  and `raoh.too_small.nonempty`. `raoh.out_of_range` and `raoh.too_small` are unchanged.

### Changed

- **`ResourceBundleMessageResolver` declines rather than degrade a message.** It looks up
  `raoh.<messageKey>` first and `raoh.<code>` second — a bundle defining only code-level keys keeps
  working — and returns `Issue.message()` when no key matches or the template asks for a placeholder
  the metadata lacks. The stored message already describes the constraint. Resolving through the
  code-and-metadata entry point has no stored message to fall back to and still falls back to
  `MessageResolver.DEFAULT`.
- **`Issue` gained a record component.** `messageKey` sits between `code` and `message`. The
  five-argument constructor and every `Issue.of` factory still exist and default the key to the code,
  but `equals`, `hashCode` and `toString` now include it, and a record deconstruction pattern written
  against five components no longer compiles. `Issues.toJsonList()` still emits `path`, `code`,
  `message` and `meta` only, so a wire format built on it is unaffected; serializing the record
  directly through a reflective mapper picks up the new field.
- **Placeholder names now have a grammar.** A placeholder is a letter or underscore followed by
  letters, digits, underscores, dots or hyphens — the shape a metadata key has. Braces around
  anything else are literal, so a custom template may contain prose such as
  `expected an object like {"id": 1}`. Previously any characters between braces were read as a
  placeholder name, which under the completeness check above would have made such a template
  permanently unusable.

## [0.7.1] - 2026-08-06

### Added

- **The guides ship inside the `raoh` jar.** `docs/*.md` are unpacked into
  `META-INF/souther-docs/raoh/` at build time, next to a registry naming the doc set and an index
  naming its topics, so a toolchain that has raoh on its class path can serve the guides without a
  checkout of this repository. Six ship: the tutorial in both languages, composition patterns,
  boundary modules, locale-aware messages, and the comparison with other libraries. Nothing in
  `docs/` moves and no guide is rewritten — the sources stay where contributors edit them. Keeping
  the copy here rather than in the consuming toolchain ties the guides to the code version:
  whichever raoh is on the class path is the raoh whose guides are read, and bumping the dependency
  brings the matching guides with it. For consumers this is additive — roughly 135 KB under
  `META-INF`, no API change and no new dependency
  ([#122](https://github.com/raoh-project/raoh-java/pull/122)).

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
  ([#114](https://github.com/raoh-project/raoh-java/issues/114)).

- **`flat(...)`** in `MapDecoders`, `JsonDecoders` and `JooqRecordDecoders` — lifts a decoder that
  reads the same whole input into a combine component, which is how a flat JOIN row gets split
  across several decoders. This was previously the second role of `nested(...)`, documented in the
  tutorial alongside the first; the two need different types now, so `nested(...)` keeps its meaning (adapting a decoder for use as a
  field *value*) and `flat(...)` takes the other one
  ([#114](https://github.com/raoh-project/raoh-java/issues/114)).

- **`InputFields<I>`** — enumerates the field names present in an input, so `strict` works on any
  representation rather than only on `Map`. `MapDecoders.MAP_FIELDS` and `JsonDecoders.JSON_FIELDS`
  are the built-in ones; implement it to bring `strict` to a boundary the library does not cover,
  and pass it to `Decoders.strict(dec, knownFields, inputFields)` or to
  `CombinePart.named(name, decoder, inputFields)`
  ([#113](https://github.com/raoh-project/raoh-java/issues/113)).

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
  ([#106](https://github.com/raoh-project/raoh-java/issues/106)).

- **Published-API diff in the build.** `japicmp` compares `raoh`, `raoh-json` and `raoh-jooq`
  against the last release during `verify`, and CI puts the per-module report in the job summary
  and the `japicmp-api-diff` artifact. Nothing used to report a change to the API surface, so the
  breaks in this release are in this file only because someone noticed them. Reporting only for
  now: before 1.0 the breaks are deliberate and frequent, and a build that fails on each one turns
  the exclusion list into the thing you edit to get back to green. At 1.0 the `breakBuild*` flags
  go to true and an intentional break needs an explicit exclusion
  ([#117](https://github.com/raoh-project/raoh-java/issues/117)).

### Fixed

- **A schema can no longer lose a field by being wrapped.** `FieldDecoder` was both a `Decoder` and
  a carrier of schema metadata, so any ordinary `Decoder` wrapper erased the metadata and `strict()`
  then rejected a field the schema plainly contained — silently, with no compile error. Combiners now
  take `CombinePart` values, which are not `Decoder`s, so that wrapper is a compile error. The same
  fragility ran the other way: a new combinator on `Decoder` reintroduced the bug unless someone
  remembered to override it on `FieldDecoder`, which is why seven such overrides existed. Composition
  now happens inside a part, so there is nothing to keep in sync
  ([#114](https://github.com/raoh-project/raoh-java/issues/114)).

- **`combine(...).strict(f)` now rejects unknown fields on the JSON boundary.** The explicit
  `JsonDecoders.strict(dec, knownFields)` always worked, but the ergonomic combiner form hardcoded
  the generic `Decoders.strict`, which only ever scanned `Map` input — so the two spellings of the
  same feature disagreed, and the convenient one silently accepted whatever it was given. The scan
  is now a capability of the field decoder rather than something the combiner assumes: `field()`
  binds a decoder to a field *on a particular boundary*, so it carries an `InputFields`, and
  `Combiner#strict()` recovers it from its components. The sixteen `Combiner*` records are
  untouched — their components, canonical constructors and value semantics are unchanged
  ([#113](https://github.com/raoh-project/raoh-java/issues/113)).

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
  ([#109](https://github.com/raoh-project/raoh-java/issues/109)).

- **A refinement on a field now reports at the field's path.** `field("age", int_())` appends the
  name inside its own `decode`, so a combinator wrapped around it only saw the enclosing path: one
  decoder reported a type mismatch at `/age` but a refinement failure on the enclosing object, and
  one level down the failure landed on `/user` instead of `/user/age`. `FieldDecoder` now threads
  the field's path through `flatMap` (the rebase target), `flatMapWithPath`, `pipe` and all three
  `refine` overloads, so `field("age", int_()).refine(...)` and
  `field("age", int_().refine(...))` agree. Error **paths move** for those four combinators — code
  that keys off the old enclosing path needs updating
  ([#109](https://github.com/raoh-project/raoh-java/issues/109)).

### Changed

- **`FieldDecoder` is removed**, along with the seven combinator overrides it carried. The field
  factories in `MapDecoders`, `JsonDecoders` and `JooqRecordDecoders` return `CombinePart`, and the
  sixteen `Combiner*` records plus `CombinerList` take `CombinePart` components. A part still decodes
  on its own — `field("age", int_()).decode(map)` — and appends its own name, so standalone and
  combined use share one path contract. Passing one where a `Decoder` is wanted needs an explicit
  `asDecoder()` ([#114](https://github.com/raoh-project/raoh-java/issues/114)).

- **`JooqRecordDecoders.nested(...)` is renamed `flat(...)`**, matching the new distinction between
  reading a field's value and reading the same whole input
  ([#114](https://github.com/raoh-project/raoh-java/issues/114)).

- **`Combiner#strict()` reports two failures separately.** A combiner containing a `flat(...)`
  component is refused because its declared field set is unknown; a combiner whose boundary has no
  `InputFields` — jOOQ, whose fields are named but whose `Record` has no scanner — is refused for
  that reason instead. Both happen when the strict decoder is assembled
  ([#114](https://github.com/raoh-project/raoh-java/issues/114)).

- **`Decoders.strict(Decoder, Set)` is removed.** It was generic in the input type but only scanned
  `Map`, and that gap between what the signature promised and what the implementation did is what
  produced the bug above. Core now has only the boundary-agnostic
  `Decoders.strict(Decoder, Set, InputFields)`. Callers on `Map` input should use
  `MapDecoders.strict`, which is unchanged and already typed to `Map<String, Object>`.
  **Removing a published method breaks both compilation and linkage** — code compiled against
  0.6.0 that called it will fail with `NoSuchMethodError` until recompiled against
  `MapDecoders.strict`. Kept as a deliberate break rather than a deprecated bridge, so the
  misleading signature does not survive a deprecation cycle
  ([#113](https://github.com/raoh-project/raoh-java/issues/113)).

- **`Combiner#strict()` and `strictFlatMap()` throw `IllegalStateException`** when no component
  carries an `InputFields` — a combiner built entirely from bare decoders has no way to tell a
  known field from an unknown one. Previously that case silently accepted everything on JSON and
  rejected everything on `Map`, since the known-field set came out empty. This is an assembly error
  rather than a data error, so it surfaces when the decoder is built rather than when it runs
  ([#113](https://github.com/raoh-project/raoh-java/issues/113)).

- **`refine()` on a builtin decoder now returns that decoder's own type**, so a refinement no
  longer has to come last in a chain: `string().refine(...).minLength(3)` compiles where it
  previously did not, because `refine` is declared on `Decoder` and returned `Decoder<I, T>`. All
  three overloads are overridden on `BoolDecoder`, `DecimalDecoder`, `DoubleDecoder`,
  `FloatDecoder`, `IntDecoder`, `ListDecoder`, `LongDecoder`, `RecordDecoder`, `StringDecoder` and
  `TemporalDecoder`. Existing callers stay binary compatible through the compiler-generated bridge
  methods; a subclass that overrode `refine` would need source changes to recompile, which is moot
  now that these classes are `final` (see below)
  ([#110](https://github.com/raoh-project/raoh-java/issues/110)).

- **The builtin decoders are `final`.** `BoolDecoder`, `DecimalDecoder`, `DoubleDecoder`,
  `FloatDecoder`, `IntDecoder`, `ListDecoder`, `LongDecoder`, `RecordDecoder`, `StringDecoder` and
  `TemporalDecoder` no longer permit subclassing. They were open by default rather than by design:
  every constraint runs through a private `chain(...)` helper, so a subclass could neither add a
  constraint in the same style nor intercept the existing ones — overriding `flatMapWithPath` caught
  `refine` and nothing else, not `minLength()`, not `email()`. Composition is the supported route,
  via the public constructor each class already takes an inner decoder through, or
  `StringDecoder.from(Decoder)`. **Breaks any existing subclass**, at both compile time and link
  time ([#115](https://github.com/raoh-project/raoh-java/issues/115)).

- **The `ObjectDecoders` temporal decoders now accept ISO-8601 text**, the representation the
  matching `ObjectEncoders` factory writes, so a codec pair built over the neutral `Object` tree
  round-trips. `date()`, `time()`, `dateTime()`, `iso8601()` and `offsetDateTime()` parse a
  `String` with the same parse and the same failure message as `string().date()` and friends;
  unparseable text is now `invalid_format` where it used to be `type_mismatch`. `dateTime()` also
  accepts `java.sql.Timestamp`, closing the gap against `iso8601()`. `offsetDateTime()` gets no
  `java.sql` conversion on purpose — `Timestamp` carries no offset, so converting one would mean
  picking a zone for the caller ([#104](https://github.com/raoh-project/raoh-java/issues/104)).

- **`StringDecoder.minLength` / `maxLength` / `fixedLength` now count Unicode code points** instead
  of UTF-16 code units, in both the comparison and the `actual` meta value. A supplementary-plane
  character — a kanji such as `𠮷`, an emoji — used to count as two, contradicting the `characters`
  / `文字` wording of the messages. Strings made only of BMP characters are unaffected; for the
  rest, `maxLength` is now more permissive and `minLength` stricter. The length guards inside
  `email()`, `url()` and `ip()` stay in UTF-16 units — they cap the size of the string before it is
  parsed or matched, and express neither a character count nor the length of the value as sent
  ([#105](https://github.com/raoh-project/raoh-java/issues/105)).

## [0.6.0] - 2026-07-15

### Added

- **`MapEncoders.lazy(Supplier<Encoder>)`** — the encode counterpart of `Decoders.lazy`, for
  self-referential (recursive) encoders. Closes the last decode/encode asymmetry among the structural
  combinators: a recursive domain type (e.g. a tree) that decodes via `lazy` can now be encoded back
  the same way ([#94](https://github.com/raoh-project/raoh-java/issues/94)).
- **`ObjectEncoders.bytes()` / `uuid()` / `uri()`** — the encode duals of the existing decoders.
  `bytes()` passes a `byte[]` through as-is (for JDBC binary columns); `uuid()` and `uri()` emit the
  canonical string form, round-tripping `StringDecoder.uuid()` / `uri()`. Also documents in
  `comparisons.md` that `object(property(...), ...)` — not a symmetric `combine` — is the intended
  encode idiom, and how to bridge a `Map<String, Object>` to a Jackson `JsonNode`
  ([#94](https://github.com/raoh-project/raoh-java/issues/94)).
- **Schema-reuse guidance** (docs) — `comparisons.md` now documents how to cover Zod's
  `.merge()`/`.extend()`/`.pick()`/`.omit()`/`.partial()` in Raoh's nominal-typed model by extracting
  each field's value decoder into a variable and reusing it across related shapes (subset `combine`
  for pick/omit, extra fragments for merge/extend, `optionalNullableField` + `Presence` for PATCH).
  Raoh has no structural schema operators today; the fragment-reuse pattern is the recommended
  approach ([#95](https://github.com/raoh-project/raoh-java/issues/95)).
- **`Decoder.refine(...)`** — a generic predicate-based refinement combinator (three overloads: a
  `code`/`message` pair, a metadata-carrying variant, and a fully caller-controlled `onFail` variant).
  Keeps the value unchanged on success and produces an `Issue` at the current path on failure, with a
  caller-supplied error code (no new built-in code) whose message survives `MessageResolver`. This is
  the ergonomic form of the common `flatMapWithPath` "keep the value, or fail with a domain rule"
  idiom, and is the direct analogue of Zod's `.refine()`. Refinement failures accumulate with sibling
  errors through `combine`. There is no encoder-side dual: the encode side is a total function with no
  failure channel ([#93](https://github.com/raoh-project/raoh-java/issues/93)).
- **jspecify `@NullMarked` nullness contract** across the `raoh` core module, extended to the
  `raoh-json` and `raoh-jooq` sibling modules. Consumers running null analysis (Eclipse JDT / ecj,
  NullAway, IntelliJ) receive precise, declared contracts instead of guessed ones
  ([#43](https://github.com/raoh-project/raoh-java/issues/43)).
- **NullAway build gate** (`mvn -Pnullcheck`) that validates the core module's nullness contract.
  Requires JDK 25 (Error Prone does not yet support JDK 26).
- **`MapEncoders.nullableProperty(...)`** — the nullable counterpart of `property(...)`, for a
  getter that may return `null`. The `null` branch is handled in the property layer: the value
  encoder is not invoked and `null` is written to the map.
- **`MapEncoders.propertyWithDefault(...)`** (value and `Supplier` overloads) — encodes a default
  value when the getter returns `null`, so the map entry is never `null`.
- **`MapEncoders.discriminate(...)`** — tagged-union encoding, mirroring the decoder side; rejects
  duplicate discriminator tags at construction ([#44](https://github.com/raoh-project/raoh-java/issues/44)).
- **`EntryEncoder<T>` abstraction plus `MapEncoders.optionalProperty(...)` / `presenceProperty(...)`.**
  `EntryEncoder` writes zero-or-more keys into the output map; `PropertyEncoder` (always one key) is a
  special case. `optionalProperty` omits the key when the getter returns `null` (the encode dual of
  decode's `optionalField` → `Optional`, distinct from `nullableProperty` which writes `key: null`);
  `presenceProperty` round-trips the tri-state `Presence` (`Absent` → omit, `PresentNull` → write
  `null`, `Present(v)` → write the value) ([#41](https://github.com/raoh-project/raoh-java/issues/41),
  [#61](https://github.com/raoh-project/raoh-java/issues/61)).
- **`MapEncoders.mapOf(...)`** — encodes a homogeneous `Map<String, V>` by applying a value encoder
  to each value, the encode mirror of `ObjectDecoders.map(...)`
  ([#63](https://github.com/raoh-project/raoh-java/issues/63)).
- **`nullableField(...)`** targeting `@Nullable T`, in core `MapDecoders`, `JsonDecoders`, and
  `JooqRecordDecoders` ([#46](https://github.com/raoh-project/raoh-java/issues/46),
  [#53](https://github.com/raoh-project/raoh-java/issues/53)).
- **Message overloads across the numeric decoders**, with `DecimalDecoder` brought to parity
  (`min` / `max` / `range` / `multipleOf` / sign / `scale`)
  ([#54](https://github.com/raoh-project/raoh-java/issues/54)).
- **Custom-message overloads on `ListDecoder` and `RecordDecoder` constraints**
  (`nonempty` / `minSize` / `maxSize` / `fixedSize` / `contains` / `unique`, and the record
  size constraints), matching the string/numeric decoders. `ListDecoder.containsAll(T...)` is
  intentionally left out, mirroring the `StringDecoder.oneOf(String...)` varargs precedent
  ([#87](https://github.com/raoh-project/raoh-java/issues/87)).
- **`JsonDecoders.double_()` / `float_()`** — the JSON boundary reached floating-point parity with
  `ObjectDecoders`, giving a primitive `double`/`float` (and the `DoubleDecoder`/`FloatDecoder`
  constraint API) instead of only `decimal()` → `BigDecimal`. JSON temporals stay on the canonical
  `string().iso8601()` / `date()` / `dateTime()` path (documented, no new primitives)
  ([#86](https://github.com/raoh-project/raoh-java/issues/86)).
- **Typed, cast-free `variant()` / `discriminate(field, Variant...)` on the decode side**, in core
  `Decoders` and re-exported from `MapDecoders` / `JsonDecoders` / `JooqRecordDecoders`. Mirrors the
  encoder's `variant()` / `discriminate()`, removing the per-arm up-cast the `Map`-based form
  requires; rejects duplicate tags at construction. The `Map`-based overload stays for back-compat
  ([#82](https://github.com/raoh-project/raoh-java/issues/82)).
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
  ([#60](https://github.com/raoh-project/raoh-java/issues/60)).
- `ListDecoder.toSet()` now preserves insertion order (previously unspecified via `Set.copyOf`)
  ([#69](https://github.com/raoh-project/raoh-java/issues/69)).
- **`MapEncoders.object(...)` now accepts `EntryEncoder<T>...`** (was `PropertyEncoder<T>...`).
  Source-compatible — `PropertyEncoder` implements `EntryEncoder`, so `object(property(...), ...)`
  is unchanged — but binary-incompatible (the erased parameter type changed), so recompile against
  the new version ([#41](https://github.com/raoh-project/raoh-java/issues/41)).

### Removed

- **`ObjectEncoders.nullable(Encoder)`** — replace `property("x", getter, nullable(enc))` with
  `nullableProperty("x", getter, enc)`.
- **`ObjectEncoders.withDefault(Encoder, …)`** — replace
  `property("x", getter, withDefault(enc, default))` with
  `propertyWithDefault("x", getter, enc, default)`.
- **`StringDecoder.allowBlank()`** and the two-argument `StringDecoder(inner, base)` constructor —
  a vestige of an earlier design; `string()` accepts blank input by default
  ([#69](https://github.com/raoh-project/raoh-java/issues/69)).
- **`ObjectDecoders.allowBlankString()` / `JsonDecoders.allowBlankString()`** — redundant with
  `string()`; the internal `enumOf` / `literal` / `discriminate` key readers now use `string()`
  ([#71](https://github.com/raoh-project/raoh-java/issues/71)).

### Fixed

- Numeric decoders (`IntDecoder`, `LongDecoder`, `FloatDecoder`, `DoubleDecoder`, `DecimalDecoder`)
  reject a zero divisor and an inverted range at construction instead of failing silently
  ([#66](https://github.com/raoh-project/raoh-java/issues/66)).
- Encode `discriminate` guards against a `null` variant key at tag injection
  ([#44](https://github.com/raoh-project/raoh-java/issues/44)).
- `JsonDecoders.double_()` / `float_()` reject an out-of-range magnitude with `type_mismatch`
  instead of letting Jackson 3's strict `doubleValue()` / `floatValue()` throw out of `decode()`;
  `ObjectDecoders.double_()` / `float_()` were aligned to reject the same rather than silently
  returning `Infinity` (both keep `NaN` flowing through for range constraints to catch)
  ([#86](https://github.com/raoh-project/raoh-java/issues/86)).

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

[Unreleased]: https://github.com/raoh-project/raoh-java/compare/v0.8.0...HEAD
[0.8.0]: https://github.com/raoh-project/raoh-java/compare/v0.7.2...v0.8.0
[0.7.2]: https://github.com/raoh-project/raoh-java/compare/v0.7.1...v0.7.2
[0.7.1]: https://github.com/raoh-project/raoh-java/compare/v0.7.0...v0.7.1
[0.7.0]: https://github.com/raoh-project/raoh-java/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/raoh-project/raoh-java/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/raoh-project/raoh-java/compare/v0.4.1...v0.5.0
[0.4.1]: https://github.com/raoh-project/raoh-java/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/raoh-project/raoh-java/compare/v0.3.1...v0.4.0
[0.3.1]: https://github.com/raoh-project/raoh-java/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/raoh-project/raoh-java/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/raoh-project/raoh-java/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/raoh-project/raoh-java/releases/tag/v0.1.0
