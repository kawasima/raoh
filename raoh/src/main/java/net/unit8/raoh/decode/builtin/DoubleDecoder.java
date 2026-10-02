package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.MessageKeys;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A decoder for double values with a fluent API for numeric constraints.
 *
 * <p>Comparisons use {@link Double#compare(double, double)} to handle
 * {@code NaN} and infinity correctly.
 *
 * @param <I> the input type
 */
public final class DoubleDecoder<I extends @Nullable Object> implements Decoder<I, Double> {

    private final Decoder<I, Double> inner;

    /**
     * Creates a new double decoder wrapping the given inner decoder.
     *
     * @param inner the inner decoder that performs the actual decoding
     */
    public DoubleDecoder(Decoder<I, Double> inner) {
        this.inner = inner;
    }

    @Override
    public Result<Double> decode(I in, Path path) {
        return inner.decode(in, path);
    }

    /**
     * Restricts the decoded value to be at least {@code n}.
     *
     * @param n the minimum allowed value (inclusive)
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if below
     */
    public DoubleDecoder<I> min(double n) {
        return min(n, null);
    }

    /**
     * Restricts the decoded value to be at least {@code n}.
     *
     * @param n       the minimum allowed value (inclusive)
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if below
     */
    public DoubleDecoder<I> min(double n, @Nullable String message) {
        return chain((value, path) -> {
            if (Double.compare(value, n) < 0) {
                var meta = Map.<String, Object>of("min", n, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_MINIMUM,
                        message, String.format(Locale.ROOT, "must be at least %s", n), meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to be at most {@code n}.
     *
     * @param n the maximum allowed value (inclusive)
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if above
     */
    public DoubleDecoder<I> max(double n) {
        return max(n, null);
    }

    /**
     * Restricts the decoded value to be at most {@code n}.
     *
     * @param n       the maximum allowed value (inclusive)
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if above
     */
    public DoubleDecoder<I> max(double n, @Nullable String message) {
        return chain((value, path) -> {
            if (Double.compare(value, n) > 0) {
                var meta = Map.<String, Object>of("max", n, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_MAXIMUM,
                        message, String.format(Locale.ROOT, "must be at most %s", n), meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to be within the given range (inclusive).
     *
     * @param min the minimum allowed value (inclusive)
     * @param max the maximum allowed value (inclusive)
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if outside
     * @throws IllegalArgumentException if {@code min} is greater than {@code max}
     */
    public DoubleDecoder<I> range(double min, double max) {
        return range(min, max, null);
    }

    /**
     * Restricts the decoded value to be within the given range (inclusive).
     *
     * @param min     the minimum allowed value (inclusive)
     * @param max     the maximum allowed value (inclusive)
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if outside
     * @throws IllegalArgumentException if {@code min} is greater than {@code max}
     */
    public DoubleDecoder<I> range(double min, double max, @Nullable String message) {
        if (Double.compare(min, max) > 0) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "min (%s) must not be greater than max (%s)", min, max));
        }
        return chain((value, path) -> {
            if (Double.compare(value, min) < 0 || Double.compare(value, max) > 0) {
                var meta = Map.<String, Object>of("min", min, "max", max, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_RANGE,
                        message, String.format(Locale.ROOT, "must be between %s and %s", min, max), meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to be strictly positive.
     *
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if not positive
     */
    public DoubleDecoder<I> positive() {
        return positive(null);
    }

    /**
     * Restricts the decoded value to be strictly positive.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if not positive
     */
    public DoubleDecoder<I> positive(@Nullable String message) {
        return chain((value, path) -> {
            if (Double.compare(value, 0.0) <= 0) {
                var meta = Map.<String, Object>of("min", 0.0, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_POSITIVE,
                        message, "must be positive", meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to be strictly negative.
     *
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if not negative
     */
    public DoubleDecoder<I> negative() {
        return negative(null);
    }

    /**
     * Restricts the decoded value to be strictly negative.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if not negative
     */
    public DoubleDecoder<I> negative(@Nullable String message) {
        return chain((value, path) -> {
            if (Double.compare(value, 0.0) >= 0) {
                var meta = Map.<String, Object>of("max", 0.0, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_NEGATIVE,
                        message, "must be negative", meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to be zero or positive.
     *
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if negative
     */
    public DoubleDecoder<I> nonNegative() {
        return nonNegative(null);
    }

    /**
     * Restricts the decoded value to be zero or positive.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if negative
     */
    public DoubleDecoder<I> nonNegative(@Nullable String message) {
        return chain((value, path) -> {
            if (Double.compare(value, 0.0) < 0) {
                var meta = Map.<String, Object>of("min", 0.0, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_NON_NEGATIVE,
                        message, "must be non-negative", meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to be zero or negative.
     *
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if positive
     */
    public DoubleDecoder<I> nonPositive() {
        return nonPositive(null);
    }

    /**
     * Restricts the decoded value to be zero or negative.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if positive
     */
    public DoubleDecoder<I> nonPositive(@Nullable String message) {
        return chain((value, path) -> {
            if (Double.compare(value, 0.0) > 0) {
                var meta = Map.<String, Object>of("max", 0.0, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_NON_POSITIVE,
                        message, "must be non-positive", meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to one of the specified allowed values.
     *
     * <p>The issue lists the allowed values in ascending order.
     *
     * <p>Values are compared with {@link Double#equals(Object)}: {@code -0.0} and {@code 0.0} differ,
     * and {@code NaN} is {@code NaN}.
     *
     * @param allowed the allowed double values, distinct
     * @return a new decoder that fails with {@link ErrorCodes#NOT_ALLOWED} if the value is not in the set
     * @throws IllegalArgumentException if a value occurs twice
     * @throws NullPointerException     if one of the values is {@code null}
     */
    public DoubleDecoder<I> oneOf(Double... allowed) {
        return oneOf(List.of(allowed), null);
    }

    /**
     * Restricts the decoded value to one of the specified allowed values.
     *
     * <p>The issue lists the allowed values in ascending order.
     *
     * <p>Values are compared with {@link Double#equals(Object)}: {@code -0.0} and {@code 0.0} differ,
     * and {@code NaN} is {@code NaN}. The collection is copied, so changing it afterwards does not
     * change the decoder.
     *
     * @param allowed the allowed double values, distinct
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#NOT_ALLOWED} if the value is not in the set
     * @throws IllegalArgumentException if a value occurs twice
     * @throws NullPointerException     if {@code allowed} or one of its values is {@code null}
     */
    public DoubleDecoder<I> oneOf(Collection<? extends Double> allowed, @Nullable String message) {
        var values = List.<Double>copyOf(allowed);
        var allowedSet = Set.copyOf(values);
        if (allowedSet.size() != values.size()) {
            throw new IllegalArgumentException("the allowed values must be distinct: " + values);
        }
        var sortedAllowed = List.copyOf(new TreeSet<>(allowedSet));
        var defaultMessage = String.format(Locale.ROOT, "must be one of %s", sortedAllowed);
        return chain((value, path) -> {
            if (!allowedSet.contains(value)) {
                var meta = Map.<String, Object>of("allowed", sortedAllowed, "actual", value);
                return Result.failWith(path, ErrorCodes.NOT_ALLOWED, message, defaultMessage, meta);
            }
            return Result.ok(value);
        });
    }


    /**
     * {@inheritDoc}
     *
     * <p>The return type is narrowed to {@link DoubleDecoder} so that double constraints can still be chained
     * after the refinement.
     */
    @Override
    public DoubleDecoder<I> refine(Predicate<? super Double> ok, String code, String message) {
        return refine(ok, code, message, v -> Map.of());
    }

    /**
     * {@inheritDoc}
     *
     * <p>The return type is narrowed to {@link DoubleDecoder} so that double constraints can still be chained
     * after the refinement.
     */
    @Override
    public DoubleDecoder<I> refine(Predicate<? super Double> ok, String code, String message,
                                   Function<? super Double, ? extends Map<String, Object>> metaFn) {
        return refine(ok, (v, path) -> Result.failCustom(path, code, message, metaFn.apply(v)));
    }

    /**
     * {@inheritDoc}
     *
     * <p>The return type is narrowed to {@link DoubleDecoder} so that double constraints can still be chained
     * after the refinement.
     */
    @Override
    public DoubleDecoder<I> refine(Predicate<? super Double> ok,
                                   BiFunction<? super Double, ? super Path, ? extends Result<Double>> onFail) {
        return chain((value, path) -> ok.test(value) ? Result.ok(value) : onFail.apply(value, path));
    }

    private DoubleDecoder<I> chain(Decoder<Double, Double> constraint) {
        return new DoubleDecoder<>((in, path) ->
                this.decode(in, path).flatMap(value -> constraint.decode(value, path))
        );
    }
}
