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
     * The components of an accepted URI.
     *
     * @param scheme the scheme, as written
     * @param authority the authority without the leading {@code //}, or {@code null} if there is none
     * @param host the host, brackets included for an {@code IP-literal}, or {@code null} if there is
     *             no authority
     * @param port the port digits without the {@code :}, possibly empty, or {@code null} if there is none
     * @param path the path, possibly empty
     * @param query the query without the {@code ?}, or {@code null} if there is none
     * @param fragment the fragment without the {@code #}, or {@code null} if there is none
     */
    record Parsed(String scheme, @Nullable String authority, @Nullable String host,
                  @Nullable String port, String path, @Nullable String query,
                  @Nullable String fragment) {

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
            if (authority == null) {
                return !path.isEmpty() || query != null;
            }
            if (authority.isEmpty() && path.isEmpty() && query == null && fragment == null) {
                return false;
            }
            // A bracket starts an IP-literal, and an IPvFuture inside it starts with "v".
            var host = this.host;
            if (host == null || !host.startsWith("[")) {
                return true;
            }
            if ((host.charAt(1) | 0x20) == 'v') {
                return false;
            }
            var port = this.port;
            return port == null || fitsInt(port);
        }

        // Whether the decimal digits denote a value no greater than Integer.MAX_VALUE.
        private static boolean fitsInt(String digits) {
            int start = 0;
            while (start < digits.length() - 1 && digits.charAt(start) == '0') {
                start++;
            }
            int length = digits.length() - start;
            return length < 10 || (length == 10 && digits.substring(start).compareTo("2147483647") <= 0);
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
            queryEnd = value.indexOf('#', hierEnd);
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

        String authority = null;
        String host = null;
        String port = null;
        int pathStart = hierStart;
        if (hierEnd - hierStart >= 2 && value.charAt(hierStart) == '/' && value.charAt(hierStart + 1) == '/') {
            int authorityStart = hierStart + 2;
            int authorityEnd = value.indexOf('/', authorityStart);
            if (authorityEnd < 0 || authorityEnd > hierEnd) {
                authorityEnd = hierEnd;
            }
            int hostStart = authorityStart;
            int at = value.indexOf('@', authorityStart);
            if (at >= 0 && at < authorityEnd) {
                if (!allMatch(value, authorityStart, at, USERINFO)) {
                    return null;
                }
                hostStart = at + 1;
            }
            int hostEnd;
            if (hostStart < authorityEnd && value.charAt(hostStart) == '[') {
                int close = value.indexOf(']', hostStart);
                if (close < 0 || close >= authorityEnd) {
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
                port = value.substring(hostEnd + 1, authorityEnd);
            }
            authority = value.substring(authorityStart, authorityEnd);
            host = value.substring(hostStart, hostEnd);
            pathStart = authorityEnd;
        }
        // Every path form is pchar and "/"; the forms differ only in how they start, and a path
        // starting with "//" has already been read as an authority.
        if (!allMatch(value, pathStart, hierEnd, PATH)) {
            return null;
        }
        return new Parsed(
                value.substring(0, schemeEnd),
                authority,
                host,
                port,
                value.substring(pathStart, hierEnd),
                queryEnd > hierEnd ? value.substring(hierEnd + 1, queryEnd) : null,
                queryEnd < length ? value.substring(queryEnd + 1) : null);
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
