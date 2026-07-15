---
name: Bug report
about: A decoder, encoder, or error-reporting behaviour that looks wrong
labels: bug
---

## What happened

<!-- What you observed, and what you expected instead. -->

## Reproduction

For a decoding problem, the most useful report shows all three:

```java
// 1. The decoder definition
Decoder<Map<String, Object>, User> dec = combine(
        field("email", string().email().map(Email::new)),
        field("age",   int_().range(0, 150))
).map(User::new);

// 2. The input
var input = Map.<String, Object>of("email", "…", "age", …);

// 3. The result — issues.toJsonList() pastes well
System.out.println(dec.decode(input));
```

## Environment

- Raoh version:
- Java version:
- Module (`raoh` / `raoh-json` / `raoh-jooq` / `raoh-gsh*`):
