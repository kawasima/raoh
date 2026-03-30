package net.unit8.raoh.encoder;

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
 * {@link net.unit8.raoh.ObjectDecoders ObjectDecoders}. Each encoder corresponds to the
 * decoder of the same name and produces an {@code Object} suitable for use as a JDBC parameter.
 *
 * <p>Combine with {@link Encoder#contramap} to unwrap value objects:
 *
 * <pre>{@code
 * import static net.unit8.raoh.encoder.ObjectEncoders.*;
 *
 * Encoder<UserId, Object> enc = long_().contramap(UserId::value);
 * }</pre>
 *
 * <p>Usage: {@code import static net.unit8.raoh.encoder.ObjectEncoders.*;}
 */
public final class ObjectEncoders {

    private ObjectEncoders() {}

    /**
     * Returns an encoder that passes a {@link String} through as-is.
     *
     * @return a string encoder
     */
    public static Encoder<String, Object> string() {
        return v -> v;
    }

    /**
     * Returns an encoder that passes an {@link Integer} through as-is.
     *
     * @return an integer encoder
     */
    public static Encoder<Integer, Object> int_() {
        return v -> v;
    }

    /**
     * Returns an encoder that passes a {@link Long} through as-is.
     *
     * @return a long encoder
     */
    public static Encoder<Long, Object> long_() {
        return v -> v;
    }

    /**
     * Returns an encoder that passes a {@link Boolean} through as-is.
     *
     * @return a boolean encoder
     */
    public static Encoder<Boolean, Object> bool() {
        return v -> v;
    }

    /**
     * Returns an encoder that passes a {@link BigDecimal} through as-is.
     *
     * @return a decimal encoder
     */
    public static Encoder<BigDecimal, Object> decimal() {
        return v -> v;
    }

    /**
     * Returns an encoder that converts an {@link Instant} to its ISO-8601 string representation.
     *
     * @return an instant encoder
     */
    public static Encoder<Instant, Object> iso8601() {
        return v -> v.toString();
    }

    /**
     * Returns an encoder that converts a {@link LocalDate} to its ISO-8601 string representation.
     *
     * @return a local date encoder
     */
    public static Encoder<LocalDate, Object> date() {
        return v -> v.toString();
    }

    /**
     * Returns an encoder that converts a {@link LocalTime} to its ISO-8601 string representation.
     *
     * @return a local time encoder
     */
    public static Encoder<LocalTime, Object> time() {
        return v -> v.toString();
    }

    /**
     * Returns an encoder that converts a {@link LocalDateTime} to its ISO-8601 string
     * representation.
     *
     * @return a local date-time encoder
     */
    public static Encoder<LocalDateTime, Object> dateTime() {
        return v -> v.toString();
    }

    /**
     * Returns an encoder that converts an {@link OffsetDateTime} to its ISO-8601 string
     * representation.
     *
     * @return an offset date-time encoder
     */
    public static Encoder<OffsetDateTime, Object> offsetDateTime() {
        return v -> v.toString();
    }

    /**
     * Returns an encoder that converts an enum constant to its {@link Enum#name() name} string.
     *
     * @param <E> the enum type
     * @return an enum encoder
     */
    public static <E extends Enum<E>> Encoder<E, Object> enumOf() {
        return v -> v.name();
    }
}
