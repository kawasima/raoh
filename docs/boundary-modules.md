# Boundary Modules

## `JsonDecoders`

`net.unit8.raoh.json.JsonDecoders` works with Jackson `JsonNode`.

Read the input with `readTree(String | Reader | InputStream | JsonParser)`. It keeps each number as
written: `decimal()` gets the exact value and scale, `float_()` rounds once from the decimal, and
`-0`, `-0.0` and `-0e5` give `-0.0` to `double_()` and `float_()` while `int_()` reads `-0` as `0`.
It throws a Jackson exception for an object with a member name written twice, for a number whose
exponent is beyond what a `BigDecimal` holds, and for anything that is not one JSON value. The
overloads that open a parser use Jackson's default `StreamReadConstraints`; pass your own
`JsonParser` to read under other limits. A tree from your own `ObjectMapper` still decodes, but its
numbers are whatever the mapper converted them to.

Supported helpers include:

- `string()`, `int_()`, `long_()`, `double_()`, `float_()`, `bool()`, `decimal()`
- `field(...)`
- `optionalField(...)`
- `nullable(...)` — `null` for a JSON `null`; a missing member still goes to the inner decoder
- `withDefault(...)` — the default for a JSON `null` or a missing member; anything else goes to the
  inner decoder, and its result is returned as it is
- `optionalNullableField(...)`
- `list(...)`
- `map(...)`
- `enumOf(...)`
- `literal(...)`
- `variant(...)` / `discriminate(...)` — tagged unions; the typed `discriminate(field, variant(...)...)` form is cast-free
- `strict(...)`
- `combine(...)`

This module is a good fit when your application boundary is already Jackson-based.

Example:

```java
JsonDecoder<List<String>> tags =
        field("tags", list(string().trim().nonBlank()).nonempty());
```

## `JooqRecordDecoders`

`net.unit8.raoh.jooq.JooqRecordDecoders` works with jOOQ `Record`.

Supported helpers include:

- `string()`, `int_()`, `long_()`, `bool()`, `decimal()`
- `field(...)`
- `optionalField(...)`
- `nullable(...)`
- `withDefault(...)` (from `ObjectDecoders`) — the default for a column holding SQL `NULL`
- `optionalNullableField(...)`
- `nested(...)`
- `enumOf(...)`
- `variant(...)` / `discriminate(...)` — tagged unions (e.g. single-table inheritance keyed on a column)
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
).map(User::new)::decode;

JooqRecordDecoder<Address> addressDecoder = combine(
        field("city", string()),
        field("zip",  string())
).map(Address::new)::decode;

// SELECT u.name, u.age, a.city, a.zip FROM users u JOIN addresses a ...
Decoder<Record, UserWithAddress> dec = combine(
        nested(userDecoder),
        nested(addressDecoder)
).map(UserWithAddress::new);
```

`nested(dec)` applies another `JooqRecordDecoder` to the same flat record.
This lets you map a single JOIN result row into a structured domain object.

For LEFT JOIN results where the joined side may be absent, use `optionalNullableField`:

```java
var presence = optionalNullableField("dept_name", string()).decode(rec);
```

This returns `Presence.Absent`, `Presence.PresentNull`, or `Presence.Present`, which is useful when a SQL NULL means "no row joined" rather than "explicitly set to null".

A column the record does not have is a different thing from a column holding SQL `NULL`. `field(...)`
refuses a missing column with `missing_field` before the value decoder runs, so
`field("currency", withDefault(string(), "JPY"))` defaults a SQL `NULL` but not a missing column. To
default both, use `optionalField` for the column and `withDefault` for the value:

```java
optionalField("currency", withDefault(string(), "JPY")).map(c -> c.orElse("JPY"))
```

`optionalField("currency", string())` alone passes SQL `NULL` to `string()`, which refuses it with
`required`.

## `MapDecoders`

`net.unit8.raoh.decode.map.MapDecoders` works with `Map<String, Object>`.

Supported helpers include:

- `string()`, `int_()`, `long_()`, `bool()`, `decimal()`
- `field(...)`
- `optionalField(...)`
- `nullable(...)`
- `withDefault(...)` (from `ObjectDecoders`) — the default for an absent key or a `null` value
- `optionalNullableField(...)`
- `nested(...)`
- `list(...)`
- `map(...)`
- `enumOf(...)`
- `literal(...)`
- `variant(...)` / `discriminate(...)` — tagged unions; the typed `discriminate(field, variant(...)...)` form is cast-free
- `strict(...)`
- `combine(...)`

This module is a good fit when your application receives already-materialized data structures.

Example:

```java
MapDecoder<Map<String, BigDecimal>> prices =
        field("prices", map(decimal()).minSize(1));
```
