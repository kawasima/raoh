package net.unit8.raoh.decode.builtin;

import net.unit8.notation199x.TemporalText.Kind;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * The values of the temporal texts Raoh accepts.
 *
 * <p>Whether a text is a date, a time, a date-time, a date-time with an offset or an instant is
 * decided by {@link net.unit8.notation199x.TemporalText}, the grammar Raoh shares with Souther.
 * That grammar is a regular expression over ASCII plus arithmetic on its fields, so no JDK
 * formatter decides acceptance and their leniency (case-insensitive letters, hours-only offsets,
 * an empty fraction, the width-based sign rule of {@code SignStyle.EXCEEDS_PAD}) cannot widen the
 * accepted language.
 *
 * <p>The JDK only builds the value of a text that was admitted. Every admitted text is in the
 * form the matching {@code java.time} parser reads, and names a value in its range.
 *
 * <p>Everything {@code toString()} of the matching {@code java.time} type writes is accepted, so
 * the {@code ObjectEncoders} output always decodes back.
 */
final class TemporalText {

    private TemporalText() {
    }

    /**
     * Reads {@code yyyy-MM-dd}, with a year as {@link LocalDate#toString()} writes it.
     *
     * @param text the text to read
     * @return the date, or {@code null} if the text is not in the form or names no date
     */
    static @Nullable LocalDate date(String text) {
        return admitted(Kind.DATE, text) ? LocalDate.parse(text) : null;
    }

    /**
     * Reads {@code HH:mm}, {@code HH:mm:ss} or {@code HH:mm:ss} with a fraction of 1 to 9 digits.
     *
     * @param text the text to read
     * @return the time, or {@code null} if the text is not in the form or names no time
     */
    static @Nullable LocalTime time(String text) {
        return admitted(Kind.TIME, text) ? LocalTime.parse(text) : null;
    }

    /**
     * Reads a date as in {@link #date(String)}, an upper-case {@code T}, and a time as in
     * {@link #time(String)}.
     *
     * @param text the text to read
     * @return the date-time, or {@code null} if the text is not in the form or names none
     */
    static @Nullable LocalDateTime dateTime(String text) {
        return admitted(Kind.DATETIME, text) ? LocalDateTime.parse(text) : null;
    }

    /**
     * Reads a date-time as in {@link #dateTime(String)} followed by {@code Z}, {@code ±HH:mm} or
     * {@code ±HH:mm:ss}.
     *
     * @param text the text to read
     * @return the offset date-time, or {@code null} if the text is not in the form or names none
     */
    static @Nullable OffsetDateTime offsetDateTime(String text) {
        return admitted(Kind.OFFSET_DATETIME, text) ? OffsetDateTime.parse(text) : null;
    }

    /**
     * Reads the form of {@link #offsetDateTime(String)} with the seconds required, as the moment
     * it names. The year may reach the {@link Instant} range, beyond {@link LocalDate}'s.
     * {@code 24:00:00} with no fraction is the start of the next day; second {@code 60} is
     * rejected.
     *
     * @param text the text to read
     * @return the instant, or {@code null} if the text is not in the form or names no instant
     */
    static @Nullable Instant instant(String text) {
        return admitted(Kind.INSTANT, text) ? Instant.parse(text) : null;
    }

    private static boolean admitted(Kind kind, String text) {
        return net.unit8.notation199x.TemporalText.refusal(kind, text).isEmpty();
    }
}
