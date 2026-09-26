package net.unit8.raoh.decode.builtin;

/**
 * The text forms of IP addresses, shared by {@code ipv4()}, {@code ipv6()}, {@code ip()} and the
 * host of {@code uri()} / {@code url()}.
 *
 * <p>The rules are those of RFC 3986 section 3.2.2, {@code IPv4address} and {@code IPv6address},
 * which write down the RFC 4291 section 2.2 text forms. They cover the address only: a zone ID is
 * not part of either rule (RFC 9844), so {@code ipv6()} handles it on its own and a URI host never
 * has one. Each check runs in time linear in the length of the text, without backtracking, and builds nothing.
 */
final class IpSyntax {

    private IpSyntax() {
    }

    /**
     * Returns whether the value is an RFC 3986 {@code IPv4address}: four {@code dec-octet}s
     * separated by {@code .}, each 0 to 255 without leading zeros.
     *
     * @param value the text to check
     * @return {@code true} if the value is accepted
     */
    static boolean isIpv4(String value) {
        return isIpv4(value, 0, value.length());
    }

    /**
     * Returns whether {@code value[from, to)} is an RFC 3986 {@code IPv4address}.
     *
     * @param value the text containing the range
     * @param from the start of the range, inclusive
     * @param to the end of the range, exclusive
     * @return {@code true} if the range is accepted
     */
    static boolean isIpv4(String value, int from, int to) {
        int pos = from;
        for (int octet = 0; octet < 4; octet++) {
            if (octet > 0) {
                if (pos >= to || value.charAt(pos) != '.') {
                    return false;
                }
                pos++;
            }
            int start = pos;
            int number = 0;
            while (pos < to && pos - start < 3 && isDigit(value.charAt(pos))) {
                number = number * 10 + (value.charAt(pos) - '0');
                pos++;
            }
            int digits = pos - start;
            if (digits == 0 || number > 255 || (digits > 1 && value.charAt(start) == '0')) {
                return false;
            }
        }
        return pos == to;
    }

    /**
     * Returns whether the value is an RFC 3986 {@code IPv6address}: eight groups of one to four
     * hexadecimal digits separated by {@code :}, at most one {@code ::} standing for one or more
     * groups of zeros, and optionally an {@code IPv4address} in place of the last two groups.
     * Brackets and a zone ID are not part of it.
     *
     * @param value the text to check
     * @return {@code true} if the value is accepted
     */
    static boolean isIpv6(String value) {
        return isIpv6(value, 0, value.length());
    }

    /**
     * Returns whether {@code value[from, to)} is an RFC 3986 {@code IPv6address}.
     *
     * @param value the text containing the range
     * @param from the start of the range, inclusive
     * @param to the end of the range, exclusive
     * @return {@code true} if the range is accepted
     */
    static boolean isIpv6(String value, int from, int to) {
        int groups = 0;
        boolean compressed = false;
        int pos = from;
        if (startsWithDoubleColon(value, pos, to)) {
            compressed = true;
            pos += 2;
            if (pos == to) {
                return true;
            }
        }
        while (true) {
            int start = pos;
            while (pos < to && pos - start < 4 && isHexDigit(value.charAt(pos))) {
                pos++;
            }
            if (pos < to && value.charAt(pos) == '.') {
                // An IPv4address stands for the last two groups and ends the address.
                return (compressed ? groups + 2 <= 7 : groups + 2 == 8) && isIpv4(value, start, to);
            }
            if (pos == start) {
                return false;
            }
            groups++;
            if (pos == to) {
                return compressed ? groups <= 7 : groups == 8;
            }
            if (value.charAt(pos) != ':') {
                return false;
            }
            pos++;
            if (pos < to && value.charAt(pos) == ':') {
                if (compressed) {
                    return false;
                }
                compressed = true;
                pos++;
                if (pos == to) {
                    return groups <= 7;
                }
            }
            if (groups > 8) {
                return false;
            }
        }
    }

    /**
     * Returns the value of the first 16-bit group of an address that {@link #isIpv6(String)}
     * accepts: {@code 0} when the address starts with {@code ::}.
     *
     * @param address an address accepted by {@link #isIpv6(String)}
     * @return the first group, 0 to {@code 0xffff}
     */
    static int ipv6FirstGroup(String address) {
        int group = 0;
        for (int i = 0; i < address.length() && address.charAt(i) != ':'; i++) {
            char c = address.charAt(i);
            group = group * 16 + (isDigit(c) ? c - '0' : (c | 0x20) - 'a' + 10);
        }
        return group;
    }

    private static boolean startsWithDoubleColon(String value, int pos, int to) {
        return pos + 1 < to && value.charAt(pos) == ':' && value.charAt(pos + 1) == ':';
    }

    static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    static boolean isHexDigit(char c) {
        return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
