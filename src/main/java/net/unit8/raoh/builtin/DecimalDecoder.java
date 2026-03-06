package net.unit8.raoh.builtin;

import net.unit8.raoh.*;

import java.math.BigDecimal;
import java.util.Map;

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
                return Result.fail(path, "out_of_range", "must be at least %s".formatted(n),
                        Map.of("min", n, "actual", value));
            }
            return Result.ok(value);
        });
    }

    public DecimalDecoder<I> max(BigDecimal n) {
        return chain((value, path) -> {
            if (value.compareTo(n) > 0) {
                return Result.fail(path, "out_of_range", "must be at most %s".formatted(n),
                        Map.of("max", n, "actual", value));
            }
            return Result.ok(value);
        });
    }

    public DecimalDecoder<I> positive() {
        return chain((value, path) -> {
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                return Result.fail(path, "out_of_range", "must be positive",
                        Map.of("actual", value));
            }
            return Result.ok(value);
        });
    }

    public DecimalDecoder<I> negative() {
        return chain((value, path) -> {
            if (value.compareTo(BigDecimal.ZERO) >= 0) {
                return Result.fail(path, "out_of_range", "must be negative",
                        Map.of("actual", value));
            }
            return Result.ok(value);
        });
    }

    public DecimalDecoder<I> nonNegative() {
        return chain((value, path) -> {
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                return Result.fail(path, "out_of_range", "must be non-negative",
                        Map.of("actual", value));
            }
            return Result.ok(value);
        });
    }

    public DecimalDecoder<I> nonPositive() {
        return chain((value, path) -> {
            if (value.compareTo(BigDecimal.ZERO) > 0) {
                return Result.fail(path, "out_of_range", "must be non-positive",
                        Map.of("actual", value));
            }
            return Result.ok(value);
        });
    }

    public DecimalDecoder<I> multipleOf(BigDecimal n) {
        return chain((value, path) -> {
            if (value.remainder(n).compareTo(BigDecimal.ZERO) != 0) {
                return Result.fail(path, "not_multiple_of",
                        "must be a multiple of %s".formatted(n),
                        Map.of("divisor", n, "actual", value));
            }
            return Result.ok(value);
        });
    }

    public DecimalDecoder<I> scale(int s) {
        return chain((value, path) -> {
            if (value.scale() > s) {
                return Result.fail(path, "invalid_scale",
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
