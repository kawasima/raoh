# Raoh

Raoh is a Java decoder library for turning untyped boundary input into typed domain values.

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

## Current Scope

Raoh currently provides:

- a generic `Decoder<I, T>` abstraction
- `Result<T>` with `Ok<T>` and `Err<T>`
- structured diagnostics with `Issue`, `Issues`, and `Path`
- built-in decoders for strings, numbers, booleans, lists, and maps
- boundary modules for Jackson `JsonNode`, `Map<String, Object>`, and jOOQ `Record`
- applicative composition for accumulating field errors
- monadic composition for dependent parsing and domain rules
- utility combinators such as `lazy`, `withDefault`, `recover`, `oneOf`, `strict`, `enumOf`, and `literal`

## Requirements

- Java 25
- Maven

Build and run tests:

```bash
mvn clean test
```

## Package Layout

### Core (`raoh`)

- `net.unit8.raoh`: core abstractions and error model
- `net.unit8.raoh.builtin`: built-in primitive and collection decoders
- `net.unit8.raoh.combinator`: applicative combinator internals
- `net.unit8.raoh.map`: decoders for `Map<String, Object>`

### JSON extension (`raoh-json`)

- `net.unit8.raoh.json`: decoders for Jackson `JsonNode`

### jOOQ extension (`raoh-jooq`)

- `net.unit8.raoh.jooq`: decoders for jOOQ `Record`

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
- `net.unit8.raoh.map.MapDecoders`

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
import com.fasterxml.jackson.databind.JsonNode;

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
    ).apply(User::new);
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

import net.unit8.raoh.map.MapDecoder;

import static net.unit8.raoh.map.MapDecoders.*;

record Config(String host, int port) {}

MapDecoder<Config> config() {
    return combine(
            field("host", string().nonBlank()),
            field("port", int_().range(1, 65535))
    ).apply(Config::new);
}
```

This is useful when the input is already materialized by another layer, for example:

- form data converted into a map
- deserialized YAML or TOML
- database-like key/value rows
- framework-specific request objects transformed into `Map<String, Object>`

## Built-in Decoders

Raoh includes the following built-in decoders in `net.unit8.raoh.builtin`.

Value decoders:

- `StringDecoder`
- `IntDecoder`
- `LongDecoder`
- `BoolDecoder`
- `DecimalDecoder`

Collection/value-container decoders:

- `ListDecoder`
- `RecordDecoder`

### String Capabilities

`StringDecoder` supports:

- `nonBlank()`
- `allowBlank()`
- `minLength(...)`
- `maxLength(...)`
- `fixedLength(...)`
- `pattern(...)`
- `startsWith(...)`
- `endsWith(...)`
- `includes(...)`
- `email()`
- `url()`
- `ipv4()`
- `ipv6()`
- `ip()`
- `cuid()`
- `ulid()`
- `trim()`
- `toLowerCase()`
- `toUpperCase()`
- `uuid()`
- `uri()`
- `iso8601()`
- `date()`
- `time()`
- `StringDecoder.from(...)`

### Numeric Capabilities

`IntDecoder` and `LongDecoder` support:

- `min(...)`
- `max(...)`
- `range(...)`
- `positive()`
- `negative()`
- `nonNegative()`
- `nonPositive()`
- `multipleOf(...)`

`DecimalDecoder` supports:

- `min(...)`
- `max(...)`
- `positive()`
- `negative()`
- `nonNegative()`
- `nonPositive()`
- `multipleOf(...)`
- `scale(...)`

### Collection Capabilities

`ListDecoder` supports:

- `nonempty()`
- `minSize(...)`
- `maxSize(...)`
- `fixedSize(...)`
- `toSet()`

`RecordDecoder` supports:

- `nonempty()`
- `minSize(...)`
- `maxSize(...)`
- `fixedSize(...)`

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
import com.fasterxml.jackson.databind.JsonNode;

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
    ).apply(User::new);
}
```

This reads naturally as:

- "read `id` as UUID"
- "read `email` as a trimmed lowercased email"
- "read `balance` structurally, then apply domain rules"
- "construct `User` only if everything succeeded"

## Applicative and Monadic Composition

Raoh intentionally supports two different composition styles.

### Accumulate Independent Errors

