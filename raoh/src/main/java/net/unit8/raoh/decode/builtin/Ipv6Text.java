package net.unit8.raoh.decode.builtin;

import org.jspecify.annotations.Nullable;

/**
 * Reads the RFC 4291 (section 2.2) text form of an IPv6 address.
 *
 * <p>The grammar is the {@code IPv6address} rule of RFC 3986 (section 3.2.2), which writes down
 * the three forms of RFC 4291: eight groups of 1 to 4 hexadecimal digits separated by colons, the
 * same with one run of zero groups replaced by {@code ::}, and either of those with the last two
 * groups written as a dotted-quad IPv4 address. The IPv4 part follows
 * {@link LexicalRules#isIpv4(String)}, so {@code ::01.2.3.4} is rejected as {@code 01.2.3.4} is.
 * No JDK parser reads the text; {@code Inet6Address.ofLiteral} also accepts a group of five digits
 * such as {@code ::00001}.
 */
final class Ipv6Text {

    private static final int GROUPS = 8;
    private static final int MAX_GROUP_DIGITS = 4;

    private Ipv6Text() {
    }

    /**
     * Reads an IPv6 address without a zone ID.
     *
     * @param text the text to read
     * @return the 16 address bytes, or {@code null} if the text is not an IPv6 address
     */
    static byte @Nullable [] parse(String text) {
        int compressed = text.indexOf("::");
        if (compressed >= 0 && text.indexOf("::", compressed + 1) >= 0) {
            return null;
        }
        String[] head;
        String[] tail;
        if (compressed < 0) {
            head = pieces(text);
            tail = new String[0];
        } else {
            head = pieces(text.substring(0, compressed));
            tail = pieces(text.substring(compressed + 2));
        }
        if (head == null || tail == null) {
            return null;
        }
        var groups = new int[GROUPS];
        // An IPv4 part ends the address text, so before "::" there is none.
        int headCount = groups(head, groups, compressed < 0);
        if (headCount < 0) {
            return null;
        }
        if (compressed < 0) {
            return headCount == GROUPS ? bytes(groups) : null;
        }
        var tailGroups = new int[GROUPS];
        int tailCount = groups(tail, tailGroups, true);
        // "::" stands for one or more zero groups (RFC 4291 section 2.2).
        if (tailCount < 0 || headCount + tailCount > GROUPS - 1) {
            return null;
        }
        System.arraycopy(tailGroups, 0, groups, GROUPS - tailCount, tailCount);
        return bytes(groups);
    }

    // Splits on ':'; an empty text has no pieces, and an empty piece (":1", "1:") is an error.
    private static String @Nullable [] pieces(String text) {
        if (text.isEmpty()) {
            return new String[0];
        }
        var pieces = text.split(":", -1);
        for (var piece : pieces) {
            if (piece.isEmpty()) {
                return null;
            }
        }
        return pieces;
    }

    // Writes the 16-bit groups of the pieces into groups and returns how many it wrote, or -1 if a
    // piece is malformed. Only the last piece of the address text may be IPv4.
    private static int groups(String[] pieces, int[] groups, boolean endsAddress) {
        int count = 0;
        for (int i = 0; i < pieces.length; i++) {
            var piece = pieces[i];
            if (piece.indexOf('.') >= 0) {
                if (!endsAddress || i != pieces.length - 1 || count + 2 > GROUPS
                        || !LexicalRules.isIpv4(piece)) {
                    return -1;
                }
                var octets = piece.split("\\.");
                groups[count++] = Integer.parseInt(octets[0]) << 8 | Integer.parseInt(octets[1]);
                groups[count++] = Integer.parseInt(octets[2]) << 8 | Integer.parseInt(octets[3]);
                continue;
            }
            if (count >= GROUPS || piece.length() > MAX_GROUP_DIGITS) {
                return -1;
            }
            int group = 0;
            for (int j = 0; j < piece.length(); j++) {
                int digit = hexDigit(piece.charAt(j));
                if (digit < 0) {
                    return -1;
                }
                group = group << 4 | digit;
            }
            groups[count++] = group;
        }
        return count;
    }

    // ASCII hexadecimal only; Character.digit would also take full-width and other digits.
    private static int hexDigit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }

    private static byte[] bytes(int[] groups) {
        var bytes = new byte[GROUPS * 2];
        for (int i = 0; i < GROUPS; i++) {
            bytes[2 * i] = (byte) (groups[i] >> 8);
            bytes[2 * i + 1] = (byte) groups[i];
        }
        return bytes;
    }
}
