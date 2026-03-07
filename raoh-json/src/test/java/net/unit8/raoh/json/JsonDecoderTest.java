package net.unit8.raoh.json;

import net.unit8.raoh.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static net.unit8.raoh.json.JsonDecoders.*;

class JsonDecoderTest {

    private static final ObjectMapper mapper = new ObjectMapper();

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

    static Decoder<JsonNode, Email> email() {
        return string().trim().toLowerCase().email().map(Email::new);
    }

    static Decoder<JsonNode, Age> age() {
        return int_().range(0, 150).map(Age::new);
    }

    static Decoder<JsonNode, UserId> userId() {
        return string().uuid().map(UserId::new);
    }

    static Decoder<JsonNode, Address> address() {
        return combine(
                field("prefecture", enumOf(Prefecture.class)),
                field("city", string().maxLength(100)),
                field("street", string().maxLength(200))
        ).apply(Address::new);
    }

    static Decoder<JsonNode, User> user() {
        return combine(
                field("id", userId()),
                field("email", email()),
                field("age", age()),
                field("address", address())
        ).apply(User::new);
    }

    static Decoder<JsonNode, Money> money() {
        return combine(
                field("amount", decimal()),
                field("currency", enumOf(Currency.class))
        ).flatMap(Money::parse);
    }

    // --- Tests ---

