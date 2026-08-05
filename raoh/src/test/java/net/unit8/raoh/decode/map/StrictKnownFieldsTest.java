package net.unit8.raoh.decode.map;

import net.unit8.raoh.Err;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static net.unit8.raoh.decode.ObjectDecoders.int_;
import static net.unit8.raoh.decode.ObjectDecoders.string;
import static net.unit8.raoh.decode.map.MapDecoders.combine;
import static net.unit8.raoh.decode.map.MapDecoders.field;
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
     * A refinement applied outside {@code field(...)} reports at the enclosing path, not at the
     * field path — {@code field(name, dec)} appends the name inside its own {@code decode}, so a
     * combinator wrapped around it only ever sees the enclosing path. Applying the refinement to
     * the inner decoder instead reports at {@code /age}. This predates the name-preservation fix
     * and is asserted here to pin the current behaviour.
     */
    @Test
    void refinementOutsideFieldReportsAtTheEnclosingPath() {
        var outer = combine(
                field("name", string()),
                field("age", int_()).refine(a -> a % 2 == 1, "must_be_odd", "must be odd")
        ).strict((name, age) -> name + age);

        switch (outer.decode(INPUT)) {
            case Ok(var v) -> fail("Expected Err, got Ok: " + v);
            case Err(var issues) -> {
                var issue = issues.asList().getFirst();
                assertEquals("must_be_odd", issue.code());
                assertEquals("", issue.path().toJsonPointer());
            }
        }

        var inner = combine(
                field("name", string()),
                field("age", int_().refine(a -> a % 2 == 1, "must_be_odd", "must be odd"))
        ).strict((name, age) -> name + age);

        switch (inner.decode(INPUT)) {
            case Ok(var v) -> fail("Expected Err, got Ok: " + v);
            case Err(var issues) -> assertEquals("/age", issues.asList().getFirst().path().toJsonPointer());
        }
    }
}
