# Comparisons with Other Libraries

## Zod Comparison

If you know Zod, the closest equivalents are:

| Zod | Raoh |
| --- | --- |
| `z.string()` | `string()` |
| `z.number().int()` | `int_()` / `long_()` |
| `z.number()` | `double_()` / `float_()` / `decimal()` |
| `z.boolean()` | `bool()` |
| `z.enum([...])` | `enumOf(MyEnum.class)` |
| `z.literal("x")` | `literal("x")` |
| `z.array(dec)` | `list(dec)` |
| `z.record(dec)` | `map(dec)` |
| `z.object({...})` | `combine(field(...), ...).map(...)` |
| `.optional()` | `optionalField(name, dec)` |
| `.nullable()` | `nullable(dec)` |
| `.default(v)` | `withDefault(dec, v)` |
| `.catch(v)` | `recover(dec, v)` |
| `z.union([...])` | `oneOf(...)` |
| `z.lazy(() => dec)` | `lazy(() -> dec)` |
| `.strict()` | `combine(...).strict(f)` |
| `.transform(...)` | `map(...)` |
| `.refine(...)` | `refine(...)` |
| `.superRefine(...)` | `flatMapWithPath(...)` |
| `.pipe(...)` | `pipe(...)` |

The important difference is conceptual:

- Zod schemas are usually described as validators with parsing
- Raoh is designed first as a decoder from boundary input into domain values

So the typical Raoh shape is:

```java
combine(
        field("email", string().trim().toLowerCase().email().map(Email::new)),
        field("age", int_().range(0, 150).map(Age::new))
).map(User::new);
```

The schema is already the parsing pipeline.

## Encoding

Encoding is the dual of decoding, but the two composition idioms are deliberately different.
Decode composes fields with the applicative `combine(field(...), ...).map(ctor)`. Encode composes
with `object(property(...), ...)` (from `MapEncoders`):

```java
import static net.unit8.raoh.encode.MapEncoders.*;
import static net.unit8.raoh.encode.ObjectEncoders.*;

Encoder<User, Map<String, Object>> user = object(
        property("id",    User::id,    uuid().contramap(UserId::value)),
        property("email", User::email, string().contramap(Email::value)),
        property("age",   User::age,   int_().contramap(Age::value))
);
```

There is intentionally no encode-side `combine`. Decode's `combine` is applicative *because decode
can fail and must accumulate errors across fields*; an encoder is a total function with no failure
channel (see #43 and the `refine` discussion in #93), so there is nothing to accumulate. `object(...)`
is the encode idiom, and the asymmetry with `combine` is by design, not a gap.

To produce JSON, encode to a `Map<String, Object>` and hand it to Jackson:

```java
Map<String, Object> body = user.encode(aUser);
JsonNode json = objectMapper.valueToTree(body); // Jackson serialization is already good here
```

A dedicated `JsonEncoders` (domain → `JsonNode`) is tracked in #94; for now the one-liner above
covers the common case.

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
| building records with `map2`, `map3`, ... | `combine(...).map(...)` |

The most important differences are:

- Elm decoders are primarily JSON decoders, while Raoh is generic over input type and ships JSON and `Map<String, Object>` boundaries out of the box
- Elm usually models failure as decoder failure text, while Raoh emphasizes structured issues with `path`, `code`, `message`, and `meta`
- Raoh has an explicit applicative/monadic split:
  `combine(...).map(...)` accumulates independent field errors, while `flatMap(...)` handles dependent parsing

If you know Elm, this Raoh code should feel familiar:

```java
JsonDecoder<User> user() {
    return combine(
            field("id", string().uuid().map(UserId::new)),
            field("email", string().trim().toLowerCase().email().map(Email::new)),
            field("age", int_().range(0, 150).map(Age::new))
    ).map(User::new);
}
```

That is close in spirit to "read fields, decode them, then build a value", which is exactly the workflow Elm decoders promote.
