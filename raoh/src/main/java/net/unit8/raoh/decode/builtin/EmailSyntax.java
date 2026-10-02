package net.unit8.raoh.decode.builtin;

/**
 * The text form {@code email()} accepts: an ASCII lexical profile of RFC 5321's {@code Mailbox},
 * {@code Dot-string "@" Domain}.
 *
 * <p>A quoted local part, an address literal and SMTPUTF8 are outside the profile. Only the syntax is
 * checked, so a single-label or all-digit domain ({@code a@localhost}, {@code a@123}) is accepted.
 * Every character the profile accepts is ASCII, so once a character outside it is refused, a count
 * of {@code char}s is a count of octets. The check runs in time linear in the length of the text,
 * without backtracking, and builds nothing.
 */
final class EmailSyntax {

    /** RFC 5321 section 4.5.3.1.1. */
    private static final int MAX_LOCAL_PART = 64;

    /** RFC 1035 section 2.3.4. */
    private static final int MAX_LABEL = 63;

    /**
     * RFC 5321 section 4.5.3.1.3 limits a path, the mailbox enclosed in {@code <} and {@code >}, to
     * 256 octets, which leaves 254 for the mailbox. The domain's own limit of 255 octets is never
     * reached inside it.
     */
    private static final int MAX_MAILBOX = 254;

    private EmailSyntax() {
    }

    /**
     * Returns whether the value is a mailbox of the profile: a local part of at most 64 octets made
     * of RFC 5322 {@code atext} atoms joined by single dots, an {@code @}, and a domain of labels
     * joined by single dots, each of at most 63 octets, starting and ending with a letter or digit
     * and holding letters, digits and hyphens; at most 254 octets in all.
     *
     * @param value the text to check
     * @return {@code true} if the value is accepted
     */
    static boolean isMailbox(String value) {
        int length = value.length();
        if (length > MAX_MAILBOX) {
            return false;
        }
        int at = value.indexOf('@');
        if (at < 0 || at > MAX_LOCAL_PART) {
            return false;
        }
        return isDotString(value, 0, at) && isDomain(value, at + 1, length);
    }

    /** RFC 5321 {@code Dot-string}: {@code Atom *("." Atom)}, an atom being one or more {@code atext}. */
    private static boolean isDotString(String value, int from, int to) {
        int atom = 0;
        for (int pos = from; pos < to; pos++) {
            char c = value.charAt(pos);
            if (c == '.') {
                if (atom == 0) {
                    return false;
                }
                atom = 0;
            } else if (isAtext(c)) {
                atom++;
            } else {
                return false;
            }
        }
        return atom > 0;
    }

    /**
     * RFC 5321 {@code Domain}: {@code sub-domain *("." sub-domain)}, a sub-domain being a letter or
     * digit, optionally followed by letters, digits and hyphens that end in a letter or digit.
     */
    private static boolean isDomain(String value, int from, int to) {
        int label = 0;
        char previous = '.';
        for (int pos = from; pos < to; pos++) {
            char c = value.charAt(pos);
            if (c == '.') {
                if (label == 0 || previous == '-') {
                    return false;
                }
                label = 0;
            } else if (isLetterOrDigit(c) || (c == '-' && label > 0)) {
                if (++label > MAX_LABEL) {
                    return false;
                }
            } else {
                return false;
            }
            previous = c;
        }
        return label > 0 && previous != '-';
    }

    /** RFC 5322 {@code atext}: a letter, a digit or one of {@code !#$%&'*+-/=?^_`{|}~}. */
    private static boolean isAtext(char c) {
        return isLetterOrDigit(c) || "!#$%&'*+-/=?^_`{|}~".indexOf(c) >= 0;
    }

    private static boolean isLetterOrDigit(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }
}
