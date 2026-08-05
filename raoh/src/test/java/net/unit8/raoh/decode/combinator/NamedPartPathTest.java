package net.unit8.raoh.decode.combinator;

import net.unit8.raoh.Err;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Issue;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static net.unit8.raoh.decode.ObjectDecoders.int_;
import static net.unit8.raoh.decode.ObjectDecoders.string;
import static net.unit8.raoh.decode.map.MapDecoders.combine;
import static net.unit8.raoh.decode.map.MapDecoders.field;
import static net.unit8.raoh.decode.map.MapDecoders.nested;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every combinator on a named component must report at the field's path.
 *
 * <p>This is the regression surface from #109, carried over to the {@code CombinePart} model. The
 * defect then was that composition moved the failure path: a decoder reported a type mismatch at
 * {@code /age} but a refinement failure on the enclosing object, and a level down the failure
 * landed on {@code /user} rather than {@code /user/age}. The mechanism has changed — a named part
 * appends its own name once, and the combinators rebuild the part around a transformed inner
 * decoder — so the same matrix is re-asserted against the new implementation rather than assumed.
 */
class NamedPartPathTest {

    private static final Map<String, Object> INPUT = Map.of("age", 20);

    private static Issue firstIssue(Result<?> result) {
        return switch (result) {
            case Ok(var v) -> throw new AssertionError("Expected Err, got Ok: " + v);
            case Err(var issues) -> issues.asList().getFirst();
        };
    }

    /** Each combinator, applied so that it fails, paired with the input that makes it fail. */
    static Stream<Arguments> failingCombinators() {
        UnaryOperator<CombinePart<Map<String, Object>, Integer>> identity = p -> p;
        return Stream.of(
                Arguments.of("no combinator", identity, Map.of("age", "not-a-number")),
                Arguments.of("map", (UnaryOperator<CombinePart<Map<String, Object>, Integer>>)
                        p -> p.map(n -> n * 2), Map.of("age", "not-a-number")),
                Arguments.of("flatMap", (UnaryOperator<CombinePart<Map<String, Object>, Integer>>)
                        p -> p.flatMap(n -> Result.fail("boom", "boom")), INPUT),
                Arguments.of("flatMapWithPath", (UnaryOperator<CombinePart<Map<String, Object>, Integer>>)
                        p -> p.flatMapWithPath((n, path) -> Result.fail(path, "boom", "boom")), INPUT),
                Arguments.of("pipe", (UnaryOperator<CombinePart<Map<String, Object>, Integer>>)
                        p -> p.pipe((n, path) -> Result.fail(path, "boom", "boom")), INPUT),
                Arguments.of("refine", (UnaryOperator<CombinePart<Map<String, Object>, Integer>>)
                        p -> p.refine(n -> false, "boom", "boom"), INPUT),
                Arguments.of("refine with meta", (UnaryOperator<CombinePart<Map<String, Object>, Integer>>)
                        p -> p.refine(n -> false, "boom", "boom", n -> Map.of("actual", n)), INPUT),
                Arguments.of("refine with custom failure", (UnaryOperator<CombinePart<Map<String, Object>, Integer>>)
                        p -> p.refine(n -> false, (n, path) -> Result.fail(path, "boom", "boom")), INPUT));
    }

    @ParameterizedTest(name = "{0} reports at /age")
    @MethodSource("failingCombinators")
    void everyCombinatorReportsAtTheFieldPath(
            String label,
            UnaryOperator<CombinePart<Map<String, Object>, Integer>> combinator,
            Map<String, Object> input) {

        var part = combinator.apply(field("age", int_()));

        assertEquals("/age", firstIssue(part.decode(input)).path().toJsonPointer(),
                label + " reported at the wrong path, standalone");

        var combined = combine(field("name", string()), part).map((name, age) -> name + age);
        var withName = new java.util.HashMap<String, Object>(input);
        withName.put("name", "Taro");
        assertEquals("/age", firstIssue(combined.decode(Map.copyOf(withName))).path().toJsonPointer(),
                label + " reported at the wrong path, inside a combiner");
    }

