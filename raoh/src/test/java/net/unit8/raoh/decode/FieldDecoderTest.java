package net.unit8.raoh.decode;

import net.unit8.raoh.Err;
import net.unit8.raoh.Issue;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that the combinators overridden on {@link FieldDecoder} keep the decoder bound to its
 * field name. Losing the name makes {@code Combiner#strict()} report the field as unknown.
 */
class FieldDecoderTest {

    private static final FieldDecoder<String, Integer> LENGTH =
            FieldDecoder.named("len", (in, path) -> Result.ok(in.length()));

    @Test
    void namedBindsTheDecoderToAName() {
        assertEquals("len", LENGTH.fieldName());
        assertEquals(3, LENGTH.decode("abc", Path.ROOT).getOrThrow());
    }

    @Test
    void mapKeepsTheName() {
        var dec = LENGTH.map(n -> n * 2);
        assertEquals("len", dec.fieldName());
        assertEquals(6, dec.decode("abc", Path.ROOT).getOrThrow());
    }

    @Test
    void flatMapKeepsTheName() {
        var dec = LENGTH.flatMap(n -> Result.ok(n + 1));
        assertEquals("len", dec.fieldName());
        assertEquals(4, dec.decode("abc", Path.ROOT).getOrThrow());
    }

    @Test
    void flatMapWithPathKeepsTheName() {
        var dec = LENGTH.flatMapWithPath((n, path) -> Result.ok(path.append("n").toJsonPointer() + n));
        assertEquals("len", dec.fieldName());
        assertEquals("/n3", dec.decode("abc", Path.ROOT).getOrThrow());
    }

    @Test
    void pipeKeepsTheName() {
        var dec = LENGTH.pipe((n, path) -> Result.ok(n.toString()));
        assertEquals("len", dec.fieldName());
        assertEquals("3", dec.decode("abc", Path.ROOT).getOrThrow());
    }

    @Test
    void refineKeepsTheName() {
        var dec = LENGTH.refine(n -> n % 2 == 0, "must_be_even", "must be even");
        assertEquals("len", dec.fieldName());
        assertEquals(4, dec.decode("abcd", Path.ROOT).getOrThrow());

        var issue = firstIssue(dec.decode("abc", Path.ROOT));
        assertEquals("must_be_even", issue.code());
        assertEquals("must be even", issue.message());
    }

    @Test
    void refineWithMetaKeepsTheName() {
        var dec = LENGTH.refine(n -> n % 2 == 0, "must_be_even", "must be even",
                n -> Map.of("actual", n));
        assertEquals("len", dec.fieldName());

        var issue = firstIssue(dec.decode("abc", Path.ROOT));
        assertEquals("must_be_even", issue.code());
        assertEquals(3, issue.meta().get("actual"));
    }

    @Test
    void refineWithCustomFailureKeepsTheName() {
        var dec = LENGTH.refine(n -> n % 2 == 0,
                (n, path) -> Result.fail(path.append("odd"), "must_be_even", "must be even"));
        assertEquals("len", dec.fieldName());

        var issue = firstIssue(dec.decode("abc", Path.ROOT));
        assertEquals("/odd", issue.path().toJsonPointer());
    }

    @Test
    void combinatorsChainWithoutLosingTheName() {
        var dec = LENGTH
                .refine(n -> n > 0, "must_be_positive", "must be positive")
                .map(n -> n * 10)
                .refine(n -> n < 100, "too_large", "too large");
        assertEquals("len", dec.fieldName());
        assertEquals(30, dec.decode("abc", Path.ROOT).getOrThrow());
    }

    private static Issue firstIssue(Result<?> result) {
        return switch (result) {
            case Ok<?> ok -> throw new AssertionError("expected Err but got: " + ok);
            case Err<?> err -> err.issues().asList().getFirst();
        };
    }
}
