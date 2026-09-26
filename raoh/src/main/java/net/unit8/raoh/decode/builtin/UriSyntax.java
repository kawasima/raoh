package net.unit8.raoh.decode.builtin;

import org.jspecify.annotations.Nullable;

/**
 * The RFC 3986 {@code URI} rule, read by {@code uri()} and {@code url()}.
 *
 * <p>{@link #parse(String)} reads the text once, left to right, without backtracking, and decides
 * acceptance on its own. It accepts the {@code URI} rule of RFC 3986 section 3, which requires a
 * scheme, so a relative reference ({@code foo/bar}, {@code #top}) is not a URI. The host is read by
 * the section 3.2.2 {@code host} rule, whose {@code IPv6address} is the one in {@link IpSyntax}; the
 * zone ID that RFC 6874 added to it was removed again by RFC 9844.
 *
 * <p>Whether {@link java.net.URI} can hold the accepted text is a separate question, answered by
 * {@link Parsed#representableAsJavaUri()}.
 */
final class UriSyntax {

    /**
     * The components of an accepted URI, kept as offsets into the text so that reading the result
     * copies nothing.
     *
     * @param value the accepted text
     * @param schemeEnd the index of the {@code :} after the scheme
     * @param authorityStart the index after the {@code //}, or {@code -1} if there is no authority
     * @param hostStart the start of the host, brackets included for an {@code IP-literal}; only
     *                  meaningful with an authority
     * @param hostEnd the end of the host, exclusive
     * @param authorityEnd the end of the authority, exclusive; the path starts here
     * @param hierEnd the end of the path, exclusive; a {@code ?} or {@code #} or the end of the
     *                text follows
     * @param hasQuery whether a query follows the path
     * @param hasFragment whether a fragment follows
     */
    record Parsed(String value, int schemeEnd, int authorityStart, int hostStart, int hostEnd,
                  int authorityEnd, int hierEnd, boolean hasQuery, boolean hasFragment) {

        /**
         * Returns whether the scheme equals the given lower-case ASCII text, ignoring case.
         *
         * @param expected the scheme to compare with
         * @return {@code true} if it is equal
         */
        boolean schemeIs(String expected) {
            return value.regionMatches(true, 0, expected, 0, schemeEnd) && schemeEnd == expected.length();
        }

        /**
         * Returns whether the URI has an authority and its host is not empty.
         *
         * @return {@code true} if there is a non-empty host
         */
        boolean hasHost() {
            return authorityStart >= 0 && hostStart < hostEnd;
        }

        /**
         * Returns whether {@link java.net.URI} can be built from the text. It follows RFC 2396 and
         * RFC 2732 and cannot hold four kinds of RFC 3986 URI: an empty scheme-specific part
         * ({@code a:}, {@code a:#f}), an empty authority followed by nothing ({@code a://}), an
         * {@code IPvFuture} host ({@code http://[v1.abc]/}), and an IPv6 host with a port above
         * {@link Integer#MAX_VALUE} ({@code http://[::1]:2147483648/}). With any other host, a port
         * that does not fit an {@code int} makes {@code java.net.URI} read the authority as
         * registry-based instead, which it cannot do for a bracketed host.
         *
         * @return {@code true} if the text can be held by {@link java.net.URI}
         */
        boolean representableAsJavaUri() {
            boolean pathEmpty = authorityEnd == hierEnd;
            if (authorityStart < 0) {
                return schemeEnd + 1 < hierEnd || hasQuery;
            }
            if (authorityStart == authorityEnd && pathEmpty && !hasQuery && !hasFragment) {
                return false;
            }
            // A bracket starts an IP-literal, and an IPvFuture inside it starts with "v".
            if (hostStart >= hostEnd || value.charAt(hostStart) != '[') {
                return true;
            }
            if ((value.charAt(hostStart + 1) | 0x20) == 'v') {
                return false;
            }
            // hostEnd is the "]"'s successor; a port, when there is one, follows a ":".
            return hostEnd == authorityEnd || fitsInt(hostEnd + 1, authorityEnd);
        }

        // Whether the decimal digits in value[from, to) denote a value no greater than
        // Integer.MAX_VALUE.
        private boolean fitsInt(int from, int to) {
            int start = from;
            while (start < to - 1 && value.charAt(start) == '0') {
                start++;
            }
            int length = to - start;
            return length < 10 || (length == 10 && compare(start) <= 0);
        }

        // Compares the ten digits at start with 2147483647.
        private int compare(int start) {
            for (int i = 0; i < 10; i++) {
                int diff = value.charAt(start + i) - "2147483647".charAt(i);
                if (diff != 0) {
                    return diff;
                }
            }
            return 0;
        }
    }

    private UriSyntax() {
    }

