package net.unit8.raoh;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Supplier;

import static net.unit8.raoh.decode.ObjectDecoders.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A decoder's result is a function of its input and configuration, never of the JVM default
 * time zone (#141). Each case is run under UTC to get a baseline, then under zones whose offset
 * differs from it; the whole {@link Result} must be identical.
 *
 * <p>The {@code java.sql} cases are the ones #141 was about: {@code new java.sql.Date(0L)}
 * converted with {@code toLocalDate()} is 1970-01-01 in UTC and 1969-12-31 in Los Angeles. The
 * temporal decoders now reject them, which gives the same issue under every zone.
 *
 * <p>This checks observable results, not call sites. The build's forbidden-API check is what
 * keeps a new zone-sensitive call from being added to a decoder that no case here covers.
 */
@ResourceLock(Resources.TIME_ZONE)
class DefaultTimeZoneIndependenceTest {

    static final List<TimeZone> ZONES = List.of(
            TimeZone.getTimeZone("America/Los_Angeles"),
            TimeZone.getTimeZone("Asia/Tokyo"),
            TimeZone.getTimeZone("Pacific/Kiritimati"));

    static Map<String, Supplier<Object>> cases() {
        var cases = new LinkedHashMap<String, Supplier<Object>>();
        cases.put("date from java.sql.Date", () -> date().decode(new java.sql.Date(0L), Path.ROOT));
        cases.put("time from java.sql.Time", () -> time().decode(new java.sql.Time(0L), Path.ROOT));
        cases.put("dateTime from java.sql.Timestamp", () -> dateTime().decode(new java.sql.Timestamp(0L), Path.ROOT));
        cases.put("iso8601 from java.sql.Timestamp", () -> iso8601().decode(new java.sql.Timestamp(0L), Path.ROOT));
        cases.put("offsetDateTime from java.sql.Timestamp",
                () -> offsetDateTime().decode(new java.sql.Timestamp(0L), Path.ROOT));
        cases.put("date from text", () -> date().decode("1970-01-01", Path.ROOT));
        cases.put("time from text", () -> time().decode("00:00:00", Path.ROOT));
        cases.put("dateTime from text", () -> dateTime().decode("1970-01-01T00:00:00", Path.ROOT));
        cases.put("iso8601 from text", () -> iso8601().decode("1970-01-01T00:00:00Z", Path.ROOT));
        cases.put("offsetDateTime from text", () -> offsetDateTime().decode("1970-01-01T00:00:00+09:00", Path.ROOT));
        cases.put("date after", () -> date().after(LocalDate.of(1970, 1, 2)).decode(LocalDate.EPOCH, Path.ROOT));
        cases.put("time before", () -> time().before(LocalTime.MIDNIGHT).decode(LocalTime.NOON, Path.ROOT));
        cases.put("dateTime between", () -> dateTime()
                .between(LocalDateTime.of(1970, 1, 1, 0, 0), LocalDateTime.of(1970, 1, 2, 0, 0))
                .decode(LocalDateTime.of(1971, 1, 1, 0, 0), Path.ROOT));
        cases.put("iso8601 before", () -> iso8601().before(Instant.EPOCH).decode(Instant.EPOCH.plusSeconds(1), Path.ROOT));
        cases.put("offsetDateTime after", () -> offsetDateTime()
                .after(OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))
                .decode(OffsetDateTime.of(1970, 1, 1, 9, 0, 0, 0, ZoneOffset.ofHours(9)), Path.ROOT));
        return cases;
    }

    @Test
    void theseZonesReallyDifferFromUtc() {
        // Without this, a zone the JDK does not know (read as GMT) would make every comparison
        // below pass for the wrong reason.
        var baseline = withDefaultTimeZone(TimeZone.getTimeZone("UTC"), DefaultTimeZoneIndependenceTest::zoneProbe);
        for (var zone : ZONES) {
            assertNotEquals(baseline, withDefaultTimeZone(zone, DefaultTimeZoneIndependenceTest::zoneProbe),
                    zone.getID());
        }
    }

    @Test
    void decoderResultsDoNotDependOnTheDefaultTimeZone() {
        // assertAll reports every case that differs, not only the first.
        assertAll(cases().entrySet().stream().flatMap(c -> {
            var baseline = withDefaultTimeZone(TimeZone.getTimeZone("UTC"), c.getValue());
            return ZONES.stream().map(zone -> () -> assertEquals(baseline,
                    withDefaultTimeZone(zone, c.getValue()), c.getKey() + " under " + zone.getID()));
        }));
    }

    @Test
    void baselineIsTheExpectedUtcResult() {
        // Pins the UTC results themselves, so that the comparison above cannot pass by every
        // zone producing the same wrong answer.
        var baseline = withDefaultTimeZone(TimeZone.getTimeZone("UTC"), () -> List.of(
                date().decode(new java.sql.Date(0L), Path.ROOT),
                date().decode("1970-01-01", Path.ROOT)));
        var err = assertInstanceOf(Err.class, baseline.get(0));
        assertEquals(ErrorCodes.TYPE_MISMATCH, err.issues().asList().getFirst().code());
        assertEquals(Result.ok(LocalDate.EPOCH), baseline.get(1));
    }

    private static LocalDateTime zoneProbe() {
        return new java.sql.Timestamp(0L).toLocalDateTime();
    }

    /**
     * Runs {@code body} with the JVM default time zone set to {@code zone}, restoring it afterwards.
     */
    static <T> T withDefaultTimeZone(TimeZone zone, Supplier<T> body) {
        var saved = TimeZone.getDefault();
        TimeZone.setDefault(zone);
        try {
            return body.get();
        } finally {
            TimeZone.setDefault(saved);
        }
    }
}
