package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A decoder for {@link BigDecimal} values with a fluent API for numeric constraints.
 *
 * @param <I> the input type
 */
public class DecimalDecoder<I extends @Nullable Object> implements Decoder<I, BigDecimal> {

    private final Decoder<I, BigDecimal> inner;

    /**
     * Creates a new decimal decoder wrapping the given inner decoder.
     *
     * @param inner the inner decoder that performs the actual decoding
     */
    public DecimalDecoder(Decoder<I, BigDecimal> inner) {
        this.inner = inner;
    }

    @Override
    public Result<BigDecimal> decode(I in, Path path) {
        return inner.decode(in, path);
    }

    /**
     * Restricts the decoded value to be at least {@code n}.
     *
     * @param n the minimum allowed value (inclusive)
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if below
     */
    public DecimalDecoder<I> min(BigDecimal n) {
        return min(n, null);
    }

    /**
     * Restricts the decoded value to be at least {@code n}.
     *
     * @param n       the minimum allowed value (inclusive)
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if below
     */
    public DecimalDecoder<I> min(BigDecimal n, @Nullable String message) {
        return chain((value, path) -> {
            if (value.compareTo(n) < 0) {
                var meta = Map.<String, Object>of("min", n, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, message,
                        "must be at least %s".formatted(n), meta);
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
    public DecimalDecoder<I> max(BigDecimal n) {
        return max(n, null);
    }

    /**
     * Restricts the decoded value to be at most {@code n}.
     *
     * @param n       the maximum allowed value (inclusive)
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if above
     */
    public DecimalDecoder<I> max(BigDecimal n, @Nullable String message) {
        return chain((value, path) -> {
            if (value.compareTo(n) > 0) {
                var meta = Map.<String, Object>of("max", n, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, message,
                        "must be at most %s".formatted(n), meta);
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
    public DecimalDecoder<I> range(BigDecimal min, BigDecimal max) {
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
    public DecimalDecoder<I> range(BigDecimal min, BigDecimal max, @Nullable String message) {
        if (min.compareTo(max) > 0) {
            throw new IllegalArgumentException("min (%s) must not be greater than max (%s)".formatted(min, max));
        }
        return chain((value, path) -> {
            if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
                var meta = Map.<String, Object>of("min", min, "max", max, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, message,
                        "must be between %s and %s".formatted(min, max), meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to be strictly positive.
     *
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if not positive
     */
    public DecimalDecoder<I> positive() {
        return positive(null);
    }

    /**
     * Restricts the decoded value to be strictly positive.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if not positive
     */
    public DecimalDecoder<I> positive(@Nullable String message) {
        return chain((value, path) -> {
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                var meta = Map.<String, Object>of("min", BigDecimal.ZERO, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, message, "must be positive", meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to be strictly negative.
     *
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if not negative
     */
    public DecimalDecoder<I> negative() {
        return negative(null);
    }

    /**
     * Restricts the decoded value to be strictly negative.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if not negative
     */
    public DecimalDecoder<I> negative(@Nullable String message) {
        return chain((value, path) -> {
            if (value.compareTo(BigDecimal.ZERO) >= 0) {
                var meta = Map.<String, Object>of("max", BigDecimal.ZERO, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, message, "must be negative", meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to be zero or positive.
     *
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if negative
     */
    public DecimalDecoder<I> nonNegative() {
        return nonNegative(null);
    }

    /**
     * Restricts the decoded value to be zero or positive.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if negative
     */
    public DecimalDecoder<I> nonNegative(@Nullable String message) {
        return chain((value, path) -> {
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                var meta = Map.<String, Object>of("min", BigDecimal.ZERO, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, message, "must be non-negative", meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to be zero or negative.
     *
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if positive
     */
    public DecimalDecoder<I> nonPositive() {
        return nonPositive(null);
    }

    /**
     * Restricts the decoded value to be zero or negative.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if positive
     */
    public DecimalDecoder<I> nonPositive(@Nullable String message) {
        return chain((value, path) -> {
            if (value.compareTo(BigDecimal.ZERO) > 0) {
                var meta = Map.<String, Object>of("max", BigDecimal.ZERO, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, message, "must be non-positive", meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to be a multiple of {@code n}.
     *
     * @param n the required divisor
     * @return a new decoder that fails with {@link ErrorCodes#NOT_MULTIPLE_OF} if not a multiple
     * @throws IllegalArgumentException if {@code n} is zero
     */
    public DecimalDecoder<I> multipleOf(BigDecimal n) {
        return multipleOf(n, null);
    }

    /**
     * Restricts the decoded value to be a multiple of {@code n}.
     *
     * @param n       the required divisor
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#NOT_MULTIPLE_OF} if not a multiple
     * @throws IllegalArgumentException if {@code n} is zero
     */
    public DecimalDecoder<I> multipleOf(BigDecimal n, @Nullable String message) {
        if (n.signum() == 0) {
            throw new IllegalArgumentException("divisor must not be zero");
        }
        return chain((value, path) -> {
            if (value.remainder(n).compareTo(BigDecimal.ZERO) != 0) {
                var meta = Map.<String, Object>of("divisor", n, "actual", value);
                return Result.failWith(path, ErrorCodes.NOT_MULTIPLE_OF, message,
                        "must be a multiple of %s".formatted(n), meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the number of decimal places.
     *
     * @param s the maximum allowed scale (number of decimal places)
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_SCALE} if the scale exceeds {@code s}
     */
    public DecimalDecoder<I> scale(int s) {
        return scale(s, null);
    }

    /**
     * Restricts the number of decimal places.
     *
     * @param s       the maximum allowed scale (number of decimal places)
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_SCALE} if the scale exceeds {@code s}
     */
    public DecimalDecoder<I> scale(int s, @Nullable String message) {
        return chain((value, path) -> {
            if (value.scale() > s) {
                var meta = Map.<String, Object>of("maxScale", s, "actualScale", value.scale());
                return Result.failWith(path, ErrorCodes.INVALID_SCALE, message,
                        "too many decimal places (max %d)".formatted(s), meta);
            }
            return Result.ok(value);
        });
    }

    private DecimalDecoder<I> chain(Decoder<BigDecimal, BigDecimal> constraint) {
        return new DecimalDecoder<>((in, path) ->
                this.decode(in, path).flatMap(value -> constraint.decode(value, path))
        );
    }
}
