# Boundary Modules

## `JsonDecoders`

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

## `JooqRecordDecoders`

`net.unit8.raoh.jooq.JooqRecordDecoders` works with jOOQ `Record`.

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

## `MapDecoders`

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
