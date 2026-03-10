# Raoh Decoder Practical Guide

This guide walks you through Raoh's decoders interactively using jetshell, from the basics to patterns commonly found in real-world applications.

---

## Why decoders?

In web applications and batch processing, data arriving from the outside world cannot be trusted. HTTP request bodies, CSV files, external API responses, database column values — none of these are guaranteed to satisfy the types and constraints your application expects.

A common approach is to write validation code for each field individually. However, this has several problems:

- **Errors stop at the first failure.** A chain of `if` statements aborts on the first error found, so when both the email address and the age are invalid, only one of them is reported.
- **Conversion and validation are hard to separate.** The pipeline of "receive a string, validate it, convert it to a domain type" tends to scatter across check code and cast code.
- **Error structure is lost.** Communicating which field has which problem to the caller requires custom exception classes or ad-hoc return-value conventions.

**Raoh's decoders solve all three problems at once.**

A decoder is "a function that takes an input value and returns either `Ok[value]` or `Err[errors]`". When you compose multiple decoders with `combine`, errors from all fields accumulate together. Each error is automatically tagged with a path to the offending field (like `/address/zip`). And when decoding succeeds, the value is already converted into a domain type.

```text
External input (Object / Map / JsonNode / JooqRecord)
    ↓  decoder.decode(input)
Ok[domain object]  or  Err[{path: "/email", code: "invalid_format", ...}, ...]
```

Values that pass through the decoder at the boundary can be treated as "already correct" inside the application. This prevents missed validations and ensures domain rules are enforced by types.

This guide progresses from validating primitive values to nested objects, lists, cross-field validation, and real-world business scenarios.

---

## 0. Setup

