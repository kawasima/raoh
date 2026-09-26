package net.unit8.raoh.decode.builtin;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks {@link UriSyntax} and {@link IpSyntax} over every string up to a bounded length drawn from
 * a small alphabet (issue #144).
 *
 * <p>The JDK is not the oracle for what is accepted. {@link java.net.URI} is used in one direction
 * only: every text the grammar accepts and marks as representable must build a {@code URI} whose
 * text is unchanged. The IPv6 rule is compared with a regular expression transcribed from the
 * RFC 3986 ABNF, which shares no code with the scanner.
 */
class UriSyntaxTest {

    @Test
    void everyAcceptedRepresentableUriBuildsAJavaUriWithTheSameText() {
        var failures = new ArrayList<String>();
        Consumer<String> check = text -> {
            var parsed = UriSyntax.parse(text);
            if (parsed == null || !parsed.representableAsJavaUri()) {
                return;
            }
            try {
                var uri = new URI(text);
                if (!uri.toString().equals(text)) {
                    failures.add(text + " -> " + uri);
                }
            } catch (URISyntaxException e) {
                failures.add(text + " -> " + e.getMessage());
            }
        };
        var alphabet = "a:/?#[]@%1v.F-!'_";
        for (var prefix : List.of("", "a:", "a://", "a://u@", "a://[v1.x]", "a://[::1]")) {
            enumerate(alphabet, prefix.isEmpty() ? 5 : 4, s -> check.accept(prefix + s));
        }
        enumerate("0:1.f", 8, s -> {
            if (IpSyntax.isIpv6(s)) {
                check.accept("http://[" + s + "]/");
                check.accept("a://u@[" + s + "]:");
                check.accept("a://[" + s + "]:2147483647");
                check.accept("a://[" + s + "]:2147483648");
            }
        });
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures.subList(0, Math.min(20, failures.size()))));
    }

    @Test
    void ipv6MatchesTheRfc3986Abnf() {
        var h16 = "[0-9A-Fa-f]{1,4}";
        var decOctet = "(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9][0-9]|[0-9])";
        var ipv4 = decOctet + "\\." + decOctet + "\\." + decOctet + "\\." + decOctet;
        var ls32 = "(?:" + h16 + ":" + h16 + "|" + ipv4 + ")";
        // IPv6address, RFC 3986 section 3.2.2, one alternative per line of the ABNF.
        var abnf = Pattern.compile(String.join("|",
                groups(h16, 6) + ls32,
                "::" + groups(h16, 5) + ls32,
                upTo(h16, 0) + "::" + groups(h16, 4) + ls32,
                upTo(h16, 1) + "::" + groups(h16, 3) + ls32,
                upTo(h16, 2) + "::" + groups(h16, 2) + ls32,
                upTo(h16, 3) + "::" + h16 + ":" + ls32,
                upTo(h16, 4) + "::" + ls32,
                upTo(h16, 5) + "::" + h16,
                upTo(h16, 6) + "::"));
        var mismatches = new ArrayList<String>();
        var accepted = new int[1];
        enumerate("0:1.2f5", 7, s -> {
            boolean expected = abnf.matcher(s).matches();
            if (expected) {
                accepted[0]++;
            }
            if (IpSyntax.isIpv6(s) != expected) {
                mismatches.add(s + " expected " + expected);
            }
        });
        assertTrue(mismatches.isEmpty(), () -> String.join("\n", mismatches.subList(0, Math.min(20, mismatches.size()))));
        assertTrue(accepted[0] > 1000, "the alphabet reaches accepted addresses");
    }

    @Test
    void ipv6FirstGroupReadsTheLeadingGroup() {
        assertEquals(0xfe80, IpSyntax.ipv6FirstGroup("fe80::1"));
        assertEquals(0xFF02, IpSyntax.ipv6FirstGroup("FF02::1"));
        assertEquals(0, IpSyntax.ipv6FirstGroup("::1"));
        assertEquals(1, IpSyntax.ipv6FirstGroup("1:2:3:4:5:6:1.2.3.4"));
    }

    private static String groups(String h16, int count) {
        return "(?:" + h16 + ":){" + count + "}";
    }

    private static String upTo(String h16, int colons) {
        return "(?:(?:" + h16 + ":){0," + colons + "}" + h16 + ")?";
    }

    // Calls the action with every string of length 0 to maxLength over the alphabet.
    private static void enumerate(String alphabet, int maxLength, Consumer<String> action) {
        enumerate(alphabet, new StringBuilder(), maxLength, action);
    }

    private static void enumerate(String alphabet, StringBuilder current, int remaining, Consumer<String> action) {
        action.accept(current.toString());
        if (remaining == 0) {
            return;
        }
        for (int i = 0; i < alphabet.length(); i++) {
            current.append(alphabet.charAt(i));
            enumerate(alphabet, current, remaining - 1, action);
            current.setLength(current.length() - 1);
        }
    }
}
