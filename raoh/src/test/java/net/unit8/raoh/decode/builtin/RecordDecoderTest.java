package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.ErrorCodes;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static net.unit8.raoh.decode.ObjectDecoders.int_;
import static net.unit8.raoh.decode.ObjectDecoders.map;
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeErr;
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeOk;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct unit tests for {@link RecordDecoder}, covering the entry-count constraints.
 * The value decoder is a plain {@code int_()} so the tests focus on record-level behaviour.
 */
class RecordDecoderTest {

    @Test
    void nonemptyAcceptsNonEmptyRejectsEmpty() {
        assertEquals(Map.of("a", 1), decodeOk(map(int_()).nonempty(), Map.of("a", 1)));
        var issue = decodeErr(map(int_()).nonempty(), Map.of());
        assertEquals(ErrorCodes.TOO_SMALL, issue.code());
        assertEquals("must not be empty", issue.message());
    }

    @Test
    void minSizeAcceptsBoundaryRejectsBelow() {
        assertEquals(Map.of("a", 1, "b", 2), decodeOk(map(int_()).minSize(2), Map.of("a", 1, "b", 2)));
        var issue = decodeErr(map(int_()).minSize(2), Map.of("a", 1));
        assertEquals(ErrorCodes.TOO_SMALL, issue.code());
        assertEquals("must have at least 2 entries", issue.message());
        assertEquals(2, issue.meta().get("min"));
        assertEquals(1, issue.meta().get("actual"));
    }

    @Test
    void maxSizeAcceptsBoundaryRejectsAbove() {
        assertEquals(Map.of("a", 1, "b", 2), decodeOk(map(int_()).maxSize(2), Map.of("a", 1, "b", 2)));
        var issue = decodeErr(map(int_()).maxSize(2), Map.of("a", 1, "b", 2, "c", 3));
        assertEquals(ErrorCodes.TOO_BIG, issue.code());
        assertEquals("must have at most 2 entries", issue.message());
    }

    @Test
    void fixedSizeAcceptsExactRejectsDifferent() {
        assertEquals(Map.of("a", 1, "b", 2), decodeOk(map(int_()).fixedSize(2), Map.of("a", 1, "b", 2)));
        var issue = decodeErr(map(int_()).fixedSize(2), Map.of("a", 1, "b", 2, "c", 3));
        assertEquals(ErrorCodes.INVALID_SIZE, issue.code());
        assertEquals("must have exactly 2 entries", issue.message());
    }

    // --- custom messages ---

    @Test
    void nonemptyUsesCustomMessageAndKeepsMeta() {
        var issue = decodeErr(map(int_()).nonempty("need at least one entry"), Map.of());
        assertEquals(ErrorCodes.TOO_SMALL, issue.code());
        assertEquals("need at least one entry", issue.message());
        assertTrue(issue.customMessage());
        assertEquals(1, issue.meta().get("min"));
        assertEquals(0, issue.meta().get("actual"));
    }

    @Test
    void minSizeUsesCustomMessageAndKeepsMeta() {
        var issue = decodeErr(map(int_()).minSize(2, "need two"), Map.of("a", 1));
        assertEquals(ErrorCodes.TOO_SMALL, issue.code());
        assertEquals("need two", issue.message());
        assertTrue(issue.customMessage());
        assertEquals(2, issue.meta().get("min"));
        assertEquals(1, issue.meta().get("actual"));
    }

    @Test
    void maxSizeUsesCustomMessage() {
        var issue = decodeErr(map(int_()).maxSize(2, "too many"), Map.of("a", 1, "b", 2, "c", 3));
        assertEquals(ErrorCodes.TOO_BIG, issue.code());
        assertEquals("too many", issue.message());
        assertTrue(issue.customMessage());
    }

    @Test
    void fixedSizeUsesCustomMessage() {
        var issue = decodeErr(map(int_()).fixedSize(2, "exactly two"), Map.of("a", 1, "b", 2, "c", 3));
        assertEquals(ErrorCodes.INVALID_SIZE, issue.code());
        assertEquals("exactly two", issue.message());
        assertTrue(issue.customMessage());
    }

    @Test
    void nullCustomMessageFallsBackToDefault() {
        // A null message keeps the built-in default and leaves customMessage() false.
        var issue = decodeErr(map(int_()).minSize(2, null), Map.of("a", 1));
        assertEquals("must have at least 2 entries", issue.message());
        assertFalse(issue.customMessage());
    }

    // --- base decoder behaviour ---

    @Test
    void rejectsNullAsRequired() {
        assertEquals(ErrorCodes.REQUIRED, decodeErr(map(int_()), null).code());
    }

    @Test
    void rejectsNonMapAsTypeMismatch() {
        assertEquals(ErrorCodes.TYPE_MISMATCH, decodeErr(map(int_()), "not a map").code());
    }
}
