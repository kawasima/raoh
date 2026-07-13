package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.ErrorCodes;
import org.junit.jupiter.api.Test;

import static net.unit8.raoh.decode.ObjectDecoders.int_;
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeErr;
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeOk;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct unit tests for {@link IntDecoder}, exercising each numeric constraint's
 * success and failure paths independent of the {@code Map} boundary. Failure paths
 * assert both the {@link ErrorCodes} value and the self-contained fallback message.
 */
class IntDecoderTest {

    // --- min ---

    @Test
    void minAcceptsBoundaryValue() {
        assertEquals(5, decodeOk(int_().min(5), 5));
    }

    @Test
    void minRejectsBelowBoundary() {
        var issue = decodeErr(int_().min(5), 4);
        assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
        assertEquals("must be at least 5", issue.message());
        assertEquals(5, issue.meta().get("min"));
        assertEquals(4, issue.meta().get("actual"));
    }

    @Test
    void minUsesCustomMessageWhenProvided() {
        var issue = decodeErr(int_().min(5, "too small"), 4);
        assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
        assertEquals("too small", issue.message());
        assertTrue(issue.customMessage());
    }

    // --- max ---

    @Test
    void maxAcceptsBoundaryValue() {
        assertEquals(5, decodeOk(int_().max(5), 5));
    }

    @Test
    void maxRejectsAboveBoundary() {
        var issue = decodeErr(int_().max(5), 6);
        assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
        assertEquals("must be at most 5", issue.message());
        assertEquals(5, issue.meta().get("max"));
    }

    // --- range ---

    @Test
    void rangeAcceptsBothBoundaries() {
        assertEquals(1, decodeOk(int_().range(1, 10), 1));
        assertEquals(10, decodeOk(int_().range(1, 10), 10));
    }

    @Test
    void rangeRejectsBelowAndAbove() {
        var below = decodeErr(int_().range(1, 10), 0);
        assertEquals(ErrorCodes.OUT_OF_RANGE, below.code());
        assertEquals("must be between 1 and 10", below.message());
        assertEquals(1, below.meta().get("min"));
        assertEquals(10, below.meta().get("max"));

        var above = decodeErr(int_().range(1, 10), 11);
        assertEquals(ErrorCodes.OUT_OF_RANGE, above.code());
    }

    @Test
    void rangeRejectsInvertedBoundsAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> int_().range(10, 1));
    }

    // --- sign constraints ---

    @Test
    void positiveAcceptsOneRejectsZero() {
        assertEquals(1, decodeOk(int_().positive(), 1));
        var issue = decodeErr(int_().positive(), 0);
        assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
        assertEquals("must be positive", issue.message());
    }

    @Test
    void negativeAcceptsMinusOneRejectsZero() {
        assertEquals(-1, decodeOk(int_().negative(), -1));
        var issue = decodeErr(int_().negative(), 0);
        assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
        assertEquals("must be negative", issue.message());
    }

    @Test
    void nonNegativeAcceptsZeroRejectsMinusOne() {
        assertEquals(0, decodeOk(int_().nonNegative(), 0));
        var issue = decodeErr(int_().nonNegative(), -1);
        assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
        assertEquals("must be non-negative", issue.message());
    }

    @Test
    void nonPositiveAcceptsZeroRejectsOne() {
        assertEquals(0, decodeOk(int_().nonPositive(), 0));
        var issue = decodeErr(int_().nonPositive(), 1);
        assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
        assertEquals("must be non-positive", issue.message());
    }

    // --- oneOf ---

    @Test
    void oneOfAcceptsMemberOfSet() {
        assertEquals(2, decodeOk(int_().oneOf(1, 2, 3), 2));
    }

    @Test
    void oneOfRejectsNonMemberWithSortedAllowedList() {
        var issue = decodeErr(int_().oneOf(3, 1, 2), 4);
        assertEquals(ErrorCodes.NOT_ALLOWED, issue.code());
        assertEquals("must be one of [1, 2, 3]", issue.message());
    }

    // --- multipleOf ---

    @Test
    void multipleOfAcceptsMultiple() {
        assertEquals(15, decodeOk(int_().multipleOf(5), 15));
    }

    @Test
    void multipleOfRejectsNonMultiple() {
        var issue = decodeErr(int_().multipleOf(5), 14);
        assertEquals(ErrorCodes.NOT_MULTIPLE_OF, issue.code());
        assertEquals("must be a multiple of 5", issue.message());
        assertEquals(5, issue.meta().get("divisor"));
    }

    @Test
    void multipleOfRejectsZeroDivisorAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> int_().multipleOf(0));
    }

    // --- base decoder behaviour ---

    @Test
    void rejectsNullAsRequired() {
        assertEquals(ErrorCodes.REQUIRED, decodeErr(int_(), null).code());
    }

    @Test
    void rejectsNonNumericAsTypeMismatch() {
        assertEquals(ErrorCodes.TYPE_MISMATCH, decodeErr(int_(), "42").code());
    }
}
