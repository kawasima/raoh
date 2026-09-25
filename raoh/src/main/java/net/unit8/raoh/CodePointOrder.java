package net.unit8.raoh;

import java.util.Comparator;

/**
 * The order Raoh lists strings in, where an issue's metadata or message lists them: by Unicode
 * code point.
 *
 * <p>{@link String#compareTo} compares UTF-16 code units instead. The two orders agree except
 * where a character above U+FFFF meets one in U+E000–U+FFFF: a surrogate pair starts with a unit
 * in U+D800–U+DBFF, so {@code compareTo} puts {@code "😀"} (U+1F600) before {@code "Ａ"}
 * (U+FF21), and code point order puts it after. Code point order is what Raoh for Rust's
 * {@code str} uses, so both list the same {@code allowed} values in the same order.
 */
public final class CodePointOrder {

    private CodePointOrder() {}

    /** Compares two strings by code point. */
    public static final Comparator<String> COMPARATOR = CodePointOrder::compare;

    /**
     * Compares {@code a} and {@code b} code point by code point; a string that is a prefix of
     * the other comes first. An unpaired surrogate compares as the code point of its own value.
     *
     * @param a the first string
     * @param b the second string
     * @return a negative number, zero or a positive number as {@code a} comes before, with or
     *         after {@code b}
     */
    public static int compare(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            int ca = a.codePointAt(i);
            int cb = b.codePointAt(j);
            if (ca != cb) {
                return Integer.compare(ca, cb);
            }
            i += Character.charCount(ca);
            j += Character.charCount(cb);
        }
        return Boolean.compare(i < a.length(), j < b.length());
    }
}
