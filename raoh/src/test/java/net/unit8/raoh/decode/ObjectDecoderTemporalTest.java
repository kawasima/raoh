package net.unit8.raoh.decode;

import net.unit8.raoh.Err;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Issue;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.encode.ObjectEncoders;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static net.unit8.raoh.decode.ObjectDecoders.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for temporal decoders in {@link ObjectDecoders}, including
 * {@code java.sql.*} type conversion support.
 */
class ObjectDecoderTemporalTest {

    // --- iso8601() : Instant ---

    @Test
    void iso8601AcceptsInstant() {
        var instant = Instant.parse("2024-06-15T10:30:00Z");
        switch (iso8601().decode(instant, Path.ROOT)) {
            case Ok<Instant>(var v) -> assertEquals(instant, v);
            case Err<Instant>(var issues) -> fail("Expected Ok but got: " + issues);
        }
    }

    @Test
    void iso8601AcceptsSqlTimestamp() {
        var instant = Instant.parse("2024-06-15T10:30:00Z");
        var ts = java.sql.Timestamp.from(instant);
        switch (iso8601().decode(ts, Path.ROOT)) {
            case Ok<Instant>(var v) -> assertEquals(instant, v);
            case Err<Instant>(var issues) -> fail("Expected Ok but got: " + issues);
        }
    }

    @Test
    void iso8601RejectsNull() {
        switch (iso8601().decode(null, Path.ROOT)) {
            case Ok<Instant> _ -> fail("Expected Err for null");
            case Err<Instant>(var issues) ->
                    assertEquals(ErrorCodes.REQUIRED, issues.asList().getFirst().code());
        }
    }

    @Test
    void iso8601RejectsWrongType() {
        switch (iso8601().decode(42, Path.ROOT)) {
            case Ok<Instant> _ -> fail("Expected Err for Integer");
            case Err<Instant>(var issues) ->
                    assertEquals(ErrorCodes.TYPE_MISMATCH, issues.asList().getFirst().code());
        }
    }

    // --- date() : LocalDate ---

    @Test
    void dateAcceptsLocalDate() {
        var date = LocalDate.of(2024, 6, 15);
        switch (date().decode(date, Path.ROOT)) {
            case Ok<LocalDate>(var v) -> assertEquals(date, v);
            case Err<LocalDate>(var issues) -> fail("Expected Ok but got: " + issues);
        }
    }

    @Test
    void dateAcceptsSqlDate() {
        var date = LocalDate.of(2024, 6, 15);
        var sqlDate = java.sql.Date.valueOf(date);
        switch (date().decode(sqlDate, Path.ROOT)) {
            case Ok<LocalDate>(var v) -> assertEquals(date, v);
            case Err<LocalDate>(var issues) -> fail("Expected Ok but got: " + issues);
        }
    }

    @Test
    void dateRejectsNull() {
        switch (date().decode(null, Path.ROOT)) {
            case Ok<LocalDate> _ -> fail("Expected Err for null");
            case Err<LocalDate>(var issues) ->
                    assertEquals(ErrorCodes.REQUIRED, issues.asList().getFirst().code());
        }
    }

    @Test
    void dateRejectsWrongType() {
        switch (date().decode(42, Path.ROOT)) {
            case Ok<LocalDate> _ -> fail("Expected Err for Integer");
            case Err<LocalDate>(var issues) ->
                    assertEquals(ErrorCodes.TYPE_MISMATCH, issues.asList().getFirst().code());
        }
    }

    // --- time() : LocalTime ---

    @Test
    void timeAcceptsLocalTime() {
        var time = LocalTime.of(10, 30, 0);
        switch (time().decode(time, Path.ROOT)) {
            case Ok<LocalTime>(var v) -> assertEquals(time, v);
            case Err<LocalTime>(var issues) -> fail("Expected Ok but got: " + issues);
        }
    }

    @Test
    void timeAcceptsSqlTime() {
        var time = LocalTime.of(10, 30, 0);
        var sqlTime = java.sql.Time.valueOf(time);
        switch (time().decode(sqlTime, Path.ROOT)) {
            case Ok<LocalTime>(var v) -> assertEquals(time, v);
            case Err<LocalTime>(var issues) -> fail("Expected Ok but got: " + issues);
        }
    }

    @Test
    void timeRejectsNull() {
        switch (time().decode(null, Path.ROOT)) {
            case Ok<LocalTime> _ -> fail("Expected Err for null");
            case Err<LocalTime>(var issues) ->
                    assertEquals(ErrorCodes.REQUIRED, issues.asList().getFirst().code());
        }
    }

    @Test
    void timeRejectsWrongType() {
        switch (time().decode(42, Path.ROOT)) {
            case Ok<LocalTime> _ -> fail("Expected Err for Integer");
            case Err<LocalTime>(var issues) ->
                    assertEquals(ErrorCodes.TYPE_MISMATCH, issues.asList().getFirst().code());
        }
    }

    // --- dateTime() : LocalDateTime (unchanged, regression test) ---

    @Test
    void dateTimeAcceptsLocalDateTime() {
        var dt = LocalDateTime.of(2024, 6, 15, 10, 30);
        switch (dateTime().decode(dt, Path.ROOT)) {
            case Ok<LocalDateTime>(var v) -> assertEquals(dt, v);
            case Err<LocalDateTime>(var issues) -> fail("Expected Ok but got: " + issues);
        }
    }