    @Test
    void validUser() {
        var json = parse("""
                {
                  "id": "550e8400-e29b-41d4-a716-446655440000",
                  "email": "Alice@Example.com",
                  "age": 30,
                  "address": {
                    "prefecture": "tokyo",
                    "city": "千代田区",
                    "street": "1-1-1"
                  }
                }
                """);

        switch (user().decode(json)) {
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
        var json = parse("""
                {
                  "id": "not-a-uuid",
                  "email": "bad",
                  "age": 200,
                  "address": {
                    "prefecture": "atlantis",
                    "city": "中央区",
                    "street": "1-1-1"
                  }
                }
                """);

        switch (user().decode(json)) {
            case Ok(var ignored) -> fail("Expected Err");
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
        var json = parse("""
                {"amount": 1500, "currency": "jpy"}
                """);
        var m = assertOk(money().decode(json));
        assertEquals(0, new BigDecimal("1500").compareTo(m.amount()));
        assertEquals(Currency.JPY, m.currency());
    }

    @Test
    void moneyInvalidScale() {
        var json = parse("""
                {"amount": 19.99, "currency": "jpy"}
                """);
        switch (money().decode(json)) {
            case Ok(var ignored) -> fail("Expected Err");
            case Err(var issues) -> {
                var codes = issues.asList().stream().map(Issue::code).toList();
                assertTrue(codes.contains("invalid_scale"));
            }
        }
    }

    @Test
    void moneyUsdValid() {
        var json = parse("""
                {"amount": 19.99, "currency": "usd"}
                """);
        var m = assertOk(money().decode(json));
        assertEquals(Currency.USD, m.currency());
    }

    @Test
    void stringConstraints() {
        var dec = field("name", string().minLength(3).maxLength(10));
        var abc = parse("""
                {"name":"abc"}
                """);
        var ab = parse("""
                {"name":"ab"}
                """);
        var tooLong = parse("""
                {"name":"12345678901"}
                """);
        assertEquals("abc", assertOk(dec.decode(abc)));
        assertErr(dec.decode(ab));
        assertErr(dec.decode(tooLong));
    }

    @Test
    void intConstraints() {
        var dec = field("n", int_().positive().multipleOf(3));
        assertEquals(9, assertOk(dec.decode(parse("{\"n\":9}"))));
        assertErr(dec.decode(parse("{\"n\":-3}")));
        assertErr(dec.decode(parse("{\"n\":5}")));
    }

    @Test
    void listDecoder() {
        var dec = field("items", list(string()));
        var result = assertOk(dec.decode(parse("{\"items\":[\"a\",\"b\",\"c\"]}")));
        assertEquals(List.of("a", "b", "c"), result);
    }

    @Test
    void listNonempty() {
        var dec = field("items", list(string()).nonempty());
        assertErr(dec.decode(parse("{\"items\":[]}")));
    }

    @Test
    void listElementErrors() {
        var dec = field("items", list(int_().positive()));
        var result = dec.decode(parse("{\"items\":[1, -2, 3, -4]}"));
        switch (result) {
            case Ok(var ignored) -> fail("Expected Err");
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
        var result = assertOk(dec.decode(parse("{\"nick\":\"foo\"}")));
        assertEquals(Optional.of("foo"), result);
    }

    @Test
    void optionalFieldAbsent() {
        var dec = optionalField("nick", string());
        var result = assertOk(dec.decode(parse("{}")));
        assertEquals(Optional.empty(), result);
    }

    @Test
    void nullableField() {
        var dec = field("val", nullable(string()));
        assertNull(assertOk(dec.decode(parse("{\"val\":null}"))));
        assertEquals("x", assertOk(dec.decode(parse("{\"val\":\"x\"}"))));
    }

    @Test
    void presenceTriState() {
        var dec = optionalNullableField("email", string());
        var absent = assertOk(dec.decode(parse("{}")));
        assertInstanceOf(Presence.Absent.class, absent);

        var presentNull = assertOk(dec.decode(parse("{\"email\":null}")));
        assertInstanceOf(Presence.PresentNull.class, presentNull);

        var present = assertOk(dec.decode(parse("{\"email\":\"a@b.com\"}")));
        assertInstanceOf(Presence.Present.class, present);
    }

    @Test
    void enumDecoder() {
        var dec = field("p", enumOf(Prefecture.class));
        assertEquals(Prefecture.TOKYO, assertOk(dec.decode(parse("{\"p\":\"tokyo\"}"))));
        assertEquals(Prefecture.OSAKA, assertOk(dec.decode(parse("{\"p\":\"OSAKA\"}"))));
        assertErr(dec.decode(parse("{\"p\":\"invalid\"}")));
    }

    sealed interface Contact permits Phone, EmailContact {}
    record Phone(String number) implements Contact {}
    record EmailContact(Email email) implements Contact {}

    @Test
    void discriminateDecoder() {
        var contactDec = discriminate("type", Map.of(
                "phone", field("number", string()).map(Phone::new),
                "email", field("address", email()).map(EmailContact::new)
        ));

        var phone = assertOk(contactDec.decode(
                parse("{\"type\":\"phone\",\"number\":\"090-1234-5678\"}")));
        assertInstanceOf(Phone.class, phone);

        var em = assertOk(contactDec.decode(
                parse("{\"type\":\"email\",\"address\":\"a@b.com\"}")));
        assertInstanceOf(EmailContact.class, em);

        assertErr(contactDec.decode(
                parse("{\"type\":\"unknown\"}")));
    }

    @Test
    void uuidParsing() {
        var dec = field("id", string().uuid());
        var id = assertOk(dec.decode(parse("{\"id\":\"550e8400-e29b-41d4-a716-446655440000\"}")));
        assertEquals(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), id);
        assertErr(dec.decode(parse("{\"id\":\"not-a-uuid\"}")));
    }

    @Test
    void mapDecoder() {
        var dec = field("prices", net.unit8.raoh.json.JsonDecoders.map(decimal()));
        var result = assertOk(dec.decode(
                parse("{\"prices\":{\"apple\":1.50,\"banana\":0.99}}")));
        assertEquals(2, result.size());
        assertEquals(0, new BigDecimal("1.50").compareTo(result.get("apple")));
    }

    @Test
    void resultFold() {
        var dec = field("n", int_());
        var ok = dec.decode(parse("{\"n\":42}"))
                .fold(v -> "got " + v, issues -> "err");
        assertEquals("got 42", ok);

        var err = dec.decode(parse("{}"))
                .fold(v -> "got " + v, issues -> "err");
        assertEquals("err", err);
    }

    @Test
    void messageResolver() {
        var dec = field("name", string().minLength(5));
        var result = dec.decode(parse("{\"name\":\"ab\"}"));
        switch (result) {
            case Ok(var ignored) -> fail("Expected Err");
            case Err(var issues) -> {
                var resolved = issues.resolve(MessageResolver.DEFAULT);
                assertEquals("must be at least 5 characters", resolved.asList().getFirst().message());
            }
        }
    }

    @Test
    void issuesFlatten() {
        var result = user().decode(parse("""
                {"id":"bad","email":"bad","age":200,
                 "address":{"prefecture":"?","city":"x","street":"y"}}
                """));
        switch (result) {
            case Ok(var ignored) -> fail("Expected Err");
            case Err(var issues) -> {
                var flat = issues.flatten();
                assertTrue(flat.containsKey("/id"));
                assertTrue(flat.containsKey("/email"));
                assertTrue(flat.containsKey("/age"));
            }
        }
    }

    @Test
    void issuesToJsonList() {
        var result = user().decode(parse("""
                {"id":"bad","email":"bad","age":200,
                 "address":{"prefecture":"?","city":"x","street":"y"}}
                """));
        switch (result) {
            case Ok(var ignored) -> fail("Expected Err");
            case Err(var issues) -> {
                var list = issues.toJsonList();
                assertTrue(list.size() >= 3);
                assertTrue(list.stream().allMatch(m ->
                        m.containsKey("path") && m.containsKey("code") && m.containsKey("message")));
            }
        }
    }

    @Test
    void withDefaultCombinator() {
        var dec = field("role", Decoders.withDefault(
                enumOf(Prefecture.class), Prefecture.TOKYO));
        assertEquals(Prefecture.TOKYO, assertOk(dec.decode(parse("{}"))));
        assertEquals(Prefecture.TOKYO, assertOk(dec.decode(parse("{\"role\":null}"))));
        assertErr(dec.decode(parse("{\"role\":\"invalid\"}")));
    }

    @Test
    void lazyRecursiveDecoder() {
        record Comment(String body, List<Comment> replies) {}

        Decoder<JsonNode, Comment>[] holder = new Decoder[1];
        holder[0] = combine(
                field("body", string()),
                Decoders.withDefault(
                        field("replies", list(Decoders.lazy(() -> holder[0]))), List.of())
        ).apply(Comment::new);

        var json = parse("""
                {
                  "body": "root",
                  "replies": [
                    { "body": "child1", "replies": [] },
                    { "body": "child2" }
                  ]
                }
                """);

        var comment = assertOk(holder[0].decode(json));
        assertEquals("root", comment.body());
        assertEquals(2, comment.replies().size());
        assertEquals("child1", comment.replies().get(0).body());
    }

    @Test
    void customMessage() {
        var dec = field("age", int_().range(0, 150, "年齢は0〜150の範囲で指定してください"));
        var result = dec.decode(parse("{\"age\":200}"));
        switch (result) {
            case Ok(var ignored) -> fail("Expected Err");
            case Err(var issues) -> {
                assertEquals("年齢は0〜150の範囲で指定してください", issues.asList().getFirst().message());
                // custom message should survive resolve
                var resolved = issues.resolve(MessageResolver.DEFAULT);
                assertEquals("年齢は0〜150の範囲で指定してください", resolved.asList().getFirst().message());
            }
        }
    }

    @Test
    void decimalScale() {
        var dec = field("v", decimal().scale(2));
        assertOk(dec.decode(parse("{\"v\":1.23}")));
        assertErr(dec.decode(parse("{\"v\":1.234}")));
    }

    @Test
    void pipeDecoder() {
        // pipe string decoder to another decoder that parses the string further
        Decoder<String, Integer> parseInt = (in, path) -> {
            try {
                return Result.ok(Integer.parseInt(in));
            } catch (NumberFormatException e) {
                return Result.fail(path, "invalid_format", "not an integer");
            }
        };
        var dec = field("n", string().pipe(parseInt));
        assertEquals(42, assertOk(dec.decode(parse("{\"n\":\"42\"}"))));
        assertErr(dec.decode(parse("{\"n\":\"abc\"}")));
    }

    @Test
    void stringAllowsBlankByDefault() {
        var dec = field("note", string());
        assertEquals("", assertOk(dec.decode(parse("{\"note\":\"\"}"))));
        assertEquals("   ", assertOk(dec.decode(parse("{\"note\":\"   \"}"))));
    }

    @Test
    void stringNonBlankRejectsBlank() {
        var dec = field("note", string().nonBlank());
        assertErr(dec.decode(parse("{\"note\":\"\"}")));
        assertErr(dec.decode(parse("{\"note\":\"   \"}")));
    }

    @Test
    void oneOfIncludesCandidateSpecificErrors() {
        Decoder<JsonNode, Contact> dec = Decoders.oneOf(
                combine(
                        field("kind", literal("email")),
                        field("value", string().email())
                ).apply((kind, value) -> new EmailContact(new Email(value))),
                combine(
                        field("kind", literal("phone")),
                        field("value", string().pattern(java.util.regex.Pattern.compile("^\\d+$")))
                ).apply((kind, value) -> new Phone(value))
        );
        var result = dec.decode(parse("{\"kind\":\"sms\",\"value\":\"abc\"}"));

        switch (result) {
            case Ok(var ignored) -> fail("Expected Err");
            case Err(var issues) -> {
                assertEquals("one_of_failed", issues.asList().getFirst().code());
                @SuppressWarnings("unchecked")
                var candidates = (List<Map<String, Object>>) issues.asList().getFirst().meta().get("candidates");
                assertEquals(2, candidates.size());
                assertTrue(candidates.stream().allMatch(c -> c.containsKey("candidate") && c.containsKey("issues")));
            }
        }
    }

    @Test
    void strictRejectsUnknownFields() {
        var dec = strict(combine(
                field("name", string()),
                field("age", int_())
        ).apply((name, age) -> Map.of("name", name, "age", age)), java.util.Set.of("name", "age"));

        var result = dec.decode(parse("{\"name\":\"Taro\",\"age\":20,\"extra\":true}"));
        switch (result) {
            case Ok(var ignored) -> fail("Expected Err");
            case Err(var issues) -> {
                assertTrue(issues.asList().stream().anyMatch(i ->
                        i.code().equals("unknown_field") && i.path().toJsonPointer().equals("/extra")));
            }
        }
    }

    @Test
    void singleValueDecoder() {
        var result = string().email().decode(parse("\"alice@example.com\""));
        assertEquals("alice@example.com", assertOk(result));
        assertErr(string().email().decode(parse("\"bad\"")));
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

        assertOk(dec.decode(parse("{\"name\":\"Alice\",\"email\":\"alice@example.com\"}")));
        var result = dec.decode(parse("{\"name\":\"Alice\"}"));
        switch (result) {
            case Ok(var ignored) -> fail("Expected Err");
            case Err(var issues) -> assertTrue(issues.asList().stream()
                    .anyMatch(i -> i.path().toJsonPointer().equals("/email") && i.code().equals("required")));
        }
    }

    @Test
    void customConstraintViaStringDecoderFrom() {
        var sku = net.unit8.raoh.builtin.StringDecoder.from(string()).pattern(java.util.regex.Pattern.compile("^[A-Z]{3}-\\d{4}$"));
        var dec = field("sku", sku);

        assertEquals("ABC-1234", assertOk(dec.decode(parse("{\"sku\":\"ABC-1234\"}"))));
        assertErr(dec.decode(parse("{\"sku\":\"abc-1234\"}")));
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

        var period = combine(
                field("start", int_()),
                field("end", int_())
        ).flatMap(Period::parse);

        var dec = field("period", period);
        var result = dec.decode(parse("{\"period\":{\"start\":10,\"end\":5}}"));
        switch (result) {
            case Ok(var ignored) -> fail("Expected Err");
            case Err(var issues) -> assertTrue(issues.asList().stream()
                    .anyMatch(i -> i.path().toJsonPointer().equals("/period") && i.code().equals("invalid_range")));
        }
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

    private JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
