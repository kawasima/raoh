package net.unit8.raoh;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static net.unit8.raoh.ObjectDecoders.*;
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
    void iso8601RejectsString() {
        switch (iso8601().decode("not a timestamp", Path.ROOT)) {
            case Ok<Instant> _ -> fail("Expected Err for String");
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
        switch (time().decode("10:30", Path.ROOT)) {
            case Ok<LocalTime> _ -> fail("Expected Err for String");
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
}
