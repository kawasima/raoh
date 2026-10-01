package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.ErrorCodes;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

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

    /**
     * Divisibility is decided however far apart the scales are, where {@code remainder} builds a
     * power of ten as large as their difference and throws (R000846).
     */
    @Test
    void multipleOfDecidesScalesAsFarApartAsTheyGo() {
        var issue = decodeErr(decimal().multipleOf(new BigDecimal("7E-2147483647")), new BigDecimal("1E+100"));
        assertEquals(ErrorCodes.NOT_MULTIPLE_OF, issue.code());
        assertEquals(new BigDecimal("1E+100"), issue.meta().get("actual"));
        assertEquals(new BigDecimal("7E-2147483647"), issue.meta().get("divisor"));
        assertEquals(ErrorCodes.NOT_MULTIPLE_OF,
                decodeErr(decimal().multipleOf(bd("3")), new BigDecimal("1E+2147483647")).code());
        var max = new BigDecimal("1E+2147483647");
        assertEquals(max, decodeOk(decimal().multipleOf(bd("0.1")), max));
        assertEquals(max, decodeOk(decimal().multipleOf(new BigDecimal("1E-2147483647")), max));
        var least = new BigDecimal(BigInteger.ONE, Integer.MAX_VALUE);
        assertEquals(least, decodeOk(decimal().multipleOf(least), least));
        assertEquals(ErrorCodes.NOT_MULTIPLE_OF,
                decodeErr(decimal().multipleOf(new BigDecimal(BigInteger.ONE, Integer.MIN_VALUE)), least).code());
        var huge = new BigDecimal(BigInteger.valueOf(6), Integer.MIN_VALUE);
        assertEquals(huge, decodeOk(decimal().multipleOf(new BigDecimal(BigInteger.valueOf(3), Integer.MIN_VALUE)), huge));
        assertEquals(huge, decodeOk(decimal().multipleOf(new BigDecimal(BigInteger.valueOf(-2), Integer.MAX_VALUE)), huge));
    }

    @Test
    void multipleOfTakesSignsAndZeroAsArithmeticDoes() {
        assertEquals(bd("-1.5"), decodeOk(decimal().multipleOf(bd("0.5")), bd("-1.5")));
        assertEquals(bd("1.5"), decodeOk(decimal().multipleOf(bd("-0.5")), bd("1.5")));
        assertEquals(bd("-1.5"), decodeOk(decimal().multipleOf(bd("-0.5")), bd("-1.5")));
        assertEquals(bd("0E+99"), decodeOk(decimal().multipleOf(bd("7")), bd("0E+99")));
        assertEquals(bd("0.000"), decodeOk(decimal().multipleOf(bd("7E-50")), bd("0.000")));
        assertEquals(ErrorCodes.NOT_MULTIPLE_OF, decodeErr(decimal().multipleOf(bd("-0.3")), bd("1")).code());
        assertEquals(bd("120"), decodeOk(decimal().multipleOf(bd("1.2E+1")), bd("120")));
        assertEquals(bd("1.20E+2"), decodeOk(decimal().multipleOf(bd("12.000")), bd("1.20E+2")));
    }

    /**
     * Where {@code remainder} still works, the two agree: values and divisors of every sign, with
     * scales on either side of each other.
     */
    @Test
    void multipleOfAgreesWithRemainderWhereRemainderWorks() {
        var random = new java.util.Random(168);
        var disagree = new java.util.ArrayList<String>();
        for (int i = 0; i < 20_000; i++) {
            var value = new BigDecimal(BigInteger.valueOf(random.nextInt(2_000_001) - 1_000_000)
                    .multiply(BigInteger.TEN.pow(random.nextInt(4))), random.nextInt(41) - 20);
            BigDecimal divisor;
            do {
                divisor = new BigDecimal(BigInteger.valueOf(random.nextInt(2001) - 1000), random.nextInt(41) - 20);
            } while (divisor.signum() == 0);
            boolean expected = value.remainder(divisor).signum() == 0;
            boolean actual = decimal().multipleOf(divisor).decode(value, net.unit8.raoh.Path.ROOT)
                    instanceof net.unit8.raoh.Ok<?>;
            if (expected != actual) {
                disagree.add(value + " % " + divisor + ": remainder says " + expected);
            }
        }
        assertEquals(java.util.List.of(), disagree);
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
