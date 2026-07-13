package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.ErrorCodes;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static net.unit8.raoh.decode.ObjectDecoders.decimal;
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeErr;
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeOk;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct unit tests for {@link DecimalDecoder}, covering the {@link BigDecimal}
 * numeric constraints plus the decimal-specific {@code scale()} constraint.
 */
class DecimalDecoderTest {

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    // --- min / max ---

    @Test
    void minAcceptsBoundaryValue() {
        assertEquals(bd("1.5"), decodeOk(decimal().min(bd("1.5")), bd("1.5")));
    }

    @Test
    void minRejectsBelowBoundary() {
        var issue = decodeErr(decimal().min(bd("1.5")), bd("1.4"));
        assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
        assertEquals("must be at least 1.5", issue.message());
        assertEquals(bd("1.5"), issue.meta().get("min"));
    }

    @Test
    void maxRejectsAboveBoundary() {
        var issue = decodeErr(decimal().max(bd("1.5")), bd("1.6"));
        assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
        assertEquals("must be at most 1.5", issue.message());
    }

    @Test
    void minUsesCustomMessageWhenProvided() {
        var issue = decodeErr(decimal().min(bd("1.5"), "too small"), bd("1.0"));
        assertEquals("too small", issue.message());
        assertTrue(issue.customMessage());
    }

    // --- range ---

    @Test
    void rangeAcceptsBothBoundaries() {
        assertEquals(bd("1"), decodeOk(decimal().range(bd("1"), bd("10")), bd("1")));
        assertEquals(bd("10"), decodeOk(decimal().range(bd("1"), bd("10")), bd("10")));
    }

    @Test
    void rangeRejectsOutside() {
        var issue = decodeErr(decimal().range(bd("1"), bd("10")), bd("11"));
        assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
        assertEquals("must be between 1 and 10", issue.message());
    }

    @Test
    void rangeRejectsInvertedBoundsAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> decimal().range(bd("10"), bd("1")));
    }

    // --- sign constraints ---

    @Test
    void positiveAcceptsPositiveRejectsZero() {
        assertEquals(bd("0.1"), decodeOk(decimal().positive(), bd("0.1")));
        assertEquals("must be positive", decodeErr(decimal().positive(), bd("0")).message());
    }

    @Test
    void negativeAcceptsNegativeRejectsZero() {
        assertEquals(bd("-0.1"), decodeOk(decimal().negative(), bd("-0.1")));
        assertEquals("must be negative", decodeErr(decimal().negative(), bd("0")).message());
    }

    @Test
    void nonNegativeAcceptsZeroRejectsNegative() {
        assertEquals(bd("0"), decodeOk(decimal().nonNegative(), bd("0")));
        assertEquals("must be non-negative", decodeErr(decimal().nonNegative(), bd("-0.1")).message());
    }

    @Test
    void nonPositiveAcceptsZeroRejectsPositive() {
        assertEquals(bd("0"), decodeOk(decimal().nonPositive(), bd("0")));
        assertEquals("must be non-positive", decodeErr(decimal().nonPositive(), bd("0.1")).message());
    }

    // --- multipleOf ---

    @Test
    void multipleOfAcceptsMultipleRejectsNonMultiple() {
        assertEquals(bd("1.5"), decodeOk(decimal().multipleOf(bd("0.5")), bd("1.5")));
        var issue = decodeErr(decimal().multipleOf(bd("0.5")), bd("1.7"));
        assertEquals(ErrorCodes.NOT_MULTIPLE_OF, issue.code());
        assertEquals("must be a multiple of 0.5", issue.message());
        assertEquals(bd("0.5"), issue.meta().get("divisor"));
    }

    @Test
    void multipleOfRejectsZeroDivisorAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> decimal().multipleOf(BigDecimal.ZERO));
    }

    // --- scale ---

    @Test
    void scaleAcceptsWithinLimit() {
        assertEquals(bd("1.23"), decodeOk(decimal().scale(2), bd("1.23")));
    }

    @Test
    void scaleRejectsTooManyFractionalDigits() {
        var issue = decodeErr(decimal().scale(2), bd("1.234"));
        assertEquals(ErrorCodes.INVALID_SCALE, issue.code());
        assertEquals("too many decimal places (max 2)", issue.message());
        assertEquals(2, issue.meta().get("maxScale"));
        assertEquals(3, issue.meta().get("actualScale"));
    }

    // --- base decoder behaviour ---

    @Test
    void rejectsNullAsRequired() {
        assertEquals(ErrorCodes.REQUIRED, decodeErr(decimal(), null).code());
    }
}
