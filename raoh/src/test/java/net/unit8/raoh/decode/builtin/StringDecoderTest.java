package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.MessageKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.net.URI;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.regex.Pattern;

import static net.unit8.raoh.decode.ObjectDecoders.string;
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeErr;
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeOk;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct unit tests for {@link StringDecoder}, covering length/format constraints,
 * preset validators, transforms, and type conversions. Failure paths assert both the
 * error code and the self-contained fallback message.
 */
class StringDecoderTest {

    // --- nonBlank ---

    @Test
    void nonBlankAcceptsNonBlankRejectsWhitespace() {
        assertEquals("hi", decodeOk(string().nonBlank(), "hi"));
        var issue = decodeErr(string().nonBlank(), "   ");
        assertEquals(ErrorCodes.BLANK, issue.code());
        assertEquals("must not be blank", issue.message());
    }

    // The expected sets are written out as literals; they are never derived from
    // Character.isWhitespace, String.isBlank or String.trim.
    private static final String[] WHITE_SPACE = {
            "\u0009", "\n", "\u000B", "\u000C", "\r", "\u0020", "\u0085", "\u00A0", "\u1680",
            "\u2000", "\u2001", "\u2002", "\u2003", "\u2004", "\u2005", "\u2006", "\u2007",
            "\u2008", "\u2009", "\u200A", "\u2028", "\u2029", "\u202F", "\u205F", "\u3000"
    };
    // Whitespace to the JDK (Java's isWhitespace or trim), but not Unicode White_Space,
    // plus invisible characters that are deliberately not whitespace.
    private static final String[] NOT_WHITE_SPACE = {
            "\u0000", "\u0001", "\u001C", "\u001D", "\u001E", "\u001F", "\u180E", "\u200B",
            "\u200C", "\u200D", "\u2060", "\uFEFF", "a"
    };

    @Test
    void nonBlankRejectsExactlyUnicodeWhiteSpace() {
        assertEquals(25, WHITE_SPACE.length);
        for (var ws : WHITE_SPACE) {
            assertEquals(ErrorCodes.BLANK, decodeErr(string().nonBlank(), ws).code(), ws);
            assertEquals(ErrorCodes.BLANK, decodeErr(string().nonBlank(), ws + ws).code(), ws);
        }
        for (var ch : NOT_WHITE_SPACE) {
            assertEquals(ch, decodeOk(string().nonBlank(), ch));
        }
        assertEquals(ErrorCodes.BLANK, decodeErr(string().nonBlank(), "").code());
    }

    @Test
    void trimStripsExactlyUnicodeWhiteSpace() {
        for (var ws : WHITE_SPACE) {
            assertEquals("x y", decodeOk(string().trim(), ws + "x y" + ws), ws);
            assertEquals("", decodeOk(string().trim(), ws), ws);
        }
        for (var ch : NOT_WHITE_SPACE) {
            assertEquals(ch + "x" + ch, decodeOk(string().trim(), ch + "x" + ch));
        }
        // Supplementary-plane characters are not whitespace and stay intact.
        assertEquals("\uD83D\uDE00", decodeOk(string().trim(), "\u3000\uD83D\uDE00\u00A0"));
    }

    @Test
    void trimAndNonBlankAgreeOnEveryInput() {
        var samples = new ArrayList<String>();
        for (var a : WHITE_SPACE) {
            samples.add(a);
            samples.add(a + "x");
        }
        for (var a : NOT_WHITE_SPACE) {
            samples.add(a);
            samples.add("\u0020" + a + "\u3000");
        }
        samples.add("");
        for (var s : samples) {
            var trimmed = decodeOk(string().trim(), s);
            assertEquals(trimmed, decodeOk(string().trim().trim(), s), "idempotent");
            assertEquals(trimmed.isEmpty(), !string().nonBlank().decode(s).isOk(), "blank iff empty after trim");
            assertEquals(string().nonBlank().decode(s).isOk(),
                    string().trim().nonBlank().decode(s).isOk(), "trim().nonBlank() agrees with nonBlank()");
        }
    }

