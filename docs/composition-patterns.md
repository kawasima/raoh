# Composition Patterns

Raoh offers four distinct composition patterns. Choosing the right one keeps error accumulation correct.

## Pattern 1: Same input, independent fields — `combine(...).map(...)`

Use this for normal object decoding from a single input source.
All field errors are accumulated even when multiple fields fail.

```java
combine(
        field("name", string().nonBlank()),
        field("age", int_().range(0, 150))
).map(Person::new);
```

If `name` and `age` are both invalid, you get both errors back.

## Pattern 2: Dependent or sequential parsing — `flatMap(...)`

Use this when the next step depends on a previous result, or when domain rules must run after structure has been decoded.

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

**Important:** do not use `flatMap` to compose two independent results just because they happen to be sequential in code.
If the first result fails, the second result's errors are silently dropped.
Use `Result.map2` (see below) for independent results from different sources.

## Pattern 3: Different input types — `Result.map2(...)`

Use this when two independent values come from different input sources, such as two database tables,
and you want to accumulate errors from both even if each fails independently.

```java
// PersonalName comes from the customer table
Result<PersonalName> nameResult = PERSONAL_NAME_DECODER.decode(customerRow, path.append("customer"));
// ContactMethods comes from the contact_methods table
Result<ContactMethods> cmResult  = CONTACT_METHODS_DECODER.decode(contactRows, path.append("contactMethods"));

return Result.map2(nameResult, cmResult,
        (name, cms) -> new Customer(name, cms.primary(), cms.secondary()));
```

If both decoders fail, the issues from both are merged into a single `Err`.

This is the right pattern when:

- the two values come from structurally different inputs (different tables, different API calls)
- the decoding of one does not depend on the result of the other
- you want the caller to see all errors at once

**Contrast with `combine`:**
`combine` composes decoders before decoding — it extracts multiple fields from the same input.
`Result.map2` combines results after decoding — it merges the outcomes of two already-run decoders.

## Pattern 4: Decoding a list — `Result.traverse(...)` / `Decoder.list()`

Use this to decode a variable-length list where every element must be checked and all errors accumulated.

```java
// Using Result.traverse directly
Result<List<Order>> orders = Result.traverse(
        rows,
        ORDER_DECODER::decode,
        path.append("orders"));

// Using the Decoder.list() convenience
Decoder<List<Record>, List<Order>> listDecoder = ORDER_DECODER.list();
Result<List<Order>> orders = listDecoder.decode(rows, path.append("orders"));
```

Each element at index `i` is decoded under the path `orders/0`, `orders/1`, and so on.
If elements 1 and 3 fail, both errors are reported with their respective paths — no short-circuiting.

## Summary

| Situation | Tool |
| --- | --- |
| Multiple fields from the same input | `combine(...).map(...)` |
| Next step depends on a previous result | `flatMap(...)` |
| Two independent results from different inputs | `Result.map2(...)` |
| Variable-length list, accumulate all errors | `Result.traverse(...)` / `Decoder.list()` |
