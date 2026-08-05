package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.ErrorCodes;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.text.Normalizer;
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
    void urlRejectsTooLongValue() {
        // Exceeds the 2048-character maximum, exercising the length guard branch of url().
        var tooLong = "https://example.com/" + "a".repeat(2048);
        var issue = decodeErr(string().url(), tooLong);
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals("not a valid URL", issue.message());
    }

    @Test
    void urlLengthGuardCountsUtf16UnitsNotCodePoints() {
        // The 2048 bound in url() caps the size of the Java string handed to URI.create, so it
        // stays in UTF-16 units even though minLength/maxLength count code points. It is neither a
        // character count nor a bound on the URL as sent: the percent-encoded ASCII form of a
        // supplementary character is 12 characters where the Java string holds two.
        // java.net.URI accepts supplementary characters in a path, so this URL is otherwise valid
        // and only the guard rejects it — under a code point count it would decode successfully.
        var url = "https://example.com/" + "𠮷".repeat(1025);   // 𠮷 ×1025
        assertTrue(url.length() > 2048, "over the guard when counted in UTF-16 units");
        assertTrue(url.codePointCount(0, url.length()) < 2048, "under it when counted in code points");

        var issue = decodeErr(string().url(), url);
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals("not a valid URL", issue.message());
    }

    @Test
    void urlRejectsMalformedValue() {
        // The embedded space makes URI.create throw, exercising the parse-failure branch of url().
        var issue = decodeErr(string().url(), "http://exa mple.com");
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals("not a valid URL", issue.message());
    }

    @Test
    void urlRejectsMissingHost() {
        // Valid http scheme but empty host, exercising the host==null/empty branch of url().
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