Start [jetshell](https://github.com/kawasima/jetshell) and load Raoh:

```bash
jetshell
/resolve net.unit8.raoh:raoh:0.4.0
import static net.unit8.raoh.ObjectDecoders.*;
import static net.unit8.raoh.map.MapDecoders.*;
import static net.unit8.raoh.Decoders.*;
import net.unit8.raoh.*;
import net.unit8.raoh.map.*;
import java.util.stream.*;
import java.time.*;
```

All subsequent examples assume these imports are in place.
`java.util.*`, `java.math.*`, `java.util.regex.*` etc. are already imported by jetshell's default startup.

---

## 1. Decoding primitive values

The smallest unit in Raoh is a decoder that reads a single value. `ObjectDecoders` provides `string()`, `int_()`, `decimal()`, and `bool()` for the corresponding types.

```java
string().decode("hello")
// ==> Ok[hello]

int_().decode(42)
// ==> Ok[42]

bool().decode(true)
// ==> Ok[true]

decimal().decode(19.99)
// ==> Ok[19.99]
```

Each method returns a `Decoder<Object, T>`. If the value is not the expected type, a `type_mismatch` error is returned.

```java
int_().decode("not a number")
// ==> Err[/: expected integer]
```

Boundary modules like `JsonDecoders`, `MapDecoders`, and `JooqRecordDecoders` are all built on top of these primitive decoders.

---

## 2. Adding constraints

Constraints can be chained onto decoders. A violated constraint causes decoding to fail.

### String constraints

```java
string().trim().toLowerCase().email().decode("  USER@Example.COM  ")
// ==> Ok[user@example.com]

string().trim().toLowerCase().email().decode("not-email")
// ==> Err[/: not a valid email]

string().minLength(3).maxLength(20).decode("ab")
// ==> Err[/: must be at least 3 characters]

string().pattern(Pattern.compile("^\\d{3}-\\d{4}$")).decode("123-4567")
// ==> Ok[123-4567]

string().uuid().decode("550e8400-e29b-41d4-a716-446655440000")
// ==> Ok[550e8400-e29b-41d4-a716-446655440000]
```

### Numeric constraints

```java
int_().range(0, 150).decode(25)
// ==> Ok[25]

int_().range(0, 150).decode(300)
// ==> Err[/: must be between 0 and 150]

int_().positive().decode(-1)
// ==> Err[/: must be positive]

decimal().scale(4).decode(new BigDecimal("0.1234"))
// ==> Ok[0.1234]
```

### Temporal constraints

```java
string().date().decode("2025-06-15")
// ==> Ok[2025-06-15]

string().iso8601().decode("2025-06-15T10:30:00Z")
// ==> Ok[2025-06-15T10:30:00Z]
```

---

## 3. Mapping to domain primitives

`map` converts a raw value into a domain-specific type. This is the core of Raoh. By promoting values to domain types at the boundary, only "already validated and converted values" flow inside the application.

```java
record Email(String value) {}
record Age(int value) {}
record UserId(UUID value) {}

var emailDec = string().trim().toLowerCase().email().map(Email::new);
var ageDec = int_().range(0, 150).map(Age::new);
var userIdDec = string().uuid().map(UserId::new);

emailDec.decode("  ALICE@example.com  ")
// ==> Ok[Email[value=alice@example.com]]

ageDec.decode(25)
// ==> Ok[Age[value=25]]

userIdDec.decode("550e8400-e29b-41d4-a716-446655440000")
// ==> Ok[UserId[value=550e8400-e29b-41d4-a716-446655440000]]
```

These decoders can be passed directly to `field()` in boundary modules. `map` only runs on success; on failure, `Err` is returned as-is.

---

## 4. Decoding objects — combine + field

From here we switch to `MapDecoders`. Fields are extracted from a `Map<String, Object>` using `field()` and assembled into objects with `combine()`.

`combine` is an applicative composition: errors from each field accumulate independently. This naturally fits the requirement "return all errors at once" in form validation.

```java
record User(UserId id, Email email, Age age) {}

var userDec = combine(
        field("id", string().uuid().map(UserId::new)),
        field("email", string().trim().toLowerCase().email().map(Email::new)),
        field("age", int_().range(0, 150).map(Age::new))
).map(User::new);

userDec.decode(Map.of(
        "id", "550e8400-e29b-41d4-a716-446655440000",
        "email", "alice@example.com",
        "age", 30
))
// ==> Ok[User[id=UserId[...], email=Email[value=alice@example.com], age=Age[value=30]]]
```

With invalid input, errors from all three fields are returned at once — processing is not cut short.

```java
userDec.decode(Map.of("id", "not-uuid", "email", "invalid", "age", 300))
// ==> Err[/id: not a valid UUID, /email: not a valid email, /age: must be between 0 and 150]
```

---

## 5. Nested objects

Nesting is expressed by passing an object decoder inside `field`. Error paths are automatically composed as `/address/city`.

```java
record Address(String city, String zip) {}
record Customer(String name, Address address) {}

var addressDec = combine(
        field("city", string().nonBlank()),
        field("zip", string().pattern(Pattern.compile("^\\d{3}-\\d{4}$")))
).map(Address::new);

var customerDec = combine(
        field("name", string().nonBlank()),
        field("address", nested(addressDec))
).map(Customer::new);

customerDec.decode(Map.of(
        "name", "Alice",
        "address", Map.of("city", "Tokyo", "zip", "100-0001")
))
// ==> Ok[Customer[name=Alice, address=Address[city=Tokyo, zip=100-0001]]]

customerDec.decode(Map.of(
        "name", "",
        "address", Map.of("city", "", "zip", "bad")
))
// ==> Err[/name: is required, /address/city: is required, /address/zip: ...]
```

---

## 6. Structuring flat data — nested

DB JOIN results and CSV files often arrive as flat data with all columns at the same level. Using `nested`, you can assemble a structured domain model from this flat input.

Whereas `field("address", nested(addressDec))` in section 5 requires a nested structure in the input, using `nested` at the top level lets each decoder read its own fields from the same flat input.

```java
record UserName(String first, String last) {}
record Department(String name, String code) {}
record Employee(UserName name, Department dept) {}

var userNameDec = combine(
        field("first_name", string().nonBlank()),
        field("last_name", string().nonBlank())
).map(UserName::new);

var deptDec = combine(
        field("dept_name", string().nonBlank()),
        field("dept_code", string().nonBlank())
).map(Department::new);

// Passing decoders directly to combine shares the same flat input
var employeeDec = combine(userNameDec, deptDec).map(Employee::new);
```

Pass a flat Map that resembles a JOIN result:

```java
employeeDec.decode(Map.of(
        "first_name", "Alice",
        "last_name", "Smith",
        "dept_name", "Engineering",
        "dept_code", "ENG"
))
// ==> Ok[Employee[name=UserName[first=Alice, last=Smith], dept=Department[name=Engineering, code=ENG]]]
```

With `combine(decA, decB)`, both decoders share the same flat input. In contrast, `field("address", nested(decA))` extracts the value for the `address` key and passes it to `decA`.

If field names collide (e.g., both tables have a `name` column), use SQL aliases to disambiguate before decoding.

```java
// SELECT u.name AS user_name, d.name AS dept_name FROM ...
var userDec2 = field("user_name", string().nonBlank());
var deptDec2 = field("dept_name", string().nonBlank());

combine(userDec2, deptDec2)
        .map((userName, deptName) -> Map.of("user", userName, "dept", deptName))
        .decode(Map.of("user_name", "Alice", "dept_name", "Engineering"))
// ==> Ok[{user=Alice, dept=Engineering}]
```

With a LEFT JOIN, when the joined side does not exist, all JOIN columns become `null`. Use `optionalNullableField` and `Presence` to represent "row was not joined" in a type-safe way.

```java
record EmployeeWithDept(UserName name, Presence<Department> dept) {}

var deptPresenceDec = optionalNullableField("dept_name", string().nonBlank());

var row = new HashMap<String, Object>();
row.put("first_name", "Bob");
row.put("last_name", "Jones");
row.put("dept_name", null);
row.put("dept_code", null);

deptPresenceDec.decode(row)
// ==> Ok[PresentNull[]]  — indicates the row was not joined
```

### One-to-many JOIN — assembling a parent-child model from flat rows

A one-to-many JOIN query returns a list of flat rows with repeated parent columns. To assemble them into a parent-child domain model like `Order { lines: [OrderLine] }`, decode each row with `nested` and then group by the parent key.

```java
record OrderHeader(String orderId, String customerName) {}
record OrderLine(String productId, int quantity, BigDecimal unitPrice) {}
record OrderWithLines(OrderHeader header, List<OrderLine> lines) {}

// Parent decoder (orders table columns)
var headerDec = combine(
        field("order_id", string().nonBlank()),
        field("customer_name", string().nonBlank())
).map(OrderHeader::new);

// Child decoder (order_lines table columns)
var lineDec = combine(
        field("product_id", string().nonBlank()),
        field("qty", int_().positive()),
        field("unit_price", decimal().positive())
).map(OrderLine::new);

// Decode parent and child from each row simultaneously
var rowDec = combine(
        nested(headerDec),
        nested(lineDec)
).map((h, l) -> Map.entry(h, l));
```

Flat rows simulating a JOIN query result — `order_id` and `customer_name` repeat per row:

```java
// SELECT o.order_id, o.customer_name, l.product_id, l.qty, l.unit_price
// FROM orders o JOIN order_lines l ON o.order_id = l.order_id
var joinRows = List.<Map<String, Object>>of(
        Map.of("order_id", "ORD-001", "customer_name", "Alice", "product_id", "A01", "qty", 2, "unit_price", 1500),
        Map.of("order_id", "ORD-001", "customer_name", "Alice", "product_id", "B02", "qty", 1, "unit_price", 3000),
        Map.of("order_id", "ORD-002", "customer_name", "Bob",   "product_id", "A01", "qty", 5, "unit_price", 1500)
);
```

Decode all rows, group by parent key, and assemble the parent-child model:

```java
var decodedRows = Result.traverse(joinRows, rowDec::decode, Path.of("rows"));
var orders = decodedRows.map(entries ->
        entries.stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ))
                .entrySet().stream()
                .map(e -> new OrderWithLines(e.getKey(), e.getValue()))
                .toList()
);
orders
// ==> Ok[[OrderWithLines[header=OrderHeader[orderId=ORD-001, ...], lines=[...]], ...]]
```

Key points:

- `nested` decodes parent and child simultaneously from each flat row
- `Result.traverse` accumulates errors from all rows
- `Result.map` + `Collectors.groupingBy` groups by parent key
- Decoding (validation) and grouping (structural transformation) are clearly separated

---

## 7. Decoding lists

All elements in a list are checked, and which element failed is reported in an index-tagged path.

```java
record OrderItem(String productId, int quantity) {}

var orderItemDec = combine(
        field("productId", string().nonBlank()),
        field("quantity", int_().positive())
).map(OrderItem::new);

var orderItemsDec = field("items", list(nested(orderItemDec)).nonempty());

orderItemsDec.decode(Map.of("items", List.of(
        Map.of("productId", "A001", "quantity", 3),
        Map.of("productId", "", "quantity", -1)
)))
// ==> Err[/items/1/productId: is required, /items/1/quantity: must be positive]
```

---

## 8. Decoding maps

For dictionary structures with dynamic keys, use `map(decoder)`.

```java
var pricesDec = field("prices", map(decimal().positive()).minSize(1));

pricesDec.decode(Map.of("prices", Map.of("apple", 120, "banana", 80)))
// ==> Ok[{apple=120, banana=80}]
```

---

## 9. Optional / Nullable / Three-state fields

The three states — "field absent", "null explicitly sent", "value present" — can be separated at the type level.

```java
// Field required, null not allowed
field("email", string().email()).decode(Map.of("email", "a@b.com"))
// ==> Ok[a@b.com]

// Field required, null allowed
field("nickname", nullable(string())).decode(Map.of("nickname", "alice"))
// ==> Ok[alice]

// Field itself is optional
optionalField("middleName", string()).decode(Map.of())
// ==> Ok[Optional.empty]
```

Three-state values are handled with `optionalNullableField`. The returned `Presence<T>` distinguishes all three states.

```java
optionalNullableField("bio", string()).decode(Map.of("bio", "hello"))
// ==> Ok[Present[value=hello]]

var m = new HashMap<String, Object>(); m.put("bio", null);
optionalNullableField("bio", string()).decode(m)
// ==> Ok[PresentNull[]]

optionalNullableField("bio", string()).decode(Map.of())
// ==> Ok[Absent[]]
```

- `Presence.Present(value)` — value is set
- `Presence.PresentNull` — null was explicitly sent
- `Presence.Absent` — the field itself does not exist

This is useful in PATCH APIs where "clear the value" must be distinguished from "leave unchanged".

```java
record ProfilePatch(Presence<String> nickname, Presence<String> bio) {}

var profilePatchDec = combine(
        optionalNullableField("nickname", string().maxLength(50)),
        optionalNullableField("bio", string().maxLength(500))
).map(ProfilePatch::new);

profilePatchDec.decode(Map.of("nickname", "alice"))
// ==> Ok[ProfilePatch[nickname=Present[value=alice], bio=Absent[]]]
```

---

## 10. Decoding enum values

Java enums can be decoded case-insensitively.

```java
enum Role { ADMIN, MEMBER, GUEST }

field("role", enumOf(Role.class)).decode(Map.of("role", "admin"))
// ==> Ok[ADMIN]

field("role", enumOf(Role.class)).decode(Map.of("role", "ADMIN"))
// ==> Ok[ADMIN]

field("role", withDefault(enumOf(Role.class), Role.MEMBER)).decode(Map.of())
// ==> Ok[MEMBER]
```

---

## 11. Union types — discrimination with oneOf

A pattern for discriminating by a `kind` field and decoding different structures accordingly.

```java
sealed interface Contact {}
record EmailContact(String address) implements Contact {}
record PhoneContact(String number) implements Contact {}

var contactDec = oneOf(
        combine(
                field("kind", literal("email")),
                field("value", string().email())
        ).map((kind, value) -> (Contact) new EmailContact(value)),
        combine(
                field("kind", literal("phone")),
                field("value", string().pattern(Pattern.compile("^\\d{10,15}$")))
        ).map((kind, value) -> (Contact) new PhoneContact(value))
);

contactDec.decode(Map.of("kind", "email", "value", "a@b.com"))
// ==> Ok[EmailContact[address=a@b.com]]

contactDec.decode(Map.of("kind", "phone", "value", "09012345678"))
// ==> Ok[PhoneContact[number=09012345678]]

contactDec.decode(Map.of("kind", "fax", "value", "123"))
// ==> Err[/: no variant matched]
```

When no candidate matches, a `no variant matched` error is returned.

---

## 12. Cross-field validation — flatMap

Use `flatMap` for consistency checks across multiple fields. Fields are read with `combine`, then domain rules are applied.

```java
record DateRange(LocalDate start, LocalDate end) {
    static Result<DateRange> parse(LocalDate start, LocalDate end) {
        if (!start.isBefore(end)) {
            return Result.fail(Path.ROOT, "invalid_range", "start must be before end");
        }
        return Result.ok(new DateRange(start, end));
    }
}

var dateRangeDec = combine(
        field("start", string().date()),
        field("end", string().date())
).flatMap(DateRange::parse);

dateRangeDec.decode(Map.of("start", "2025-01-01", "end", "2025-12-31"))
// ==> Ok[DateRange[start=2025-01-01, end=2025-12-31]]

dateRangeDec.decode(Map.of("start", "2025-12-31", "end", "2025-01-01"))
// ==> Err[/: start must be before end]
```

The path of a `Result.fail` returned inside `flatMap` is automatically rebased to the caller's path. If used as `field("period", dateRangeDec)`, the error path becomes `/period`.

`flatMap` only executes the second stage when the first succeeds. Use `Result.map2` (see below) when composing two independent results.

---

## 13. Conditional decoding — branching on a method field

A case where the decoding method for subsequent fields changes based on the value of one field. Written as a `Decoder<I, T>` lambda, the `method` field is read first and then the remaining fields are switched accordingly.

```java
interface Payment {}
record CreditCard(String number, String expiry) implements Payment {}
record BankTransfer(String bankCode, String accountNumber) implements Payment {}

Decoder<Map<String, Object>, Payment> paymentDec = (in, path) -> {
    var mr = field("method", string()).decode(in, path);
    if (mr instanceof Err<String> e) return Result.err(e.issues());
    return switch (((Ok<String>) mr).value()) {
        case "credit_card" -> combine(
                field("number", string().nonBlank()),
                field("expiry", string().pattern(Pattern.compile("^\\d{2}/\\d{2}$")))
        ).map((n, e) -> (Payment) new CreditCard(n, e)).decode(in, path);
        case "bank_transfer" -> combine(
                field("bankCode", string().nonBlank()),
                field("accountNumber", string().nonBlank())
        ).map((b, a) -> (Payment) new BankTransfer(b, a)).decode(in, path);
        default -> Result.fail("unsupported_method", "Unknown payment method: " + ((Ok<String>) mr).value());
    };
};

paymentDec.decode(Map.of("method", "credit_card", "number", "4111111111111111", "expiry", "12/26"))
// ==> Ok[CreditCard[number=4111111111111111, expiry=12/26]]

paymentDec.decode(Map.of("method", "bank_transfer", "bankCode", "0001", "accountNumber", "1234567"))
// ==> Ok[BankTransfer[bankCode=0001, accountNumber=1234567]]
```

---

## 14. Composing results from different sources — Result.map2

`combine` composes by extracting fields from the same input. To compose two `Result` values obtained from different data sources (different tables, different API responses, etc.), use `Result.map2`.

```java
record PersonalName(String first, String last) {}
record ContactInfo(String email, String phone) {}
record CustomerProfile(PersonalName name, ContactInfo contact) {}

var nameDec = combine(
        field("first", string().nonBlank()),
        field("last", string().nonBlank())
).map(PersonalName::new);

var contactInfoDec = combine(
        field("email", string().email()),
        field("phone", string().nonBlank())
).map(ContactInfo::new);

var nameResult = nameDec.decode(Map.of("first", "Alice", "last", "Smith"));
var contactResult = contactInfoDec.decode(Map.of("email", "a@b.com", "phone", "090-1234-5678"));

Result.map2(nameResult, contactResult, CustomerProfile::new)
// ==> Ok[CustomerProfile[name=PersonalName[...], contact=ContactInfo[...]]]
```

When both fail, errors from both sides are merged. `combine` is "pre-decode" composition; `Result.map2` is "post-decode" composition.

---

## 15. Bulk decoding of list elements — Result.traverse

When converting multiple rows to a list of domain objects, `Result.traverse` checks all rows and accumulates errors.

```java
record Order(String orderId, BigDecimal total) {}

var orderDec = combine(
        field("order_id", string().nonBlank()),
        field("total", decimal().nonNegative())
).map(Order::new);

var rows = List.<Map<String, Object>>of(
        Map.of("order_id", "A001", "total", 1000),
        Map.of("order_id", "", "total", -500),
        Map.of("order_id", "A003", "total", 300)
);

Result.traverse(rows, orderDec::decode, Path.of("orders"))
// ==> Err[/orders/1/order_id: is required, /orders/1/total: must be non-negative]
```

Index-tagged paths (like `/orders/1/order_id`) tell you exactly which row failed.

---

## 16. Default values and recovery

### withDefault — fallback when missing

Applies a default value only when the field is absent or `null`. When the field is present but invalid, an error is returned.

```java
field("role", withDefault(enumOf(Role.class), Role.MEMBER)).decode(Map.of())
// ==> Ok[MEMBER]

field("role", withDefault(enumOf(Role.class), Role.MEMBER)).decode(Map.of("role", "admin"))
// ==> Ok[ADMIN]

field("role", withDefault(enumOf(Role.class), Role.MEMBER)).decode(Map.of("role", "invalid"))
// ==> Err[/role: ...]
```

### recover — fallback from any failure

Absorbs any decoding failure, including invalid values, with a fallback value.

```java
recover(field("pageSize", int_().range(1, 100)), 20).decode(Map.of("pageSize", 999))
// ==> Ok[20]

recover(field("pageSize", int_().range(1, 100)), 20).decode(Map.of())
// ==> Ok[20]
```

Use `withDefault` for "conceptually optional" fields, and `recover` for "must not break no matter what input arrives".

---

## 17. Strict mode — rejecting unknown fields

Reject any fields not defined in the schema.

```java
record ApiRequest(String action, int amount) {}

var apiRequestDec = combine(
        field("action", string().nonBlank()),
        field("amount", int_().positive())
).strict(ApiRequest::new);

apiRequestDec.decode(Map.of("action", "transfer", "amount", 100))
// ==> Ok[ApiRequest[action=transfer, amount=100]]

apiRequestDec.decode(Map.of("action", "transfer", "amount", 100, "extra", true))
// ==> Err[/extra: unknown_field]
```

---

## 18. Recursive structures — lazy

When a type references itself, use `lazy` to resolve the circular reference.

```java
record Comment(String body, List<Comment> replies) {}

Decoder[] self = new Decoder[1];
self[0] = combine(
        field("body", string().nonBlank()),
        withDefault(field("replies", list(lazy(() -> self[0]))), List.of())
).map(Comment::new);
var commentDec = self[0];

commentDec.decode(Map.of(
        "body", "top",
        "replies", List.of(
                Map.of("body", "reply1", "replies", List.of()),
                Map.of("body", "reply2", "replies", List.of(
                        Map.of("body", "nested", "replies", List.of())
                ))
        )
))
// ==> Ok[Comment[body=top, replies=[Comment[body=reply1, ...], Comment[body=reply2, ...]]]]
```

---

## 19. Error handling patterns in practice

Raoh errors are structured and can be extracted in multiple representations depending on the use case.

```java
var checkDec = combine(
        field("email", string().email()),
        field("age", int_().range(0, 150))
).map((email, age) -> Map.of("email", email, "age", age));

var result = checkDec.decode(Map.of("email", "bad", "age", 300));
```

### For forms — flatten

A flat map with path as key and a list of messages as value. Maps directly to front-end form validation display.

```java
switch (result) {
    case Err(var issues) -> issues.flatten();
    default -> {}
}
// ==> {/email=[not a valid email], /age=[must be between 0 and 150]}
```

### For APIs — toJsonList

Returns each error as a list of objects containing `path`, `code`, `message`, and `meta`. Maps directly to REST API error responses.

```java
switch (result) {
    case Err(var issues) -> issues.toJsonList();
    default -> {}
}
// ==> [{path=/email, code=invalid_format, message=not a valid email, meta={}}, ...]
```

### Locale-aware messages

Combined with `ResourceBundleMessageResolver`, you can generate locale-appropriate messages without changing the decoders.

```java
var resolver = new ResourceBundleMessageResolver("messages");
switch (result) {
    case Err(var issues) -> issues.resolve(resolver, Locale.JAPANESE);
    default -> {}
}
```

---

## 20. Business scenario: User registration API

A real-world example combining the patterns covered so far.

```java
sealed interface ContactMethod {}
record EmailMethod(String address) implements ContactMethod {}
record PhoneMethod(String number) implements ContactMethod {}
record Password(String value) {}
record UserRegistration(Email email, Password password, Role role, List<ContactMethod> contacts) {}

var contactMethodDec = oneOf(
        combine(
                field("kind", literal("email")),
                field("value", string().email())
        ).map((k, v) -> (ContactMethod) new EmailMethod(v)),
        combine(
                field("kind", literal("phone")),
                field("value", string().pattern(Pattern.compile("^\\d{10,15}$")))
        ).map((k, v) -> (ContactMethod) new PhoneMethod(v))
);

var userRegDec = combine(
        field("email", string().trim().toLowerCase().email().map(Email::new)),
        field("password", string().minLength(8).maxLength(128).map(Password::new)),
        field("role", withDefault(enumOf(Role.class), Role.MEMBER)),
        field("contacts", list(contactMethodDec).nonempty())
).map(UserRegistration::new);

userRegDec.decode(Map.of(
        "email", "  ALICE@Example.com  ",
        "password", "secureP@ss1",
        "contacts", List.of(
                Map.of("kind", "email", "value", "alice@example.com"),
                Map.of("kind", "phone", "value", "09012345678")
        )
))
// ==> Ok[UserRegistration[email=Email[value=alice@example.com], password=Password[...], role=MEMBER, contacts=[EmailMethod[...], PhoneMethod[...]]]]
```

Verify that all errors accumulate with invalid input:

```java
userRegDec.decode(Map.of(
        "email", "bad",
        "password", "short",
        "contacts", List.of(
                Map.of("kind", "email", "value", "not-email")
        )
))
// ==> Err[/email: not a valid email, /password: must be at least 8 characters, /contacts/0/value: not a valid email]
```

For Spring MVC controllers receiving JSON input, swap in `JsonDecoders` (reference):

```java
// import static net.unit8.raoh.json.JsonDecoders.*;
//
// @PostMapping("/users")
// ResponseEntity<?> register(@RequestBody JsonNode body) {
//     return switch (userRegDec.decode(body)) {
//         case Ok(var reg) -> ResponseEntity.status(201).body(userService.register(reg));
//         case Err(var issues) -> ResponseEntity.badRequest().body(issues.toJsonList());
//     };
// }
```

---

## 21. Converting a list to a Map

**Scenario:** When a product master API returns data as `[{"id": "APPLE", "price": 120}, ...]`, you may want to hold it internally as `Map<String, BigDecimal>`. If you repeatedly look up by ID, a list requires linear search each time, so converting to a Map at decode time is natural.

Decode the list with `list()`, then apply `Collectors.toMap` via `map`.

```java
record Product(String id, BigDecimal price) {}

var productDec = combine(
        field("id", string().nonBlank()),
        field("price", decimal().positive())
).map(Product::new);

// Decode the list then convert to a Map
var priceMapDec = field("products",
        list(nested(productDec))
                .map(products -> products.stream()
                        .collect(Collectors.toMap(Product::id, Product::price))));

priceMapDec.decode(Map.of("products", List.of(
        Map.of("id", "APPLE",  "price", new BigDecimal("120")),
        Map.of("id", "BANANA", "price", new BigDecimal("80"))
)))
// ==> Ok[{APPLE=120, BANANA=80}]
```

Duplicate IDs are a domain rule violation. A check can be added with `flatMap`. `Collectors.toMap` throws on duplicate keys, but returning a Raoh error is more user-friendly.

```java
var strictPriceMapDec = field("products",
        list(nested(productDec))
                .flatMap(products -> {
                    var ids = products.stream().map(Product::id).toList();
                    var duplicates = ids.stream()
                            .filter(id -> ids.stream().filter(id::equals).count() > 1)
                            .distinct().toList();
                    if (!duplicates.isEmpty())
                        return Result.fail("duplicate_ids", "duplicate product ids: " + duplicates);
                    return Result.ok(products.stream()
                            .collect(Collectors.toMap(Product::id, Product::price)));
                }));

strictPriceMapDec.decode(Map.of("products", List.of(
        Map.of("id", "APPLE",  "price", new BigDecimal("120")),
        Map.of("id", "APPLE",  "price", new BigDecimal("99"))
)))
// ==> Err[/products: duplicate product ids: [APPLE]]
```

---

## 22. Known fields + collecting remaining fields into a Map

**Scenario:** When handling entities with "fixed attributes + free extension attributes" like products or events, fixed schema fields and dynamic additional attributes coexist in the same Map. For example, in `{"name": "T-shirt", "color": "red", "size": "L"}`, `name` is a required field, while `color` and `size` are extension attributes that vary by category.

The equivalent of Jackson's `@JsonAnySetter` is achieved with `Result.map2`. While `strict` rejects unknown fields, this pattern **collects** them into a `Map`.

```java
record Item(String name, Map<String, String> attrs) {}

// Set of known fields
var knownFields = Set.of("name");

Decoder<Map<String, Object>, Item> itemDec = (in, path) -> {
    // Decode fixed fields
    var nameDec = field("name", string().nonBlank());
    // Collect all remaining fields as String
    Decoder<Map<String, Object>, Map<String, String>> attrsDec = (m, p) -> {
        var attrs = m.entrySet().stream()
                .filter(e -> !knownFields.contains(e.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> String.valueOf(e.getValue())));
        return Result.ok(attrs);
    };
    // Use map2 to run both independently and merge errors
    return Result.map2(
            nameDec.decode(in, path),
            attrsDec.decode(in, path),
            Item::new);
};

itemDec.decode(Map.of("name", "T-shirt", "color", "red", "size", "L"))
// ==> Ok[Item[name=T-shirt, attrs={color=red, size=L}]]
```

Using `Result.map2` ensures that fixed field validation errors and remaining field collection run independently. To validate the values of remaining fields with `string()`, pipe each value through `string().decode(e.getValue(), ...)` inside `attrsDec`.

---

## 23. Password confirmation — a classic cross-field validation

**Scenario:** A password change form has two cross-field rules: "new password and confirmation password must match" and "new password must differ from the current password". Neither is a constraint on an individual field; both apply to a combination of multiple field values.

Fields are validated independently with `combine`, then cross-field checks are added with `flatMap`. The rules inside `flatMap` only execute after all preceding validations pass.

```java
record PasswordChange(String current, String newPassword) {}

var passwordChangeDec = combine(
        field("currentPassword", string().nonBlank()),
        field("newPassword",     string().minLength(8).maxLength(128)),
        field("confirmPassword", string().nonBlank())
).flatMap((current, newPw, confirm) -> {
    if (!newPw.equals(confirm))
        return Result.fail("password_mismatch", "newPassword and confirmPassword do not match");
    if (current.equals(newPw))
        return Result.fail("same_password", "new password must differ from current password");
    return Result.ok(new PasswordChange(current, newPw));
});

passwordChangeDec.decode(Map.of(
        "currentPassword", "oldSecret",
        "newPassword",     "newSecret1",
        "confirmPassword", "newSecret1"
))
// ==> Ok[PasswordChange[current=oldSecret, newPassword=newSecret1]]

passwordChangeDec.decode(Map.of(
        "currentPassword", "oldSecret",
        "newPassword",     "short",
        "confirmPassword", "different"
))
// ==> Err[/newPassword: must be at least 8 characters]
// (flatMap is skipped when the first stage fails; the match check only runs when all fields are valid)
```

---

## 24. Pagination / query parameters

**Scenario:** A search API receives query parameters like `page`, `size`, and `sort`. These have two dimensions: "default value when omitted" and "policy for malformed values". Typically, `page` should explicitly error on negative numbers, while `size` and `sort` should fall back to a safe default and continue operating even with slightly wrong values.

Using `withDefault` (fallback only when missing) and `recover` (fallback from any failure) enables explicit control of resilience per parameter.

```java
enum SortOrder { ASC, DESC }
record PageRequest(int page, int size, SortOrder order) {}

var pageRequestDec = combine(
        // page: default 0 when absent, but negative is an explicit error
        withDefault(field("page", int_().range(0, Integer.MAX_VALUE)), 0),
        // size: both absent and malformed fall back to 20
        recover(withDefault(field("size", int_().range(1, 100)), 20), 20),
        // order: both absent and invalid fall back to ASC
        recover(withDefault(field("sort", enumOf(SortOrder.class)), SortOrder.ASC), SortOrder.ASC)
).map(PageRequest::new);

// Normal request
pageRequestDec.decode(Map.of("page", 2, "size", 50, "sort", "desc"))
// ==> Ok[PageRequest[page=2, size=50, order=DESC]]

// All parameters omitted — defaults are used
pageRequestDec.decode(Map.of())
// ==> Ok[PageRequest[page=0, size=20, order=ASC]]

// size out of range — recovered by fallback
pageRequestDec.decode(Map.of("size", 9999))
// ==> Ok[PageRequest[page=0, size=20, order=ASC]]

// negative page — explicit error as per specification
pageRequestDec.decode(Map.of("page", -1))
// ==> Err[/page: must be between 0 and 2147483647]
```

`withDefault` expresses "this field is conceptually optional, but if sent it must be valid". `recover` expresses "must operate safely no matter what value arrives".

---

## 25. Amount and currency cross-field validation

**Scenario:** Payment systems and accounting often receive amount and currency code pairs. Amount validity differs by currency: JPY allows no decimal places, USD/EUR allow up to 2. This rule cannot be determined from individual fields alone — it requires both fields together, making it a cross-field rule.

```java
enum Currency { JPY, USD, EUR }
record Money(BigDecimal amount, Currency currency) {}

var moneyDec = combine(
        field("amount",   decimal()),
        field("currency", enumOf(Currency.class))
).flatMap((amount, currency) -> {
    // Maximum decimal places allowed per currency
    int maxScale = switch (currency) {
        case JPY -> 0;
        case USD, EUR -> 2;
    };
    if (amount.scale() > maxScale)
        return Result.fail("invalid_scale",
                currency + " does not allow more than " + maxScale + " decimal places");
    if (amount.compareTo(BigDecimal.ZERO) < 0)
        return Result.fail("negative_amount", "amount must not be negative");
    return Result.ok(new Money(amount, currency));
});

moneyDec.decode(Map.of("amount", new BigDecimal("1000"), "currency", "jpy"))
// ==> Ok[Money[amount=1000, currency=JPY]]

moneyDec.decode(Map.of("amount", new BigDecimal("10.5"), "currency", "jpy"))
// ==> Err[/: JPY does not allow more than 0 decimal places]

moneyDec.decode(Map.of("amount", new BigDecimal("9.99"), "currency", "usd"))
// ==> Ok[Money[amount=9.99, currency=USD]]
```

---

## 26. CSV import — accumulating errors with row numbers

**Scenario:** Bulk member registration or data migration often involves uploading a CSV file. Rather than stopping at the first error, checking all rows and reporting "email on row 2 is invalid, age on row 4 is out of range" all at once is friendlier to users. `Result.traverse` fits this pattern.

```java
record MemberImport(String email, String name, int age) {}

var memberDec = combine(
        field("email", string().trim().toLowerCase().email()),
        field("name",  string().trim().nonBlank().maxLength(100)),
        field("age",   int_().range(0, 150))
).map(MemberImport::new);

// Assume CSV is parsed into a list of Maps
var rows = List.<Map<String, Object>>of(
        Map.of("email", "alice@example.com", "name", "Alice", "age", 30),
        Map.of("email", "bad-email",          "name", "",      "age", 200),
        Map.of("email", "bob@example.com",   "name", "Bob",   "age", 25),
        Map.of("email", "also-bad",           "name", "Carol", "age", -1)
);

var result = Result.traverse(rows, memberDec::decode, Path.of("rows"));
switch (result) {
    case Ok(var members) -> System.out.println("Imported: " + members.size());
    case Err(var issues) -> issues.flatten().forEach((path, msgs) ->
            System.out.println(path + ": " + msgs));
}
// /rows/1/email: [not a valid email]
// /rows/1/name:  [is required]
// /rows/1/age:   [must be between 0 and 150]
// /rows/3/email: [not a valid email]
// /rows/3/age:   [must be between 0 and 150]
```

Row numbers are included in the paths, so you can return directly to users which rows have problems in the UI or error report. Even when errors span multiple rows, a single decode call collects all of them.

---

## 27. Decoding configuration files — nested structure + withDefault

**Scenario:** When loading application configuration files (YAML/TOML parsed into a Map), required and optional sections coexist: "DB connection is required, but cache settings should use defaults if absent", "log level defaults to INFO if omitted". Decoding with type safety lets you detect configuration errors at application startup.

```java
record DbConfig(String host, int port, String database) {}
record CacheConfig(String host, int port, int ttlSeconds) {}
record AppConfig(DbConfig db, CacheConfig cache, String logLevel) {}

var dbConfigDec = combine(
        field("host",     string().nonBlank()),
        withDefault(field("port", int_().range(1, 65535)), 5432),
        field("database", string().nonBlank())
).map(DbConfig::new);

var cacheConfigDec = combine(
        withDefault(field("host",       string().nonBlank()), "localhost"),
        withDefault(field("port",       int_().range(1, 65535)), 6379),
        withDefault(field("ttlSeconds", int_().positive()), 300)
).map(CacheConfig::new);

var appConfigDec = combine(
        field("db",    nested(dbConfigDec)),
        // cache section itself is optional — use default config if absent
        withDefault(
                field("cache", nested(cacheConfigDec)),
                new CacheConfig("localhost", 6379, 300)),
        withDefault(field("logLevel", string().nonBlank()), "INFO")
).map(AppConfig::new);

// Only db specified — others use defaults
appConfigDec.decode(Map.of(
        "db", Map.of("host", "db.example.com", "database", "myapp")
))
// ==> Ok[AppConfig[db=DbConfig[host=db.example.com, port=5432, database=myapp],
//                  cache=CacheConfig[host=localhost, port=6379, ttlSeconds=300],
//                  logLevel=INFO]]

// db host is empty — detected as an error at startup
appConfigDec.decode(Map.of(
        "db", Map.of("host", "", "database", "myapp")
))
// ==> Err[/db/host: is required]
```

Running the configuration decoder at application startup lets you catch missing environment variables and type mismatches before they reach production code.
