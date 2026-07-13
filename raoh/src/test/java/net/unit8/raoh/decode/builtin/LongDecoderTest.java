package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.ErrorCodes;
import org.junit.jupiter.api.Test;

import static net.unit8.raoh.decode.ObjectDecoders.long_;
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeErr;
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeOk;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct unit tests for {@link LongDecoder}, mirroring {@link IntDecoderTest} for the
 * {@code long} type. Failure paths assert both the error code and the fallback message.
 */
class LongDecoderTest {

    // --- min / max ---

    @Test
    void minAcceptsBoundaryValue() {
        assertEquals(5L, decodeOk(long_().min(5), 5L));
    }

    @Test
    void minRejectsBelowBoundary() {
        var issue = decodeErr(long_().min(5), 4L);
        assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
        assertEquals("must be at least 5", issue.message());
        assertEquals(5L, issue.meta().get("min"));
    }

    @Test
    void maxRejectsAboveBoundary() {
        var issue = decodeErr(long_().max(5), 6L);
        assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
        assertEquals("must be at most 5", issue.message());
    }

    @Test
    void minUsesCustomMessageWhenProvided() {
        var issue = decodeErr(long_().min(5, "too small"), 4L);
        assertEquals("too small", issue.message());
        assertTrue(issue.customMessage());
    }

    // --- range ---

    @Test
    void rangeAcceptsBothBoundaries() {
        assertEquals(1L, decodeOk(long_().range(1, 10), 1L));
        assertEquals(10L, decodeOk(long_().range(1, 10), 10L));
    }

    @Test
    void rangeRejectsOutside() {
        var issue = decodeErr(long_().range(1, 10), 11L);
        assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
        assertEquals("must be between 1 and 10", issue.message());
    }

    @Test
    void rangeRejectsInvertedBoundsAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> long_().range(10, 1));
    }

    // --- sign constraints ---

    @Test
    void positiveAcceptsOneRejectsZero() {
        assertEquals(1L, decodeOk(long_().positive(), 1L));
        assertEquals("must be positive", decodeErr(long_().positive(), 0L).message());
    }

    @Test
    void negativeAcceptsMinusOneRejectsZero() {
        assertEquals(-1L, decodeOk(long_().negative(), -1L));
        assertEquals("must be negative", decodeErr(long_().negative(), 0L).message());
    }

    @Test
    void nonNegativeAcceptsZeroRejectsMinusOne() {
        assertEquals(0L, decodeOk(long_().nonNegative(), 0L));
        assertEquals("must be non-negative", decodeErr(long_().nonNegative(), -1L).message());
    }

    @Test
    void nonPositiveAcceptsZeroRejectsOne() {
        assertEquals(0L, decodeOk(long_().nonPositive(), 0L));
        assertEquals("must be non-positive", decodeErr(long_().nonPositive(), 1L).message());
    }

    // --- oneOf ---

    @Test
    void oneOfAcceptsMemberRejectsNonMember() {
        assertEquals(2L, decodeOk(long_().oneOf(1L, 2L, 3L), 2L));
        var issue = decodeErr(long_().oneOf(3L, 1L, 2L), 4L);
        assertEquals(ErrorCodes.NOT_ALLOWED, issue.code());
        assertEquals("must be one of [1, 2, 3]", issue.message());
    }

    // --- multipleOf ---

    @Test
    void multipleOfAcceptsMultipleRejectsNonMultiple() {
        assertEquals(15L, decodeOk(long_().multipleOf(5), 15L));
        var issue = decodeErr(long_().multipleOf(5), 14L);
        assertEquals(ErrorCodes.NOT_MULTIPLE_OF, issue.code());
        assertEquals("must be a multiple of 5", issue.message());
    }

    @Test
    void multipleOfRejectsZeroDivisorAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> long_().multipleOf(0));
    }

    // --- base decoder behaviour ---

    @Test
    void rejectsNullAsRequired() {
        assertEquals(ErrorCodes.REQUIRED, decodeErr(long_(), null).code());
    }
}
