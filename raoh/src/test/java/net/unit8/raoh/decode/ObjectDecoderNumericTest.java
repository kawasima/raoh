package net.unit8.raoh.decode;

import net.unit8.raoh.Err;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Issue;
import net.unit8.raoh.MessageKeys;
import net.unit8.raoh.MessageResolver;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static net.unit8.raoh.decode.ObjectDecoders.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the source-to-target conversions of the numeric decoders in {@link ObjectDecoders}.
 * The decoders accept a closed set of JDK number types and convert them by Raoh's rules instead
 * of the input's own {@code intValue()} / {@code toString()} (#152). Post-decode constraints such
 * as {@code min()} are covered by the tests under {@code decode.builtin}.
 */
class ObjectDecoderNumericTest {

    /** A {@link Number} whose conversions are whatever its author chose. */
    static final class CustomNumber extends Number {
        @Override public int intValue() { return 42; }
        @Override public long longValue() { return 42L; }
        @Override public float floatValue() { return 42f; }
        @Override public double doubleValue() { return 42d; }
        @Override public String toString() { return "42"; }
    }

    /** A {@link BigInteger} subclass could override the methods the decoders call on it. */
    static final class CustomBigInteger extends BigInteger {
        CustomBigInteger(String val) { super(val); }
        @Override public int intValue() { return 42; }
    }

    /** A {@link BigDecimal} subclass could override the methods the decoders call on it. */
    static final class CustomBigDecimal extends BigDecimal {
        CustomBigDecimal(String val) { super(val); }
        @Override public int intValueExact() { return 42; }
    }

    // --- int_() ---

    @Test
    void intAcceptsIntegralBoxes() {
        assertEquals(7, ok(int_().decode((byte) 7, Path.ROOT)));
        assertEquals(7, ok(int_().decode((short) 7, Path.ROOT)));
        assertEquals(7, ok(int_().decode(7, Path.ROOT)));
    }

    @Test
    void intAcceptsLongWithinRange() {
        assertEquals(Integer.MAX_VALUE, ok(int_().decode((long) Integer.MAX_VALUE, Path.ROOT)));
        assertEquals(Integer.MIN_VALUE, ok(int_().decode((long) Integer.MIN_VALUE, Path.ROOT)));
    }

    @Test
    void intRejectsLongOutsideRangeInsteadOfWrapping() {
        // Number#intValue() wrapped 5_000_000_000L to 705032704.
        assertOutsideRange(int_().decode(5_000_000_000L, Path.ROOT), "integer");
        assertOutsideRange(int_().decode(Integer.MAX_VALUE + 1L, Path.ROOT), "integer");
        assertOutsideRange(int_().decode(Integer.MIN_VALUE - 1L, Path.ROOT), "integer");
    }

    @Test
    void intAcceptsBigIntegerWithinRange() {
        assertEquals(Integer.MAX_VALUE, ok(int_().decode(BigInteger.valueOf(Integer.MAX_VALUE), Path.ROOT)));
        assertEquals(Integer.MIN_VALUE, ok(int_().decode(BigInteger.valueOf(Integer.MIN_VALUE), Path.ROOT)));
    }

    @Test
    void intRejectsBigIntegerOutsideRange() {
        assertOutsideRange(int_().decode(BigInteger.valueOf(Integer.MAX_VALUE + 1L), Path.ROOT), "integer");
        assertOutsideRange(int_().decode(BigInteger.valueOf(Integer.MIN_VALUE - 1L), Path.ROOT), "integer");
    }

    @Test
    void intAcceptsIntegralBigDecimal() {
        assertEquals(5, ok(int_().decode(new BigDecimal("5"), Path.ROOT)));
        assertEquals(5, ok(int_().decode(new BigDecimal("5.00"), Path.ROOT)));
        assertEquals(500, ok(int_().decode(new BigDecimal("5E+2"), Path.ROOT)));
        assertEquals(0, ok(int_().decode(new BigDecimal("0.000"), Path.ROOT)));
    }

    @Test
    void intRejectsBigDecimalWithFraction() {
        assertTypeMismatch(int_().decode(new BigDecimal("1.5"), Path.ROOT), "integer");
    }

    @Test
    void intRejectsIntegralBigDecimalOutsideRange() {
        assertOutsideRange(int_().decode(new BigDecimal("5000000000"), Path.ROOT), "integer");
        // A huge exponent is rejected without expanding the value to its digits.
        assertOutsideRange(int_().decode(new BigDecimal("1E+999999999"), Path.ROOT), "integer");
    }

    @Test
    void intRejectsFloatingPointEvenWhenIntegral() {
        // Number#intValue() truncated 1.9 to 1.
        assertTypeMismatch(int_().decode(1.9d, Path.ROOT), "integer");
        assertTypeMismatch(int_().decode(1.0d, Path.ROOT), "integer");
        assertTypeMismatch(int_().decode(1.0f, Path.ROOT), "integer");
    }

