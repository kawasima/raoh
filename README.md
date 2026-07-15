# Raoh

[![Maven Central](https://img.shields.io/maven-central/v/net.unit8.raoh/raoh?label=Maven%20Central)](https://central.sonatype.com/artifact/net.unit8.raoh/raoh)
[![Javadoc](https://javadoc.io/badge2/net.unit8.raoh/raoh/javadoc.svg)](https://javadoc.io/doc/net.unit8.raoh/raoh)
[![License](https://img.shields.io/github/license/kawasima/raoh)](https://github.com/kawasima/raoh/blob/main/LICENSE)
[![Java 25](https://img.shields.io/badge/Java-25-437291?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)

Raoh is a Java decoder library for turning untyped boundary input into typed domain values.

![raoh logo](./docs/raoh.png)

It is built around a parse-don't-validate approach:

- decode at the boundary
- keep invalid states out of the domain model
- return failures as values instead of throwing
- attach structured errors to precise paths

Raoh is closer to a parser/decoder library than to a traditional bean validation library.

If you are coming from a validator-oriented library, the main difference in feel is this:

- you do not validate an already-constructed domain object
- you decode raw input into a domain object
- object construction happens only after decoding succeeds

## Tutorials

- [Tutorial (English)](docs/tutorial.md)
- [Tutorial (日本語)](docs/tutorial.ja.md)

## Requirements

- **Java 25** (current LTS). Raoh targets the latest Java LTS deliberately: the API relies on
  records, sealed types, pattern matching, and JSpecify type-use nullness, so it does not attempt to
  support older baselines.

## Installation

Raoh is published to Maven Central under the `net.unit8.raoh` group ID. Add the
core module, plus whichever boundary module matches your input source. Define the
version once as a property — use the latest shown on the Maven Central badge above.

```xml
<properties>
    <!-- Use the latest version from the Maven Central badge above -->
    <raoh.version>0.6.0</raoh.version>
</properties>

<dependencies>
    <!-- Core: decoders, encoders, error model -->
    <dependency>
        <groupId>net.unit8.raoh</groupId>
        <artifactId>raoh</artifactId>
        <version>${raoh.version}</version>
    </dependency>

    <!-- Optional: decode Jackson JsonNode -->
    <dependency>
        <groupId>net.unit8.raoh</groupId>
        <artifactId>raoh-json</artifactId>
        <version>${raoh.version}</version>
    </dependency>
    <!-- raoh-json scopes Jackson as 'provided', so add Jackson 3 yourself.
         Note the groupId is tools.jackson.core (Jackson 3), not com.fasterxml.jackson (Jackson 2). -->
    <dependency>
        <groupId>tools.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>3.1.0</version>
    </dependency>

    <!-- Optional: decode jOOQ Record (jOOQ is a provided dependency) -->
    <dependency>
        <groupId>net.unit8.raoh</groupId>
        <artifactId>raoh-jooq</artifactId>
        <version>${raoh.version}</version>
    </dependency>
</dependencies>
```

## Building from source

- Java 25
- Maven

```bash
mvn clean test
```

`mvn verify` additionally runs [JaCoCo](https://www.jacoco.org/jacoco/) and writes a
per-module coverage report to `target/site/jacoco/` (reporting only — no thresholds are
enforced). CI publishes the same reports as a job-summary table and a downloadable artifact.

## Package Layout

### Core (`raoh`)

- `net.unit8.raoh`: core abstractions and error model
- `net.unit8.raoh.decode`: decoder core (`Decoder`, `Decoders`, `ObjectDecoders`, `FieldDecoder`)
- `net.unit8.raoh.decode.builtin`: built-in primitive and collection decoders
- `net.unit8.raoh.decode.combinator`: applicative combinator internals
- `net.unit8.raoh.decode.map`: decoders for `Map<String, Object>`
- `net.unit8.raoh.encode`: encoder core (`Encoder`, `ObjectEncoders`, `MapEncoders`)

### JSON extension (`raoh-json`)

- `net.unit8.raoh.json`: decoders for Jackson `JsonNode`

### jOOQ extension (`raoh-jooq`)

- `net.unit8.raoh.jooq`: decoders for jOOQ `Record`

### Domain Construction Guard (`raoh-gsh`, `raoh-gsh-weaver`, `raoh-gsh-maven-plugin`)

Test/CI-time guard that detects accidental `new` construction of domain objects outside of `Decoder.decode()`.

- `raoh-gsh`: runtime — `DomainConstructionScope`, `DomainConstructionGuardException`
- `raoh-gsh-weaver`: bytecode weaver (ClassFile API), Java Agent, CLI
- `raoh-gsh-maven-plugin`: Maven plugin for build-time weaving

**Stability:** the `raoh-gsh*` modules are experimental / incubating. Their bytecode-weaving surface
is built on the evolving JDK ClassFile API and may change outside the core library's SemVer promise;
treat them as separate from the stability guarantees of `raoh` / `raoh-json` / `raoh-jooq`.

See [raoh-gsh README](raoh-gsh/README.md) for usage.

## Core Model

### `Result<T>`

Decoding returns a value instead of throwing:

- `Ok<T>` for success
- `Err<T>` for failure

`Result<T>` supports:

- `map(...)`
- `flatMap(...)`
- `fold(...)`
- `orElseThrow(...)`

### `Issue`, `Issues`, and `Path`

Each error includes:

- `path`
- `code`
- `message`
- `meta`

Paths use JSON Pointer-like notation, for example:

- `/email`
- `/address/city`
- `/items/0/name`

`Issues` can be merged, rebased, flattened, formatted, or converted to JSON-like data.

### `Decoder<I, T>`

The core abstraction is:

```java
public interface Decoder<I, T> {
    Result<T> decode(I in, Path path);
}
```

A decoder reads an input value of type `I` and produces either:

- a typed value `T`
- structured issues

Two boundary implementations are included:

- `net.unit8.raoh.json.JsonDecoders`
- `net.unit8.raoh.decode.map.MapDecoders`

## What It Feels Like

The normal Raoh workflow looks like this:

1. Start from raw input such as JSON or `Map<String, Object>`.
2. Define small decoders for domain primitives such as `Email`, `Age`, or `UserId`.
3. Combine them into object decoders.
4. If decoding succeeds, you get a fully-typed value.
5. If decoding fails, you get structured issues with paths.

That means the "happy path" looks like object construction, while the failure path looks like machine-readable diagnostics.

## Quick Start

### Decode JSON into a domain object

```java
import tools.jackson.databind.JsonNode;

import net.unit8.raoh.json.JsonDecoder;

import static net.unit8.raoh.json.JsonDecoders.*;

record Email(String value) {}
record Age(int value) {}
record User(Email email, Age age) {}

JsonDecoder<Email> email() {
    return string().trim().toLowerCase().email().map(Email::new);
}

JsonDecoder<Age> age() {
    return int_().range(0, 150).map(Age::new);
}

JsonDecoder<User> user() {
    return combine(
            field("email", email()),
            field("age", age())
    ).map(User::new);
}
```

Use it like this:

```java
Result<User> result = user().decode(jsonNode);
```

Success case:

```java
switch (result) {
    case Ok<User>(var user) -> {
        // user is already typed and normalized
        // for example: email lowercased, age range-checked
    }
    case Err<User>(var issues) -> {
        // inspect issues
    }
}
```

Example failure shape:

```java
{
  "path": "/email",
  "code": "invalid_format",
  "message": "not a valid email",
  "meta": {}
}
```

### Decode a `Map<String, Object>`

```java
import java.util.Map;

import net.unit8.raoh.decode.map.MapDecoder;

import static net.unit8.raoh.decode.map.MapDecoders.*;

record Config(String host, int port) {}

MapDecoder<Config> config() {
    return combine(
            field("host", string().nonBlank()),
            field("port", int_().range(1, 65535))
    ).map(Config::new);
}
```

This is useful when the input is already materialized by another layer, for example:

- form data converted into a map
- deserialized YAML or TOML
- database-like key/value rows
- framework-specific request objects transformed into `Map<String, Object>`

## Built-in Decoders

Raoh includes the following built-in decoders in `net.unit8.raoh.decode.builtin`.

Value decoders:

- `StringDecoder`
- `IntDecoder`
- `LongDecoder`
- `DoubleDecoder`
- `FloatDecoder`
- `BoolDecoder`
- `DecimalDecoder`

Collection/value-container decoders:

- `ListDecoder`
- `RecordDecoder`

Other:

- `ObjectDecoders.bytes()` — decodes `byte[]` (for JDBC binary columns such as BYTEA/VARBINARY)

### String Capabilities

`StringDecoder` supports:

- `nonBlank()`
- `minLength(...)`
- `maxLength(...)`
- `fixedLength(...)`
- `pattern(...)`
- `startsWith(...)`
- `endsWith(...)`
- `includes(...)`
- `oneOf(...)`
- `email()`
- `url()` — validates http/https and returns `URI`
- `ipv4()`
- `ipv6()`
- `ip()`
- `cuid()`
- `ulid()`
- `trim()`
- `toLowerCase()`
- `toUpperCase()`
- `uuid()`
- `uri()` — accepts any scheme, returns `URI`
- `iso8601()`
- `date()`
- `time()`
- `localDateTime()`
- `offsetDateTime()`
- `toInt()`
- `toLong()`
- `toDecimal()`
- `toBool()`
- `StringDecoder.from(...)`

Temporal decoders (`iso8601()`, `date()`, `time()`, `localDateTime()`, `offsetDateTime()`) return a `TemporalDecoder` that supports:

- `before(...)`
- `after(...)`
- `between(...)`
- `past()`
- `future()`
- `pastOrPresent()`
- `futureOrPresent()`

### Numeric Capabilities

`IntDecoder`, `LongDecoder`, `DoubleDecoder`, and `FloatDecoder` support:

- `min(...)`
- `max(...)`
- `range(...)`
- `positive()`
- `negative()`
- `nonNegative()`
- `nonPositive()`
- `multipleOf(...)` (integer/long only — not available for double/float)
- `oneOf(...)`

`DecimalDecoder` supports:

- `min(...)`
- `max(...)`
- `positive()`
- `negative()`
- `nonNegative()`
- `nonPositive()`
- `multipleOf(...)`
- `scale(...)`

### Boolean Capabilities

`BoolDecoder` supports:

- `isTrue()`
- `isFalse()`

### Collection Capabilities

`ListDecoder` supports:

- `nonempty()`
- `minSize(...)`
- `maxSize(...)`
- `fixedSize(...)`
- `contains(...)`
- `containsAll(...)`
- `unique()`
- `toSet()`

`RecordDecoder` supports:

- `nonempty()`
- `minSize(...)`
- `maxSize(...)`
- `fixedSize(...)`

Each constraint above (except `containsAll` / `toSet`) also takes an optional trailing custom
message, e.g. `list(string()).minSize(1, "select at least one")`.

## Object Decoding

Raoh distinguishes these cases:

- `field(name, dec)`: required field
- `optionalField(name, dec)`: missing field is allowed
- `nullable(dec)`: `null` value is allowed

There is also tri-state presence handling:

```java
optionalNullableField("email", string())
```

This returns one of:

- `Presence.Absent`
- `Presence.PresentNull`
- `Presence.Present`

This distinction matters when "missing" and "explicitly null" have different meanings.

For example:

```java
var dec = optionalNullableField("nickname", string());
```

This lets you distinguish:

- no update requested
- clear the existing value
- set a new value

That is often useful for PATCH-like APIs.

## A More Realistic Example

The following example shows the common Raoh shape:

- decode primitive fields
- decode a nested object
- run domain-specific rules afterwards

```java
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.json.JsonDecoder;

import static net.unit8.raoh.json.JsonDecoders.*;

record Email(String value) {}
record UserId(java.util.UUID value) {}
enum Currency { JPY, USD }

record Money(BigDecimal amount, Currency currency) {
    static Result<Money> parse(BigDecimal amount, Currency currency) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Result.fail(Path.ROOT, "out_of_range", "amount must be positive");
        }
        return Result.ok(new Money(amount, currency));
    }
}

record User(UserId id, Email email, Money balance) {}

JsonDecoder<Email> email() {
    return string().trim().toLowerCase().email().map(Email::new);
}

JsonDecoder<UserId> userId() {
    return string().uuid().map(UserId::new);
}

JsonDecoder<Money> money() {
    return combine(
            field("amount", decimal()),
            field("currency", enumOf(Currency.class))
    ).flatMap(Money::parse);
}

JsonDecoder<User> user() {
    return combine(
            field("id", userId()),
            field("email", email()),
            field("balance", money())
    ).map(User::new);
}
```

This reads naturally as:

- "read `id` as UUID"
- "read `email` as a trimmed lowercased email"
- "read `balance` structurally, then apply domain rules"
- "construct `User` only if everything succeeded"

## Composition Patterns

Raoh offers four distinct composition patterns — `combine(...).map(...)`, `flatMap(...)`, `Result.map2(...)`, and `Result.traverse(...)` / `Decoder.list()`. Choosing the right one keeps error accumulation correct.

See [docs/composition-patterns.md](docs/composition-patterns.md) for details and examples.

## Error Accumulation Example

Given this decoder:

```java
var dec = combine(
        field("email", string().email()),
        field("age", int_().range(0, 150))
).map((email, age) -> Map.of("email", email, "age", age));
```

And this input:

```json
{
  "email": "not-an-email",
  "age": 300
}
```

Raoh returns both issues, for example:

```java
issues.flatten()
// {
//   "/email": ["not a valid email"],
//   "/age": ["must be between 0 and 150"]
// }
```

## Utility Combinators

The `net.unit8.raoh.decode.Decoders` class provides reusable combinators.

- `lazy(...)`
  For recursive decoders.
- `withDefault(...)`
  Uses a fallback when decoding fails only with `required` errors.
- `recover(...)`
  Uses a fallback for any decoding error.
- `oneOf(...)`
  Tries multiple candidates and returns a `one_of_failed` issue if all fail.
- `strict(...)`
  Rejects unknown fields.
- `enumOf(...)`
  Matches enum constants case-insensitively.
- `literal(...)`
  Matches one exact string value.

Example:

```java
var dec = combine(
        field("name", string()),
        field("age", int_())
).strict(Person::new);
```

### `lazy(...)`

Use `lazy(...)` for recursive structures:

```java
record Comment(String body, List<Comment> replies) {}

JsonDecoder<Comment>[] self = new JsonDecoder[1];
self[0] = combine(
        field("body", string().nonBlank()),
        withDefault(field("replies", list(lazy(() -> self[0]))), List.of())
).map(Comment::new);
```

### `oneOf(...)`

Use `oneOf(...)` for union-like decoding:

```java
var contact = oneOf(
        combine(
                field("kind", literal("email")),
                field("value", string().email())
        ).map((kind, value) -> new EmailContact(value)),
        combine(
                field("kind", literal("phone")),
                field("value", string().pattern(Pattern.compile("^\\d+$")))
        ).map((kind, value) -> new PhoneContact(value))
);
```

If all candidates fail, Raoh returns `one_of_failed` and keeps candidate-specific errors in `meta.candidates`.

### `enumOf(...)` and `literal(...)`

These are often used as small building blocks inside larger decoders:

```java
field("currency", enumOf(Currency.class))
field("kind", literal("email"))
```

`enumOf(...)` is case-insensitive. `literal(...)` is exact.

### `withDefault(...)` vs `recover(...)`

These two are similar in shape but different in intent.

Use `withDefault(...)` when a value is conceptually optional and you want a fallback for missing/null-like cases:

```java
field("role", withDefault(enumOf(Role.class), Role.MEMBER))
```

Use `recover(...)` when you want to tolerate any decoding failure:

```java
recover(field("pageSize", int_().range(1, 100)), 20)
```

`recover(...)` is more permissive. `withDefault(...)` is stricter.

## Boundary Modules

Raoh ships three boundary modules for different input types:

- **`JsonDecoders`** — Jackson `JsonNode` (`raoh-json`)
- **`JooqRecordDecoders`** — jOOQ `Record` (`raoh-jooq`)
- **`MapDecoders`** — `Map<String, Object>` (`raoh`)

Each provides the same set of helpers (`string()`, `field(...)`, `combine(...)`, etc.) adapted to its input type.

Both integration modules scope their third-party library as `provided`: `raoh-json` for
Jackson 3 (`tools.jackson.core:jackson-databind`) and `raoh-jooq` for jOOQ (`org.jooq:jooq`).
Because each module exposes that library's type (`JsonNode`, `Record`) in its public API, a
consumer already supplies it on the classpath — so `provided` avoids pinning a specific
version transitively on downstreams. Add the matching library to your own build.

See [docs/boundary-modules.md](docs/boundary-modules.md) for the full API listing and examples.

## Error Handling

You can use pattern matching:

```java
switch (result) {
    case Ok<User>(var user) -> {
        // success
    }
    case Err<User>(var issues) -> {
        // inspect issues
    }
}
```

Useful helpers on `Issues`:

- `flatten()`
- `format()`
- `toJsonList()`
- `groupByPath()`
- `resolve(MessageResolver)`
- `resolve(MessageResolver, Locale)` — locale-aware message resolution

Two especially practical shapes are:

```java
issues.flatten()
```

which is convenient for form-like UIs, and:

```java
issues.toJsonList()
```

which is convenient for APIs.

### Locale-Aware Message Resolution

Raoh supports locale-aware error messages via `ResourceBundleMessageResolver`. The locale is passed at resolution time, not baked into the decoder — so a single decoder can serve multiple locales.

See [docs/locale-aware-messages.md](docs/locale-aware-messages.md) for setup instructions and examples.

## Supported Usage Patterns

The current implementation is already tested for:

- decoding nested objects
- decoding lists and maps
- optional, nullable, and tri-state fields
- custom constraints
- cross-field validation
- defaults and recovery
- strict mode
- recursive decoders
- discriminated variants
- single-value decoding
- conditional validation using `flatMap`

Examples:

- nested object decoding

```java
combine(
        field("name", string()),
        field("address", address())
).map(User::new);
```

- cross-field validation

```java
combine(
        field("start", int_()),
        field("end", int_())
).flatMap(Period::parse);
```

- defaults

```java
field("role", withDefault(enumOf(Role.class), Role.MEMBER))
```

- strict mode

```java
combine(
        field("id", userId()),
        field("email", email()),
        field("age", age())
).strict(User::new)
```

- single value decoding

```java
string().email().decode(node)
```

## Encoders

The `net.unit8.raoh.encode` package provides the mirror image of decoders: converting domain objects to `Map<String, Object>` for JDBC binding or JSON serialization.

```java
import static net.unit8.raoh.encode.MapEncoders.*;
import static net.unit8.raoh.encode.ObjectEncoders.*;

Encoder<Item, Map<String, Object>> ITEM_ENCODER = object(
        property("id",    Item::id,    long_().contramap(ItemId::value)),
        property("name",  Item::name,  string()),
        property("price", Item::price, decimal())
);

Map<String, Object> row = ITEM_ENCODER.encode(item);
```

### Built-in Encoders

`ObjectEncoders` provides: `string()`, `int_()`, `long_()`, `double_()`, `float_()`, `bool()`, `decimal()`, `bytes()`, `date()`, `time()`, `dateTime()`, `iso8601()`, `offsetDateTime()`, `uuid()`, `uri()`, `enumOf()`.

Null / optionality is handled in the property layer (encoders themselves are total, non-null functions):

- `nullableProperty(key, getter, enc)` — writes `key: null` when the getter returns `null`
- `propertyWithDefault(key, getter, enc, default)` — writes a default (value or `Supplier`) when the getter returns `null`
- `optionalProperty(key, getter, enc)` — omits the key entirely when the getter returns `null`
- `presenceProperty(key, getter, enc)` — round-trips the tri-state `Presence` (omit / `null` / value)

`MapEncoders` provides: `property()`, `nullableProperty()`, `propertyWithDefault()`, `optionalProperty()`, `presenceProperty()`, `object()`, `nested()`, `list()`, `mapOf()`, and `variant()` / `discriminate()` for tagged unions.

### Scope

Encoding targets the `Map<String, Object>` boundary by design. To produce JSON, encode to a map and bridge with Jackson (`objectMapper.valueToTree(map)`); to write with jOOQ, hand the map to the DSL. There is intentionally **no** separate JSON or jOOQ encoder, and no encode-side `combine`: an encoder is a total function with no failure channel, so the write path does not need the error accumulation that justifies the applicative `combine` and the boundary decoder modules on the decode side. See the [Encoding](docs/comparisons.md#encoding) notes for the reasoning.

## Comparisons

For mapping tables between Raoh and other libraries (Zod, Elm), see [docs/comparisons.md](docs/comparisons.md).

## Design Direction

The intended workflow is:

1. Read dirty external input at the boundary.
2. Decode it into domain values.
3. Either get a fully-typed object or a structured error value.

This avoids passing partially-valid data deeper into the application and keeps the domain model focused on valid states.
