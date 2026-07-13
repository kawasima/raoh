package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.ErrorCodes;
import org.junit.jupiter.api.Test;

import static net.unit8.raoh.decode.ObjectDecoders.double_;
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeErr;
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeOk;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct unit tests for {@link DoubleDecoder}. The decoder's range checks use
 * {@link Double#compare(double, double)}, so {@code NaN} is treated as greater than
 * every other value; the {@code rangeRejectsNaN} test guards that behaviour.
 */
class DoubleDecoderTest {

    // --- min / max ---

    @Test
    void minAcceptsBoundaryValue() {
        assertEquals(1.5, decodeOk(double_().min(1.5), 1.5));
    }

    @Test
    void minRejectsBelowBoundary() {
        var issue = decodeErr(double_().min(1.5), 1.4);
        assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
        assertEquals("must be at least 1.5", issue.message());
        assertEquals(1.5, issue.meta().get("min"));
    }

    @Test
    void maxRejectsAboveBoundary() {
        var issue = decodeErr(double_().max(1.5), 1.6);
        assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
        assertEquals("must be at most 1.5", issue.message());
    }

    @Test
    void minUsesCustomMessageWhenProvided() {
        var issue = decodeErr(double_().min(1.5, "too small"), 1.0);
        assertEquals("too small", issue.message());
        assertTrue(issue.customMessage());
    }

    // --- range ---

    @Test
    void rangeAcceptsBothBoundaries() {
        assertEquals(1.0, decodeOk(double_().range(1.0, 10.0), 1.0));
        assertEquals(10.0, decodeOk(double_().range(1.0, 10.0), 10.0));
    }

    @Test
    void rangeRejectsOutside() {
        var issue = decodeErr(double_().range(1.0, 10.0), 10.5);
        assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
        assertEquals("must be between 1.0 and 10.0", issue.message());
    }

    @Test
    void rangeRejectsNaN() {
        var issue = decodeErr(double_().range(0.0, 10.0), Double.NaN);
        assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
    }

    @Test
    void rangeRejectsInvertedBoundsAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> double_().range(10.0, 1.0));
    }

    // --- sign constraints ---

    @Test
    void positiveAcceptsPositiveRejectsZero() {
        assertEquals(0.1, decodeOk(double_().positive(), 0.1));
        assertEquals("must be positive", decodeErr(double_().positive(), 0.0).message());
    }

    @Test
    void negativeAcceptsNegativeRejectsZero() {
        assertEquals(-0.1, decodeOk(double_().negative(), -0.1));
        assertEquals("must be negative", decodeErr(double_().negative(), 0.0).message());
    }

    @Test
    void nonNegativeAcceptsZeroRejectsNegative() {
        assertEquals(0.0, decodeOk(double_().nonNegative(), 0.0));
        assertEquals("must be non-negative", decodeErr(double_().nonNegative(), -0.1).message());
    }

    @Test
    void nonPositiveAcceptsZeroRejectsPositive() {
        assertEquals(0.0, decodeOk(double_().nonPositive(), 0.0));
        assertEquals("must be non-positive", decodeErr(double_().nonPositive(), 0.1).message());
    }

    // --- oneOf ---

    @Test
    void oneOfAcceptsMemberRejectsNonMember() {
        assertEquals(2.0, decodeOk(double_().oneOf(1.0, 2.0, 3.0), 2.0));
        var issue = decodeErr(double_().oneOf(3.0, 1.0, 2.0), 4.0);
        assertEquals(ErrorCodes.NOT_ALLOWED, issue.code());
        assertEquals("must be one of [1.0, 2.0, 3.0]", issue.message());
    }

    // --- base decoder behaviour ---

    @Test
    void rejectsNullAsRequired() {
        assertEquals(ErrorCodes.REQUIRED, decodeErr(double_(), null).code());
    }
}
