# Comparisons with Other Libraries

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
| `z.object({...})` | `combine(field(...), ...).map(...)` |
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
).map(User::new);
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