    // --- length constraints ---

    @Test
    void minLengthAcceptsBoundaryRejectsShorter() {
        assertEquals("abc", decodeOk(string().minLength(3), "abc"));
        var issue = decodeErr(string().minLength(3), "ab");
        assertEquals(ErrorCodes.TOO_SHORT, issue.code());
        assertEquals("must be at least 3 characters", issue.message());
        assertEquals(3, issue.meta().get("min"));
        assertEquals(2, issue.meta().get("actual"));
    }

    @Test
    void maxLengthAcceptsBoundaryRejectsLonger() {
        assertEquals("abc", decodeOk(string().maxLength(3), "abc"));
        var issue = decodeErr(string().maxLength(3), "abcd");
        assertEquals(ErrorCodes.TOO_LONG, issue.code());
        assertEquals("must be at most 3 characters", issue.message());
    }

    @Test
    void maxLengthUsesCustomMessageWhenProvided() {
        var issue = decodeErr(string().maxLength(3, "too long"), "abcd");
        assertEquals("too long", issue.message());
        assertTrue(issue.customMessage());
    }

    @Test
    void fixedLengthAcceptsExactRejectsDifferent() {
        assertEquals("abc", decodeOk(string().fixedLength(3), "abc"));
        var issue = decodeErr(string().fixedLength(3), "ab");
        assertEquals(ErrorCodes.INVALID_LENGTH, issue.code());
        assertEquals("must be exactly 3 characters", issue.message());
    }

    // --- length is counted in code points ---

    /**
     * A supplementary-plane character occupies two UTF-16 units and is one character. Counting the
     * units makes a length limit depend on which characters a name happens to contain, which is a
     * surprise wherever such characters are ordinary: 𠮷田, an emoji in a free-text field.
     */
    @Test
    void lengthConstraintsCountCodePointsNotUtf16Units() {
        String twoKanji = "\uD842\uDFB7\u7530";   // 𠮷田: two characters, three UTF-16 units

        // Counting units, both of these reject the value; counting code points, neither does.
        assertEquals(twoKanji, decodeOk(string().maxLength(2), twoKanji));
        assertEquals(twoKanji, decodeOk(string().fixedLength(2), twoKanji));

        var tooLong = decodeErr(string().maxLength(1), twoKanji);
        assertEquals(ErrorCodes.TOO_LONG, tooLong.code());
        assertEquals(2, tooLong.meta().get("actual"), "the reported length is in code points too");
    }

    /**
     * {@code minLength} is the direction the change tightens, so it needs a value that the old
     * unit count would have let through.
     */
    @Test
    void minLengthCountsCodePointsAndSoBecomesStricter() {
        String oneKanji = "𠮷";   // 𠮷: one character, two UTF-16 units

        var tooShort = decodeErr(string().minLength(2), oneKanji);
        assertEquals(ErrorCodes.TOO_SHORT, tooShort.code());
        assertEquals(1, tooShort.meta().get("actual"));

        assertEquals(oneKanji, decodeOk(string().minLength(1), oneKanji));
    }

    // --- oneOf ---

    @Test
    void oneOfAcceptsMemberRejectsNonMember() {
        assertEquals("a", decodeOk(string().oneOf("a", "b"), "a"));
        var issue = decodeErr(string().oneOf("b", "a"), "c");
        assertEquals(ErrorCodes.NOT_ALLOWED, issue.code());
        assertEquals("must be one of [a, b]", issue.message());
    }

    @Test
    void oneOfListsAllowedValuesInCodePointOrder() {
        // U+1F600 is a surrogate pair starting at U+D83D, so String.compareTo puts it before
        // U+FF21; by code point it comes after.
        var issue = decodeErr(string().oneOf("\ud83d\ude00", "\uff21", "a"), "z");
        assertEquals(java.util.List.of("a", "\uff21", "\ud83d\ude00"), issue.meta().get("allowed"));
        assertEquals("must be one of [a, \uff21, \ud83d\ude00]", issue.message());
    }

