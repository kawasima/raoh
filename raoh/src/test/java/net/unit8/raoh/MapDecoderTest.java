package net.unit8.raoh;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static net.unit8.raoh.map.MapDecoders.*;
import static net.unit8.raoh.ObjectDecoders.*;

class MapDecoderTest {

    // --- Domain types ---

    record Email(String value) {}
    record Age(int value) {}
    record UserId(UUID value) {}

    enum Prefecture { TOKYO, OSAKA, HOKKAIDO }

    record Address(Prefecture prefecture, String city, String street) {}
    record User(UserId id, Email email, Age age, Address address) {}

    enum Currency {
        JPY(0), USD(2), EUR(2);
        private final int fractionDigits;
        Currency(int fractionDigits) { this.fractionDigits = fractionDigits; }
        public int fractionDigits() { return fractionDigits; }
    }

    record Money(BigDecimal amount, Currency currency) {
        public static Result<Money> parse(BigDecimal amount, Currency currency) {
            var issues = Issues.EMPTY;
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                issues = issues.add(Issue.of(Path.ROOT, "out_of_range",
                        "amount must be positive", Map.of("actual", amount)));
            }
            if (amount.scale() > currency.fractionDigits()) {
                issues = issues.add(Issue.of(Path.ROOT, "invalid_scale",
                        "too many decimal places for " + currency.name(),
                        Map.of("maxScale", currency.fractionDigits(), "actualScale", amount.scale())));
            }
            return issues.isEmpty()
                    ? Result.ok(new Money(amount, currency))
                    : Result.err(issues);
        }
    }

    // --- Decoders ---

    static Decoder<Object, Email> email() {
        return string().trim().toLowerCase().email().map(Email::new);
    }

    static Decoder<Object, Age> age() {
        return int_().range(0, 150).map(Age::new);
    }

    static Decoder<Object, UserId> userId() {
        return string().uuid().map(UserId::new);
    }

    static Decoder<Map<String, Object>, Address> address() {
        return combine(
                field("prefecture", enumOf(Prefecture.class)),
                field("city", string().maxLength(100)),
                field("street", string().maxLength(200))
        ).map(Address::new);
    }

    static Decoder<Map<String, Object>, User> user() {
        return combine(
                field("id", userId()),
                field("email", email()),
                field("age", age()),
                field("address", nested(address()))
        ).map(User::new);
    }

    static Decoder<Map<String, Object>, Money> money() {
        return combine(
                field("amount", decimal()),
                field("currency", enumOf(Currency.class))
        ).flatMap(Money::parse);
    }

    // --- Tests ---

    @Test
    void validUser() {
        var input = Map.<String, Object>of(
                "id", "550e8400-e29b-41d4-a716-446655440000",
                "email", "Alice@Example.com",
                "age", 30,
                "address", Map.of(
                        "prefecture", "tokyo",
                        "city", "千代田区",
                        "street", "1-1-1"
                )
        );

        switch (user().decode(input)) {
            case Ok(var u) -> {
                assertEquals("alice@example.com", u.email().value());
                assertEquals(30, u.age().value());
                assertEquals(Prefecture.TOKYO, u.address().prefecture());
            }
            case Err(var issues) -> fail("Expected Ok, got: " + issues);
        }
    }

    @Test
    void multipleErrors() {
        var input = Map.<String, Object>of(
                "id", "not-a-uuid",
                "email", "bad",
                "age", 200,
                "address", Map.of(
                        "prefecture", "atlantis",
                        "city", "中央区",
                        "street", "1-1-1"
                )
        );

        switch (user().decode(input)) {
            case Ok(_) -> fail("Expected Err");
            case Err(var issues) -> {
                assertTrue(issues.asList().size() >= 3);
                var paths = issues.asList().stream()
                        .map(i -> i.path().toJsonPointer()).toList();
                assertTrue(paths.contains("/id"));
                assertTrue(paths.contains("/email"));
                assertTrue(paths.contains("/age"));
                assertTrue(paths.contains("/address/prefecture"));
            }
        }
    }

    @Test
    void moneyValid() {
        var input = Map.<String, Object>of("amount", 1500, "currency", "jpy");
        var m = assertOk(money().decode(input));
        assertEquals(0, new BigDecimal("1500").compareTo(m.amount()));
        assertEquals(Currency.JPY, m.currency());
    }

    @Test
    void moneyInvalidScale() {
        var input = Map.<String, Object>of("amount", 19.99, "currency", "jpy");
        switch (money().decode(input)) {
            case Ok(_) -> fail("Expected Err");
            case Err(var issues) -> {
                var codes = issues.asList().stream().map(Issue::code).toList();
                assertTrue(codes.contains("invalid_scale"));
            }
        }
    }

    @Test
    void stringConstraints() {
        var dec = field("name", string().minLength(3).maxLength(10));
        assertEquals("abc", assertOk(dec.decode(Map.of("name", "abc"))));
        assertErr(dec.decode(Map.of("name", "ab")));
        assertErr(dec.decode(Map.of("name", "12345678901")));
    }

    @Test
    void stringOneOf() {
        var dec = field("status", string().oneOf("active", "inactive", "pending"));
        assertEquals("active", assertOk(dec.decode(Map.of("status", "active"))));
        assertEquals("pending", assertOk(dec.decode(Map.of("status", "pending"))));
        var result = dec.decode(Map.of("status", "deleted"));
        switch (result) {
            case Ok(var v) -> fail("Expected Err, got Ok: " + v);
            case Err(var issues) -> {
                var issue = issues.asList().getFirst();
                assertEquals(ErrorCodes.NOT_ALLOWED, issue.code());
                assertEquals(List.of("active", "inactive", "pending"), issue.meta().get("allowed"));
                assertEquals("deleted", issue.meta().get("actual"));
            }
        }
    }

    @Test
    void intOneOf() {
        var dec = field("priority", int_().oneOf(1, 2, 3));
        assertEquals(2, assertOk(dec.decode(Map.of("priority", 2))));
        var result = dec.decode(Map.of("priority", 5));
        switch (result) {
            case Ok(var v) -> fail("Expected Err, got Ok: " + v);
            case Err(var issues) -> assertEquals(ErrorCodes.NOT_ALLOWED, issues.asList().getFirst().code());
        }
    }

    @Test
    void longOneOf() {
        var dec = field("id", long_().oneOf(100L, 200L, 300L));
        assertEquals(200L, assertOk(dec.decode(Map.of("id", 200))));
        var result = dec.decode(Map.of("id", 999));
        switch (result) {
            case Ok(var v) -> fail("Expected Err, got Ok: " + v);
            case Err(var issues) -> assertEquals(ErrorCodes.NOT_ALLOWED, issues.asList().getFirst().code());
        }
    }

    @Test
    void intConstraints() {
        var dec = field("n", int_().positive().multipleOf(3));
        assertEquals(9, assertOk(dec.decode(Map.of("n", 9))));
        assertErr(dec.decode(Map.of("n", -3)));
        assertErr(dec.decode(Map.of("n", 5)));
    }

    @Test
    void longConstraints() {
        var dec = field("n", long_().min(100L).max(1000L));
        assertEquals(500L, assertOk(dec.decode(Map.of("n", 500))));
        assertErr(dec.decode(Map.of("n", 50)));
        assertErr(dec.decode(Map.of("n", 2000)));
    }

    @Test
    void boolDecoder() {
        var dec = field("flag", bool());
        assertTrue(assertOk(dec.decode(Map.of("flag", true))));
        assertFalse(assertOk(dec.decode(Map.of("flag", false))));
        assertErr(dec.decode(Map.of("flag", "true")));
    }

    @Test
    void listDecoder() {
        var dec = field("items", list(string()));
        var result = assertOk(dec.decode(Map.of("items", List.of("a", "b", "c"))));
        assertEquals(List.of("a", "b", "c"), result);
    }

    @Test
    void listNonempty() {
        var dec = field("items", list(string()).nonempty());
        assertErr(dec.decode(Map.of("items", List.of())));
    }

    @Test
    void listContains() {
        var dec = field("tags", list(string()).contains("required-tag"));
        assertOk(dec.decode(Map.of("tags", List.of("required-tag", "other"))));
        var result = dec.decode(Map.of("tags", List.of("foo", "bar")));
        switch (result) {
            case Ok(var v) -> fail("Expected Err, got Ok: " + v);
            case Err(var issues) -> {
                var issue = issues.asList().getFirst();
                assertEquals(ErrorCodes.MISSING_ELEMENT, issue.code());
                assertEquals("required-tag", issue.meta().get("expected"));
            }
        }
    }

    @Test
    void listContainsAll() {
        var dec = field("roles", list(string()).containsAll("read", "write"));
        assertOk(dec.decode(Map.of("roles", List.of("read", "write", "admin"))));
        var result = dec.decode(Map.of("roles", List.of("read", "admin")));
        switch (result) {
            case Ok(var v) -> fail("Expected Err, got Ok: " + v);
            case Err(var issues) -> {
                var issue = issues.asList().getFirst();
                assertEquals(ErrorCodes.MISSING_ELEMENTS, issue.code());
                assertEquals(List.of("write"), issue.meta().get("missing"));
            }
        }
    }

    @Test
    void listContainsAllEmptyThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> list(string()).containsAll());
    }

    @Test
    void listContainsNullThrows() {
        assertThrows(NullPointerException.class,
                () -> list(string()).contains(null));
    }

    @Test
    void listUniqueEmpty() {
        var dec = field("items", list(string()).unique());
        assertOk(dec.decode(Map.of("items", List.of())));
    }

    @Test
    void listUnique() {
        var dec = field("emails", list(string()).unique());
        assertOk(dec.decode(Map.of("emails", List.of("a@example.com", "b@example.com"))));
        var result = dec.decode(Map.of("emails", List.of("a@example.com", "b@example.com", "a@example.com")));
        switch (result) {
            case Ok(var v) -> fail("Expected Err, got Ok: " + v);
            case Err(var issues) -> {
                var issue = issues.asList().getFirst();
                assertEquals(ErrorCodes.DUPLICATE_ELEMENT, issue.code());
                assertEquals(List.of("a@example.com"), issue.meta().get("duplicates"));
            }
        }
    }

    @Test
    void listUniqueNullDuplicate() {
        // Test unique() directly at the ListDecoder level to avoid List.copyOf restriction in MapDecoders.list()
        var dec = new net.unit8.raoh.builtin.ListDecoder<List<String>, String>(
                (in, path) -> Result.ok(in)).unique();
        var input = new java.util.ArrayList<String>();
        input.add(null);
        input.add("a");
        input.add(null);
        var result = dec.decode(input, Path.ROOT);
        switch (result) {
            case Ok(var v) -> fail("Expected Err, got Ok: " + v);
            case Err(var issues) -> assertEquals(ErrorCodes.DUPLICATE_ELEMENT, issues.asList().getFirst().code());
        }
    }

    @Test
    void listElementErrors() {
        var dec = field("items", list(int_().positive()));
        var result = dec.decode(Map.of("items", List.of(1, -2, 3, -4)));
        switch (result) {
            case Ok(_) -> fail("Expected Err");
            case Err(var issues) -> {
                assertEquals(2, issues.asList().size());
                var paths = issues.asList().stream()
                        .map(i -> i.path().toJsonPointer()).toList();
                assertTrue(paths.contains("/items/1"));
                assertTrue(paths.contains("/items/3"));
            }
        }
    }

    @Test
    void optionalFieldPresent() {
        var dec = optionalField("nick", string());
        var result = assertOk(dec.decode(Map.of("nick", "foo")));
        assertEquals(Optional.of("foo"), result);
    }

    @Test
    void optionalFieldAbsent() {
        var dec = optionalField("nick", string());
        var result = assertOk(dec.decode(Map.of()));
        assertEquals(Optional.empty(), result);
    }

    @Test
    void nullableField() {
        var dec = field("val", nullable(string()));
        // null value in map
        var mapWithNull = new java.util.HashMap<String, Object>();
        mapWithNull.put("val", null);
        assertNull(assertOk(dec.decode(mapWithNull)));
        assertEquals("x", assertOk(dec.decode(Map.of("val", "x"))));
    }

    @Test
    void presenceTriState() {
        var dec = optionalNullableField("email", string());
        var absent = assertOk(dec.decode(Map.of()));
        assertInstanceOf(Presence.Absent.class, absent);

        var mapWithNull = new java.util.HashMap<String, Object>();
        mapWithNull.put("email", null);
        var presentNull = assertOk(dec.decode(mapWithNull));
        assertInstanceOf(Presence.PresentNull.class, presentNull);

        var present = assertOk(dec.decode(Map.of("email", "a@b.com")));
        assertInstanceOf(Presence.Present.class, present);
    }

    @Test
    void enumDecoder() {
        var dec = field("p", enumOf(Prefecture.class));
        assertEquals(Prefecture.TOKYO, assertOk(dec.decode(Map.of("p", "tokyo"))));
        assertEquals(Prefecture.OSAKA, assertOk(dec.decode(Map.of("p", "OSAKA"))));
        assertErr(dec.decode(Map.of("p", "invalid")));
    }

    @Test
    void uuidParsing() {
        var dec = field("id", string().uuid());
        var id = assertOk(dec.decode(Map.of("id", "550e8400-e29b-41d4-a716-446655440000")));
        assertEquals(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), id);
        assertErr(dec.decode(Map.of("id", "not-a-uuid")));
    }

    @Test
    void mapDecoder() {
        var dec = field("prices", map(decimal()));
        var result = assertOk(dec.decode(
                Map.of("prices", Map.of("apple", 1.50, "banana", 0.99))));
        assertEquals(2, result.size());
        assertNotNull(result.get("apple"));
    }

    @Test
    void mapDecoderSizeConstraints() {
        var dec = field("tags", map(string()).minSize(1).maxSize(3));
        assertOk(dec.decode(Map.of("tags", Map.of("a", "x"))));
        assertErr(dec.decode(Map.of("tags", Map.of())));
        assertErr(dec.decode(Map.of("tags", Map.of("a", "x", "b", "y", "c", "z", "d", "w"))));
    }

    @Test
    void missingRequiredField() {
        var dec = field("name", string());
        var result = dec.decode(Map.of());
        switch (result) {
            case Ok(_) -> fail("Expected Err");
            case Err(var issues) -> {
                assertEquals("/name", issues.asList().getFirst().path().toJsonPointer());
                assertEquals("required", issues.asList().getFirst().code());
            }
        }
    }

    @Test
    void typeMismatch() {
        var dec = field("age", int_());
        assertErr(dec.decode(Map.of("age", "thirty")));
    }

    @Test
    void allowBlankString() {
        var dec = field("note", string().allowBlank());
        assertEquals("", assertOk(dec.decode(Map.of("note", ""))));
        assertEquals("hello", assertOk(dec.decode(Map.of("note", "hello"))));
    }

    @Test
    void stringAllowsBlankByDefault() {
        var dec = field("name", string());
        assertEquals("", assertOk(dec.decode(Map.of("name", ""))));
        assertEquals("   ", assertOk(dec.decode(Map.of("name", "   "))));
    }

    @Test
    void nonBlankStringRejectsBlank() {
        var dec = field("name", string().nonBlank());
        for (var input : new String[]{"", "   "}) {
            var result = dec.decode(Map.of("name", input));
            assertErr(result);
            switch (result) {
                case Err<?> err -> assertEquals(ErrorCodes.BLANK, err.issues().asList().getFirst().code());
                default -> fail("expected Err for input: " + input);
            }
        }
    }

    @Test
    void nonBlankStringHasDifferentCodeFromRequired() {
        var dec = field("name", string().nonBlank());
        // missing key → REQUIRED
        var missingResult = dec.decode(Map.of());
        switch (missingResult) {
            case Err<?> err -> assertEquals(ErrorCodes.REQUIRED, err.issues().asList().getFirst().code());
            default -> fail("expected Err");
        }
        // blank value → BLANK
        var blankResult = dec.decode(Map.of("name", "   "));
        switch (blankResult) {
            case Err<?> err -> assertEquals(ErrorCodes.BLANK, err.issues().asList().getFirst().code());
            default -> fail("expected Err");
        }
    }

    @Test
    void customMessage() {
        var dec = field("age", int_().range(0, 150, "年齢は0〜150の範囲で指定してください"));
        var result = dec.decode(Map.of("age", 200));
        switch (result) {
            case Ok(_) -> fail("Expected Err");
            case Err(var issues) -> {
                assertEquals("年齢は0〜150の範囲で指定してください", issues.asList().getFirst().message());
                var resolved = issues.resolve(MessageResolver.DEFAULT);
                assertEquals("年齢は0〜150の範囲で指定してください", resolved.asList().getFirst().message());
            }
        }
    }

    @Test
    void nestedObject() {
        var dec = combine(
                field("name", string()),
                field("address", nested(combine(
                        field("city", string()),
                        field("zip", string().fixedLength(7))
                ).map((city, zip) -> Map.of("city", city, "zip", zip))))
        ).map((name, addr) -> Map.of("name", name, "address", addr));

        var input = Map.<String, Object>of(
                "name", "Taro",
                "address", Map.of("city", "Tokyo", "zip", "1234567")
        );
        var result = assertOk(dec.decode(input));
        assertEquals("Taro", result.get("name"));
    }

    @Test
    void withDefaultCombinator() {
        var dec = field("role", Decoders.withDefault(
                enumOf(Prefecture.class), Prefecture.TOKYO));
        assertEquals(Prefecture.TOKYO, assertOk(dec.decode(Map.of())));

        var mapWithNull = new java.util.HashMap<String, Object>();
        mapWithNull.put("role", null);
        assertEquals(Prefecture.TOKYO, assertOk(dec.decode(mapWithNull)));
        assertErr(dec.decode(Map.of("role", "invalid")));
    }

    @Test
    void oneOfIncludesCandidateSpecificErrors() {
        Decoder<Map<String, Object>, Contact> dec = Decoders.oneOf(
                combine(
                        field("kind", literal("email")),
                        field("value", string().email())
                ).map((kind, value) -> new EmailContact(new Email(value))),
                combine(
                        field("kind", literal("phone")),
                        field("value", string().pattern(java.util.regex.Pattern.compile("^\\d+$")))
                ).map((kind, value) -> new Phone(value))
        );

        var result = dec.decode(Map.of("kind", "sms", "value", "abc"));
        switch (result) {
            case Ok(_) -> fail("Expected Err");
            case Err(var issues) -> {
                assertEquals("one_of_failed", issues.asList().getFirst().code());
                @SuppressWarnings("unchecked")
                var candidates = (List<Map<String, Object>>) issues.asList().getFirst().meta().get("candidates");
                assertEquals(2, candidates.size());
                assertTrue(candidates.stream().allMatch(c -> c.containsKey("candidate") && c.containsKey("issues")));
            }
        }
    }

    sealed interface Contact permits Phone, EmailContact {}
    record Phone(String number) implements Contact {}
    record EmailContact(Email email) implements Contact {}

    @Test
    void strictRejectsUnknownFields() {
        var dec = strict(combine(
                field("name", string()),
                field("age", int_())
        ).map((name, age) -> Map.of("name", name, "age", age)), java.util.Set.of("name", "age"));

        var result = dec.decode(Map.of("name", "Taro", "age", 20, "extra", true));
        switch (result) {
            case Ok(_) -> fail("Expected Err");
            case Err(var issues) -> {
                assertTrue(issues.asList().stream().anyMatch(i ->
                        i.code().equals("unknown_field") && i.path().toJsonPointer().equals("/extra")));
            }
        }
    }

    @Test
    void singleValueDecoder() {
        assertEquals("alice@example.com", assertOk(string().email().decode("alice@example.com")));
        assertErr(string().email().decode("bad"));
    }

    @Test
    void conditionalValidationWithFlatMap() {
        var dec = combine(
                optionalField("name", string().nonBlank()),
                optionalField("email", string().email())
        ).flatMap((name, email) -> {
            if (name.isPresent() && email.isEmpty()) {
                return Result.fail(Path.ROOT.append("email"), "required", "is required");
            }
            return Result.ok("ok");
        });

        assertOk(dec.decode(Map.of("name", "Alice", "email", "alice@example.com")));
        var result = dec.decode(Map.of("name", "Alice"));
        switch (result) {
            case Ok(_) -> fail("Expected Err");
            case Err(var issues) -> assertTrue(issues.asList().stream()
                    .anyMatch(i -> i.path().toJsonPointer().equals("/email") && i.code().equals("required")));
        }
    }

    @Test
    void customConstraintViaStringDecoderFrom() {
        var sku = net.unit8.raoh.builtin.StringDecoder.from(string()).pattern(java.util.regex.Pattern.compile("^[A-Z]{3}-\\d{4}$"));
        var dec = field("sku", sku);

        assertEquals("ABC-1234", assertOk(dec.decode(Map.of("sku", "ABC-1234"))));
        assertErr(dec.decode(Map.of("sku", "abc-1234")));
    }

    @Test
    void crossFieldValidationRebasesNestedPath() {
        record Period(int start, int end) {
            static Result<Period> parse(int start, int end) {
                if (start > end) {
                    return Result.fail(Path.ROOT, "invalid_range", "start must be less than or equal to end");
                }
                return Result.ok(new Period(start, end));
            }
        }

        Decoder<Map<String, Object>, Period> period = combine(
                field("start", int_()),
                field("end", int_())
        ).flatMap(Period::parse);

        var dec = field("period", nested(period));
        var result = dec.decode(Map.of("period", Map.of("start", 10, "end", 5)));
        switch (result) {
            case Ok(_) -> fail("Expected Err");
            case Err(var issues) -> assertTrue(issues.asList().stream()
                    .anyMatch(i -> i.path().toJsonPointer().equals("/period") && i.code().equals("invalid_range")));
        }
    }

    // --- String coerce ---

    @Test
    void toIntValid() {
        var dec = field("age", string().toInt());
        assertEquals(42, assertOk(dec.decode(Map.of("age", "42"))));
        assertEquals(-7, assertOk(dec.decode(Map.of("age", "-7"))));
    }

    @Test
    void toIntInvalid() {
        var dec = field("age", string().toInt());
        var result = dec.decode(Map.of("age", "abc"));
        switch (result) {
            case Ok(var v) -> fail("Expected Err, got Ok: " + v);
            case Err(var issues) -> {
                assertEquals(ErrorCodes.TYPE_MISMATCH, issues.asList().getFirst().code());
                assertEquals("integer", issues.asList().getFirst().meta().get("expected"));
            }
        }
    }

    @Test
    void toIntChainRange() {
        var dec = field("age", string().toInt().range(0, 150));
        assertEquals(25, assertOk(dec.decode(Map.of("age", "25"))));
        assertErr(dec.decode(Map.of("age", "200")));
    }

    @Test
    void toIntCustomMessage() {
        var dec = field("n", string().toInt("整数を入力してください"));
        var result = dec.decode(Map.of("n", "abc"));
        switch (result) {
            case Ok(var v) -> fail("Expected Err, got Ok: " + v);
            case Err(var issues) -> assertEquals("整数を入力してください", issues.asList().getFirst().message());
        }
    }

    @Test
    void toLongValid() {
        var dec = field("id", string().toLong());
        assertEquals(9999999999L, assertOk(dec.decode(Map.of("id", "9999999999"))));
    }

    @Test
    void toLongInvalid() {
        var dec = field("id", string().toLong());
        assertErr(dec.decode(Map.of("id", "not-a-number")));
    }

    @Test
    void toDecimalValid() {
        var dec = field("price", string().toDecimal());
        assertEquals(0, new BigDecimal("19.99").compareTo(assertOk(dec.decode(Map.of("price", "19.99")))));
    }

    @Test
    void toDecimalChainScale() {
        var dec = field("price", string().toDecimal().scale(2));
        assertOk(dec.decode(Map.of("price", "19.99")));
        assertErr(dec.decode(Map.of("price", "19.999")));
    }

    @Test
    void toDecimalInvalid() {
        var dec = field("price", string().toDecimal());
        var result = dec.decode(Map.of("price", "abc"));
        switch (result) {
            case Ok(var v) -> fail("Expected Err, got Ok: " + v);
            case Err(var issues) -> assertEquals(ErrorCodes.TYPE_MISMATCH, issues.asList().getFirst().code());
        }
    }

    @Test
    void toBoolTrue() {
        var dec = field("agree", string().toBool());
        for (var input : new String[]{"true", "TRUE", "1", "yes", "YES", "on", "On"}) {
            assertTrue(assertOk(dec.decode(Map.of("agree", input))),
                    "expected true for input: " + input);
        }
    }

    @Test
    void toBoolFalse() {
        var dec = field("agree", string().toBool());
        for (var input : new String[]{"false", "FALSE", "0", "no", "NO", "off", "Off"}) {
            assertFalse(assertOk(dec.decode(Map.of("agree", input))),
                    "expected false for input: " + input);
        }
    }

    @Test
    void toBoolInvalid() {
        var dec = field("agree", string().toBool());
        var result = dec.decode(Map.of("agree", "maybe"));
        switch (result) {
            case Ok(var v) -> fail("Expected Err, got Ok: " + v);
            case Err(var issues) -> {
                assertEquals(ErrorCodes.TYPE_MISMATCH, issues.asList().getFirst().code());
                assertEquals("boolean", issues.asList().getFirst().meta().get("expected"));
            }
        }
    }

    @Test
    void toBoolChainIsTrue() {
        var dec = field("terms", string().toBool().isTrue());
        assertTrue(assertOk(dec.decode(Map.of("terms", "true"))));
        assertErr(dec.decode(Map.of("terms", "false")));
    }

    @Test
    void toBoolCustomMessage() {
        var dec = field("flag", string().toBool("真偽値を入力してください"));
        var result = dec.decode(Map.of("flag", "maybe"));
        switch (result) {
            case Ok(var v) -> fail("Expected Err, got Ok: " + v);
            case Err(var issues) -> assertEquals("真偽値を入力してください", issues.asList().getFirst().message());
        }
    }

    @Test
    void trimThenToInt() {
        var dec = field("age", string().trim().toInt());
        assertEquals(42, assertOk(dec.decode(Map.of("age", "  42  "))));
        assertErr(dec.decode(Map.of("age", "  abc  ")));
    }

    // --- Helpers ---

    static <T> T assertOk(Result<T> result) {
        return switch (result) {
            case Ok(var value) -> value;
            case Err(var issues) -> { fail("Expected Ok, got: " + issues); yield null; }
        };
    }

    static void assertErr(Result<?> result) {
        if (result instanceof Ok<?>) {
            fail("Expected Err, got Ok: " + result);
        }
    }
}
