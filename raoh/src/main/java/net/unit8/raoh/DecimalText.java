package net.unit8.raoh;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Decimal text as Raoh reads it: the number it writes, with the scale it is written with.
 *
 * <p>The text is an optional {@code +} or {@code -}, ASCII digits with an optional decimal point
 * that has a digit on at least one side ({@code 12}, {@code 12.5}, {@code 5.}, {@code .5}), and an
 * optional exponent: {@code e} or {@code E} followed by an optionally signed integer
 * ({@code 1e3}, {@code 2.5E-4}). Its value is the {@link BigDecimal} that
 * {@link BigDecimal#BigDecimal(String)} gives for the same text, the scale included, so
 * {@code 1.50} reads as {@code 1.50} and {@code 1e3} as {@code 1E+3}.
 *
 * <p>Not {@code new BigDecimal(String)} itself, which takes time that grows with the square of the
 * number of digits: it folds the digits into the magnitude a group at a time, each fold going over
 * the whole magnitude. Here the digits are split in halves and joined as
 * {@code high × 10^k + low}, with the powers of ten worked out once, so the time grows about as a
 * large multiplication does. It still grows faster than linearly; a caller reading text from an
 * untrusted source bounds its length first.
 */
public final class DecimalText {

    /**
     * Digits read by {@link BigInteger#BigInteger(String)} directly, below which splitting costs
     * more than it saves.
     */
    private static final int DIRECT = 1024;

    private DecimalText() {
    }

    /**
     * Reads {@code text} as a decimal number.
     *
     * @param text the text to read
     * @return the number, with the scale it is written with, or {@code null} if the text is not
     *         in the form or its exponent puts the scale outside the {@code int} range
     */
    public static @Nullable BigDecimal read(String text) {
        int at = 0;
        int length = text.length();
        boolean negative = false;
        if (at < length && (text.charAt(at) == '+' || text.charAt(at) == '-')) {
            negative = text.charAt(at) == '-';
            at++;
        }
        int wholeFrom = at;
        at = digits(text, at);
        int wholeTo = at;
        int fractionFrom = at;
        int fractionTo = at;
        if (at < length && text.charAt(at) == '.') {
            fractionFrom = at + 1;
            at = digits(text, fractionFrom);
            fractionTo = at;
        }
        if (wholeTo == wholeFrom && fractionTo == fractionFrom) {
            // No digit on either side of the point, or no digit at all.
            return null;
        }
        long exponent = 0;
        if (at < length && (text.charAt(at) == 'e' || text.charAt(at) == 'E')) {
            at++;
            boolean exponentNegative = false;
            if (at < length && (text.charAt(at) == '+' || text.charAt(at) == '-')) {
                exponentNegative = text.charAt(at) == '-';
                at++;
            }
            int exponentFrom = at;
            // Held past the int range and no further: anything beyond it gives no scale anyway.
            for (; at < length && isDigit(text.charAt(at)); at++) {
                exponent = Math.min(exponent * 10 + (text.charAt(at) - '0'), 1L << 40);
            }
            if (at == exponentFrom) {
                return null;
            }
            if (exponentNegative) {
                exponent = -exponent;
            }
        }
        if (at != length) {
            return null;
        }
        long scale = (long) (fractionTo - fractionFrom) - exponent;
        if (scale < Integer.MIN_VALUE || scale > Integer.MAX_VALUE) {
            return null;
        }
        String coefficient = text.substring(wholeFrom, wholeTo) + text.substring(fractionFrom, fractionTo);
        // 10^(DIRECT · 2^j) at index j, worked out the first time a split asks for it.
        BigInteger unscaled = magnitude(coefficient, 0, coefficient.length(), new BigInteger[32]);
        return new BigDecimal(negative ? unscaled.negate() : unscaled, (int) scale);
    }

    private static int digits(String text, int from) {
        int at = from;
        while (at < text.length() && isDigit(text.charAt(at))) {
            at++;
        }
        return at;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /**
     * The number the ASCII digits {@code digits[from, to)} write.
     *
     * <p>The low part is a power of two times {@link #DIRECT} digits long and at least half of the
     * whole, so every split of every part asks for one of a few powers of ten, each worked out once.
     */
    private static BigInteger magnitude(String digits, int from, int to, @Nullable BigInteger[] powers) {
        int count = to - from;
        if (count <= DIRECT) {
            return count == 0 ? BigInteger.ZERO : new BigInteger(digits.substring(from, to));
        }
        int low = DIRECT;
        int j = 0;
        while (low < count - low) {
            low *= 2;
            j++;
        }
        BigInteger high = magnitude(digits, from, to - low, powers);
        BigInteger rest = magnitude(digits, to - low, to, powers);
        BigInteger power = powers[j];
        if (power == null) {
            power = BigInteger.TEN.pow(low);
            powers[j] = power;
        }
        return high.multiply(power).add(rest);
    }
}
