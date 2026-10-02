package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.MessageKeys;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A decoder for temporal values with a fluent API for range constraints.
 *
 * <p>Supports any {@link Comparable} temporal type such as {@link java.time.LocalDate},
 * {@link java.time.LocalTime}, {@link java.time.Instant}, {@link java.time.LocalDateTime},
 * and {@link java.time.OffsetDateTime}.
 *
 * <p>The decoder compares values by the temporal order it holds, which it keeps through every
 * constraint and {@code refine} chained on it. Unless one is given, the order is the type's natural
 * ordering ({@link Comparable#compareTo compareTo}).
 *
 * <p><strong>Boundary semantics</strong></p>
 * <ul>
 *   <li>{@link #before(Comparable) before} and {@link #after(Comparable) after} are <strong>exclusive</strong>.</li>
 *   <li>{@link #between(Comparable, Comparable) between} is <strong>inclusive</strong> on both ends,
 *       consistent with SQL {@code BETWEEN}.</li>
 *   <li>For inclusive before/after, use {@link Decoder#flatMap} directly.</li>
 * </ul>
 *
 * <p><strong>Offset date-times</strong></p>
 * <p>The {@link java.time.OffsetDateTime} decoders Raoh provides compare by instant alone
 * ({@link java.time.OffsetDateTime#timeLineOrder()}), as the Raoh Specification does: {@code 09:00Z}
 * and {@code 10:00+01:00} are the same instant, so neither is before the other, and
 * {@code between(10:00+01:00, 10:00+01:00)} accepts {@code 09:00Z}. They are still different values:
 * the decoded value keeps the offset it was written with. {@link java.time.OffsetDateTime#compareTo}
 * would order them by local date-time after the instant, and is not used.
 *
 * @param <I> the input type
 * @param <T> the temporal type (must be {@link Comparable} to itself)
 */
public final class TemporalDecoder<I extends @Nullable Object, T extends Comparable<? super T>> implements Decoder<I, T> {

    private final Decoder<I, T> inner;
    private final Comparator<? super T> order;

    /**
     * Creates a new temporal decoder wrapping the given inner decoder, comparing values by their
     * natural ordering.
     *
     * @param inner the inner decoder that produces the temporal value
     */
    public TemporalDecoder(Decoder<I, T> inner) {
        this(inner, Comparator.naturalOrder());
    }

    /**
     * Creates a new temporal decoder wrapping the given inner decoder, comparing values by the
     * given temporal order.
     *
     * <p>The order decides {@link #before(Comparable) before}, {@link #after(Comparable) after},
     * {@link #between(Comparable, Comparable) between} and the check that {@code between}'s bounds
     * are ordered, and is kept by every decoder chained on this one.
     *
     * @param inner the inner decoder that produces the temporal value
     * @param order the temporal order to compare values by
     * @throws NullPointerException if {@code order} is {@code null}
     */
    public TemporalDecoder(Decoder<I, T> inner, Comparator<? super T> order) {
        this.inner = inner;
        this.order = Objects.requireNonNull(order, "order");
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
    public TemporalDecoder<I, T> before(T bound, @Nullable String message) {
        return chain((value, path) -> {
            if (order.compare(value, bound) >= 0) {
                var meta = Map.<String, Object>of("before", bound, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_BEFORE,
                        message, String.format(Locale.ROOT, "must be before %s", bound), meta);
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
    public TemporalDecoder<I, T> after(T bound, @Nullable String message) {
        return chain((value, path) -> {
            if (order.compare(value, bound) <= 0) {
                var meta = Map.<String, Object>of("after", bound, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_AFTER,
                        message, String.format(Locale.ROOT, "must be after %s", bound), meta);
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
     * @throws IllegalArgumentException if {@code from} is after {@code to} in this decoder's order
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
     * @throws IllegalArgumentException if {@code from} is after {@code to} in this decoder's order
     */
    public TemporalDecoder<I, T> between(T from, T to, @Nullable String message) {
        if (order.compare(from, to) > 0) {
            throw new IllegalArgumentException(
                    String.format(Locale.ROOT, "from (%s) must not be greater than to (%s)", from, to));
        }
        return chain((value, path) -> {
            if (order.compare(value, from) < 0 || order.compare(value, to) > 0) {
                var meta = Map.<String, Object>of("from", from, "to", to, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_BETWEEN,
                        message, String.format(Locale.ROOT, "must be between %s and %s", from, to), meta);
            }
            return Result.ok(value);
        });
    }


    /**
     * {@inheritDoc}
     *
     * <p>The return type is narrowed to {@link TemporalDecoder} so that temporal constraints can still be chained
     * after the refinement.
     */
    @Override
    public TemporalDecoder<I, T> refine(Predicate<? super T> ok, String code, String message) {
        return refine(ok, code, message, v -> Map.of());
    }

    /**
     * {@inheritDoc}
     *
     * <p>The return type is narrowed to {@link TemporalDecoder} so that temporal constraints can still be chained
     * after the refinement.
     */
    @Override
    public TemporalDecoder<I, T> refine(Predicate<? super T> ok, String code, String message,
                                        Function<? super T, ? extends Map<String, Object>> metaFn) {
        return refine(ok, (v, path) -> Result.failCustom(path, code, message, metaFn.apply(v)));
    }

    /**
     * {@inheritDoc}
     *
     * <p>The return type is narrowed to {@link TemporalDecoder} so that temporal constraints can still be chained
     * after the refinement.
     */
    @Override
    public TemporalDecoder<I, T> refine(Predicate<? super T> ok,
                                        BiFunction<? super T, ? super Path, ? extends Result<T>> onFail) {
        return chain((value, path) -> ok.test(value) ? Result.ok(value) : onFail.apply(value, path));
    }

    private TemporalDecoder<I, T> chain(Decoder<T, T> constraint) {
        return new TemporalDecoder<I, T>((in, path) ->
                this.decode(in, path).flatMap(value -> constraint.decode(value, path)),
                order);
    }
}
