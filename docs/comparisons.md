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
| `.merge()` / `.extend()` | reuse field-decoder fragments — see [Schema reuse](#schema-reuse) |
| `.pick()` / `.omit()` | combine a subset of fragments — see [Schema reuse](#schema-reuse) |
| `.partial()` | reuse fragments with `optionalNullableField` — see [Schema reuse](#schema-reuse) |
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

## Schema reuse

Zod derives one object schema from another with `.merge()`, `.extend()`, `.pick()`, `.omit()`, and
`.partial()`. Those operators rely on Zod's first-class field map (`ZodObject.shape`) and TypeScript's
structural typing to reshape the *result type*. Java has neither: `combine(...).map(ctor)` produces a
nominal record type, and the caller must name each target type explicitly. So Raoh has no structural
schema operators — but the value they provide (defining a field's parsing and validation once and
reusing it across related request shapes) is available today by extracting the **value decoders** into
variables:

```java
// Define each field's parsing + validation once — the part worth reusing.
var emailValue = string().trim().toLowerCase().email().map(Email::new); // Decoder<Object, Email>
var nameValue  = string().nonBlank().map(Name::new);
var roleValue  = enumOf(Role.class);
```

**merge / extend** — compose different shapes from the shared fragments:

```java
Decoder<Map<String, Object>, CreateUser> createUser = combine(
        field("email", emailValue),
        field("name",  nameValue)
).map(CreateUser::new);

Decoder<Map<String, Object>, AdminUser> adminUser = combine(
        field("email", emailValue),
        field("name",  nameValue),
        field("role",  roleValue)      // extend: one more field, same fragments reused
).map(AdminUser::new);
```

**pick / omit** — combine only the subset of fragments you want:

```java
Decoder<Map<String, Object>, Contact> contact = combine(
        field("email", emailValue),
        field("role",  roleValue)
).map(Contact::new);
```

**partial (PATCH)** — reuse the same value decoders, wrapped with `optionalNullableField` so every
field becomes a `Presence` (`Absent` / `PresentNull` / `Present`):

```java
Decoder<Map<String, Object>, UserPatch> userPatch = combine(
        optionalNullableField("email", emailValue),
        optionalNullableField("name",  nameValue)
).map(UserPatch::new);

record UserPatch(Presence<Email> email, Presence<Name> name) {}
```

The `Presence` tri-state distinguishes "field absent" from "field explicitly null" — exactly what a
PATCH request needs. In every case the target type (`UserPatch`, `Contact`, …) is written by the
caller; Java cannot derive it structurally the way Zod does. A first-class schema-object
representation is discussed in #95, but in Raoh's nominal-typed model the fragment-reuse pattern above
is the intended approach.

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
