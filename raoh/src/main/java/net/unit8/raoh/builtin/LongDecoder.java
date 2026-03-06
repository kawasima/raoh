package net.unit8.raoh.builtin;

import net.unit8.raoh.*;

import java.util.Map;

/**
 * A decoder for long integer values with a fluent API for numeric constraints.
 *
 * @param <I> the input type
 */
public class LongDecoder<I> implements Decoder<I, Long> {

    private final Decoder<I, Long> inner;

    public LongDecoder(Decoder<I, Long> inner) {
        this.inner = inner;
    }

    @Override
    public Result<Long> decode(I in, Path path) {
        return inner.decode(in, path);
    }

    public LongDecoder<I> min(long n) {
        return min(n, null);
    }

    public LongDecoder<I> min(long n, String message) {
        return chain((value, path) -> {
            if (value < n) {
                var meta = Map.<String, Object>of("min", n, "actual", value);
                return message != null
                        ? Result.failCustom(path, ErrorCodes.OUT_OF_RANGE, message, meta)
                        : Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be at least %d".formatted(n), meta);
            }
            return Result.ok(value);
        });
    }

    public LongDecoder<I> max(long n) {
        return max(n, null);
    }

    public LongDecoder<I> max(long n, String message) {
        return chain((value, path) -> {
            if (value > n) {
                var meta = Map.<String, Object>of("max", n, "actual", value);
                return message != null
                        ? Result.failCustom(path, ErrorCodes.OUT_OF_RANGE, message, meta)
                        : Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be at most %d".formatted(n), meta);
            }
            return Result.ok(value);
        });
    }

    public LongDecoder<I> range(long min, long max) {
        return range(min, max, null);
    }

    public LongDecoder<I> range(long min, long max, String message) {
        return chain((value, path) -> {
            if (value < min || value > max) {
                var meta = Map.<String, Object>of("min", min, "max", max, "actual", value);
                return message != null
                        ? Result.failCustom(path, ErrorCodes.OUT_OF_RANGE, message, meta)
                        : Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be between %d and %d".formatted(min, max), meta);
            }
            return Result.ok(value);
        });
    }

    public LongDecoder<I> positive() {
        return chain((value, path) -> {
            if (value <= 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be positive", Map.of("min", 1L, "actual", value));
            }
            return Result.ok(value);
        });
    }

    public LongDecoder<I> negative() {
        return chain((value, path) -> {
            if (value >= 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be negative", Map.of("max", -1L, "actual", value));
            }
            return Result.ok(value);
        });
    }

    public LongDecoder<I> nonNegative() {
        return chain((value, path) -> {
            if (value < 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be non-negative", Map.of("min", 0L, "actual", value));
            }
            return Result.ok(value);
        });
    }

    public LongDecoder<I> nonPositive() {
        return chain((value, path) -> {
            if (value > 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be non-positive", Map.of("max", 0L, "actual", value));
            }
            return Result.ok(value);
        });
    }

    public LongDecoder<I> multipleOf(long n) {
        return chain((value, path) -> {
            if (value % n != 0) {
                return Result.fail(path, ErrorCodes.NOT_MULTIPLE_OF,
                        "must be a multiple of %d".formatted(n),
                        Map.of("divisor", n, "actual", value));
            }
            return Result.ok(value);
        });
    }

    private LongDecoder<I> chain(Decoder<Long, Long> constraint) {
        return new LongDecoder<>((in, path) ->
                this.decode(in, path).flatMap(value -> constraint.decode(value, path))
        );
    }
}
