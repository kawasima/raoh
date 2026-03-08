package net.unit8.raoh.json;

import net.unit8.raoh.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static net.unit8.raoh.json.JsonDecoders.*;

class TemporalDecoderTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    // --- before (exclusive) ---

    @Test
    void beforeLocalDatePasses() {
        var dec = field("d", string().date().before(LocalDate.of(2025, 1, 1)));
        assertEquals(LocalDate.of(2024, 12, 31), assertOk(dec.decode(parse("{\"d\":\"2024-12-31\"}"))));
    }

    @Test
    void beforeLocalDateFailsOnEqual() {
        var dec = field("d", string().date().before(LocalDate.of(2025, 1, 1)));
        assertErr(dec.decode(parse("{\"d\":\"2025-01-01\"}")));
    }

    @Test
    void beforeLocalDateFailsOnLater() {
        var dec = field("d", string().date().before(LocalDate.of(2025, 1, 1)));
        assertErr(dec.decode(parse("{\"d\":\"2025-01-02\"}")));
    }

    // --- after (exclusive) ---

    @Test
    void afterLocalDatePasses() {
        var dec = field("d", string().date().after(LocalDate.of(2024, 1, 1)));
        assertEquals(LocalDate.of(2024, 1, 2), assertOk(dec.decode(parse("{\"d\":\"2024-01-02\"}"))));
    }

    @Test
    void afterLocalDateFailsOnEqual() {
        var dec = field("d", string().date().after(LocalDate.of(2024, 1, 1)));
        assertErr(dec.decode(parse("{\"d\":\"2024-01-01\"}")));
    }

    @Test
    void afterLocalDateFailsOnEarlier() {
        var dec = field("d", string().date().after(LocalDate.of(2024, 1, 1)));
        assertErr(dec.decode(parse("{\"d\":\"2023-12-31\"}")));
    }

    // --- between (inclusive) ---

    @Test
    void betweenLocalDatePassesOnLowerBound() {
        var dec = field("d", string().date().between(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)));
        assertEquals(LocalDate.of(2024, 1, 1), assertOk(dec.decode(parse("{\"d\":\"2024-01-01\"}"))));
    }

    @Test
    void betweenLocalDatePassesOnUpperBound() {
        var dec = field("d", string().date().between(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)));
        assertEquals(LocalDate.of(2024, 12, 31), assertOk(dec.decode(parse("{\"d\":\"2024-12-31\"}"))));
    }

    @Test
    void betweenLocalDateFailsBeforeLower() {
        var dec = field("d", string().date().between(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)));
        assertErr(dec.decode(parse("{\"d\":\"2023-12-31\"}")));
    }

    @Test
    void betweenLocalDateFailsAfterUpper() {
        var dec = field("d", string().date().between(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)));
        assertErr(dec.decode(parse("{\"d\":\"2025-01-01\"}")));
    }

    @Test
    void betweenRejectsInvertedRange() {
        assertThrows(IllegalArgumentException.class, () ->
                string().date().between(LocalDate.of(2025, 1, 1), LocalDate.of(2024, 1, 1)));
    }

    // --- chaining ---

    @Test
    void afterAndBeforeChain() {
        var dec = field("d", string().date()
                .after(LocalDate.of(2024, 1, 1))
                .before(LocalDate.of(2024, 12, 31)));
        assertEquals(LocalDate.of(2024, 6, 15), assertOk(dec.decode(parse("{\"d\":\"2024-06-15\"}"))));
        assertErr(dec.decode(parse("{\"d\":\"2024-01-01\"}")));
        assertErr(dec.decode(parse("{\"d\":\"2024-12-31\"}")));
    }

    // --- LocalTime ---

    @Test
    void beforeLocalTime() {
        var dec = field("t", string().time().before(LocalTime.of(17, 0)));
        assertEquals(LocalTime.of(9, 0), assertOk(dec.decode(parse("{\"t\":\"09:00:00\"}"))));
        assertErr(dec.decode(parse("{\"t\":\"17:00:00\"}")));
    }

    // --- Instant ---

    @Test
    void afterInstant() {
        var bound = Instant.parse("2024-01-01T00:00:00Z");
        var dec = field("ts", string().iso8601().after(bound));
        var result = assertOk(dec.decode(parse("{\"ts\":\"2024-06-01T00:00:00Z\"}")));
        assertTrue(result.isAfter(bound));
        assertErr(dec.decode(parse("{\"ts\":\"2024-01-01T00:00:00Z\"}")));
    }

    // --- LocalDateTime ---

    @Test
    void dateTimeParsing() {
        var dec = field("dt", string().dateTime());
        assertEquals(LocalDateTime.of(2024, 3, 15, 10, 30, 0),
                assertOk(dec.decode(parse("{\"dt\":\"2024-03-15T10:30:00\"}"))));
    }

    @Test
    void dateTimeWithBefore() {
        var dec = field("dt", string().dateTime()
                .before(LocalDateTime.of(2025, 1, 1, 0, 0)));
        assertOk(dec.decode(parse("{\"dt\":\"2024-12-31T23:59:59\"}")));
        assertErr(dec.decode(parse("{\"dt\":\"2025-01-01T00:00:00\"}")));
    }

    @Test
    void dateTimeInvalidFormat() {
        var dec = field("dt", string().dateTime());
        assertErr(dec.decode(parse("{\"dt\":\"not-a-datetime\"}")));
    }

    // --- OffsetDateTime ---

    @Test
    void offsetDateTimeParsing() {
        var dec = field("odt", string().offsetDateTime());
        var result = assertOk(dec.decode(parse("{\"odt\":\"2024-03-15T10:30:00+09:00\"}")));
        assertEquals(OffsetDateTime.of(2024, 3, 15, 10, 30, 0, 0, ZoneOffset.ofHours(9)), result);
    }

    @Test
    void offsetDateTimeWithAfter() {
        var bound = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        var dec = field("odt", string().offsetDateTime().after(bound));
        assertOk(dec.decode(parse("{\"odt\":\"2024-06-01T00:00:00Z\"}")));
        assertErr(dec.decode(parse("{\"odt\":\"2024-01-01T00:00:00Z\"}")));
    }

    @Test
    void offsetDateTimeInvalidFormat() {
        var dec = field("odt", string().offsetDateTime());
        assertErr(dec.decode(parse("{\"odt\":\"2024-03-15T10:30:00\"}")));
    }

    // --- custom message ---

    @Test
    void customMessageOnBefore() {
        var dec = field("d", string().date().before(LocalDate.of(2025, 1, 1), "too late"));
        var result = dec.decode(parse("{\"d\":\"2025-06-01\"}"));
        switch (result) {
            case Ok(_) -> fail("Expected Err");
            case Err(var issues) -> assertEquals("too late", issues.asList().getFirst().message());
        }
    }

    // --- meta verification ---

    @Test
    void metaContainsBeforeAndActual() {
        var dec = field("d", string().date().before(LocalDate.of(2025, 1, 1)));
        var result = dec.decode(parse("{\"d\":\"2025-06-01\"}"));
        switch (result) {
            case Ok(_) -> fail("Expected Err");
            case Err(var issues) -> {
                var issue = issues.asList().getFirst();
                assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
                assertEquals(LocalDate.of(2025, 1, 1), issue.meta().get("before"));
                assertEquals(LocalDate.of(2025, 6, 1), issue.meta().get("actual"));
            }
        }
    }

    @Test
    void metaContainsBetweenRange() {
        var from = LocalDate.of(2024, 1, 1);
        var to = LocalDate.of(2024, 12, 31);
        var dec = field("d", string().date().between(from, to));
        var result = dec.decode(parse("{\"d\":\"2025-06-01\"}"));
        switch (result) {
            case Ok(_) -> fail("Expected Err");
            case Err(var issues) -> {
                var issue = issues.asList().getFirst();
                assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
                assertEquals(from, issue.meta().get("from"));
                assertEquals(to, issue.meta().get("to"));
                assertEquals(LocalDate.of(2025, 6, 1), issue.meta().get("actual"));
            }
        }
    }

    // --- parse error regression ---

    @Test
    void invalidDateStillProducesFormatError() {
        var dec = field("d", string().date().before(LocalDate.of(2025, 1, 1)));
        var result = dec.decode(parse("{\"d\":\"not-a-date\"}"));
        switch (result) {
            case Ok(_) -> fail("Expected Err");
            case Err(var issues) -> assertEquals(ErrorCodes.INVALID_FORMAT, issues.asList().getFirst().code());
        }
    }

    // --- Helpers ---

    static <T> T assertOk(Result<T> result) {
        return switch (result) {
            case Ok(var value) -> value;
            case Err(var issues) -> { fail("Expected Ok, got: " + issues); yield null; }
        };
    }

    static void assertErr(Result<?> result) {
        if (result instanceof Ok<?>) {
            fail("Expected Err, got Ok: " + result);
        }
    }

    private static JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
