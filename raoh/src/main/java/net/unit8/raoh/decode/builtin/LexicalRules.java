package net.unit8.raoh.decode.builtin;

import java.util.regex.Pattern;

/**
 * The lexical languages Raoh accepts for its string conversions.
 *
 * <p>Decimal text has a reader of its own, {@link net.unit8.raoh.DecimalText}, which decides the form
 * and builds the value in one pass.
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
}