    // --- pattern ---

    @Test
    void patternAcceptsMatchRejectsMismatch() {
        var p = Pattern.compile("\\d+");
        assertEquals("123", decodeOk(string().pattern(p), "123"));
        var issue = decodeErr(string().pattern(p), "12a");
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals("invalid format", issue.message());
        assertEquals("\\d+", issue.meta().get("pattern"));
    }

    @Test
    void patternUsesCustomCode() {
        var issue = decodeErr(string().pattern(Pattern.compile("\\d+"), "must_be_digits"), "x");
        assertEquals("must_be_digits", issue.code());
    }

    @Test
    void patternUsesCustomCodeAndMessage() {
        var issue = decodeErr(string().pattern(Pattern.compile("\\d+"), "must_be_digits", "digits only"), "x");
        assertEquals("must_be_digits", issue.code());
        assertEquals("digits only", issue.message());
        assertTrue(issue.customMessage());
    }

    // --- substring constraints ---

    @Test
    void startsWithAcceptsPrefixRejectsOther() {
        assertEquals("foobar", decodeOk(string().startsWith("foo"), "foobar"));
        var issue = decodeErr(string().startsWith("foo"), "barfoo");
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals("must start with \"foo\"", issue.message());
        assertEquals("foo", issue.meta().get("prefix"));
    }

    @Test
    void endsWithAcceptsSuffixRejectsOther() {
        assertEquals("foobar", decodeOk(string().endsWith("bar"), "foobar"));
        var issue = decodeErr(string().endsWith("bar"), "barfoo");
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals("must end with \"bar\"", issue.message());
    }

    @Test
    void includesAcceptsSubstringRejectsOther() {
        assertEquals("amidb", decodeOk(string().includes("mid"), "amidb"));
        var issue = decodeErr(string().includes("mid"), "abc");
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals("must include \"mid\"", issue.message());
    }

    // --- preset validators ---

    @Test
    void emailAcceptsValidRejectsInvalid() {
        assertEquals("a@b.co", decodeOk(string().email(), "a@b.co"));
        var issue = decodeErr(string().email(), "not-an-email");
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals("not a valid email", issue.message());
    }

    @Test
    void emailRejectsTooLongValue() {
        // Matches the email pattern but exceeds the 254-character maximum, so only the
        // length guard rejects it — exercising that branch independently of the pattern.
        var tooLong = "a".repeat(64) + "@" + "b".repeat(250) + ".co";
        var issue = decodeErr(string().email(), tooLong);
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals("not a valid email", issue.message());
    }

    @Test
    void urlAcceptsHttpsRejectsNonHttpScheme() {
        assertEquals(URI.create("https://example.com"), decodeOk(string().url(), "https://example.com"));
        var issue = decodeErr(string().url(), "ftp://example.com");
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals("not a valid URL", issue.message());
    }

    @Test
    void urlDoesNotLimitLengthButComposesWithMaxLength() {
        // url() has no length bound of its own (issue #144); maxLength() is how a caller sets one.
        var url = "https://example.com/" + "a".repeat(2048);
        assertEquals(URI.create(url), decodeOk(string().url(), url));
        var issue = decodeErr(string().maxLength(2048).url(), url);
        assertEquals(ErrorCodes.TOO_LONG, issue.code());
    }

    @Test
    void urlRejectsMalformedValue() {
        // A space is outside the RFC 3986 grammar.
        var issue = decodeErr(string().url(), "http://exa mple.com");
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals("not a valid URL", issue.message());
    }

    @Test
    void urlRejectsMissingHost() {
        // Valid http scheme but empty authority, which RFC 9110 rejects for http URIs.
        var issue = decodeErr(string().url(), "http://");
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals("not a valid URL", issue.message());
    }

    @Test
    void ipv4AcceptsValidRejectsInvalid() {
        assertEquals("192.168.0.1", decodeOk(string().ipv4(), "192.168.0.1"));
        var issue = decodeErr(string().ipv4(), "999.1.1.1");
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals("not a valid IPv4 address", issue.message());
    }