    /**
     * Parses the value by the RFC 3986 {@code URI} rule.
     *
     * @param value the text to parse
     * @return the components, or {@code null} if the value is not a URI
     */
    static @Nullable Parsed parse(String value) {
        int length = value.length();
        int schemeEnd = scheme(value);
        if (schemeEnd < 0) {
            return null;
        }
        int hierStart = schemeEnd + 1;
        int hierEnd = hierStart;
        while (hierEnd < length && value.charAt(hierEnd) != '?' && value.charAt(hierEnd) != '#') {
            hierEnd++;
        }
        int queryEnd = hierEnd;
        if (hierEnd < length && value.charAt(hierEnd) == '?') {
            queryEnd = indexOf(value, '#', hierEnd, length);
            if (queryEnd < 0) {
                queryEnd = length;
            }
            if (!allMatch(value, hierEnd + 1, queryEnd, QUERY)) {
                return null;
            }
        }
        if (queryEnd < length && !allMatch(value, queryEnd + 1, length, QUERY)) {
            return null;
        }

        int authorityStart = -1;
        int hostStart = hierStart;
        int hostEnd = hierStart;
        int authorityEnd = hierStart;
        if (hierEnd - hierStart >= 2 && value.charAt(hierStart) == '/' && value.charAt(hierStart + 1) == '/') {
            authorityStart = hierStart + 2;
            authorityEnd = indexOf(value, '/', authorityStart, hierEnd);
            if (authorityEnd < 0) {
                authorityEnd = hierEnd;
            }
            hostStart = authorityStart;
            int at = indexOf(value, '@', authorityStart, authorityEnd);
            if (at >= 0) {
                if (!allMatch(value, authorityStart, at, USERINFO)) {
                    return null;
                }
                hostStart = at + 1;
            }
            if (hostStart < authorityEnd && value.charAt(hostStart) == '[') {
                int close = indexOf(value, ']', hostStart, authorityEnd);
                if (close < 0) {
                    return null;
                }
                if (!IpSyntax.isIpv6(value, hostStart + 1, close) && !isIpFuture(value, hostStart + 1, close)) {
                    return null;
                }
                hostEnd = close + 1;
            } else {
                hostEnd = hostStart;
                while (hostEnd < authorityEnd && value.charAt(hostEnd) != ':') {
                    hostEnd++;
                }
                // IPv4address is a subset of reg-name, so one check covers both.
                if (!allMatch(value, hostStart, hostEnd, REG_NAME)) {
                    return null;
                }
            }
            if (hostEnd < authorityEnd) {
                if (value.charAt(hostEnd) != ':') {
                    return null;
                }
                for (int i = hostEnd + 1; i < authorityEnd; i++) {
                    if (!IpSyntax.isDigit(value.charAt(i))) {
                        return null;
                    }
                }
            }
        }
        // Every path form is pchar and "/"; the forms differ only in how they start, and a path
        // starting with "//" has already been read as an authority.
        if (!allMatch(value, authorityEnd, hierEnd, PATH)) {
            return null;
        }
        return new Parsed(value, schemeEnd, authorityStart, hostStart, hostEnd, authorityEnd, hierEnd,
                queryEnd > hierEnd, queryEnd < length);
    }

    // Like String#indexOf(int, int) but stops at "to". Returns -1 if the character is not in
    // value[from, to).
    private static int indexOf(String value, char c, int from, int to) {
        for (int i = from; i < to; i++) {
            if (value.charAt(i) == c) {
                return i;
            }
        }
        return -1;
    }

    // scheme = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." ), followed by ":".
    // Returns the index of the ":", or -1.
    private static int scheme(String value) {
        if (value.isEmpty() || !isAlpha(value.charAt(0))) {
            return -1;
        }
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ':') {
                return i;
            }
            if (!isAlpha(c) && !IpSyntax.isDigit(c) && c != '+' && c != '-' && c != '.') {
                return -1;
            }
        }
        return -1;
    }

    // IPvFuture = "v" 1*HEXDIG "." 1*( unreserved / sub-delims / ":" ). The "v" is case-insensitive,
    // as every quoted string in ABNF is (RFC 5234 section 2.3).
    private static boolean isIpFuture(String value, int from, int to) {
        if (from >= to || (value.charAt(from) | 0x20) != 'v') {
            return false;
        }
        int pos = from + 1;
        int start = pos;
        while (pos < to && IpSyntax.isHexDigit(value.charAt(pos))) {
            pos++;
        }
        if (pos == start || pos >= to || value.charAt(pos) != '.') {
            return false;
        }
        pos++;
        if (pos == to) {
            return false;
        }
        for (; pos < to; pos++) {
            char c = value.charAt(pos);
            if (!isUnreserved(c) && !isSubDelim(c) && c != ':') {
                return false;
            }
        }
        return true;
    }

    // The characters each component allows besides unreserved, sub-delims and pct-encoded.
    // userinfo = *( unreserved / pct-encoded / sub-delims / ":" )
    private static final String USERINFO = ":";
    // reg-name = *( unreserved / pct-encoded / sub-delims )
    private static final String REG_NAME = "";
    // pchar = unreserved / pct-encoded / sub-delims / ":" / "@"; paths add "/"
    private static final String PATH = ":@/";
    // query = fragment = *( pchar / "/" / "?" )
    private static final String QUERY = ":@/?";

    // Whether value[from, to) is made of unreserved, sub-delims, pct-encoded and the extra
    // characters.
    private static boolean allMatch(String value, int from, int to, String extra) {
        for (int i = from; i < to; i++) {
            char c = value.charAt(i);
            if (c == '%') {
                if (i + 2 >= to || !IpSyntax.isHexDigit(value.charAt(i + 1))
                        || !IpSyntax.isHexDigit(value.charAt(i + 2))) {
                    return false;
                }
                i += 2;
            } else if (!isUnreserved(c) && !isSubDelim(c) && extra.indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    // unreserved = ALPHA / DIGIT / "-" / "." / "_" / "~"
    private static boolean isUnreserved(char c) {
        return isAlpha(c) || IpSyntax.isDigit(c) || c == '-' || c == '.' || c == '_' || c == '~';
    }

    // sub-delims = "!" / "$" / "&" / "'" / "(" / ")" / "*" / "+" / "," / ";" / "="
    private static boolean isSubDelim(char c) {
        return "!$&'()*+,;=".indexOf(c) >= 0;
    }
}
