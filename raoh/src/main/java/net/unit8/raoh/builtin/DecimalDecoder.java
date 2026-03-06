package net.unit8.raoh.builtin;

import net.unit8.raoh.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A decoder for {@link BigDecimal} values with a fluent API for numeric constraints.
 *
 * @param <I> the input type
 */
public class DecimalDecoder<I> implements Decoder<I, BigDecimal> {

    private final Decoder<I, BigDecimal> inner;

    public DecimalDecoder(Decoder<I, BigDecimal> inner) {
        this.inner = inner;
    }

    @Override
    public Result<BigDecimal> decode(I in, Path path) {
        return inner.decode(in, path);
    }

    public DecimalDecoder<I> min(BigDecimal n) {
        return chain((value, path) -> {
            if (value.compareTo(n) < 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be at least %s".formatted(n),
                        Map.of("min", n, "actual", value));
            }
            return Result.ok(value);
        });
    }

    public DecimalDecoder<I> max(BigDecimal n) {
        return chain((value, path) -> {
            if (value.compareTo(n) > 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be at most %s".formatted(n),
                        Map.of("max", n, "actual", value));
            }
            return Result.ok(value);
        });
    }

    public DecimalDecoder<I> positive() {
        return chain((value, path) -> {
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be positive",
                        Map.of("min", BigDecimal.ZERO, "actual", value));
            }
            return Result.ok(value);
        });
    }

    public DecimalDecoder<I> negative() {
        return chain((value, path) -> {
            if (value.compareTo(BigDecimal.ZERO) >= 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be negative",
                        Map.of("max", BigDecimal.ZERO, "actual", value));
            }
            return Result.ok(value);
        });
    }

    public DecimalDecoder<I> nonNegative() {
        return chain((value, path) -> {
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be non-negative",
                        Map.of("min", BigDecimal.ZERO, "actual", value));
            }
            return Result.ok(value);
        });
    }

    public DecimalDecoder<I> nonPositive() {
        return chain((value, path) -> {
            if (value.compareTo(BigDecimal.ZERO) > 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be non-positive",
                        Map.of("max", BigDecimal.ZERO, "actual", value));
            }
            return Result.ok(value);
        });
    }

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