Use `combine(...).apply(...)` when fields are independent and you want all field errors:

```java
combine(
        field("name", string().nonBlank()),
        field("age", int_().range(0, 150))
).apply(Person::new);
```

This is the normal object-decoding style.

If `name` and `age` are both invalid, you get both errors back.

### Parse Dependent Values

Use `flatMap(...)` when the next step depends on previous decoded values, or when domain rules must run after structure has already been decoded:

```java
record Money(BigDecimal amount, Currency currency) {
    static Result<Money> parse(BigDecimal amount, Currency currency) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Result.fail(Path.ROOT, "out_of_range", "amount must be positive");
        }
        return Result.ok(new Money(amount, currency));
    }
}

JsonDecoder<Money> money = combine(
        field("amount", decimal()),
        field("currency", enumOf(Currency.class))
).flatMap(Money::parse);
```

Inner issues returned by `flatMap(...)` are automatically rebased to the current path.

So if `Money.parse(...)` fails under `/balance`, the issue is reported under `/balance`, not at the root.

## Error Accumulation Example

Given this decoder:

```java
var dec = combine(
        field("email", string().email()),
        field("age", int_().range(0, 150))
).apply((email, age) -> Map.of("email", email, "age", age));
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

The `net.unit8.raoh.Decoders` class provides reusable combinators.

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
).apply(Comment::new);
```

### `oneOf(...)`

Use `oneOf(...)` for union-like decoding:

```java
var contact = oneOf(
        combine(
                field("kind", literal("email")),
                field("value", string().email())
        ).apply((kind, value) -> new EmailContact(value)),
        combine(
                field("kind", literal("phone")),
                field("value", string().pattern(Pattern.compile("^\\d+$")))
        ).apply((kind, value) -> new PhoneContact(value))
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

### `JsonDecoders`

`net.unit8.raoh.json.JsonDecoders` works with Jackson `JsonNode`.

Supported helpers include:

- `string()`, `int_()`, `long_()`, `bool()`, `decimal()`
- `field(...)`
- `optionalField(...)`
- `nullable(...)`
- `optionalNullableField(...)`
- `list(...)`
- `map(...)`
- `enumOf(...)`
- `literal(...)`
- `discriminate(...)`
- `strict(...)`
- `combine(...)`

This module is a good fit when your application boundary is already Jackson-based.

Example:

```java
JsonDecoder<List<String>> tags =
        field("tags", list(string().trim().nonBlank()).nonempty());
```

### `JooqDecoders`

`net.unit8.raoh.jooq.JooqDecoders` works with jOOQ `Record`.

Supported helpers include:

- `string()`, `int_()`, `long_()`, `bool()`, `decimal()`
- `field(...)`
- `optionalField(...)`
- `nullable(...)`
- `optionalNullableField(...)`
- `nested(...)`
- `enumOf(...)`
- `combine(...)`

This module is a good fit when you fetch data from a database via jOOQ and want to map flat query results (including JOIN results) into nested domain objects.

Example:

```java
record User(String name, int age) {}
record Address(String city, String zip) {}
record UserWithAddress(User user, Address address) {}

JooqRecordDecoder<User> userDecoder = combine(
        field("name", string()),
        field("age",  int_())
).apply(User::new)::decode;

JooqRecordDecoder<Address> addressDecoder = combine(
        field("city", string()),
        field("zip",  string())
).apply(Address::new)::decode;

// SELECT u.name, u.age, a.city, a.zip FROM users u JOIN addresses a ...
Decoder<Record, UserWithAddress> dec = combine(
        nested(userDecoder),
        nested(addressDecoder)
).apply(UserWithAddress::new);
```

`nested(dec)` applies another `JooqRecordDecoder` to the same flat record.
This lets you map a single JOIN result row into a structured domain object.

For LEFT JOIN results where the joined side may be absent, use `optionalNullableField`:

```java
var presence = optionalNullableField("dept_name", string()).decode(rec);
```

This returns `Presence.Absent`, `Presence.PresentNull`, or `Presence.Present`, which is useful when a SQL NULL means "no row joined" rather than "explicitly set to null".

### `MapDecoders`

`net.unit8.raoh.map.MapDecoders` works with `Map<String, Object>`.

Supported helpers include:

- `string()`, `int_()`, `long_()`, `bool()`, `decimal()`
- `field(...)`
- `optionalField(...)`
- `nullable(...)`
- `optionalNullableField(...)`
- `nested(...)`
- `list(...)`
- `map(...)`
- `enumOf(...)`
- `literal(...)`
- `strict(...)`
- `combine(...)`

This module is a good fit when your application receives already-materialized data structures.

Example:

```java
MapDecoder<Map<String, BigDecimal>> prices =
        field("prices", map(decimal()).minSize(1));
