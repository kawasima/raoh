package net.unit8.raoh.decode;

import net.unit8.raoh.Err;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.map.MapDecoders;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.unit8.raoh.decode.ObjectDecoders.int_;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * An outer {@code strict} leaves out a field an inner {@code strict} already reported, and only
 * such a field: what counts is that a {@code strict} made the issue, not what the issue looks like.
 * The specification cases are in {@code JsonStrictTest}; these pin what they cannot, since no case
 * has a decoder of its own return {@code unknown_field}.
 */
class NestedStrictTest {

    private static final Map<String, Object> A_AND_B = Map.of("a", 1, "b", 2);

    /** Reads field {@code a} of a map, whatever else it holds. */
    private static final Decoder<Map<String, Object>, Integer> A = MapDecoders.field("a", int_()).asDecoder();

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
    void anUnknownFieldIssueADecoderOfYourOwnReturnsIsReportedAgain() {
        Decoder<Map<String, Object>, Integer> own = (in, path) ->
                Result.fail(path.append("b"), ErrorCodes.UNKNOWN_FIELD, "unknown field", Map.of("field", "b"));

        assertEquals(List.of("/b unknown_field", "/b unknown_field"),
                issues(MapDecoders.strict(own, Set.of("a")).decode(A_AND_B, Path.ROOT)));
    }

    @Test
    void theIssuesOfAStrictRebasedByFlatMapStillCount() {
        // The inner strict runs at the root inside the function, and flatMap rebases its issues to
        // the outer strict's path; rebasing must keep what made them.
        var inner = MapDecoders.strict(A, Set.of("a"));
        Decoder<Map<String, Object>, Integer> dec = MapDecoders.strict(
                A.flatMap(a -> inner.decode(A_AND_B, Path.ROOT)), Set.of("a"));

        assertEquals(List.of("/o/b unknown_field"), issues(dec.decode(A_AND_B, Path.of("o"))));
    }

    @Test
    void aStrictInsideAOneOfCandidateDoesNotCount() {
        // The candidate's unknown_field is inside the one_of_failed issue's metadata, not among the
        // issues the outer strict is given.
        Decoder<Map<String, Object>, Integer> never = (in, path) -> Result.fail(path, "never", "never");
        var dec = MapDecoders.strict(Decoders.oneOf(MapDecoders.strict(A, Set.of("a")), never), Set.of("a"));

        assertEquals(List.of(" one_of_failed", "/b unknown_field"), issues(dec.decode(A_AND_B, Path.ROOT)));
    }

    @Test
    void theResultOfTheInnerDecoderIsKeptWhenNoFieldIsUnknown() {
        var dec = MapDecoders.strict(MapDecoders.strict(A, Set.of("a", "b")), Set.of("a", "b"));

        assertEquals(new Ok<>(1), dec.decode(A_AND_B, Path.ROOT));
    }
}
