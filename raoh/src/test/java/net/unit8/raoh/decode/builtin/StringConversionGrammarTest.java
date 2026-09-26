package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.MessageKeys;
import net.unit8.raoh.encode.ObjectEncoders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.unit8.raoh.decode.ObjectDecoders.string;
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeErr;
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeOk;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fixes the lexical language of the {@link StringDecoder} conversions (issue #137).
 *
 * <p>Expected values are literals, not the output of the JDK parser the conversion uses, so a
 * change in what that parser accepts cannot move the boundary unnoticed. The round-trip tests
 * check that everything {@link ObjectEncoders} writes is still accepted.
 */
class StringConversionGrammarTest {

    // --- uuid ---

    @Test
    void uuidAcceptsTheRfc9562FormInAnyCase() {
        var expected = new UUID(0x550e8400e29b41d4L, 0xa716446655440000L);
        assertEquals(expected, decodeOk(string().uuid(), "550e8400-e29b-41d4-a716-446655440000"));
        assertEquals(expected, decodeOk(string().uuid(), "550E8400-E29B-41D4-A716-446655440000"));
        assertEquals(expected, decodeOk(string().uuid(), "550e8400-E29B-41d4-A716-446655440000"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1-1-1-1-1",                                     // UUID.fromString pads the groups
            "550e8400-e29b-41d4-a716-44665544000",           // 11 digits in the last group
            "550e8400-e29b-41d4-a716-4466554400000",         // 13 digits in the last group
            "550e8400e29b41d4a716446655440000",              // no hyphens
            "{550e8400-e29b-41d4-a716-446655440000}",
            "urn:uuid:550e8400-e29b-41d4-a716-446655440000",
            "550e8400-e29b-41d4-a716-44665544000g",
            "５50e8400-e29b-41d4-a716-446655440000",         // full-width digit
            " 550e8400-e29b-41d4-a716-446655440000"
    })
    void uuidRejectsOtherForms(String text) {
        var issue = decodeErr(string().uuid(), text);
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals(MessageKeys.INVALID_FORMAT_UUID, issue.messageKey());
        assertEquals("not a valid UUID", issue.message());
    }

    // --- toInt / toLong ---

    @Test
    void toIntAcceptsSignsAndLeadingZeros() {
        assertEquals(5, decodeOk(string().toInt(), "+5"));
        assertEquals(-5, decodeOk(string().toInt(), "-5"));
        assertEquals(0, decodeOk(string().toInt(), "+0"));
        assertEquals(0, decodeOk(string().toInt(), "-0"));
        assertEquals(7, decodeOk(string().toInt(), "0007"));
        assertEquals(2147483647, decodeOk(string().toInt(), "2147483647"));
        assertEquals(-2147483648, decodeOk(string().toInt(), "-2147483648"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "１２３",          // full-width digits; Integer.parseInt accepts them
            "٣",              // Arabic-Indic digit
            "१२",             // Devanagari digits
            "1２",             // mixed
            "", "+", "-", "+-1", "1.0", "1e3", " 1", "1 ", "0x10", "1_000"
    })
    void toIntRejectsOtherForms(String text) {
        var issue = decodeErr(string().toInt(), text);
        assertEquals(ErrorCodes.TYPE_MISMATCH, issue.code());
        assertEquals("expected integer", issue.message());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2147483648", "-2147483649", "99999999999999999999"})
    void toIntReportsAWellFormedIntegerOutsideTheRange(String text) {
        // Same issue as ObjectDecoders.int_() gives for a number outside the int range.
        var issue = decodeErr(string().toInt(), text);
        assertEquals(ErrorCodes.TYPE_MISMATCH, issue.code());
        assertEquals(MessageKeys.TYPE_MISMATCH_NUMERIC_RANGE, issue.messageKey());
        assertEquals("value is outside the integer range", issue.message());
    }

    @Test
    void toLongAcceptsSignsAndLeadingZeros() {
        assertEquals(5L, decodeOk(string().toLong(), "+5"));
        assertEquals(7L, decodeOk(string().toLong(), "0007"));
        assertEquals(9223372036854775807L, decodeOk(string().toLong(), "9223372036854775807"));
        assertEquals(-9223372036854775808L, decodeOk(string().toLong(), "-9223372036854775808"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"٣", "１２３", "", "1.0", " 1"})
    void toLongRejectsOtherForms(String text) {
        var issue = decodeErr(string().toLong(), text);
        assertEquals(ErrorCodes.TYPE_MISMATCH, issue.code());
        assertEquals("expected long", issue.message());
    }

    @ParameterizedTest
    @ValueSource(strings = {"9223372036854775808", "-9223372036854775809"})
    void toLongReportsAWellFormedIntegerOutsideTheRange(String text) {
        var issue = decodeErr(string().toLong(), text);
        assertEquals(ErrorCodes.TYPE_MISMATCH, issue.code());
        assertEquals(MessageKeys.TYPE_MISMATCH_NUMERIC_RANGE, issue.messageKey());
        assertEquals("value is outside the long range", issue.message());
    }

    // --- toDecimal ---

    @Test
    void toDecimalAcceptsSignsLooseDecimalPointsAndExponents() {
        assertEquals(new BigDecimal(BigInteger.valueOf(125), 1), decodeOk(string().toDecimal(), "12.5"));
        assertEquals(new BigDecimal(BigInteger.valueOf(125), 1), decodeOk(string().toDecimal(), "+12.5"));
        assertEquals(new BigDecimal(BigInteger.valueOf(-125), 1), decodeOk(string().toDecimal(), "-12.5"));
        assertEquals(new BigDecimal(BigInteger.valueOf(5), 0), decodeOk(string().toDecimal(), "5."));
        assertEquals(new BigDecimal(BigInteger.valueOf(5), 1), decodeOk(string().toDecimal(), ".5"));
        assertEquals(new BigDecimal(BigInteger.valueOf(-5), 1), decodeOk(string().toDecimal(), "-.5"));
        assertEquals(new BigDecimal(BigInteger.valueOf(1), -3), decodeOk(string().toDecimal(), "1e3"));
        assertEquals(new BigDecimal(BigInteger.valueOf(1), -3), decodeOk(string().toDecimal(), "1E+3"));
        assertEquals(new BigDecimal(BigInteger.valueOf(25), 5), decodeOk(string().toDecimal(), "2.5e-4"));
        assertEquals(new BigDecimal(BigInteger.valueOf(7), 0), decodeOk(string().toDecimal(), "007"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "１２.5",               // full-width digits; new BigDecimal accepts them
            "٣.5",
            "1e٣",
            ".", "+.", "e3", "1e", "1e+", "1.2.3", "", " 1", "1 ",
            "NaN", "Infinity", "0x1p3", "1_000",
            "1e2147483649"          // exponent beyond what BigDecimal can scale
    })
    void toDecimalRejectsOtherForms(String text) {
        var issue = decodeErr(string().toDecimal(), text);
        assertEquals(ErrorCodes.TYPE_MISMATCH, issue.code());
        assertEquals("expected decimal", issue.message());
    }

    // --- date ---

    @Test
    void dateAcceptsFourDigitYearsAndSignedExpandedYears() {
        assertEquals(LocalDate.of(2024, 1, 15), decodeOk(string().date(), "2024-01-15"));
        assertEquals(LocalDate.of(0, 1, 1), decodeOk(string().date(), "0000-01-01"));
        assertEquals(LocalDate.of(-1, 1, 1), decodeOk(string().date(), "-0001-01-01"));
        assertEquals(LocalDate.of(12016, 1, 1), decodeOk(string().date(), "+12016-01-01"));
        assertEquals(LocalDate.of(999999999, 12, 31), decodeOk(string().date(), "+999999999-12-31"));
        assertEquals(LocalDate.of(-999999999, 1, 1), decodeOk(string().date(), "-999999999-01-01"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "+2024-01-15",       // a four-digit year takes no sign
            "12016-01-01",       // a longer year needs one
            "+00001-01-01",      // a signed year has no leading zeros beyond four digits
            "+09999-01-01",
            "-00001-01-01",
            "+0999999999-12-31",
            "-0000-01-01",       // year 0 is written 0000
            "+1000000000-01-01", // beyond LocalDate.MAX
            "2024-1-15", "2024-01-5", "24-01-15", "2024/01/15", "20240115",
            "2023-02-29", "2024-13-01", "2024-00-10",
            "２０２４-01-15", " 2024-01-15", "2024-01-15T00:00"
    })
    void dateRejectsOtherForms(String text) {
        var issue = decodeErr(string().date(), text);
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals(MessageKeys.INVALID_FORMAT_DATE, issue.messageKey());
        assertEquals("not a valid ISO-8601 date (e.g., 2024-01-15)", issue.message());
    }

    // --- time ---

    @Test
    void timeAcceptsOptionalSecondsAndOneToNineFractionDigits() {
        assertEquals(LocalTime.of(10, 30), decodeOk(string().time(), "10:30"));
        assertEquals(LocalTime.of(10, 30, 45), decodeOk(string().time(), "10:30:45"));
        assertEquals(LocalTime.of(10, 30, 45, 500_000_000), decodeOk(string().time(), "10:30:45.5"));
        assertEquals(LocalTime.of(10, 30, 45, 123_456_789), decodeOk(string().time(), "10:30:45.123456789"));
        assertEquals(LocalTime.of(23, 59, 59, 999_999_999), decodeOk(string().time(), "23:59:59.999999999"));
        assertEquals(LocalTime.of(0, 0), decodeOk(string().time(), "00:00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "10:30:00.",           // decimal point without digits; LocalTime.parse accepts it
            "10:30:00.1234567891", // ten fraction digits
            "10:30.5",             // fraction without seconds
            "24:00", "24:00:00", "23:60", "23:59:60",
            "1:30", "10:3", "1030", "10", "10:30:00Z", "10:30+09:00", "１０:30"
    })
    void timeRejectsOtherForms(String text) {
        var issue = decodeErr(string().time(), text);
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals(MessageKeys.INVALID_FORMAT_TIME, issue.messageKey());
        assertEquals("not a valid ISO-8601 local time (e.g., 10:30 or 10:30:45)", issue.message());
    }

    // --- dateTime ---

    @Test
    void dateTimeJoinsDateAndTimeWithAnUpperCaseT() {
        assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30), decodeOk(string().dateTime(), "2024-01-15T10:30"));
        assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30, 45, 123_000_000),
                decodeOk(string().dateTime(), "2024-01-15T10:30:45.123"));
        assertEquals(LocalDateTime.of(12016, 1, 1, 0, 0), decodeOk(string().dateTime(), "+12016-01-01T00:00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2016-12-31t23:59",       // lower-case t; LocalDateTime.parse accepts it
            "2024-01-15T10:30:00.",
            "2024-01-15 10:30", "2024-01-15T", "2024-01-15T10:30Z", "2024-01-15T24:00"
    })
    void dateTimeRejectsOtherForms(String text) {
        var issue = decodeErr(string().dateTime(), text);
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals(MessageKeys.INVALID_FORMAT_DATE_TIME, issue.messageKey());
    }

    // --- offsetDateTime ---

    @Test
    void offsetDateTimeAcceptsZAndOffsetsWithMinutesOrSeconds() {
        assertEquals(OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC),
                decodeOk(string().offsetDateTime(), "2024-01-15T10:30:00Z"));
        assertEquals(OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.ofHours(9)),
                decodeOk(string().offsetDateTime(), "2024-01-15T10:30+09:00"));
        assertEquals(OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.ofHoursMinutes(-5, -30)),
                decodeOk(string().offsetDateTime(), "2024-01-15T10:30-05:30"));
        assertEquals(OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.ofHoursMinutesSeconds(9, 0, 30)),
                decodeOk(string().offsetDateTime(), "2024-01-15T10:30+09:00:30"));
        assertEquals(OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC),
                decodeOk(string().offsetDateTime(), "2024-01-15T10:30-00:00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2016-12-31t23:59:59z",       // lower case; OffsetDateTime.parse accepts it
            "2016-12-31T23:59:59z",
            "2016-12-31t23:59:59Z",
            "2024-01-15T10:30+09",        // hours-only offset; OffsetDateTime.parse accepts it
            "2024-01-15T10:30:00.+09:00", // decimal point without digits
            "2024-01-15T10:30+0900", "2024-01-15T10:30+9:00", "2024-01-15T10:30",
            "2024-01-15T10:30+19:00", "2024-01-15T10:30 +09:00", "2024-01-15T10:30UTC"
    })
    void offsetDateTimeRejectsOtherForms(String text) {
        var issue = decodeErr(string().offsetDateTime(), text);
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals(MessageKeys.INVALID_FORMAT_OFFSET_DATE_TIME, issue.messageKey());
    }

    // --- iso8601 ---

    @Test
    void iso8601AcceptsFractionsAndOffsetsWithSeconds() {
        assertEquals(Instant.ofEpochSecond(1705314600, 500_000_000),
                decodeOk(string().iso8601(), "2024-01-15T10:30:00.5Z"));
        assertEquals(Instant.ofEpochSecond(1705314600 - 9 * 3600 - 30),
                decodeOk(string().iso8601(), "2024-01-15T10:30:00+09:00:30"));
        assertEquals(Instant.ofEpochSecond(253402300800L),
                decodeOk(string().iso8601(), "+10000-01-01T00:00:00Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2016-12-31t23:59:59z",       // lower case; Instant.parse accepts it
            "2016-12-31T23:59:59z",
            "2016-12-31T23:59:59.Z",      // decimal point without digits; Instant.parse accepts it
            "2016-12-31T23:59:59.1234567891Z",
            "2016-12-31T23:59Z",          // seconds are required
            "2016-12-31T23:59:59+09",
            "2016-12-31T23:59:59",
            "+2016-12-31T23:59:59Z",
            "+00000-01-01T00:00:00Z",
            "+00001-01-01T00:00:00Z",
            "-00001-01-01T00:00:00Z",
            "-0000-01-01T00:00:00Z",
            "2016-12-31T24:00:01Z",       // only 24:00:00 is an end of day
            "2016-12-31T24:00:00.5Z",
            "2016-12-31T23:59:59+18:01",  // beyond the offset range
            "2016-13-01T00:00:00Z",
            "２０１６-12-31T23:59:59Z"
    })
    void iso8601RejectsOtherForms(String text) {
        var issue = decodeErr(string().iso8601(), text);
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals(MessageKeys.INVALID_FORMAT_INSTANT, issue.messageKey());
        assertEquals("not a valid ISO 8601 instant", issue.message());
    }

    // --- year production, checked against the text java.time writes ---

    /**
     * Every sign and digit string up to seven digits from {@code 0}, {@code 1} and {@code 9}, as
     * the year of a date. A text is accepted exactly when {@link LocalDate#toString()} writes it,
     * which is what the year production is defined to be.
     */
    @Test
    void dateAcceptsAYearExactlyWhenLocalDateWritesIt() {
        for (var year : yearCandidates()) {
            var text = year + "-01-01";
            var value = new java.math.BigInteger(year);
            var written = value.abs().compareTo(java.math.BigInteger.valueOf(999_999_999)) <= 0
                    ? LocalDate.of(value.intValueExact(), 1, 1).toString() : null;
            var result = string().date().decode(text, net.unit8.raoh.Path.ROOT);
            assertEquals(text.equals(written), result instanceof net.unit8.raoh.Ok<?>, text);
        }
    }

    /** The same candidates as the year of an instant, against {@link Instant#toString()}. */
    @Test
    void iso8601AcceptsAYearExactlyWhenInstantWritesIt() {
        for (var year : yearCandidates()) {
            var text = year + "-01-01T00:00:00Z";
            var value = new java.math.BigInteger(year);
            String written = null;
            if (value.abs().compareTo(java.math.BigInteger.valueOf(999_999_999)) <= 0) {
                written = LocalDate.of(value.intValueExact(), 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toString();
            }
            var result = string().iso8601().decode(text, net.unit8.raoh.Path.ROOT);
            assertEquals(text.equals(written), result instanceof net.unit8.raoh.Ok<?>, text);
        }
    }

    private static List<String> yearCandidates() {
        var digits = new ArrayList<String>();
        digits.add("");
        var all = new ArrayList<String>();
        for (int length = 1; length <= 7; length++) {
            var next = new ArrayList<String>();
            for (var prefix : digits) {
                for (var d : new String[]{"0", "1", "9"}) {
                    next.add(prefix + d);
                }
            }
            digits = next;
            for (var d : digits) {
                all.add(d);
                all.add("+" + d);
                all.add("-" + d);
            }
        }
        return all;
    }

    // --- uri / url (issue #144) ---

    @ParameterizedTest
    @ValueSource(strings = {
            "urn:isbn:0451450523",
            "mailto:user@example.com",
            "file:///tmp/data.csv",
            "a:?q",                              // path-empty with a query
            "a://#f",                            // empty authority followed by a fragment
            "http://my_host/",                   // "_" is allowed in a reg-name
            "http://example.123/",               // a reg-name need not end in a letter
            "http://%41.com/",
            "HTTP://example.com/",
            "http://[::1]:8080/",
            "http://[::ffff:192.0.2.1]/",
            "http://h:99999999999/",             // a port is any digits (RFC 3986 section 3.2.3)
            "http://[::1]:2147483647/",
            "https://user:pass@example.com:8443/a/b;c?x=1&y=%20#frag/?"
    })
    void uriAcceptsRfc3986UrisAndKeepsTheirText(String text) {
        assertEquals(text, decodeOk(string().uri(), text).toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "foo/bar",                           // relative-ref, not a URI
            "../foo",
            "#fragment",
            "//example.com/",
            "1a:b",                              // a scheme starts with a letter
            "http://[fe80::1%25eth0]/",          // zone ID, removed by RFC 9844
            "http://[fe80::1%eth0]/",
            "http://exa mple.com",
            "https://example.com/%ZZ",
            "https://example.com/%2",
            "http://ex\u00e9.com/",             // raw non-ASCII
            "http://example.com/\u00e9",
            "http://[::1/",
            "http://[::00001]/",                 // a group is at most four digits
            "http://[::01.2.3.4]/",              // dec-octet has no leading zero
            "http://h:8x/",
            "http://a@b@c/",
            "a:b#c#d",
            "a:b c"
    })
    void uriRejectsTextOutsideRfc3986(String text) {
        var issue = decodeErr(string().uri(), text);
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals(MessageKeys.INVALID_FORMAT_URI, issue.messageKey());
        assertEquals("not a valid URI", issue.message());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "a:",                                // empty scheme-specific part
            "a:#f",
            "a://",                              // empty authority followed by nothing
            "http://[v1.abc]/",                  // IPvFuture
            "http://[V1f.a:b]/",
            "http://[::1]:2147483648/"           // IPv6 host with a port above Integer.MAX_VALUE
    })
    void uriRejectsRfc3986UrisThatJavaNetUriCannotHold(String text) {
        var issue = decodeErr(string().uri(), text);
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals("not a valid URI", issue.message());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://my_host/",
            "http://example.123/",
            "http://%41.com/",
            "HTTP://example.com/",
            "HtTpS://example.com",
            "http://[::1]/",
            "http://192.0.2.1:8080",
            "http://h:/",                        // empty port
            "https://user@example.com/a?b#c"
    })
    void urlAcceptsHttpUrisWithAnRfc3986Host(String text) {
        assertEquals(text, decodeOk(string().url(), text).toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://example.com",
            "https+x://example.com",
            "http:/path",                        // no authority
            "http:example.com",
            "http://",                           // empty host
            "http:///path",
            "http://user@/",
            "http://:80/",
            "http://[v1.abc]/",
            "http://[fe80::1%25eth0]/",
            "example.com"
    })
    void urlRejectsOtherText(String text) {
        var issue = decodeErr(string().url(), text);
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals(MessageKeys.INVALID_FORMAT_URL, issue.messageKey());
        assertEquals("not a valid URL", issue.message());
    }

    // --- ipv6 (issue #146) ---

    @ParameterizedTest
    @ValueSource(strings = {
            "1:2:3:4:5:6:7:8",
            "1:2:3:4:5:6:7::",
            "::2:3:4:5:6:7:8",
            "1:2:3:4:5:6:1.2.3.4",
            "1:2:3:4:5::1.2.3.4",
            "::",
            "fe80::1.2.3.4%eth0",
            "FE80::1%eth0"
    })
    void ipv6AcceptsTheRfc4291TextForms(String text) {
        assertEquals(text, decodeOk(string().ipv6(), text));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "::00001",                           // a group is 1 to 4 hex digits
            "::01.2.3.4",                        // dec-octet has no leading zeros
            "::1.2.3.04",
            "::256.1.1.1",
            "1:2:3:4:5:6:7:8:9",
            "1:2:3:4:5:6:7",
            "1::2::3",
            ":1::",
            "1::2:",
            ":::",
            "1:2:3:4:5:6::1.2.3.4",              // "::" stands for at least one group
            "1:2:3:4:5:6:7:1.2.3.4",
            "::1.2.3.4:1",
            "::g"
    })
    void ipv6RejectsTextOutsideRfc4291(String text) {
        var issue = decodeErr(string().ipv6(), text);
        assertEquals(ErrorCodes.INVALID_FORMAT, issue.code());
        assertEquals("not a valid IPv6 address", issue.message());
    }

    // --- round trip with ObjectEncoders ---

    @Test
    void everyLocalDateTheEncoderWritesDecodesBack() {
        for (var value : List.of(LocalDate.MIN, LocalDate.MAX, LocalDate.of(0, 1, 1),
                LocalDate.of(-1, 12, 31), LocalDate.of(9999, 12, 31), LocalDate.of(10000, 1, 1))) {
            assertEquals(value, decodeOk(string().date(), ObjectEncoders.date().encode(value)));
        }
    }

    @Test
    void everyLocalTimeTheEncoderWritesDecodesBack() {
        for (var value : List.of(LocalTime.MIN, LocalTime.MAX, LocalTime.of(10, 30),
                LocalTime.of(10, 30, 1), LocalTime.of(10, 30, 0, 100_000_000),
                LocalTime.of(10, 30, 0, 100_000), LocalTime.of(10, 30, 0, 1))) {
            assertEquals(value, decodeOk(string().time(), ObjectEncoders.time().encode(value)));
        }
    }

    @Test
    void everyLocalDateTimeTheEncoderWritesDecodesBack() {
        for (var value : List.of(LocalDateTime.MIN, LocalDateTime.MAX,
                LocalDateTime.of(2024, 1, 15, 10, 30))) {
            assertEquals(value, decodeOk(string().dateTime(), ObjectEncoders.dateTime().encode(value)));
        }
    }

    @Test
    void everyOffsetDateTimeTheEncoderWritesDecodesBack() {
        for (var value : List.of(OffsetDateTime.MIN, OffsetDateTime.MAX,
                OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.ofHoursMinutesSeconds(9, 0, 30)),
                OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 1, ZoneOffset.ofHoursMinutes(-5, -30)))) {
            assertEquals(value, decodeOk(string().offsetDateTime(), ObjectEncoders.offsetDateTime().encode(value)));
        }
    }

    @Test
    void everyInstantTheEncoderWritesDecodesBack() {
        for (var value : List.of(Instant.MIN, Instant.MAX, Instant.EPOCH,
                Instant.ofEpochSecond(1705314600, 100_000_000), Instant.ofEpochSecond(-1, 1))) {
            assertEquals(value, decodeOk(string().iso8601(), ObjectEncoders.iso8601().encode(value)));
        }
    }

    @Test
    void everyUuidTheEncoderWritesDecodesBack() {
        for (var value : List.of(new UUID(0, 0), new UUID(-1, -1), new UUID(0x550e8400e29b41d4L, 0xa716446655440000L))) {
            assertEquals(value, decodeOk(string().uuid(), ObjectEncoders.uuid().encode(value)));
        }
    }

    @Test
    void everyUriTheDecoderReturnsEncodesToTextItDecodesBack() {
        for (var text : List.of("urn:isbn:0451450523", "a:?q", "http://my_host/", "HTTP://example.com/",
                "http://[::1]:8080/p?q#f", "https://user@example.com/%7Efoo")) {
            var decoded = decodeOk(string().uri(), text);
            assertEquals(decoded, decodeOk(string().uri(), ObjectEncoders.uri().encode(decoded)));
        }
    }
}
