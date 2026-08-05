package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import org.jspecify.annotations.Nullable;

import java.util.List;
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
public class DoubleDecoder<I extends @Nullable Object> implements Decoder<I, Double> {

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
                return message != null
                        ? Result.failCustom(path, ErrorCodes.OUT_OF_RANGE, message, meta)
                        : Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be at least %s".formatted(n), meta);
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
                return message != null
                        ? Result.failCustom(path, ErrorCodes.OUT_OF_RANGE, message, meta)
                        : Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be at most %s".formatted(n), meta);
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
            throw new IllegalArgumentException("min (%s) must not be greater than max (%s)".formatted(min, max));
        }
        return chain((value, path) -> {
            if (Double.compare(value, min) < 0 || Double.compare(value, max) > 0) {
                var meta = Map.<String, Object>of("min", min, "max", max, "actual", value);
                return message != null
                        ? Result.failCustom(path, ErrorCodes.OUT_OF_RANGE, message, meta)
                        : Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be between %s and %s".formatted(min, max), meta);
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
        return chain((value, path) -> {
            if (Double.compare(value, 0.0) <= 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be positive",
                        Map.of("min", 0.0, "actual", value));
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
        return chain((value, path) -> {
            if (Double.compare(value, 0.0) >= 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be negative",
                        Map.of("max", 0.0, "actual", value));
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
        return chain((value, path) -> {
            if (Double.compare(value, 0.0) < 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be non-negative",
                        Map.of("min", 0.0, "actual", value));
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
        return chain((value, path) -> {
            if (Double.compare(value, 0.0) > 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be non-positive",
                        Map.of("max", 0.0, "actual", value));
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to one of the specified allowed values.
     *
     * @param allowed the set of allowed double values
     * @return a new decoder that fails with {@link ErrorCodes#NOT_ALLOWED} if the value is not in the set
     */
    public DoubleDecoder<I> oneOf(Double... allowed) {
        var allowedSet = Set.of(allowed);
        var sortedAllowed = List.copyOf(new TreeSet<>(allowedSet));
        var message = "must be one of %s".formatted(sortedAllowed);
        return chain((value, path) -> {
            if (!allowedSet.contains(value)) {
                var meta = Map.<String, Object>of("allowed", sortedAllowed, "actual", value);
                return Result.fail(path, ErrorCodes.NOT_ALLOWED, message, meta);
            }
            return Result.ok(value);
        });
    }


    /**
     * Refines the decoded value with a predicate, narrowing the inherited return type so that this
     * decoder's own constraints can still be chained after the refinement.
     *
     * @param ok      the predicate the decoded value must satisfy
     * @param code    the error code to report on failure
     * @param message the error message to report on failure
     * @return a decoder that fails with the given code and message when {@code ok} rejects the value
     */
    @Override
    public DoubleDecoder<I> refine(Predicate<? super Double> ok, String code, String message) {
        return refine(ok, code, message, v -> Map.of());
    }

    /**
     * Refines the decoded value with a predicate, attaching metadata derived from the failing value.
     * Narrows the inherited return type so that this decoder's own constraints can still be chained.
     *
     * @param ok      the predicate the decoded value must satisfy
     * @param code    the error code to report on failure
     * @param message the error message to report on failure
     * @param metaFn  a function producing the issue metadata from the failing value
     * @return a decoder that fails with the given code, message, and metadata when {@code ok}
     *         rejects the value
     */
    @Override
    public DoubleDecoder<I> refine(Predicate<? super Double> ok, String code, String message,
                                   Function<? super Double, ? extends Map<String, Object>> metaFn) {
        return refine(ok, (v, path) -> Result.failCustom(path, code, message, metaFn.apply(v)));
    }

    /**
     * Refines the decoded value with a predicate whose failure branch is caller-controlled.
     * Narrows the inherited return type so that this decoder's own constraints can still be chained.
     *
     * @param ok     the predicate the decoded value must satisfy
     * @param onFail builds the failing result from the rejected value and the current path
     * @return a decoder that delegates to {@code onFail} when {@code ok} rejects the value
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
