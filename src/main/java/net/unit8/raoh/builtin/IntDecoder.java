package net.unit8.raoh.builtin;

import net.unit8.raoh.*;

import java.util.Map;

/**
 * A decoder for integer values with a fluent API for numeric constraints.
 *
 * @param <I> the input type
 */
public class IntDecoder<I> implements Decoder<I, Integer> {

    private final Decoder<I, Integer> inner;

    public IntDecoder(Decoder<I, Integer> inner) {
        this.inner = inner;
    }

    @Override
    public Result<Integer> decode(I in, Path path) {
        return inner.decode(in, path);
    }

    public IntDecoder<I> min(int n) {
        return min(n, null);
    }

    public IntDecoder<I> min(int n, String message) {
        return chain((value, path) -> {
            if (value < n) {
                var meta = Map.<String, Object>of("min", n, "actual", value);
                return message != null
                        ? Result.failCustom(path, "out_of_range", message, meta)
                        : Result.fail(path, "out_of_range", "must be at least %d".formatted(n), meta);
            }
            return Result.ok(value);
        });
    }

    public IntDecoder<I> max(int n) {
        return max(n, null);
    }

    public IntDecoder<I> max(int n, String message) {
        return chain((value, path) -> {
            if (value > n) {
                var meta = Map.<String, Object>of("max", n, "actual", value);
                return message != null
                        ? Result.failCustom(path, "out_of_range", message, meta)
                        : Result.fail(path, "out_of_range", "must be at most %d".formatted(n), meta);
            }
            return Result.ok(value);
        });
    }

    public IntDecoder<I> range(int min, int max) {
        return range(min, max, null);
    }

    public IntDecoder<I> range(int min, int max, String message) {
        return chain((value, path) -> {
            if (value < min || value > max) {
                var meta = Map.<String, Object>of("min", min, "max", max, "actual", value);
                return message != null
                        ? Result.failCustom(path, "out_of_range", message, meta)
                        : Result.fail(path, "out_of_range", "must be between %d and %d".formatted(min, max), meta);
            }
            return Result.ok(value);
        });
    }

    public IntDecoder<I> positive() {
        return chain((value, path) -> {
            if (value <= 0) {
                return Result.fail(path, "out_of_range", "must be positive",
                        Map.of("actual", value));
            }
            return Result.ok(value);
        });
    }

    public IntDecoder<I> negative() {
        return chain((value, path) -> {
            if (value >= 0) {
                return Result.fail(path, "out_of_range", "must be negative",
                        Map.of("actual", value));
            }
            return Result.ok(value);
        });
    }

    public IntDecoder<I> nonNegative() {
        return chain((value, path) -> {
            if (value < 0) {
                return Result.fail(path, "out_of_range", "must be non-negative",
                        Map.of("actual", value));
            }
            return Result.ok(value);
        });
    }

    public IntDecoder<I> nonPositive() {
        return chain((value, path) -> {
            if (value > 0) {
                return Result.fail(path, "out_of_range", "must be non-positive",
                        Map.of("actual", value));
            }
            return Result.ok(value);
        });
    }

    public IntDecoder<I> multipleOf(int n) {
        return chain((value, path) -> {
            if (value % n != 0) {
                return Result.fail(path, "not_multiple_of",
                        "must be a multiple of %d".formatted(n),
                        Map.of("divisor", n, "actual", value));
            }
            return Result.ok(value);
        });
    }

    private IntDecoder<I> chain(Decoder<Integer, Integer> constraint) {
        return new IntDecoder<>((in, path) ->
                this.decode(in, path).flatMap(value -> constraint.decode(value, path))
        );
    }
}
