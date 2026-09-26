package net.unit8.raoh.decode.builtin;

import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.format.SignStyle;
import java.util.Locale;
import java.util.regex.Pattern;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;

/**
 * The text forms Raoh accepts for its temporal conversions.
 *
 * <p>The formatters are built field by field instead of reusing {@code DateTimeFormatter.ISO_*},
 * whose grammar belongs to the JDK: those parse case-insensitively, accept an offset without
 * minutes ({@code +09}) and a decimal point with no digits after it ({@code 10:30:00.}). Here the
 * {@code T} and {@code Z} are upper case only, an offset is {@code Z}, {@code ±HH:mm} or
 * {@code ±HH:mm:ss}, and a fraction has 1 to 9 digits. Everything {@code toString()} of the
 * matching {@code java.time} type writes is accepted, so the {@code ObjectEncoders} output always
 * decodes back.
 */
final class TemporalFormats {

    /** {@code yyyy-MM-dd}; a year outside 0000–9999 carries a sign, as {@code LocalDate.toString()} writes it. */
    static final DateTimeFormatter DATE = strict(builder()
            .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
            .appendLiteral('-')
            .appendValue(MONTH_OF_YEAR, 2)
            .appendLiteral('-')
            .appendValue(DAY_OF_MONTH, 2));

    /** {@code HH:mm}, {@code HH:mm:ss} or {@code HH:mm:ss.S} with 1 to 9 fraction digits. */
    static final DateTimeFormatter TIME = strict(builder()
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
            .optionalStart()
            // A minimum width of 1 rejects a decimal point with no digits after it.
            .appendFraction(NANO_OF_SECOND, 1, 9, true));

    /** {@link #DATE}, an upper-case {@code T}, then {@link #TIME}. */
    static final DateTimeFormatter DATE_TIME = strict(builder()
            .append(DATE)
            .appendLiteral('T')
            .append(TIME));

    /** {@link #DATE_TIME} followed by {@code Z}, {@code ±HH:mm} or {@code ±HH:mm:ss}. */
    static final DateTimeFormatter OFFSET_DATE_TIME = strict(builder()
            .append(DATE_TIME)
            .appendOffset("+HH:MM:ss", "Z"));

    // An Instant spans years beyond LocalDate (Instant.MIN is year -1000000000), which only the
    // JDK's appendInstant can build, and appendInstant owns its grammar: it would accept "59.Z".
    // So the instant text is checked here and DateTimeFormatter.ISO_INSTANT only builds the value.
    private static final Pattern INSTANT = Pattern.compile(
            "(?:[0-9]{4}|\\+[0-9]{5,10}|-[0-9]{4,10})-[0-9]{2}-[0-9]{2}"
                    + "T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?"
                    + "(?:Z|[+-][0-9]{2}:[0-9]{2}(?::[0-9]{2})?)");

    private TemporalFormats() {
    }

    /**
     * Returns whether the value is an instant in the accepted text form: a date as in
     * {@link #DATE} (with years up to ten digits), an upper-case {@code T}, {@code HH:mm:ss} with
     * an optional fraction of 1 to 9 digits, and an offset as in {@link #OFFSET_DATE_TIME}.
     *
     * @param value the text to check
     * @return {@code true} if the value is accepted
     */
    static boolean isInstant(String value) {
        return INSTANT.matcher(value).matches();
    }

    // Case sensitivity is a parse state that applies to the elements appended after it.
    private static DateTimeFormatterBuilder builder() {
        return new DateTimeFormatterBuilder().parseCaseSensitive();
    }

    private static DateTimeFormatter strict(DateTimeFormatterBuilder builder) {
        return builder.toFormatter(Locale.ROOT)
                .withResolverStyle(ResolverStyle.STRICT)
                .withChronology(IsoChronology.INSTANCE);
    }
}
