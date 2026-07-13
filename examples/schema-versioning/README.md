# Raoh Schema-Versioning Example

A Spring Boot application showing how Raoh handles an **evolving database schema** at the
JDBC boundary: rows written under several historical schema versions are decoded transparently
into a single current domain model, without a data migration.

## What it shows

| Raoh feature | Where it appears |
| --- | --- |
| `MapDecoders.discriminate(field, variants)` | `ORDER_ROW` dispatches on the `schema_version` column |
| `combine` + `field` + `map` | Version-specific row decoders (`ORDER_V1` / `V2` / `V3`) |
| `Decoders.withDefault` | Missing `currency` column defaults to `"JPY"`, so V2 and V3 share one `MONEY` decoder |
| Shared decoder building blocks | `SPLIT_NAME` and `MONEY` are reused across versions |
| Sealed `Result` pattern matching | `OrderController` switches on `Ok` / `Err` instead of throwing |
| `Issues.resolve(...)` | A row with an unknown version becomes a structured `not_allowed` error |

## Schema evolution

The `orders` table carries a `schema_version` column. Each version has its own decoder that
normalizes the row into the current `Order` model — old rows are never rewritten.

| Version | Columns | Notes |
| --- | --- | --- |
| **V1** (legacy) | `customer_name`, `amount` | single name field (split on first space); currency assumed JPY |
| **V2** | `first_name`, `last_name`, `amount` | name split into two columns; currency still JPY |
| **V3** | `first_name`, `last_name`, `amount`, `currency` | explicit `currency` column added |

Because `withDefault` absorbs the missing `currency` column, V2 and V3 reuse the same `MONEY`
decoder; they stay as separate `discriminate` entries to keep the dispatch explicit and allow
future divergence.

## Domain model

```text
Order
├── OrderId       id
├── CustomerName  customer   (firstName, lastName)
└── Money         total      (amount: BigDecimal, currency: String)
```

All schema versions decode into this same shape.

## How to run

From the repository root, install the Raoh jars locally (the example depends on the
current `0.6.0-SNAPSHOT` line, which is not published):

```bash
mvn install -DskipTests
```

Then start the example:

```bash
cd examples/schema-versioning
mvn spring-boot:run
```

An in-memory H2 database is seeded from `src/main/resources/schema.sql` with a mix of V1, V2,
and V3 rows on startup.

## REST API

| Method | Path | Description |
| --- | --- | --- |
| `GET /orders` | List all orders across every schema version | 200, or 500 if a row fails to decode |
| `GET /orders/{id}` | Show a single order | 200, 404 if not found, 500 on decode failure |

## Quick manual check

Start the app, then:

### 1. List all orders (mixed versions, one uniform shape)

```bash
curl -s http://localhost:8080/orders | jq .
```

Expected: every row — regardless of whether it was stored as V1, V2, or V3 — comes back as the
same `Order` structure (`id`, `customer.firstName` / `customer.lastName`, `total.amount` /
`total.currency`).

### 2. Show a single order

```bash
curl -s http://localhost:8080/orders/1 | jq .
```

## Example error response

A row whose `schema_version` is not one of `1` / `2` / `3` decodes to a `not_allowed` issue,
returned as `500 Internal Server Error` (a data-integrity problem rather than bad user input):

```json
[
  {
    "path": "/schema_version",
    "code": "not_allowed",
    "message": "must be one of [1, 2, 3]",
    "meta": { "allowed": ["1", "2", "3"] }
  }
]
```
