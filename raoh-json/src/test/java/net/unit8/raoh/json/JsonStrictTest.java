package net.unit8.raoh.json;

import net.unit8.raoh.Err;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.unit8.raoh.decode.Decoder;

import static net.unit8.raoh.json.JsonDecoders.combine;
import static net.unit8.raoh.json.JsonDecoders.discriminate;
import static net.unit8.raoh.json.JsonDecoders.field;
import static net.unit8.raoh.json.JsonDecoders.int_;
import static net.unit8.raoh.json.JsonDecoders.optionalField;
import static net.unit8.raoh.json.JsonDecoders.strict;
import static net.unit8.raoh.json.JsonDecoders.string;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@code Combiner#strict()} used to hardcode a {@code Map}-only scan, so on the JSON boundary it
 * silently accepted unknown properties while the explicit {@link JsonDecoders#strict} rejected
 * them. Both spellings must now agree.
 */
class JsonStrictTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static JsonNode json(String s) {
        return MAPPER.readTree(s);
    }

    private static void assertUnknownField(Result<?> result, String pointer) {
        switch (result) {
            case Ok(var v) -> fail("Expected Err, got Ok: " + v);
            case Err(var issues) -> {
                var issue = issues.asList().getFirst();
                assertEquals(ErrorCodes.UNKNOWN_FIELD, issue.code());
                assertEquals(pointer, issue.path().toJsonPointer());
            }
        }
    }

    private static void assertAccepted(Result<?> result) {
        switch (result) {
            case Ok(_) -> { }
            case Err(var issues) -> fail("Expected Ok, got: " + issues.asList());
        }
    }

    @Test
    void combinerStrictRejectsAnUnknownJsonProperty() {
        var dec = combine(field("name", string()), field("age", int_()))
                .strict((name, age) -> name + age);

        assertAccepted(dec.decode(json("{\"name\":\"Taro\",\"age\":20}")));
        assertUnknownField(dec.decode(json("{\"name\":\"Taro\",\"age\":20,\"extra\":true}")), "/extra");
    }

    @Test
    void bothSpellingsOfStrictAgree() {
        var node = json("{\"name\":\"Taro\",\"age\":20,\"extra\":true}");

        var viaCombiner = combine(field("name", string()), field("age", int_()))
                .strict((name, age) -> name + age);
        var explicit = strict(
                combine(field("name", string()), field("age", int_())).map((name, age) -> name + age),
                Set.of("name", "age"));

        assertUnknownField(viaCombiner.decode(node), "/extra");
        assertUnknownField(explicit.decode(node), "/extra");
    }

    @Test
    void optionalFieldsAreKnownToCombinerStrict() {
        var dec = combine(field("name", string()), optionalField("age", int_()))
                .strict((name, age) -> name + age.orElse(0));

        assertAccepted(dec.decode(json("{\"name\":\"Taro\",\"age\":20}")));
        assertAccepted(dec.decode(json("{\"name\":\"Taro\"}")));
        assertUnknownField(dec.decode(json("{\"name\":\"Taro\",\"extra\":1}")), "/extra");
    }

    @Test
    void strictFlatMapScansTheJsonBoundaryToo() {
        var dec = combine(field("name", string()), field("age", int_()))
                .strictFlatMap((name, age) -> Result.ok(name + age));

        assertAccepted(dec.decode(json("{\"name\":\"Taro\",\"age\":20}")));
        assertUnknownField(dec.decode(json("{\"name\":\"Taro\",\"age\":20,\"extra\":1}")), "/extra");
    }

    @Test
    void aRefinedFieldStaysKnown() {
        var dec = combine(
                field("name", string()),
                field("age", int_()).refine(a -> a >= 0, "must_be_positive", "must be positive")
        ).strict((name, age) -> name + age);

        assertAccepted(dec.decode(json("{\"name\":\"Taro\",\"age\":20}")));
        assertUnknownField(dec.decode(json("{\"name\":\"Taro\",\"age\":20,\"extra\":1}")), "/extra");
    }

    @Test
    void unknownFieldAccumulatesWithADecodeFailure() {
        var dec = combine(field("name", string()), field("age", int_()))
                .strict((name, age) -> name + age);

        switch (dec.decode(json("{\"name\":\"Taro\",\"age\":\"nope\",\"extra\":1}"))) {
            case Ok(var v) -> fail("Expected Err, got Ok: " + v);
            case Err(var issues) -> {
                var codes = issues.asList().stream().map(i -> i.code()).toList();
                assertEquals(2, codes.size(), "expected both issues, got " + issues.asList());
                assertEquals(true, codes.contains(ErrorCodes.UNKNOWN_FIELD));
                assertEquals(true, codes.contains(ErrorCodes.TYPE_MISMATCH));
            }
        }
    }

    @Test
    void aNonObjectNodeHasNoFieldsToReject() {
        var dec = combine(field("name", string()), field("age", int_()))
                .strict((name, age) -> name + age);

        // The decode still fails — the fields are missing — but not with unknown_field.
        switch (dec.decode(json("[1,2,3]"))) {
            case Ok(var v) -> fail("Expected Err, got Ok: " + v);
            case Err(var issues) -> assertEquals(
                    0,
                    issues.asList().stream().filter(i -> i.code().equals(ErrorCodes.UNKNOWN_FIELD)).count(),
                    "an array has no fields to report as unknown: " + issues.asList());
        }
    }

    // --- Nested strict: a field is reported once, by the innermost strict that does not know it ---
    // The cases follow the Raoh Specification's R000862–R000866.

    /** The issues as {@code "<pointer> <code>"}, in order. */
    private static List<String> issues(Result<?> result) {
        return switch (result) {
            case Ok(var v) -> fail("Expected Err, got Ok: " + v);
            case Err(var issues) -> issues.asList().stream()
                    .map(i -> i.path().toJsonPointer() + " " + i.code())
                    .toList();
        };
    }

    @Test
    void aStrictInsideADiscriminatedVariantIsNotRepeatedByTheOuterOne() {
        // R000862
        Decoder<JsonNode, Integer> square = strict(field("side", int_()).asDecoder(), Set.of("kind", "side"));
        Decoder<JsonNode, Integer> rect = strict(
                combine(field("w", int_()), field("h", int_())).map((w, h) -> w * h),
                Set.of("kind", "w", "h"));
        var dec = strict(
                discriminate("kind", Map.<String, Decoder<JsonNode, ? extends Integer>>of("square", square, "rect", rect)),
                Set.of("kind", "side", "w", "h"));

        assertEquals(List.of("/w type_mismatch", "/extra unknown_field"),
                issues(dec.decode(json("{\"kind\":\"rect\",\"w\":\"2\",\"extra\":1,\"h\":3}"))));
    }

    @Test
    void aFieldNeitherStrictKnowsIsReportedOnce() {
        // R000863
        var dec = strict(strict(field("a", int_()).asDecoder(), Set.of("a")), Set.of("a"));

        assertEquals(List.of("/b unknown_field"), issues(dec.decode(json("{\"a\":1,\"b\":2}"))));
    }

    @Test
    void theOuterStrictReportsWhatOnlyItDoesNotKnow() {
        // R000864
        var dec = strict(
                strict(combine(field("a", int_()), field("b", int_())).map(Integer::sum), Set.of("a", "b")),
                Set.of("a"));

        assertEquals(List.of("/b unknown_field"), issues(dec.decode(json("{\"a\":1,\"b\":2}"))));
    }

    @Test
    void theInnerStrictReportsWhatOnlyItDoesNotKnow() {
        // R000865
        var dec = strict(strict(field("a", int_()).asDecoder(), Set.of("a")), Set.of("a", "b"));

        assertEquals(List.of("/b unknown_field"), issues(dec.decode(json("{\"a\":1,\"b\":2}"))));
    }

    @Test
    void anIssueOfAnotherCodeAtTheFieldDoesNotKeepItFromBeingReported() {
        // R000866
        var dec = strict(field("b", int_()).asDecoder(), Set.of("a"));

        assertEquals(List.of("/b type_mismatch", "/b unknown_field"), issues(dec.decode(json("{\"b\":\"x\"}"))));
    }
}