    @Test
    void intRejectsOtherNumberTypes() {
        assertTypeMismatch(int_().decode(new AtomicInteger(1), Path.ROOT), "integer");
        assertTypeMismatch(int_().decode(new CustomNumber(), Path.ROOT), "integer");
        assertTypeMismatch(int_().decode(new CustomBigInteger("1"), Path.ROOT), "integer");
        assertTypeMismatch(int_().decode(new CustomBigDecimal("1"), Path.ROOT), "integer");
    }

    // --- long_() ---

    @Test
    void longAcceptsIntegralBoxes() {
        assertEquals(7L, ok(long_().decode((byte) 7, Path.ROOT)));
        assertEquals(7L, ok(long_().decode((short) 7, Path.ROOT)));
        assertEquals(7L, ok(long_().decode(7, Path.ROOT)));
        assertEquals(Long.MAX_VALUE, ok(long_().decode(Long.MAX_VALUE, Path.ROOT)));
    }

    @Test
    void longAcceptsBigIntegerWithinRange() {
        assertEquals(Long.MAX_VALUE, ok(long_().decode(BigInteger.valueOf(Long.MAX_VALUE), Path.ROOT)));
        assertEquals(Long.MIN_VALUE, ok(long_().decode(BigInteger.valueOf(Long.MIN_VALUE), Path.ROOT)));
    }