    @Test
    void dateTimeRejectsNull() {
        switch (dateTime().decode(null, Path.ROOT)) {
            case Ok<LocalDateTime> _ -> fail("Expected Err for null");
            case Err<LocalDateTime>(var issues) ->
                    assertEquals(ErrorCodes.REQUIRED, issues.asList().getFirst().code());
        }
    }

    // --- offsetDateTime() : OffsetDateTime (unchanged, regression test) ---

    @Test
    void offsetDateTimeAcceptsOffsetDateTime() {
        var odt = OffsetDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneOffset.ofHours(9));
        switch (offsetDateTime().decode(odt, Path.ROOT)) {
            case Ok<OffsetDateTime>(var v) -> assertEquals(odt, v);
            case Err<OffsetDateTime>(var issues) -> fail("Expected Ok but got: " + issues);
        }
    }

    @Test
    void offsetDateTimeRejectsNull() {
        switch (offsetDateTime().decode(null, Path.ROOT)) {
            case Ok<OffsetDateTime> _ -> fail("Expected Err for null");
            case Err<OffsetDateTime>(var issues) ->
                    assertEquals(ErrorCodes.REQUIRED, issues.asList().getFirst().code());
        }
    }

    @Test
    void dateTimeAcceptsSqlTimestamp() {
        var dt = LocalDateTime.of(2024, 6, 15, 10, 30);
        var ts = java.sql.Timestamp.valueOf(dt);
        switch (dateTime().decode(ts, Path.ROOT)) {
            case Ok<LocalDateTime>(var v) -> assertEquals(dt, v);
            case Err<LocalDateTime>(var issues) -> fail("Expected Ok but got: " + issues);
        }
    }

    // --- ISO text: the representation ObjectEncoders writes ---

    @Test
    void iso8601DecodesWhatTheEncoderWrote() {
        var instant = Instant.parse("2024-06-15T10:30:00Z");
        assertRoundTrips(instant, ObjectEncoders.iso8601().encode(instant), iso8601());
    }

    @Test
    void dateDecodesWhatTheEncoderWrote() {
        var date = LocalDate.of(2024, 6, 15);
        assertRoundTrips(date, ObjectEncoders.date().encode(date), date());
    }

    @Test
    void timeDecodesWhatTheEncoderWrote() {
        var time = LocalTime.of(10, 30, 0);
        assertRoundTrips(time, ObjectEncoders.time().encode(time), time());
    }

    @Test
    void dateTimeDecodesWhatTheEncoderWrote() {
        var dt = LocalDateTime.of(2024, 6, 15, 10, 30);
        assertRoundTrips(dt, ObjectEncoders.dateTime().encode(dt), dateTime());
    }

    @Test
    void offsetDateTimeDecodesWhatTheEncoderWrote() {
        var odt = OffsetDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneOffset.ofHours(9));
        assertRoundTrips(odt, ObjectEncoders.offsetDateTime().encode(odt), offsetDateTime());
    }

    // --- unparseable text fails the same way as the string route ---

    @Test
    void iso8601FailsOnUnparseableTextLikeTheStringRoute() {
        assertSameFailure(iso8601().decode("nonsense", Path.ROOT),
                string().iso8601().decode("nonsense", Path.ROOT));
    }

    @Test
    void dateFailsOnUnparseableTextLikeTheStringRoute() {
        assertSameFailure(date().decode("nonsense", Path.ROOT),
                string().date().decode("nonsense", Path.ROOT));
    }

    @Test
    void timeFailsOnUnparseableTextLikeTheStringRoute() {
        assertSameFailure(time().decode("nonsense", Path.ROOT),
                string().time().decode("nonsense", Path.ROOT));
    }

    @Test
    void dateTimeFailsOnUnparseableTextLikeTheStringRoute() {
        assertSameFailure(dateTime().decode("nonsense", Path.ROOT),
                string().dateTime().decode("nonsense", Path.ROOT));
    }

    @Test
    void offsetDateTimeFailsOnUnparseableTextLikeTheStringRoute() {
        assertSameFailure(offsetDateTime().decode("nonsense", Path.ROOT),
                string().offsetDateTime().decode("nonsense", Path.ROOT));
    }

    private static <T> void assertRoundTrips(T original, Object encoded, Decoder<Object, T> decoder) {
        assertInstanceOf(String.class, encoded, "the encoder writes ISO text");
        switch (decoder.decode(encoded, Path.ROOT)) {
            case Ok<T>(var v) -> assertEquals(original, v);
            case Err<T>(var issues) -> fail("Expected Ok but got: " + issues);
        }
    }

    /**
     * Both routes read the same ISO text, so a value they both reject must be rejected with the
     * same code and the same message.
     */
    private static void assertSameFailure(Result<?> viaObject, Result<?> viaString) {
        Issue expected = firstIssue(viaString);
        Issue actual = firstIssue(viaObject);
        assertEquals(ErrorCodes.INVALID_FORMAT, actual.code());
        assertEquals(expected.code(), actual.code());
        assertEquals(expected.message(), actual.message());
    }

    private static Issue firstIssue(Result<?> result) {
        return switch (result) {
            case Ok<?> ok -> fail("Expected Err but got: " + ok);
            case Err<?>(var issues) -> issues.asList().getFirst();
        };
    }
}
