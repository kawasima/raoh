package net.unit8.raoh.decode.builtin;

import java.util.regex.Pattern;

/**
 * The lexical languages Raoh accepts for its string conversions.
 *
 * <p>Each rule decides on its own whether a string is accepted. The JDK parser that runs afterwards
 * only builds the value and reports what the target type cannot represent (an {@code int}
 * overflow, for example); its own leniency, such as reading non-ASCII digits, never widens the
 * accepted language. All digit classes are ASCII only.
 */
final class LexicalRules {

    // RFC 9562, Section 4: 8-4-4-4-12 hexadecimal digits, upper or lower case.
    private static final Pattern UUID = Pattern.compile(
            "[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}");
    private static final Pattern INTEGER = Pattern.compile("[+-]?[0-9]+");
    // RFC 3986 IPv4address: four dec-octets 0-255, no leading zeros.
    private static final Pattern IPV4 = Pattern.compile(
            "((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9][0-9]|[0-9])\\.){3}(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9][0-9]|[0-9])");
    private static final int MAX_IPV4_LENGTH = 15;
    private static final Pattern DECIMAL = Pattern.compile(
            "[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");

    private LexicalRules() {
    }

    /**
     * Returns whether the value is a UUID in the RFC 9562 text form, {@code 8-4-4-4-12}
     * hexadecimal digits in any case.
     *
     * @param value the text to check
     * @return {@code true} if the value is accepted
     */
    static boolean isUuid(String value) {
        return UUID.matcher(value).matches();
    }

    /**
     * Returns whether the value is a decimal integer: an optional {@code +} or {@code -} followed
     * by one or more ASCII digits. Leading zeros are allowed.
     *
     * @param value the text to check
     * @return {@code true} if the value is accepted
     */
    static boolean isInteger(String value) {
        return INTEGER.matcher(value).matches();
    }

    /**
     * Returns whether the value is an IPv4 address in dotted-quad form: four decimal numbers from
     * 0 to 255 without leading zeros, separated by {@code .} (RFC 3986 {@code IPv4address}).
     *
     * @param value the text to check
     * @return {@code true} if the value is accepted
     */
    static boolean isIpv4(String value) {
        return value.length() <= MAX_IPV4_LENGTH && IPV4.matcher(value).matches();
    }

    /**
     * Lower-cases the ASCII letters {@code A}–{@code Z} and leaves every other character as it
     * is. Case-insensitive matching in Raoh folds only these letters: Unicode case mapping would
     * also turn the KELVIN SIGN (U+212A) into {@code k}, so {@code String#toLowerCase} and
     * {@code String#equalsIgnoreCase} would accept text outside the documented language.
     *
     * @param value the text to fold
     * @return the value with ASCII upper-case letters lower-cased
     */
    static String asciiLowerCase(String value) {
        var chars = value.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] >= 'A' && chars[i] <= 'Z') {
                chars[i] = (char) (chars[i] + ('a' - 'A'));
            }
        }
        return new String(chars);
    }

    /**
     * Returns whether the value is a decimal number: an optional sign, ASCII digits with an
     * optional {@code .} (at least one digit on either side of it), and an optional exponent
     * {@code e} or {@code E} with an optionally signed integer.
     *
     * @param value the text to check
     * @return {@code true} if the value is accepted
     */
    static boolean isDecimal(String value) {
        return DECIMAL.matcher(value).matches();
    }
}
