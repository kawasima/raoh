package net.unit8.raoh.decode;

import net.unit8.raoh.Err;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Issues;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.map.MapDecoders;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static net.unit8.raoh.decode.Decoders.combine;
import static net.unit8.raoh.decode.Decoders.lazy;
import static net.unit8.raoh.decode.Decoders.oneOf;
import static net.unit8.raoh.decode.Decoders.recover;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Direct unit tests for the {@link Decoders} utility combinators that previously lacked dedicated
 * coverage: {@code oneOf}, {@code recover}, {@code lazy}, and
 * {@link ObjectDecoders#enumOf(Class)}.
 *
 * <p>Each test asserts the documented contract (Javadoc), not the current implementation shape:
 * expected error codes and metadata are taken from the spec, and the decoders under test are driven
 * by tiny hand-written stubs so the exact success/failure of each input is controlled. Fallback
 * message wording is deliberately not asserted — it is not part of the documented contract.
 */
class DecodersCombinatorTest {

    /** A stub decoder that always succeeds with the given value, ignoring its input. */
    private static Decoder<Object, String> succeed(String value) {
        return (in, path) -> Result.ok(value);
    }

    /** A stub decoder that always fails with a single issue carrying the given code. */
    private static Decoder<Object, String> failWith(String code) {
        return (in, path) -> Result.fail(path, code, code + " failed");
    }

    // --- recover: recovers from ANY error ---

    @Test
    void recoverReplacesAnyErrorWithFallback() {
        // Spec: recover ignores the error code — even a non-required error is replaced.
        assertEquals("r", ok(recover(failWith(ErrorCodes.TYPE_MISMATCH), "r").decode(null)));
        assertEquals("r", ok(recover(failWith(ErrorCodes.REQUIRED), "r").decode(null)));
    }

    @Test
    void recoverPassesSuccessThrough() {
        assertEquals("value", ok(recover(succeed("value"), "r").decode(null)));
    }

    @Test
    void recoverFunctionComputesFallbackFromIssues() {
        // Spec: the function variant derives the recovery value from the accumulated issues.
        var dec = recover(failWith(ErrorCodes.OUT_OF_RANGE),
                (Function<Issues, String>) issues -> "recovered:" + issues.asList().size());
        assertEquals("recovered:1", ok(dec.decode(null)));
    }

    // --- oneOf: first success wins; all-fail yields one_of_failed with candidate meta ---

    @Test
    void oneOfReturnsFirstSuccessfulCandidate() {
        // Both candidates succeed; the first one must win (order-sensitive).
        assertEquals("first", ok(oneOf(succeed("first"), succeed("second")).decode(null)));
    }

    @Test
    void oneOfFallsThroughToLaterCandidate() {
        assertEquals("x", ok(oneOf(failWith(ErrorCodes.TYPE_MISMATCH), succeed("x")).decode(null)));
    }

    @Test
    void oneOfFailsWithOneOfFailedAndPerCandidateMeta() {
        var dec = oneOf(failWith(ErrorCodes.TYPE_MISMATCH), failWith(ErrorCodes.INVALID_FORMAT));
        switch (dec.decode(null)) {
            case Ok<String>(var v) -> fail("expected Err, got " + v);
            case Err<String>(var issues) -> {
                var issue = issues.asList().getFirst();
                // The error code is the documented contract; the fallback message wording is not,
                // so it is deliberately not asserted here.
                assertEquals(ErrorCodes.ONE_OF_FAILED, issue.code());
                // Spec: the error carries one meta entry per failed candidate, in order.
                var candidates = (List<?>) issue.meta().get("candidates");
                assertEquals(2, candidates.size());
                var first = (Map<?, ?>) candidates.getFirst();
                assertEquals(0, first.get("candidate"));
                assertNotNull(first.get("issues"), "each candidate records its own issues");
            }
        }
    }

    // --- lazy: deferred, recursive decoder definitions ---

    @Test
    void lazyDefersSupplierUntilEachDecodeInvocation() {
        // Spec: "supplies the decoder on each invocation." Construction must not call the supplier;
        // each decode call must.
        var calls = new AtomicInteger();
        Decoder<Object, String> dec = lazy(() -> {
            calls.incrementAndGet();
            return succeed("value");
        });
        assertEquals(0, calls.get(), "supplier must not run at construction time");
        dec.decode(null);
        dec.decode(null);
        assertEquals(2, calls.get(), "supplier runs once per decode invocation");
    }

    record Node(int value, Optional<Node> next) {}

    /** A self-referential decoder that only compiles because {@code lazy} defers the recursion. */
    private static Decoder<Map<String, Object>, Node> node() {
        return combine(
                MapDecoders.field("value", ObjectDecoders.int_()),
                MapDecoders.optionalField("next", MapDecoders.nested(lazy(DecodersCombinatorTest::node)))
        ).map(Node::new);
    }

    @Test
    void lazyEnablesRecursiveDecoding() {
        var input = Map.<String, Object>of(
                "value", 1,
                "next", Map.of(
                        "value", 2,
                        "next", Map.of("value", 3)));
        var node = ok(node().decode(input));
        assertEquals(1, node.value());
        assertEquals(2, node.next().orElseThrow().value());
        assertEquals(3, node.next().orElseThrow().next().orElseThrow().value());
        assertEquals(Optional.empty(), node.next().orElseThrow().next().orElseThrow().next());
    }

    // --- enumOf: ASCII case-insensitive name lookup, invalid_format on miss ---

    enum Color { RED, GREEN, BLUE }

    @Test
    void enumOfMatchesExactName() {
        assertEquals(Color.RED, ok(ObjectDecoders.enumOf(Color.class).decode("RED")));
    }

    @Test
    void enumOfIsCaseInsensitive() {
        // Spec: ASCII case-insensitive; A-Z equal a-z, every other character matches exactly.
        assertEquals(Color.GREEN, ok(ObjectDecoders.enumOf(Color.class).decode("green")));
        assertEquals(Color.BLUE, ok(ObjectDecoders.enumOf(Color.class).decode("bLuE")));
    }

    @Test
    void enumOfRejectsUnknownValueWithAllowedList() {
        switch (ObjectDecoders.enumOf(Color.class).decode("purple")) {
            case Ok<Color>(var v) -> fail("expected Err, got " + v);
            case Err<Color>(var issues) -> {
                var issue = issues.asList().getFirst();
                assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
                // Spec: the error lists the accepted values — the lower-cased constant names,
                // in code point order rather than declaration order.
                assertEquals(List.of("blue", "green", "red"), issue.meta().get("allowed"));
            }
        }
    }

    /** Constant names outside ASCII: U+FF21 (fullwidth A) and U+10400 (Deseret), which String.compareTo orders the other way. */
    enum Wide { Ａ, 𐐀 }

    @Test
    void enumOfListsAllowedInCodePointOrder() {
        switch (ObjectDecoders.enumOf(Wide.class).decode("x")) {
            case Ok<Wide>(var v) -> fail("expected Err, got " + v);
            case Err<Wide>(var issues) ->
                    assertEquals(List.of("Ａ", "𐐀"), issues.asList().getFirst().meta().get("allowed"));
        }
    }

    @Test
    void enumOfFoldsOnlyAscii() {
        var dec = ObjectDecoders.enumOf(Thread.State.class);
        assertEquals(Thread.State.BLOCKED, ok(dec.decode("BLOCKED")));
        assertEquals(Thread.State.BLOCKED, ok(dec.decode("bLoCkEd")));
        // "bloc" + U+212A KELVIN SIGN + "ed": String.toLowerCase(Locale.ROOT) turns it into "blocked",
        // so the old implementation accepted it. It is not an ASCII letter and must not match.
        assertInstanceOf(Err.class, dec.decode("bloc\u212Aed"));
    }

    @Test
    void enumOfMatchesNonAsciiNamesExactly() {
        var dec = ObjectDecoders.enumOf(Wide.class);
        assertEquals(Wide.Ａ, ok(dec.decode("Ａ")));
        assertEquals(Wide.𐐀, ok(dec.decode("𐐀")));
        assertInstanceOf(Err.class, dec.decode("ａ"));
        assertInstanceOf(Err.class, dec.decode("𐐨"));
    }

    enum Mixed { CaféX }

    @Test
    void enumOfFoldsOnlyTheAsciiPartOfAMixedName() {
        var dec = ObjectDecoders.enumOf(Mixed.class);
        assertEquals(Mixed.CaféX, ok(dec.decode("cAféx")));
        assertInstanceOf(Err.class, dec.decode("cafÉx"));
    }

    enum Clash { A, a }

    @Test
    void enumOfRejectsConstantsThatCollideAtConstruction() {
        var e = assertThrows(IllegalArgumentException.class, () -> ObjectDecoders.enumOf(Clash.class));
        assertTrue(e.getMessage().contains("'A'") && e.getMessage().contains("'a'"), e.getMessage());
    }

    // --- helpers ---

    private static <T> T ok(Result<T> result) {
        return switch (result) {
            case Ok<T>(var value) -> value;
            case Err<T>(var issues) -> fail("expected Ok, got Err: " + issues);
        };
    }
}