```

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

Two especially practical shapes are:

```java
issues.flatten()
```

which is convenient for form-like UIs, and:

```java
issues.toJsonList()
```

which is convenient for APIs.

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
).apply(User::new);
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

## Zod Comparison

If you know Zod, the closest equivalents are:

| Zod | Raoh |
| --- | --- |
| `z.string()` | `string()` |
| `z.number().int()` | `int_()` |
| `z.number()` | `decimal()` |
| `z.boolean()` | `bool()` |
| `z.enum([...])` | `enumOf(MyEnum.class)` |
| `z.literal("x")` | `literal("x")` |
| `z.array(dec)` | `list(dec)` |
| `z.record(dec)` | `map(dec)` |
| `z.object({...})` | `combine(field(...), ...).apply(...)` |
| `.optional()` | `optionalField(name, dec)` |
| `.nullable()` | `nullable(dec)` |
| `.default(v)` | `withDefault(dec, v)` |
| `.catch(v)` | `recover(dec, v)` |
| `z.union([...])` | `oneOf(...)` |
| `z.lazy(() => dec)` | `lazy(() -> dec)` |
| `.strict()` | `combine(...).strict(f)` |
| `.transform(...)` | `map(...)` |
| `.refine(...)` / `.superRefine(...)` | `flatMap(...)` or `flatMapWithPath(...)` |
| `.pipe(...)` | `pipe(...)` |

The important difference is conceptual:

- Zod schemas are usually described as validators with parsing
- Raoh is designed first as a decoder from boundary input into domain values

So the typical Raoh shape is:

```java
combine(
        field("email", string().trim().toLowerCase().email().map(Email::new)),
        field("age", int_().range(0, 150).map(Age::new))
).apply(User::new);
```

The schema is already the parsing pipeline.

## Elm Decoder Comparison

Raoh also has a strong family resemblance to Elm's `Json.Decode.Decoder`.

The biggest similarities are:

- both treat decoding as a first-class operation
- both build decoders compositionally
- both separate raw boundary input from trusted domain values
- both encourage constructing domain values only after decoding succeeds
- both feel more like "reading" data than "validating" an already-built object

Rough correspondences:

| Elm | Raoh |
| --- | --- |
| `Decoder a` | `Decoder<I, T>` |
| `field "name" string` | `field("name", string())` |
| `nullable decoder` | `nullable(decoder)` |
| `list decoder` | `list(decoder)` |
| `map` | `map(...)` |
| `andThen` | `flatMap(...)` |
| `oneOf` | `oneOf(...)` |
| building records with `map2`, `map3`, ... | `combine(...).apply(...)` |

The most important differences are:

- Elm decoders are primarily JSON decoders, while Raoh is generic over input type and ships JSON and `Map<String, Object>` boundaries out of the box
- Elm usually models failure as decoder failure text, while Raoh emphasizes structured issues with `path`, `code`, `message`, and `meta`
- Raoh has an explicit applicative/monadic split:
  `combine(...).apply(...)` accumulates independent field errors, while `flatMap(...)` handles dependent parsing

If you know Elm, this Raoh code should feel familiar:

```java
JsonDecoder<User> user() {
    return combine(
            field("id", string().uuid().map(UserId::new)),
            field("email", string().trim().toLowerCase().email().map(Email::new)),
            field("age", int_().range(0, 150).map(Age::new))
    ).apply(User::new);
}
```

That is close in spirit to "read fields, decode them, then build a value", which is exactly the workflow Elm decoders promote.

## Design Direction

The intended workflow is:

1. Read dirty external input at the boundary.
2. Decode it into domain values.
3. Either get a fully-typed object or a structured error value.

This avoids passing partially-valid data deeper into the application and keeps the domain model focused on valid states.
