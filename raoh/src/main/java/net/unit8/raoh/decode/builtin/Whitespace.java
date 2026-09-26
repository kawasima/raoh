package net.unit8.raoh.decode.builtin;

/**
 * What counts as whitespace for {@link StringDecoder#nonBlank()} and {@link StringDecoder#trim()}.
 *
 * <p>Whitespace is the set of code points with the Unicode {@code White_Space} property, as of
 * Unicode 18.0.0. The set is written out below and does not follow the Unicode data of the running
 * JDK, so it does not change with the JDK version; taking a later Unicode version is a
 * compatibility change made here on purpose. It differs from {@link String#isBlank()} (NO-BREAK
 * SPACE is whitespace here, U+001C to U+001F are not) and from {@link String#trim()} (which
 * strips every code point up to U+0020).
 *
 * <p>Both operations are derived from {@link #isWhitespace(int)}, so a string is blank exactly
 * when trimming it leaves nothing.
 */
final class Whitespace {

    private Whitespace() {
    }

    /**
     * Returns whether the code point has the Unicode {@code White_Space} property.
     *
     * @param cp the code point to check
     * @return {@code true} if the code point is whitespace
     */
    static boolean isWhitespace(int cp) {
        return (cp >= 0x0009 && cp <= 0x000D)
                || cp == 0x0020
                || cp == 0x0085
                || cp == 0x00A0
                || cp == 0x1680
                || (cp >= 0x2000 && cp <= 0x200A)
                || cp == 0x2028
                || cp == 0x2029
                || cp == 0x202F
                || cp == 0x205F
                || cp == 0x3000;
    }

    /**
     * Returns whether the string is empty or consists only of whitespace.
     *
     * @param value the text to check
     * @return {@code true} if every code point of the value is whitespace
     */
    static boolean isBlank(String value) {
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            if (!isWhitespace(cp)) {
                return false;
            }
            i += Character.charCount(cp);
        }
        return true;
    }

    /**
     * Removes the leading and trailing whitespace.
     *
     * @param value the text to trim
     * @return the value without its whitespace prefix and suffix; the value itself if it has none
     */
    static String trim(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int cp = value.codePointAt(start);
            if (!isWhitespace(cp)) {
                break;
            }
            start += Character.charCount(cp);
        }
        while (start < end) {
            int cp = value.codePointBefore(end);
            if (!isWhitespace(cp)) {
                break;
            }
            end -= Character.charCount(cp);
        }
        if (start == 0 && end == value.length()) {
            return value;
        }
        return value.substring(start, end);
    }
}
