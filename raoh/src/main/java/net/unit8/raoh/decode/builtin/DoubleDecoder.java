package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A decoder for double values with a fluent API for numeric constraints.
 *
 * <p>Comparisons use {@link Double#compare(double, double)} to handle
 * {@code NaN} and infinity correctly.
 *
 * @param <I> the input type
 */
public class DoubleDecoder<I> implements Decoder<I, Double> {

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
    public DoubleDecoder<I> min(double n, String message) {
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
    public DoubleDecoder<I> max(double n, String message) {
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
     */
    public DoubleDecoder<I> range(double min, double max, String message) {
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

    private DoubleDecoder<I> chain(Decoder<Double, Double> constraint) {
        return new DoubleDecoder<>((in, path) ->
                this.decode(in, path).flatMap(value -> constraint.decode(value, path))
        );
    }
}