    @Test
    void ipv6AcceptsValidRejectsInvalid() {
        assertEquals("::1", decodeOk(string().ipv6(), "::1"));
        var issue = decodeErr(string().ipv6(), "not-an-ip");
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals("not a valid IPv6 address", issue.message());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "::ffff:192.0.2.1",
            "::FFFF:129.144.52.38",
            "fe80::1%eth0",
            // Intentionally unlikely to name a real interface; the zone ID must not be looked up.
            "fe80::1%this-interface-does-not-exist",
            "fe80::1%3",
            "febf::1%eth0",       // last /16 of fe80::/10
            // Every multicast scope below global, including the unassigned ones the JDK has no
            // classifier for.
            "ff01::1%eth0",
            "ff02::1%uplink",
            "ff03::1%eth0",       // realm-local (RFC 7346)
            "ff04::1%eth0",
            "ff05::1%eth0",
            "ff06::1%eth0",
            "ff07::1%eth0",
            "ff08::1%eth0",
            "ff09::1%eth0",
            "ff0d::1%eth0",
            "ff32::1%eth0",       // flags set, link-local scope
            "ff33::1%eth0",       // flags set, realm-local scope
            // Longer than 45 characters with the zone ID; the address part alone is 45.
            "fe80:0000:0000:0000:0000:ffff:255.255.255.255%long-interface-name"
    })
    void ipv6AcceptsWithoutConsultingTheHost(String value) {
        assertEquals(value, decodeOk(string().ipv6(), value));
        assertEquals(value, decodeOk(string().ip(), value));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2001:db8::1%eth0",   // global unicast
            "fec0::1%eth0",       // deprecated site-local, now global unicast
            "ff00::1%eth0",       // reserved multicast scope 0
            "ff0e::1%eth0",       // global multicast
            "ff0f::1%eth0",       // reserved multicast scope F
            "ff3e::1%eth0",       // flags set, global scope
            "::1%lo0",            // loopback
            "::%0",               // unspecified
            "::ffff:192.0.2.1%eth0",
            "fe80::1%",           // empty zone ID
            "fe80::1%a%b",        // delimiter inside the zone ID
            "fe80::1%\0",         // NUL inside the zone ID
            "[::1]",              // URI host framing
            "[fe80::1%eth0]",
            "%eth0"
    })
    void ipv6Rejects(String value) {
        var issue = decodeErr(string().ipv6(), value);
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals("not a valid IPv6 address", issue.message());
        assertEquals(ErrorCodes.INVALID_FORMAT, decodeErr(string().ip(), value).code());
    }

    @Test
    void ipAcceptsIpv4AndIpv6RejectsOther() {
        assertEquals("10.0.0.1", decodeOk(string().ip(), "10.0.0.1"));
        assertEquals("::1", decodeOk(string().ip(), "::1"));
        var issue = decodeErr(string().ip(), "nope");
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals("not a valid IP address", issue.message());
    }

    @Test
    void cuidAcceptsValidRejectsInvalid() {
        assertEquals("cjld2cyuq0000t3rmniod1foy", decodeOk(string().cuid(), "cjld2cyuq0000t3rmniod1foy"));
        var issue = decodeErr(string().cuid(), "not-a-cuid");
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals("not a valid CUID", issue.message());
    }

    @Test
    void ulidAcceptsValidRejectsInvalid() {
        assertEquals("01ARZ3NDEKTSV4RRFFQ69G5FAV", decodeOk(string().ulid(), "01ARZ3NDEKTSV4RRFFQ69G5FAV"));
        var issue = decodeErr(string().ulid(), "not-a-ulid");
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals("not a valid ULID", issue.message());
    }

    // --- transforms ---

    @Test
    void trimRemovesSurroundingWhitespace() {
        assertEquals("hi", decodeOk(string().trim(), "  hi  "));
    }

    @Test
    void toLowerCaseLowercasesValue() {
        assertEquals("abc", decodeOk(string().toLowerCase(), "ABC"));
    }

    @Test
    void toUpperCaseUppercasesValue() {
        assertEquals("ABC", decodeOk(string().toUpperCase(), "abc"));
    }

    // --- normalization ---
    //
    // Written as escapes on purpose: NFC_GA and NFD_GA are indistinguishable as literals.

    /** が — one code point. */
    private static final String NFC_GA = "\u304C";

    /** か + combining voiced sound mark — canonically equivalent to {@link #NFC_GA}, two code points. */
    private static final String NFD_GA = "\u304B\u3099";

    /** 葛 followed by variation selector U+E0101 — an ideographic variation sequence. */
    private static final String IVS_KATSU = "\u845B\uDB40\uDD01";

    /** ｱ — halfwidth katakana, a compatibility character NFC leaves alone. */
    private static final String HALFWIDTH_A = "\uFF71";

    @Test
    void normalizeComposesCanonicallyEquivalentInput() {
        assertEquals(NFC_GA, decodeOk(string().normalize(), NFD_GA));
    }

    /**
     * The point of the transform: the same text counts the same however the client encoded it.
     */
    @Test
    void normalizeMakesTheLengthIndependentOfTheInputForm() {
        assertEquals(NFC_GA, decodeOk(string().normalize().maxLength(1), NFD_GA));
    }

    @Test
    void normalizeMakesOneOfMatchCanonicallyEquivalentInput() {
        assertEquals(NFC_GA, decodeOk(string().normalize().oneOf(NFC_GA), NFD_GA));
    }

    /**
     * Transforms and constraints compose in the order they are written, and normalization is no
     * exception: put after a constraint, it runs after that constraint has already seen the raw form.
     */
    @Test
    void constraintsPlacedBeforeNormalizeSeeTheInputForm() {
        var issue = decodeErr(string().maxLength(1).normalize(), NFD_GA);
        assertEquals(ErrorCodes.TOO_LONG, issue.code());
        assertEquals(2, issue.meta().get("actual"));
    }

    /**
     * Variation sequences are normalization-stable by design, so {@code normalize()} neither strips
     * the selector nor shortens the count. Stripping variation selectors and counting grapheme
     * clusters are separate constraints, not a more thorough version of this one.
     */
    @Test
    void normalizeKeepsVariationSequencesIntact() {
        assertEquals(IVS_KATSU, decodeOk(string().normalize(), IVS_KATSU));

        var issue = decodeErr(string().normalize().maxLength(1), IVS_KATSU);
        assertEquals(ErrorCodes.TOO_LONG, issue.code());
        assertEquals(2, issue.meta().get("actual"));
    }

    @Test
    void normalizeDefaultsToNfcAndSoKeepsCompatibilityCharacters() {
        assertEquals(HALFWIDTH_A, decodeOk(string().normalize(), HALFWIDTH_A));
    }

    @Test
    void normalizeWithNfkcFoldsCompatibilityCharacters() {
        assertEquals("\u30A2", decodeOk(string().normalize(Normalizer.Form.NFKC), HALFWIDTH_A));
    }

    // --- type conversions ---

    @Test
    void uuidParsesValidRejectsInvalid() {
        var uuid = "550e8400-e29b-41d4-a716-446655440000";
        assertEquals(UUID.fromString(uuid), decodeOk(string().uuid(), uuid));
        var issue = decodeErr(string().uuid(), "not-a-uuid");
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals("not a valid UUID", issue.message());
    }

    @Test
    void uriParsesValidRejectsInvalid() {
        assertEquals(URI.create("urn:isbn:0451450523"), decodeOk(string().uri(), "urn:isbn:0451450523"));
        var issue = decodeErr(string().uri(), "http://exa mple.com");
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals("not a valid URI", issue.message());
    }

    @Test
    void iso8601ParsesValidRejectsInvalid() {
        assertEquals(Instant.parse("2016-12-31T23:59:59Z"), decodeOk(string().iso8601(), "2016-12-31T23:59:59Z"));
        var issue = decodeErr(string().iso8601(), "nonsense");
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals(MessageKeys.INVALID_FORMAT_INSTANT, issue.messageKey());
        assertEquals("not a valid ISO 8601 instant", issue.message());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2016-12-31T23:59:60Z",       // leap second; Instant.parse would answer 23:59:59Z
            "2016-12-31T23:59:60.5Z",
            "2016-12-31T23:59:60+09:00",  // Instant.parse would answer 14:59:59Z
            "2017-01-01T08:59:60+09:00",  // the UTC leap second, written in +09:00
            "2016-12-31T12:34:60Z",
            "+1000000001-01-01T00:00:00Z" // parses, but beyond Instant.MAX
    })
    void iso8601RejectsSecondSixtyAndYearsBeyondTheInstantRange(String text) {
        var issue = decodeErr(string().iso8601(), text);
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals(MessageKeys.INVALID_FORMAT_INSTANT, issue.messageKey());
        assertEquals("not a valid ISO 8601 instant", issue.message());
    }

    @Test
    void iso8601AcceptsTheWholeInstantRange() {
        assertEquals(Instant.ofEpochSecond(31556889864403199L, 999_999_999),
                decodeOk(string().iso8601(), "+1000000000-12-31T23:59:59.999999999Z"));
        assertEquals(Instant.ofEpochSecond(-31557014167219200L),
                decodeOk(string().iso8601(), "-1000000000-01-01T00:00:00Z"));
    }

    @Test
    void iso8601AcceptsEndOfDayAsTheStartOfTheNextDay() {
        assertEquals(Instant.ofEpochSecond(1483228800), decodeOk(string().iso8601(), "2016-12-31T24:00:00Z"));
    }

    @Test
    void iso8601AppliesTheOffset() {
        assertEquals(Instant.ofEpochSecond(1483228799), decodeOk(string().iso8601(), "2017-01-01T00:59:59+01:00"));
    }

    @Test
    void toIntParsesAndSupportsFurtherConstraints() {
        assertEquals(42, decodeOk(string().toInt(), "42"));
        assertEquals(ErrorCodes.OUT_OF_RANGE, decodeErr(string().toInt().positive(), "-5").code());
        var issue = decodeErr(string().toInt(), "x");
        assertEquals(ErrorCodes.TYPE_MISMATCH, issue.code());
        assertEquals("expected integer", issue.message());
    }

    @Test
    void toLongParsesRejectsInvalid() {
        assertEquals(42L, decodeOk(string().toLong(), "42"));
        var issue = decodeErr(string().toLong(), "x");
        assertEquals(ErrorCodes.TYPE_MISMATCH, issue.code());
        assertEquals("expected long", issue.message());
    }

    @Test
    void toDecimalParsesRejectsInvalid() {
        assertEquals(new BigDecimal("1.5"), decodeOk(string().toDecimal(), "1.5"));
        var issue = decodeErr(string().toDecimal(), "x");
        assertEquals(ErrorCodes.TYPE_MISMATCH, issue.code());
        assertEquals("expected decimal", issue.message());
    }

    @Test
    void toBoolParsesCommonFormsRejectsOther() {
        assertEquals(true, decodeOk(string().toBool(), "yes"));
        assertEquals(false, decodeOk(string().toBool(), "off"));
        var issue = decodeErr(string().toBool(), "maybe");
        assertEquals(ErrorCodes.TYPE_MISMATCH, issue.code());
        assertEquals("expected boolean", issue.message());
    }

    // --- base decoder behaviour ---

    @Test
    void rejectsNullAsRequired() {
        assertEquals(ErrorCodes.REQUIRED, decodeErr(string(), null).code());
    }

    @Test
    void rejectsNonStringAsTypeMismatch() {
        assertEquals(ErrorCodes.TYPE_MISMATCH, decodeErr(string(), 42).code());
    }
}
