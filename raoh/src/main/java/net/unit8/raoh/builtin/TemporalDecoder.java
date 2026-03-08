package net.unit8.raoh.builtin;

import net.unit8.raoh.*;

import java.util.Map;

/**
 * A decoder for temporal values with a fluent API for range constraints.
 *
 * <p>Supports any {@link Comparable} temporal type such as {@link java.time.LocalDate},
 * {@link java.time.LocalTime}, {@link java.time.Instant}, {@link java.time.LocalDateTime},
 * and {@link java.time.OffsetDateTime}.
 *
 * <p><strong>Boundary semantics</strong></p>
 * <ul>
 *   <li>{@link #before(Comparable) before} and {@link #after(Comparable) after} are <strong>exclusive</strong>,
 *       based on the type's natural ordering ({@link Comparable#compareTo compareTo}).</li>
 *   <li>{@link #between(Comparable, Comparable) between} is <strong>inclusive</strong> on both ends,
 *       consistent with SQL {@code BETWEEN}.</li>
 *   <li>For inclusive before/after, use {@link Decoder#flatMap} directly.</li>
 * </ul>
 *
 * <p><strong>Note on {@link java.time.OffsetDateTime}</strong></p>
 * <p>{@link java.time.OffsetDateTime#compareTo} follows the type's natural ordering, which
 * differs from {@code isBefore}/{@code isAfter}: {@code compareTo} compares by instant first,
 * then by offset as a tiebreaker, so two values representing the same instant with different
 * offsets (e.g., {@code 10:00+09:00} and {@code 01:00Z}) are <em>not</em> considered equal.
 * This means exclusive boundaries may behave unexpectedly when comparing across offsets.
 *
 * @param <I> the input type
 * @param <T> the temporal type (must be {@link Comparable} to itself)
 */
public class TemporalDecoder<I, T extends Comparable<? super T>> implements Decoder<I, T> {

    private final Decoder<I, T> inner;

    /**
     * Creates a new temporal decoder wrapping the given inner decoder.
     *
     * @param inner the inner decoder that produces the temporal value
     */
    public TemporalDecoder(Decoder<I, T> inner) {
        this.inner = inner;
    }

    @Override
    public Result<T> decode(I in, Path path) {
        return inner.decode(in, path);
    }

    /**
     * Constrains the value to be strictly before the given bound (exclusive).
     *
     * @param bound the upper bound (exclusive)
     * @return a new decoder with the constraint applied
     */
    public TemporalDecoder<I, T> before(T bound) {
        return before(bound, null);
    }

    /**
     * Constrains the value to be strictly before the given bound (exclusive).
     *
     * @param bound   the upper bound (exclusive)
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder with the constraint applied
     */
    public TemporalDecoder<I, T> before(T bound, String message) {
        return chain((value, path) -> {
            if (value.compareTo(bound) >= 0) {
                var meta = Map.<String, Object>of("before", bound, "actual", value);
                return message != null
                        ? Result.failCustom(path, ErrorCodes.OUT_OF_RANGE, message, meta)
                        : Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be before %s".formatted(bound), meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Constrains the value to be strictly after the given bound (exclusive).
     *
     * @param bound the lower bound (exclusive)
     * @return a new decoder with the constraint applied
     */
    public TemporalDecoder<I, T> after(T bound) {
        return after(bound, null);
    }

    /**
     * Constrains the value to be strictly after the given bound (exclusive).
     *
     * @param bound   the lower bound (exclusive)
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder with the constraint applied
     */
    public TemporalDecoder<I, T> after(T bound, String message) {
        return chain((value, path) -> {
            if (value.compareTo(bound) <= 0) {
                var meta = Map.<String, Object>of("after", bound, "actual", value);
                return message != null
                        ? Result.failCustom(path, ErrorCodes.OUT_OF_RANGE, message, meta)
                        : Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be after %s".formatted(bound), meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Constrains the value to be within the given range, inclusive on both ends.
     *
     * @param from the lower bound (inclusive)
     * @param to   the upper bound (inclusive)
     * @return a new decoder with the constraint applied
     * @throws IllegalArgumentException if {@code from} is greater than {@code to}
     */
    public TemporalDecoder<I, T> between(T from, T to) {
        return between(from, to, null);
    }

    /**
     * Constrains the value to be within the given range, inclusive on both ends.
     *
     * @param from    the lower bound (inclusive)
     * @param to      the upper bound (inclusive)
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder with the constraint applied
     * @throws IllegalArgumentException if {@code from} is greater than {@code to}
     */
    public TemporalDecoder<I, T> between(T from, T to, String message) {
        if (from.compareTo(to) > 0) {
            throw new IllegalArgumentException(
                    "from (%s) must not be greater than to (%s)".formatted(from, to));
        }
        return chain((value, path) -> {
            if (value.compareTo(from) < 0 || value.compareTo(to) > 0) {
                var meta = Map.<String, Object>of("from", from, "to", to, "actual", value);
                return message != null
                        ? Result.failCustom(path, ErrorCodes.OUT_OF_RANGE, message, meta)
                        : Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be between %s and %s".formatted(from, to), meta);
            }
            return Result.ok(value);
        });
    }

    private TemporalDecoder<I, T> chain(Decoder<T, T> constraint) {
        return new TemporalDecoder<>((in, path) ->
                this.decode(in, path).flatMap(value -> constraint.decode(value, path))
        );
    }
}