    @Test
    void longRejectsBigIntegerOutsideRangeInsteadOfWrapping() {
        // Number#longValue() wrapped this to 7766279631452241919.
        assertOutsideRange(long_().decode(new BigInteger("99999999999999999999"), Path.ROOT), "long");
        assertOutsideRange(long_().decode(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), Path.ROOT), "long");
        assertOutsideRange(long_().decode(BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE), Path.ROOT), "long");
    }

    @Test
    void longAcceptsIntegralBigDecimalAndRejectsFraction() {
        assertEquals(5_000_000_000L, ok(long_().decode(new BigDecimal("5000000000.00"), Path.ROOT)));
        assertTypeMismatch(long_().decode(new BigDecimal("0.5"), Path.ROOT), "long");
        assertOutsideRange(long_().decode(new BigDecimal("1E+19"), Path.ROOT), "long");
    }

    @Test
    void longRejectsFloatingPointAndOtherNumberTypes() {
        assertTypeMismatch(long_().decode(1.0d, Path.ROOT), "long");
        assertTypeMismatch(long_().decode(new AtomicLong(1), Path.ROOT), "long");
        assertTypeMismatch(long_().decode(new CustomNumber(), Path.ROOT), "long");
        assertTypeMismatch(long_().decode(new CustomBigInteger("1"), Path.ROOT), "long");
    }

    // --- double_() ---

    @Test
    void doubleAcceptsEveryJdkNumberType() {
        assertEquals(1.5d, ok(double_().decode(1.5d, Path.ROOT)));
        assertEquals(1.5d, ok(double_().decode(1.5f, Path.ROOT)));
        assertEquals(7d, ok(double_().decode((byte) 7, Path.ROOT)));
        assertEquals(7d, ok(double_().decode((short) 7, Path.ROOT)));
        assertEquals(7d, ok(double_().decode(7, Path.ROOT)));
        assertEquals(7d, ok(double_().decode(7L, Path.ROOT)));
        assertEquals(7d, ok(double_().decode(BigInteger.valueOf(7), Path.ROOT)));
        assertEquals(0.1d, ok(double_().decode(new BigDecimal("0.1"), Path.ROOT)));
    }

    @Test
    void doubleRoundsToNearest() {
        // double_() accepts IEEE 754 rounding by design; these are not precision bugs.
        assertEquals(9007199254740992d, ok(double_().decode(9007199254740993L, Path.ROOT)));
        assertEquals(9007199254740992d, ok(double_().decode(new BigInteger("9007199254740993"), Path.ROOT)));
    }

    @Test
    void doubleRejectsValuesBeyondRange() {
        assertOutsideRange(double_().decode(BigInteger.TEN.pow(400), Path.ROOT), "double");
        assertOutsideRange(double_().decode(new BigDecimal("1E+400"), Path.ROOT), "double");
        assertOutsideRange(double_().decode(Double.POSITIVE_INFINITY, Path.ROOT), "double");
    }

    @Test
    void doubleLetsNaNThrough() {
        assertTrue(Double.isNaN(ok(double_().decode(Double.NaN, Path.ROOT))));
    }

    @Test
    void doubleRejectsOtherNumberTypes() {
        assertTypeMismatch(double_().decode(new AtomicLong(1), Path.ROOT), "double");
        assertTypeMismatch(double_().decode(new CustomNumber(), Path.ROOT), "double");
        assertTypeMismatch(double_().decode(new CustomBigDecimal("1"), Path.ROOT), "double");
    }

    // --- float_() ---

    @Test
    void floatAcceptsEveryJdkNumberType() {
        assertEquals(1.5f, ok(float_().decode(1.5f, Path.ROOT)));
        assertEquals(1.5f, ok(float_().decode(1.5d, Path.ROOT)));
        assertEquals(7f, ok(float_().decode(7, Path.ROOT)));
        assertEquals(7f, ok(float_().decode(7L, Path.ROOT)));
        assertEquals(7f, ok(float_().decode(BigInteger.valueOf(7), Path.ROOT)));
        assertEquals(0.1f, ok(float_().decode(new BigDecimal("0.1"), Path.ROOT)));
    }

    @Test
    void floatRoundsToNearest() {
        // float_() accepts IEEE 754 rounding by design; these are not precision bugs.
        assertEquals(16777216f, ok(float_().decode(16777217, Path.ROOT)));
        assertEquals(0.1f, ok(float_().decode(0.1d, Path.ROOT)));
    }

    @Test
    void floatRejectsValuesBeyondRange() {
        assertOutsideRange(float_().decode(1e40d, Path.ROOT), "float");
        assertOutsideRange(float_().decode(new BigDecimal("1E+40"), Path.ROOT), "float");
        assertOutsideRange(float_().decode(Float.NEGATIVE_INFINITY, Path.ROOT), "float");
    }

    @Test
    void floatRejectsOtherNumberTypes() {
        assertTypeMismatch(float_().decode(new AtomicInteger(1), Path.ROOT), "float");
        assertTypeMismatch(float_().decode(new CustomNumber(), Path.ROOT), "float");
    }

    // --- decimal() ---

    @Test
    void decimalConvertsIntegersExactly() {
        assertEquals(new BigDecimal("7"), ok(decimal().decode((byte) 7, Path.ROOT)));
        assertEquals(new BigDecimal("7"), ok(decimal().decode((short) 7, Path.ROOT)));
        assertEquals(new BigDecimal("7"), ok(decimal().decode(7, Path.ROOT)));
        assertEquals(new BigDecimal("9007199254740993"), ok(decimal().decode(9007199254740993L, Path.ROOT)));
        assertEquals(new BigDecimal("99999999999999999999"),
                ok(decimal().decode(new BigInteger("99999999999999999999"), Path.ROOT)));
    }

    @Test
    void decimalKeepsBigDecimalAsIs() {
        var bd = new BigDecimal("1.230");
        assertSame(bd, ok(decimal().decode(bd, Path.ROOT)));
    }

    @Test
    void decimalConvertsFloatingPointToItsShortestDecimal() {
        // Not new BigDecimal(0.1d), which is 0.1000000000000000055511151231257827...
        assertEquals(new BigDecimal("0.1"), ok(decimal().decode(0.1d, Path.ROOT)));
        assertEquals(new BigDecimal("0.1"), ok(decimal().decode(0.1f, Path.ROOT)));
        assertEquals(new BigDecimal("1.0"), ok(decimal().decode(1.0d, Path.ROOT)));
    }

    @Test
    void decimalRejectsNonFiniteInsteadOfThrowing() {
        // new BigDecimal(Double.toString(NaN)) threw NumberFormatException out of decode().
        assertTypeMismatch(decimal().decode(Double.NaN, Path.ROOT), "number");
        assertTypeMismatch(decimal().decode(Double.POSITIVE_INFINITY, Path.ROOT), "number");
        assertTypeMismatch(decimal().decode(Float.NaN, Path.ROOT), "number");
    }

    @Test
    void decimalRejectsOtherNumberTypes() {
        assertTypeMismatch(decimal().decode(new AtomicInteger(1), Path.ROOT), "number");
        assertTypeMismatch(decimal().decode(new CustomNumber(), Path.ROOT), "number");
        assertTypeMismatch(decimal().decode(new CustomBigInteger("1"), Path.ROOT), "number");
        assertTypeMismatch(decimal().decode(new CustomBigDecimal("1"), Path.ROOT), "number");
    }

    // --- messages ---

    @Test
    void outsideRangeMessageNamesTheTarget() {
        var issue = issue(int_().decode(5_000_000_000L, Path.ROOT));
        assertEquals("value is outside the integer range", issue.message());
        assertEquals("value is outside the integer range", MessageResolver.DEFAULT.resolve(issue));
    }

    // --- helpers ---

    private static <T> T ok(Result<T> result) {
        return switch (result) {
            case Ok<T>(var v) -> v;
            case Err<T>(var issues) -> fail("Expected Ok but got: " + issues);
        };
    }

    private static Issue issue(Result<?> result) {
        return switch (result) {
            case Ok<?> ok -> fail("Expected Err but got: " + ok);
            case Err<?>(var issues) -> {
                assertEquals(1, issues.asList().size());
                yield issues.asList().getFirst();
            }
        };
    }

    private static void assertOutsideRange(Result<?> result, String expected) {
        var issue = issue(result);
        assertEquals(ErrorCodes.TYPE_MISMATCH, issue.code());
        assertEquals(MessageKeys.TYPE_MISMATCH_NUMERIC_RANGE, issue.messageKey());
        assertEquals(expected, issue.meta().get("expected"));
    }

    private static void assertTypeMismatch(Result<?> result, String expected) {
        var issue = issue(result);
        assertEquals(ErrorCodes.TYPE_MISMATCH, issue.code());
        assertEquals(ErrorCodes.TYPE_MISMATCH, issue.messageKey());
        assertEquals(expected, issue.meta().get("expected"));
    }
}
