package net.unit8.raoh;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Supplier;

import static net.unit8.raoh.decode.ObjectDecoders.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The temporal decoding paths covered here do not themselves read the JVM default time zone
 * (#141). Each case decodes one input object, created once, under UTC to get a baseline and then
 * under zones whose offset differs from it; the whole {@link Result} must be identical.
 *
 * <p>The {@code java.sql} cases are the ones #141 was about: converted with {@code toLocalDate()}
 * and its siblings, the same object gave a different value under each zone. The temporal decoders
 * now reject them, which gives the same issue under every zone.
 *
 * <p>No single instant falls on a different date both west and east of UTC: moving the date back
 * in Los Angeles needs an early UTC time, moving it forward in Tokyo a late one. So every case
 * runs on {@link #EARLY} and {@link #LATE}. The zones are one west of UTC, one east, and one whose
 * offset is not a whole number of hours; {@link #everyZoneMovesTheValues()} checks that each of
 * them moves the local date-time of both instants and the date of at least one, so the
 * comparison has something to detect.
 *
 * <p>This checks observable results for the cases listed here, not call sites. Keeping a new
 * zone-sensitive call out of a decoder that no case here covers is #151.
 */
@ResourceLock(Resources.TIME_ZONE)
class DefaultTimeZoneIndependenceTest {

    static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    static final List<TimeZone> ZONES = List.of(
            TimeZone.getTimeZone("America/Los_Angeles"),
            TimeZone.getTimeZone("Asia/Tokyo"),
            TimeZone.getTimeZone("Asia/Kolkata"));

    /** 03:30 UTC: the previous day in Los Angeles (UTC-7 in June). */
    static final Instant EARLY = Instant.parse("2024-06-15T03:30:00Z");
    /** 20:30 UTC: the next day in Tokyo (UTC+9) and Kolkata (UTC+5:30). */
    static final Instant LATE = Instant.parse("2024-06-15T20:30:00Z");

    static final List<Instant> INSTANTS = List.of(EARLY, LATE);

    static Map<String, Supplier<Object>> cases() {
        var cases = new LinkedHashMap<String, Supplier<Object>>();
        for (var instant : INSTANTS) {
            long millis = instant.toEpochMilli();
            var utc = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
            // One object per case, decoded under every zone: #141 was the same object giving
            // different results.
            var sqlDate = new java.sql.Date(millis);
            var sqlTime = new java.sql.Time(millis);
            var sqlTimestamp = new java.sql.Timestamp(millis);
            cases.put("date from java.sql.Date " + instant, () -> date().decode(sqlDate, Path.ROOT));
            cases.put("time from java.sql.Time " + instant, () -> time().decode(sqlTime, Path.ROOT));
            cases.put("dateTime from java.sql.Timestamp " + instant, () -> dateTime().decode(sqlTimestamp, Path.ROOT));
            cases.put("iso8601 from java.sql.Timestamp " + instant, () -> iso8601().decode(sqlTimestamp, Path.ROOT));
            cases.put("offsetDateTime from java.sql.Timestamp " + instant,
                    () -> offsetDateTime().decode(sqlTimestamp, Path.ROOT));
            // Text is the other representation the decoders read; none of it names a zone except
            // through an explicit offset, so parsing must not fall back to the default.
            cases.put("date from text " + instant, () -> date().decode(utc.toLocalDate().toString(), Path.ROOT));
            cases.put("time from text " + instant, () -> time().decode(utc.toLocalTime().toString(), Path.ROOT));
            cases.put("dateTime from text " + instant, () -> dateTime().decode(utc.toString(), Path.ROOT));
            cases.put("iso8601 from text " + instant, () -> iso8601().decode(instant.toString(), Path.ROOT));
            cases.put("offsetDateTime from text " + instant,
                    () -> offsetDateTime().decode(utc.atOffset(ZoneOffset.ofHoursMinutes(5, 30)).toString(), Path.ROOT));
        }
        return cases;
    }

    @Test
    void everyZoneMovesTheValues() {
        // Without this, a zone the JDK does not know (read as GMT), or instants whose date no zone
        // changes, would make every comparison below pass for the wrong reason.
        var baseline = withDefaultTimeZone(UTC, DefaultTimeZoneIndependenceTest::zoneProbe);
        for (var zone : ZONES) {
            var probe = withDefaultTimeZone(zone, DefaultTimeZoneIndependenceTest::zoneProbe);
            for (int i = 0; i < INSTANTS.size(); i++) {
                assertNotEquals(baseline.get(i), probe.get(i), zone.getID() + " at " + INSTANTS.get(i));
            }
            assertTrue(!baseline.get(0).toLocalDate().equals(probe.get(0).toLocalDate())
                            || !baseline.get(1).toLocalDate().equals(probe.get(1).toLocalDate()),
                    zone.getID() + " moves the date of neither instant");
        }
    }

    @Test
    void decoderResultsDoNotDependOnTheDefaultTimeZone() {
        // cases() is built once, so each case's input object is shared by every zone.
        // assertAll reports every case that differs, not only the first.
        assertAll(cases().entrySet().stream().flatMap(c -> {
            var baseline = withDefaultTimeZone(UTC, c.getValue());
            return ZONES.stream().map(zone -> () -> assertEquals(baseline,
                    withDefaultTimeZone(zone, c.getValue()), c.getKey() + " under " + zone.getID()));
        }));
    }

    @Test
    void baselineIsTheExpectedUtcResult() {
        // Pins the UTC results themselves, so that the comparison above cannot pass by every
        // zone producing the same wrong answer.
        var baseline = withDefaultTimeZone(UTC, () -> List.of(
                date().decode(new java.sql.Date(EARLY.toEpochMilli()), Path.ROOT),
                date().decode("2024-06-15", Path.ROOT)));
        var err = assertInstanceOf(Err.class, baseline.get(0));
        assertEquals(ErrorCodes.TYPE_MISMATCH, err.issues().asList().getFirst().code());
        assertEquals(Result.ok(LocalDate.of(2024, 6, 15)), baseline.get(1));
    }

    /** How the default zone reads each instant, through the conversion #141 removed. */
    private static List<LocalDateTime> zoneProbe() {
        return INSTANTS.stream().map(i -> new java.sql.Timestamp(i.toEpochMilli()).toLocalDateTime()).toList();
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
