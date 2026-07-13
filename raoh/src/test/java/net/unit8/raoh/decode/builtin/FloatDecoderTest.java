package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.ErrorCodes;
import org.junit.jupiter.api.Test;

import static net.unit8.raoh.decode.ObjectDecoders.float_;
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeErr;
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeOk;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct unit tests for {@link FloatDecoder}, mirroring {@link DoubleDecoderTest} for the
 * {@code float} type. The decoder's range checks use {@link Float#compare(float, float)}.
 */
class FloatDecoderTest {

    // --- min / max ---

    @Test
    void minAcceptsBoundaryValue() {
        assertEquals(1.5f, decodeOk(float_().min(1.5f), 1.5f));
    }

    @Test
    void minRejectsBelowBoundary() {
        var issue = decodeErr(float_().min(1.5f), 1.4f);
        assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
        assertEquals("must be at least 1.5", issue.message());
        assertEquals(1.5f, issue.meta().get("min"));
    }

    @Test
    void maxRejectsAboveBoundary() {
        var issue = decodeErr(float_().max(1.5f), 1.6f);
        assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
        assertEquals("must be at most 1.5", issue.message());
    }

    @Test
    void minUsesCustomMessageWhenProvided() {
        var issue = decodeErr(float_().min(1.5f, "too small"), 1.0f);
        assertEquals("too small", issue.message());
        assertTrue(issue.customMessage());
    }

    // --- range ---

    @Test
    void rangeAcceptsBothBoundaries() {
        assertEquals(1.0f, decodeOk(float_().range(1.0f, 10.0f), 1.0f));
        assertEquals(10.0f, decodeOk(float_().range(1.0f, 10.0f), 10.0f));
    }

    @Test
    void rangeRejectsOutside() {
        var issue = decodeErr(float_().range(1.0f, 10.0f), 10.5f);
        assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
        assertEquals("must be between 1.0 and 10.0", issue.message());
    }

    @Test
    void rangeRejectsNaN() {
        var issue = decodeErr(float_().range(0.0f, 10.0f), Float.NaN);
        assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
    }

    @Test
    void rangeRejectsInvertedBoundsAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> float_().range(10.0f, 1.0f));
    }

    // --- sign constraints ---

    @Test
    void positiveAcceptsPositiveRejectsZero() {
        assertEquals(0.1f, decodeOk(float_().positive(), 0.1f));
        assertEquals("must be positive", decodeErr(float_().positive(), 0.0f).message());
    }

    @Test
    void negativeAcceptsNegativeRejectsZero() {
        assertEquals(-0.1f, decodeOk(float_().negative(), -0.1f));
        assertEquals("must be negative", decodeErr(float_().negative(), 0.0f).message());
    }

    @Test
    void nonNegativeAcceptsZeroRejectsNegative() {
        assertEquals(0.0f, decodeOk(float_().nonNegative(), 0.0f));
        assertEquals("must be non-negative", decodeErr(float_().nonNegative(), -0.1f).message());
    }

    @Test
    void nonPositiveAcceptsZeroRejectsPositive() {
        assertEquals(0.0f, decodeOk(float_().nonPositive(), 0.0f));
        assertEquals("must be non-positive", decodeErr(float_().nonPositive(), 0.1f).message());
    }

    // --- oneOf ---

    @Test
    void oneOfAcceptsMemberRejectsNonMember() {
        assertEquals(2.0f, decodeOk(float_().oneOf(1.0f, 2.0f, 3.0f), 2.0f));
        var issue = decodeErr(float_().oneOf(3.0f, 1.0f, 2.0f), 4.0f);
        assertEquals(ErrorCodes.NOT_ALLOWED, issue.code());
        assertEquals("must be one of [1.0, 2.0, 3.0]", issue.message());
    }

    // --- base decoder behaviour ---

    @Test
    void rejectsNullAsRequired() {
        assertEquals(ErrorCodes.REQUIRED, decodeErr(float_(), null).code());
    }
}
