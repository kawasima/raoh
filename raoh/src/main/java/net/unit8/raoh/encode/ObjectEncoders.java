package net.unit8.raoh.encode;

import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * Factory for primitive {@code Encoder} instances that encode domain values to {@code Object}.
 *
 * <p>These encoders are the dual of the primitive decoders in
 * {@link net.unit8.raoh.decode.ObjectDecoders ObjectDecoders}. Each encoder corresponds to the
 * decoder of the same name and produces an {@code Object} suitable for use as a JDBC parameter.
 *
 * <p>Combine with {@link Encoder#contramap} to unwrap value objects:
 *
 * <pre>{@code
 * import static net.unit8.raoh.encode.ObjectEncoders.*;
 *
 * Encoder<UserId, Object> enc = long_().contramap(UserId::value);
 * }</pre>
 *
 * <p>Usage: {@code import static net.unit8.raoh.encode.ObjectEncoders.*;}
 */
public final class ObjectEncoders {

    private ObjectEncoders() {}

    /**
     * Returns an encoder that passes a {@link String} through as-is.
     *
     * @return a string encoder
     */
    public static Encoder<@NonNull String, Object> string() {
        return v -> v;
    }

    /**
     * Returns an encoder that passes an {@link Integer} through as-is.
     *
     * @return an integer encoder
     */
    public static Encoder<@NonNull Integer, Object> int_() {
        return v -> v;
    }

    /**
     * Returns an encoder that passes a {@link Long} through as-is.
     *
     * @return a long encoder
     */
    public static Encoder<@NonNull Long, Object> long_() {
        return v -> v;
    }

    /**
     * Returns an encoder that passes a {@link Double} through as-is.
     *
     * @return a double encoder
     */
    public static Encoder<@NonNull Double, Object> double_() {
        return v -> v;
    }

    /**
     * Returns an encoder that passes a {@link Float} through as-is.
     *
     * @return a float encoder
     */
    public static Encoder<@NonNull Float, Object> float_() {
        return v -> v;
    }

    /**
     * Returns an encoder that passes a {@link Boolean} through as-is.
     *
     * @return a boolean encoder
     */
    public static Encoder<@NonNull Boolean, Object> bool() {
        return v -> v;
    }

    /**
     * Returns an encoder that passes a {@link BigDecimal} through as-is.
     *
     * @return a decimal encoder
     */
    public static Encoder<@NonNull BigDecimal, Object> decimal() {
        return v -> v;
    }

    /**
     * Returns an encoder that converts an {@link Instant} to its ISO-8601 string representation.
     *
     * @return an instant encoder
     */
    public static Encoder<@NonNull Instant, Object> iso8601() {
        return v -> v.toString();
    }

    /**
     * Returns an encoder that converts a {@link LocalDate} to its ISO-8601 string representation.
     *
     * @return a local date encoder
     */
    public static Encoder<@NonNull LocalDate, Object> date() {
        return v -> v.toString();
    }

    /**
     * Returns an encoder that converts a {@link LocalTime} to its ISO-8601 string representation.
     *
     * @return a local time encoder
     */
    public static Encoder<@NonNull LocalTime, Object> time() {
        return v -> v.toString();
    }

    /**
     * Returns an encoder that converts a {@link LocalDateTime} to a string via
     * {@link LocalDateTime#toString()}.
     *
     * <p>The output follows ISO-8601 but omits trailing zero seconds and nanoseconds
     * (e.g., {@code "2024-06-15T10:30"} rather than {@code "2024-06-15T10:30:00"}).
     *
     * @return a local date-time encoder
     */
    public static Encoder<@NonNull LocalDateTime, Object> dateTime() {
        return v -> v.toString();
    }

    /**
     * Returns an encoder that converts an {@link OffsetDateTime} to a string via
     * {@link OffsetDateTime#toString()}.
     *
     * <p>The output follows ISO-8601 but omits trailing zero seconds and nanoseconds
     * (e.g., {@code "2024-06-15T10:30+09:00"} rather than {@code "2024-06-15T10:30:00+09:00"}).
     *
     * @return an offset date-time encoder
     */
    public static Encoder<@NonNull OffsetDateTime, Object> offsetDateTime() {
        return v -> v.toString();
    }

    /**
     * Returns an encoder that converts an enum constant to its {@link Enum#name() name} string.
     *
     * @param <E> the enum type
     * @return an enum encoder
     */
    public static <E extends Enum<E>> Encoder<@NonNull E, Object> enumOf() {
        return v -> v.name();
    }

}
