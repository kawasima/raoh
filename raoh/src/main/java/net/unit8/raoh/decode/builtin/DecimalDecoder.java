package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A decoder for {@link BigDecimal} values with a fluent API for numeric constraints.
 *
 * @param <I> the input type
 */
public class DecimalDecoder<I> implements Decoder<I, BigDecimal> {

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
        return chain((value, path) -> {
            if (value.compareTo(n) < 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be at least %s".formatted(n),
                        Map.of("min", n, "actual", value));
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
        return chain((value, path) -> {
            if (value.compareTo(n) > 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be at most %s".formatted(n),
                        Map.of("max", n, "actual", value));
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
        return chain((value, path) -> {
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be positive",
                        Map.of("min", BigDecimal.ZERO, "actual", value));
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
        return chain((value, path) -> {
            if (value.compareTo(BigDecimal.ZERO) >= 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be negative",
                        Map.of("max", BigDecimal.ZERO, "actual", value));
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
        return chain((value, path) -> {
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be non-negative",
                        Map.of("min", BigDecimal.ZERO, "actual", value));
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
        return chain((value, path) -> {
            if (value.compareTo(BigDecimal.ZERO) > 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be non-positive",
                        Map.of("max", BigDecimal.ZERO, "actual", value));
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to be a multiple of {@code n}.
     *
     * @param n the required divisor
     * @return a new decoder that fails with {@link ErrorCodes#NOT_MULTIPLE_OF} if not a multiple
     */
    public DecimalDecoder<I> multipleOf(BigDecimal n) {
        return chain((value, path) -> {
            if (value.remainder(n).compareTo(BigDecimal.ZERO) != 0) {
                return Result.fail(path, ErrorCodes.NOT_MULTIPLE_OF,
                        "must be a multiple of %s".formatted(n),
                        Map.of("divisor", n, "actual", value));
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
        return chain((value, path) -> {
            if (value.scale() > s) {
                return Result.fail(path, ErrorCodes.INVALID_SCALE,
                        "too many decimal places (max %d)".formatted(s),
                        Map.of("maxScale", s, "actualScale", value.scale()));
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
