package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.MessageKeys;
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
 * A decoder for long integer values with a fluent API for numeric constraints.
 *
 * @param <I> the input type
 */
public final class LongDecoder<I extends @Nullable Object> implements Decoder<I, Long> {

    private final Decoder<I, Long> inner;

    /**
     * Creates a new long decoder wrapping the given inner decoder.
     *
     * @param inner the inner decoder that performs the actual decoding
     */
    public LongDecoder(Decoder<I, Long> inner) {
        this.inner = inner;
    }

    @Override
    public Result<Long> decode(I in, Path path) {
        return inner.decode(in, path);
    }

    /**
     * Restricts the decoded value to be at least {@code n}.
     *
     * @param n the minimum allowed value (inclusive)
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if below
     */
    public LongDecoder<I> min(long n) {
        return min(n, null);
    }

    /**
     * Restricts the decoded value to be at least {@code n}.
     *
     * @param n       the minimum allowed value (inclusive)
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if below
     */
    public LongDecoder<I> min(long n, @Nullable String message) {
        return chain((value, path) -> {
            if (value < n) {
                var meta = Map.<String, Object>of("min", n, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_MINIMUM, message,
                        "must be at least %d".formatted(n), meta);
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
    public LongDecoder<I> max(long n) {
        return max(n, null);
    }

    /**
     * Restricts the decoded value to be at most {@code n}.
     *
     * @param n       the maximum allowed value (inclusive)
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if above
     */
    public LongDecoder<I> max(long n, @Nullable String message) {
        return chain((value, path) -> {
            if (value > n) {
                var meta = Map.<String, Object>of("max", n, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_MAXIMUM, message,
                        "must be at most %d".formatted(n), meta);
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
    public LongDecoder<I> range(long min, long max) {
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
    public LongDecoder<I> range(long min, long max, @Nullable String message) {
        if (min > max) {
            throw new IllegalArgumentException("min (%d) must not be greater than max (%d)".formatted(min, max));
        }
        return chain((value, path) -> {
            if (value < min || value > max) {
                var meta = Map.<String, Object>of("min", min, "max", max, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_RANGE, message,
                        "must be between %d and %d".formatted(min, max), meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to be strictly positive.
     *
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if not positive
     */
    public LongDecoder<I> positive() {
        return positive(null);
    }

    /**
     * Restricts the decoded value to be strictly positive.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if not positive
     */
    public LongDecoder<I> positive(@Nullable String message) {
        return chain((value, path) -> {
            if (value <= 0) {
                var meta = Map.<String, Object>of("min", 1L, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_POSITIVE, message, "must be positive", meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to be strictly negative.
     *
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if not negative
     */
    public LongDecoder<I> negative() {
        return negative(null);
    }

    /**
     * Restricts the decoded value to be strictly negative.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if not negative
     */
    public LongDecoder<I> negative(@Nullable String message) {
        return chain((value, path) -> {
            if (value >= 0) {
                var meta = Map.<String, Object>of("max", -1L, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_NEGATIVE, message, "must be negative", meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to be zero or positive.
     *
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if negative
     */
    public LongDecoder<I> nonNegative() {
        return nonNegative(null);
    }

    /**
     * Restricts the decoded value to be zero or positive.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if negative
     */
    public LongDecoder<I> nonNegative(@Nullable String message) {
        return chain((value, path) -> {
            if (value < 0) {
                var meta = Map.<String, Object>of("min", 0L, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_NON_NEGATIVE, message, "must be non-negative", meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to be zero or negative.
     *
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if positive
     */
    public LongDecoder<I> nonPositive() {
        return nonPositive(null);
    }

    /**
     * Restricts the decoded value to be zero or negative.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if positive
     */
    public LongDecoder<I> nonPositive(@Nullable String message) {
        return chain((value, path) -> {
            if (value > 0) {
                var meta = Map.<String, Object>of("max", 0L, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_NON_POSITIVE, message, "must be non-positive", meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to one of the specified allowed values.
     *
     * @param allowed the set of allowed long values
     * @return a new decoder that fails with {@link ErrorCodes#NOT_ALLOWED} if the value is not in the set
     */
    public LongDecoder<I> oneOf(Long... allowed) {
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
     * Restricts the decoded value to be a multiple of {@code n}.
     *
     * @param n the divisor
     * @return a new decoder that fails with {@link ErrorCodes#NOT_MULTIPLE_OF} if not divisible
     * @throws IllegalArgumentException if {@code n} is zero
     */
    public LongDecoder<I> multipleOf(long n) {
        return multipleOf(n, null);
    }

    /**
     * Restricts the decoded value to be a multiple of {@code n}.
     *
     * @param n       the divisor
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#NOT_MULTIPLE_OF} if not divisible
     * @throws IllegalArgumentException if {@code n} is zero
     */
    public LongDecoder<I> multipleOf(long n, @Nullable String message) {
        if (n == 0) {
            throw new IllegalArgumentException("divisor must not be zero");
        }
        return chain((value, path) -> {
            if (value % n != 0) {
                var meta = Map.<String, Object>of("divisor", n, "actual", value);
                return Result.failWith(path, ErrorCodes.NOT_MULTIPLE_OF, message,
                        "must be a multiple of %d".formatted(n), meta);
            }
            return Result.ok(value);
        });
    }


    /**
     * {@inheritDoc}
     *
     * <p>The return type is narrowed to {@link LongDecoder} so that long constraints can still be chained
     * after the refinement.
     */
    @Override
    public LongDecoder<I> refine(Predicate<? super Long> ok, String code, String message) {
        return refine(ok, code, message, v -> Map.of());
    }

    /**
     * {@inheritDoc}
     *
     * <p>The return type is narrowed to {@link LongDecoder} so that long constraints can still be chained
     * after the refinement.
     */
    @Override
    public LongDecoder<I> refine(Predicate<? super Long> ok, String code, String message,
                                 Function<? super Long, ? extends Map<String, Object>> metaFn) {
        return refine(ok, (v, path) -> Result.failCustom(path, code, message, metaFn.apply(v)));
    }

    /**
     * {@inheritDoc}
     *
     * <p>The return type is narrowed to {@link LongDecoder} so that long constraints can still be chained
     * after the refinement.
     */
    @Override
    public LongDecoder<I> refine(Predicate<? super Long> ok,
                                 BiFunction<? super Long, ? super Path, ? extends Result<Long>> onFail) {
        return chain((value, path) -> ok.test(value) ? Result.ok(value) : onFail.apply(value, path));
    }

    private LongDecoder<I> chain(Decoder<Long, Long> constraint) {
        return new LongDecoder<>((in, path) ->
                this.decode(in, path).flatMap(value -> constraint.decode(value, path))
        );
    }
}
