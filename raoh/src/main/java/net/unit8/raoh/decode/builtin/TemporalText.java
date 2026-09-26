package net.unit8.raoh.decode.builtin;

import org.jspecify.annotations.Nullable;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The text forms Raoh accepts for its temporal conversions, and the values they denote.
 *
 * <p>Each form is a regular expression over ASCII characters, and that expression alone decides
 * whether a text is accepted. The JDK only builds the value and checks that it exists and is in
 * range (February 30, hour 25, an offset beyond 18 hours): the local forms hand the captured
 * fields to factories such as {@link LocalDate#of(int, int, int)} and
 * {@link ZoneOffset#ofHoursMinutesSeconds(int, int, int)}, and an instant, once matched, is built
 * by {@link DateTimeFormatter#ISO_INSTANT}. No JDK formatter decides acceptance, so their leniency
 * (case-insensitive letters, hours-only offsets, an empty fraction, the width-based sign rule of
 * {@code SignStyle.EXCEEDS_PAD}) cannot widen the accepted language.
 *
 * <p>Everything {@code toString()} of the matching {@code java.time} type writes is accepted, so
 * the {@code ObjectEncoders} output always decodes back.
 */
final class TemporalText {

    // A year as LocalDate.toString() and Instant.toString() write it: four digits and no sign for
    // 0000 to 9999; otherwise a sign, and no leading zero beyond the four-digit minimum. "-0000"
    // is not year 0. Ten digits cover the Instant range; the factories reject what is beyond.
    private static final String YEAR =
            "(?<year>[0-9]{4}|\\+[1-9][0-9]{4,9}|-(?!0000)[0-9]{4}|-[1-9][0-9]{4,9})";
    private static final String DATE = YEAR + "-(?<month>[0-9]{2})-(?<day>[0-9]{2})";
    private static final String HOUR_MINUTE = "(?<hour>[0-9]{2}):(?<minute>[0-9]{2})";
    // A fraction has 1 to 9 digits; a decimal point with none after it is not a fraction.
    private static final String SECOND = ":(?<second>[0-9]{2})(?:\\.(?<fraction>[0-9]{1,9}))?";
    private static final String TIME = HOUR_MINUTE + "(?:" + SECOND + ")?";
    private static final String OFFSET = "(?:(?<utc>Z)|(?<offsetSign>[+-])"
            + "(?<offsetHour>[0-9]{2}):(?<offsetMinute>[0-9]{2})(?::(?<offsetSecond>[0-9]{2}))?)";

    private static final Pattern DATE_PATTERN = Pattern.compile(DATE);
    private static final Pattern TIME_PATTERN = Pattern.compile(TIME);
    private static final Pattern DATE_TIME_PATTERN = Pattern.compile(DATE + "T" + TIME);
    private static final Pattern OFFSET_DATE_TIME_PATTERN = Pattern.compile(DATE + "T" + TIME + OFFSET);
    // An instant requires the seconds.
    private static final Pattern INSTANT_PATTERN = Pattern.compile(DATE + "T" + HOUR_MINUTE + SECOND + OFFSET);

    private TemporalText() {
    }

    /**
     * Reads {@code yyyy-MM-dd}, with a year as {@link LocalDate#toString()} writes it.
     *
     * @param text the text to read
     * @return the date, or {@code null} if the text is not in the form or names no date
     */
    static @Nullable LocalDate date(String text) {
        var m = DATE_PATTERN.matcher(text);
        return m.matches() ? date(m) : null;
    }

    /**
     * Reads {@code HH:mm}, {@code HH:mm:ss} or {@code HH:mm:ss} with a fraction of 1 to 9 digits.
     *
     * @param text the text to read
     * @return the time, or {@code null} if the text is not in the form or names no time
     */
    static @Nullable LocalTime time(String text) {
        var m = TIME_PATTERN.matcher(text);
        return m.matches() ? time(m) : null;
    }

    /**
     * Reads a date as in {@link #date(String)}, an upper-case {@code T}, and a time as in
     * {@link #time(String)}.
     *
     * @param text the text to read
     * @return the date-time, or {@code null} if the text is not in the form or names none
     */
    static @Nullable LocalDateTime dateTime(String text) {
        var m = DATE_TIME_PATTERN.matcher(text);
        return m.matches() ? dateTime(m) : null;
    }

    /**
     * Reads a date-time as in {@link #dateTime(String)} followed by {@code Z}, {@code ±HH:mm} or
     * {@code ±HH:mm:ss}.
     *
     * @param text the text to read
     * @return the offset date-time, or {@code null} if the text is not in the form or names none
     */
    static @Nullable OffsetDateTime offsetDateTime(String text) {
        var m = OFFSET_DATE_TIME_PATTERN.matcher(text);
        if (!m.matches()) {
            return null;
        }
        var dateTime = dateTime(m);
        var offset = offset(m);
        return dateTime == null || offset == null ? null : OffsetDateTime.of(dateTime, offset);
    }

    /**
     * Reads the form of {@link #offsetDateTime(String)} with the seconds required, as the moment
     * it names. The year may reach the {@link Instant} range, beyond {@link LocalDate}'s.
     * {@code 24:00:00} is the start of the next day; second {@code 60} is rejected.
     *
     * @param text the text to read
     * @return the instant, or {@code null} if the text is not in the form or names no instant
     */
    static @Nullable Instant instant(String text) {
        if (!INSTANT_PATTERN.matcher(text).matches()) {
            return null;
        }
        // The pattern decided the grammar. ISO_INSTANT only builds the value: it is the JDK's
        // constructor for the whole Instant range, whose years LocalDate cannot hold, and it
        // gives 24:00:00 its meaning as the start of the next day.
        try {
            var parsed = DateTimeFormatter.ISO_INSTANT.parse(text);
            // parsedLeapSecond() reports that the parser replaced second 60 with 59, a moment the
            // text does not name.
            return parsed.query(DateTimeFormatter.parsedLeapSecond()) ? null : Instant.from(parsed);
        } catch (DateTimeException e) {
            // A field out of range (month 13, hour 25), or a moment beyond Instant.MIN/MAX.
            return null;
        }
    }

    private static @Nullable LocalDate date(Matcher m) {
        long year = Long.parseLong(m.group("year"));
        if (year < Year.MIN_VALUE || year > Year.MAX_VALUE) {
            return null;
        }
        try {
            return LocalDate.of((int) year, Integer.parseInt(m.group("month")), Integer.parseInt(m.group("day")));
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static @Nullable LocalTime time(Matcher m) {
        var second = m.group("second");
        try {
            return LocalTime.of(Integer.parseInt(m.group("hour")), Integer.parseInt(m.group("minute")),
                    second == null ? 0 : Integer.parseInt(second), nano(m));
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static @Nullable LocalDateTime dateTime(Matcher m) {
        var date = date(m);
        var time = time(m);
        return date == null || time == null ? null : LocalDateTime.of(date, time);
    }

    private static int nano(Matcher m) {
        var fraction = m.group("fraction");
        if (fraction == null) {
            return 0;
        }
        int nano = Integer.parseInt(fraction);
        for (int i = fraction.length(); i < 9; i++) {
            nano *= 10;
        }
        return nano;
    }

    private static @Nullable ZoneOffset offset(Matcher m) {
        if (m.group("utc") != null) {
            return ZoneOffset.UTC;
        }
        int sign = "-".equals(m.group("offsetSign")) ? -1 : 1;
        var second = m.group("offsetSecond");
        try {
            return ZoneOffset.ofHoursMinutesSeconds(
                    sign * Integer.parseInt(m.group("offsetHour")),
                    sign * Integer.parseInt(m.group("offsetMinute")),
                    sign * (second == null ? 0 : Integer.parseInt(second)));
        } catch (DateTimeException e) {
            return null;
        }
    }
}