    @Test
    void refineWithMetaCarriesTheFailingValue() {
        var part = field("age", int_())
                .refine(n -> n % 2 == 1, "must_be_odd", "must be odd", n -> Map.of("actual", n));

        var issue = firstIssue(part.decode(INPUT));
        assertEquals("must_be_odd", issue.code());
        assertEquals(20, issue.meta().get("actual"));
        assertEquals("/age", issue.path().toJsonPointer());
    }

    @Test
    void aCustomFailurePathIsRelativeToTheField() {
        var part = field("age", int_())
                .refine(n -> false, (n, path) -> Result.fail(path.append("odd"), "boom", "boom"));

        assertEquals("/age/odd", firstIssue(part.decode(INPUT)).path().toJsonPointer());
    }

    @Test
    void chainedCombinatorsAppendTheNameOnlyOnce() {
        var part = field("age", int_())
                .refine(n -> n > 0, "must_be_positive", "must be positive")
                .map(n -> n * 10)
                .flatMap(n -> Result.ok(n + 1))
                .refine(n -> false, "boom", "boom");

        assertEquals("/age", firstIssue(part.decode(INPUT)).path().toJsonPointer());
    }

    /**
     * The failure that motivated #109's path fix: a rule about a field, one level down, used to be
     * reported against the enclosing object.
     */
    @Test
    void aNestedFieldReportsAtItsFullPath() {
        var dec = field("user", nested(combine(
                field("age", int_()).refine(n -> n % 2 == 1, "must_be_odd", "must be odd"),
                field("name", string())
        ).map((age, name) -> name + age)));

        assertEquals("/user/age",
                firstIssue(dec.decode(Map.of("user", Map.of("age", 20, "name", "Taro")))).path().toJsonPointer());
    }

    /**
     * One decoder must not report two different paths depending on which check failed — the
     * asymmetry that made the old behaviour indefensible.
     */
    @Test
    void typeMismatchAndRefinementFailureShareTheFieldPath() {
        var part = field("age", int_()).refine(n -> n % 2 == 1, "must_be_odd", "must be odd");

        var mismatch = firstIssue(part.decode(Map.of("age", "not-a-number")));
        var refinement = firstIssue(part.decode(INPUT));

        assertEquals(ErrorCodes.TYPE_MISMATCH, mismatch.code());
        assertEquals("must_be_odd", refinement.code());
        assertEquals(mismatch.path().toJsonPointer(), refinement.path().toJsonPointer());
        assertEquals("/age", mismatch.path().toJsonPointer());
    }

    /** A flat component reads the whole input, so it must not append anything. */
    @Test
    void aFlatComponentDoesNotTouchThePath() {
        Decoder<Map<String, Object>, Integer> whole = (in, path) -> Result.fail(path, "boom", "boom");
        var part = net.unit8.raoh.decode.map.MapDecoders.flat(whole);

        assertEquals("", firstIssue(part.decode(INPUT)).path().toJsonPointer());
    }

    @Test
    void combinatorsOnAFlatComponentAlsoLeaveThePathAlone() {
        Decoder<Map<String, Object>, Integer> whole = (in, path) -> Result.ok(1);
        var part = net.unit8.raoh.decode.map.MapDecoders.flat(whole)
                .refine(n -> false, "boom", "boom");

        assertEquals("", firstIssue(part.decode(INPUT)).path().toJsonPointer());
    }

    @Test
    void accumulatedIssuesFromSiblingFieldsKeepTheirOwnPaths() {
        var dec = combine(
                field("name", string()),
                field("age", int_()).refine(n -> false, "boom", "boom")
        ).map((name, age) -> name + age);

        switch (dec.decode(Map.of("name", 1, "age", 20))) {
            case Ok(var v) -> fail("Expected Err, got Ok: " + v);
            case Err(var issues) -> assertEquals(List.of("/name", "/age"),
                    issues.asList().stream().map(i -> i.path().toJsonPointer()).toList());
        }
    }
}
