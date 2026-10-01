package net.unit8.raoh.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecimalConversionTest {

    /** The form toDecimal() accepted before DecimalConversion, as the regular expression it was. */
    private static final Pattern FORM = Pattern.compile(
            "[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");

    /** {@code new BigDecimal(text)} where the form holds, or null where it throws. */
    private static BigDecimal expected(String text) {
        if (!FORM.matcher(text).matches()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The value, scale included, is the one {@code new BigDecimal(String)} gives, for text of every
     * shape the form has: signs, points on either side, long runs of digits, and exponents up to
     * and past where the scale leaves the int range.
     */
    @Test
    void readsWhatBigDecimalReads() {
        var random = new Random(161);
        var wrong = new ArrayList<String>();
        for (int i = 0; i < 50_000; i++) {
            var text = new StringBuilder();
            switch (random.nextInt(3)) {
                case 1 -> text.append('+');
                case 2 -> text.append('-');
                default -> { }
            }
            text.append(digits(random, random.nextInt(4) == 0 ? random.nextInt(3000) : random.nextInt(25)));
            if (random.nextBoolean()) {
                text.append('.').append(digits(random, random.nextInt(25)));
            }
            if (random.nextBoolean()) {
                text.append(random.nextBoolean() ? 'e' : 'E');
                switch (random.nextInt(3)) {
                    case 1 -> text.append('+');
                    case 2 -> text.append('-');
                    default -> { }
                }
                text.append(switch (random.nextInt(4)) {
                    case 0 -> String.valueOf(Integer.MAX_VALUE - random.nextInt(40));
                    case 1 -> String.valueOf(Integer.MAX_VALUE + 1L + random.nextInt(40));
                    case 2 -> "0000" + random.nextInt(100);
                    default -> String.valueOf(random.nextInt(1000));
                });
            }
            check(wrong, text.toString());
        }
        assertEquals(List.of(), wrong);
    }

    /** Text outside the form is refused exactly where the regular expression refused it. */
    @Test
    void refusesWhatTheFormRefuses() {
        var random = new Random(1610);
        var alphabet = "0123456789+-.eE x١";
        var wrong = new ArrayList<String>();
        for (int i = 0; i < 200_000; i++) {
            var text = new StringBuilder();
            for (int n = random.nextInt(9); n > 0; n--) {
                text.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            check(wrong, text.toString());
        }
        for (var text : List.of("", "+", "-", ".", "+.", "e3", ".e3", "1e", "1e+", "1..2", "1.2.3",
                "١٢", "1 ", " 1", "NaN", "Infinity", "0x10", "1_000")) {
            check(wrong, text);
        }
        assertEquals(List.of(), wrong);
    }

    /** Up to 18 digits the coefficient is built in a long; from 19 on, as a BigInteger. */
    @Test
    void readsBothSidesOfTheLongCoefficient() {
        var wrong = new ArrayList<String>();
        for (String text : List.of(
                "999999999999999999", "-999999999999999999", "9999999999999999999", "-9999999999999999999",
                "99999999.9999999999", "99999999.99999999999", "-.999999999999999999", ".9999999999999999999",
                "000000000000000000", "0000000000000000001", "123456789012345678e-5", "1234567890123456789E+5",
                "9223372036854775807", "-9223372036854775808", "1.", "-0.000000000000000001")) {
            check(wrong, text);
        }
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }

    /** A value conversion: the sign of a zero is not a BigDecimal's to keep. */
    @Test
    void aNegativeZeroGivesTheValueZero() {
        assertEquals(new BigDecimal("0"), DecimalConversion.toBigDecimal("-0"));
        assertEquals(new BigDecimal("0.0"), DecimalConversion.toBigDecimal("-0.0"));
        assertEquals(new BigDecimal("0E+7"), DecimalConversion.toBigDecimal("-0.000e10"));
    }

    @Test
    void anExponentThatPutsTheScaleOutsideTheIntRangeGivesNothing() {
        assertNull(DecimalConversion.toBigDecimal("1e2147483649"));
        assertNull(DecimalConversion.toBigDecimal("1e-2147483648"));
        assertNull(DecimalConversion.toBigDecimal("1.5e-2147483647"));
        assertNull(DecimalConversion.toBigDecimal("1e99999999999999999999"));
        // The int range takes both of its ends as a scale, Integer.MIN_VALUE included.
        assertEquals(new BigDecimal("1e2147483648"), DecimalConversion.toBigDecimal("1e2147483648"));
        assertEquals(new BigDecimal("1e-2147483647"), DecimalConversion.toBigDecimal("1e-2147483647"));
        assertEquals(new BigDecimal("0.1e2147483649"), DecimalConversion.toBigDecimal("0.1e2147483649"));
    }

    /**
     * Not a performance contract: a bound loose enough that only the quadratic reading it replaced
     * fails it. Four million digits took over three minutes that way, and about a second this way.
     */
    @Test
    void millionsOfDigitsAreNotReadInQuadraticTime() {
        var random = new Random(4);
        var text = "1" + digits(random, 3_999_999);
        var read = assertTimeoutPreemptively(Duration.ofSeconds(60), () -> DecimalConversion.toBigDecimal(text));
        assertEquals(4_000_000, read.precision(), "every digit is held");
        assertEquals(0, read.scale());
    }

    private static void check(List<String> wrong, String text) {
        var expected = expected(text);
        var actual = DecimalConversion.toBigDecimal(text);
        // equals, not compareTo: the scale is part of the value.
        if (expected == null ? actual != null : !expected.equals(actual)) {
            wrong.add('"' + (text.length() > 60 ? text.substring(0, 60) + "…" : text) + "\": expected "
                    + expected + ", read " + actual);
        }
    }

    private static String digits(Random random, int count) {
        var out = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            out.append((char) ('0' + random.nextInt(10)));
        }
        return out.toString();
    }
}
