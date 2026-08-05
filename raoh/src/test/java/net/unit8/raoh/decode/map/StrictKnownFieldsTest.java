package net.unit8.raoh.decode.map;

import net.unit8.raoh.Err;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.unit8.raoh.decode.ObjectDecoders.int_;
import static net.unit8.raoh.decode.ObjectDecoders.string;
import static net.unit8.raoh.decode.map.MapDecoders.combine;
import static net.unit8.raoh.decode.map.MapDecoders.field;
import static net.unit8.raoh.decode.map.MapDecoders.nested;
import static net.unit8.raoh.decode.map.MapDecoders.nullableField;
import static net.unit8.raoh.decode.map.MapDecoders.optionalField;
import static net.unit8.raoh.decode.map.MapDecoders.optionalNullableField;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@code Combiner#strict()} collects known field names by testing each sub-decoder with
 * {@code instanceof FieldDecoder}. Every field shape below must therefore stay a
 * {@link net.unit8.raoh.decode.FieldDecoder FieldDecoder}, or a valid payload is rejected
 * with {@code unknown_field}.
 */
class StrictKnownFieldsTest {

    private static final Map<String, Object> INPUT = Map.of("name", "Taro", "age", 20);

    private static void assertAccepted(Result<?> result) {
        switch (result) {
            case Ok(_) -> { }
            case Err(var issues) -> fail("Expected Ok, got: " + issues.asList());
        }
    }

    @Test
    void refineOnAFieldStaysKnown() {
        assertAccepted(combine(
                field("name", string()),
                field("age", int_()).refine(a -> a >= 0, "must_be_positive", "must be positive")
        ).strict((name, age) -> name + age).decode(INPUT));
    }

    @Test
    void mapOnAFieldStaysKnown() {
        assertAccepted(combine(
                field("name", string()),
                field("age", int_()).map(a -> a + 1)
        ).strict((name, age) -> name + age).decode(INPUT));
    }

    @Test
    void pipeOnAFieldStaysKnown() {
        assertAccepted(combine(
                field("name", string()),
                field("age", int_()).pipe((a, path) -> Result.ok(a.toString()))
        ).strict((name, age) -> name + age).decode(INPUT));
    }

    @Test
    void optionalFieldIsKnown() {
        assertAccepted(combine(
                field("name", string()),
                optionalField("age", int_())
        ).strict((name, age) -> name + age.orElse(0)).decode(INPUT));
    }

    @Test
    void optionalNullableFieldIsKnown() {
        assertAccepted(combine(
                field("name", string()),
                optionalNullableField("age", int_())
        ).strict((name, age) -> name + age).decode(INPUT));
    }

    @Test
    void nullableFieldIsKnown() {
        assertAccepted(combine(
                field("name", string()),
                nullableField("age", int_())
        ).strict((name, age) -> name + age).decode(INPUT));
    }

    @Test
    void genuinelyUnknownFieldIsStillRejected() {
        var dec = combine(
                field("name", string()),
                optionalField("age", int_()).refine(a -> true, "always_ok", "always ok")
        ).strict((name, age) -> name + age.orElse(0));

        switch (dec.decode(Map.of("name", "Taro", "age", 20, "extra", true))) {
            case Ok(var v) -> fail("Expected Err, got Ok: " + v);
            case Err(var issues) -> {
                assertEquals(1, issues.asList().size());
                var issue = issues.asList().getFirst();
                assertEquals(ErrorCodes.UNKNOWN_FIELD, issue.code());
                assertEquals("/extra", issue.path().toJsonPointer());
            }
        }
    }

    /**
     * A refinement reports at the field's path whether it is applied inside or outside
     * {@code field(...)}. The two spellings express the same rule about the same value, so they
     * must not disagree about where the failure belongs.
     */
    @Test
    void refinementReportsAtTheFieldPathEitherWayRound() {
        var outer = combine(
                field("name", string()),
                field("age", int_()).refine(a -> a % 2 == 1, "must_be_odd", "must be odd")
        ).strict((name, age) -> name + age);

        var inner = combine(
                field("name", string()),
                field("age", int_().refine(a -> a % 2 == 1, "must_be_odd", "must be odd"))
        ).strict((name, age) -> name + age);

        for (var dec : List.of(outer, inner)) {
            switch (dec.decode(INPUT)) {
                case Ok(var v) -> fail("Expected Err, got Ok: " + v);
                case Err(var issues) -> {
                    var issue = issues.asList().getFirst();
                    assertEquals("must_be_odd", issue.code());
                    assertEquals("/age", issue.path().toJsonPointer());
                }
            }
        }
    }

    /**
     * The same decoder must not report two different paths depending on which check failed. Before
     * the field's path was threaded through the overridden combinators, a type mismatch landed on
     * {@code /age} while a refinement failure landed on the enclosing object.
     */
    @Test
    void typeMismatchAndRefinementFailureShareTheFieldPath() {
        var dec = field("age", int_()).refine(a -> a % 2 == 1, "must_be_odd", "must be odd");

        assertEquals("/age", firstIssuePath(dec.decode(Map.of("age", "not-a-number"))));
        assertEquals("/age", firstIssuePath(dec.decode(Map.of("age", 20))));
    }

    @Test
    void flatMapAndPipeFailuresLandOnTheFieldPath() {
        var flatMapped = field("age", int_()).flatMap(a -> Result.fail("boom", "boom"));
        assertEquals("/age", firstIssuePath(flatMapped.decode(Map.of("age", 20))));

        var piped = field("age", int_()).pipe((a, path) -> Result.fail(path, "boom", "boom"));
        assertEquals("/age", firstIssuePath(piped.decode(Map.of("age", 20))));
    }

    @Test
    void aNestedFieldReportsAtItsFullPath() {
        var dec = field("user", nested(combine(
                field("age", int_()).refine(a -> a % 2 == 1, "must_be_odd", "must be odd"),
                field("name", string())
        ).map((age, name) -> name + age)));

        assertEquals("/user/age",
                firstIssuePath(dec.decode(Map.of("user", Map.of("age", 20, "name", "Taro")))));
    }

    @Test
    void chainedRefinementsAppendTheFieldNameOnlyOnce() {
        var dec = field("age", int_())
                .refine(a -> a > 0, "must_be_positive", "must be positive")
                .refine(a -> a % 2 == 1, "must_be_odd", "must be odd");

        assertEquals("/age", firstIssuePath(dec.decode(INPUT)));
    }

    private static String firstIssuePath(Result<?> result) {
        return switch (result) {
            case Ok(var v) -> throw new AssertionError("Expected Err, got Ok: " + v);
            case Err(var issues) -> issues.asList().getFirst().path().toJsonPointer();
        };
    }
}
