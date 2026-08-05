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
 * A decoder for float values with a fluent API for numeric constraints.
 *
 * <p>Comparisons use {@link Float#compare(float, float)} to handle
 * {@code NaN} and infinity correctly.
 *
 * @param <I> the input type
 */
public final class FloatDecoder<I extends @Nullable Object> implements Decoder<I, Float> {

    private final Decoder<I, Float> inner;

    /**
     * Creates a new float decoder wrapping the given inner decoder.
     *
     * @param inner the inner decoder that performs the actual decoding
     */
    public FloatDecoder(Decoder<I, Float> inner) {
        this.inner = inner;
    }

    @Override
    public Result<Float> decode(I in, Path path) {
        return inner.decode(in, path);
    }

    /**
     * Restricts the decoded value to be at least {@code n}.
     *
     * @param n the minimum allowed value (inclusive)
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if below
     */
    public FloatDecoder<I> min(float n) {
        return min(n, null);
    }

    /**
     * Restricts the decoded value to be at least {@code n}.
     *
     * @param n       the minimum allowed value (inclusive)
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if below
     */
    public FloatDecoder<I> min(float n, @Nullable String message) {
        return chain((value, path) -> {
            if (Float.compare(value, n) < 0) {
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
    public FloatDecoder<I> max(float n) {
        return max(n, null);
    }

    /**
     * Restricts the decoded value to be at most {@code n}.
     *
     * @param n       the maximum allowed value (inclusive)
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if above
     */
    public FloatDecoder<I> max(float n, @Nullable String message) {
        return chain((value, path) -> {
            if (Float.compare(value, n) > 0) {
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
    public FloatDecoder<I> range(float min, float max) {
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
    public FloatDecoder<I> range(float min, float max, @Nullable String message) {
        if (Float.compare(min, max) > 0) {
            throw new IllegalArgumentException("min (%s) must not be greater than max (%s)".formatted(min, max));
        }
        return chain((value, path) -> {
            if (Float.compare(value, min) < 0 || Float.compare(value, max) > 0) {
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
    public FloatDecoder<I> positive() {
        return chain((value, path) -> {
            if (Float.compare(value, 0.0f) <= 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be positive",
                        Map.of("min", 0.0f, "actual", value));
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to be strictly negative.
     *
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if not negative
     */
    public FloatDecoder<I> negative() {
        return chain((value, path) -> {
            if (Float.compare(value, 0.0f) >= 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be negative",
                        Map.of("max", 0.0f, "actual", value));
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to be zero or positive.
     *
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if negative
     */
    public FloatDecoder<I> nonNegative() {
        return chain((value, path) -> {
            if (Float.compare(value, 0.0f) < 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be non-negative",
                        Map.of("min", 0.0f, "actual", value));
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to be zero or negative.
     *
     * @return a new decoder that fails with {@link ErrorCodes#OUT_OF_RANGE} if positive
     */
    public FloatDecoder<I> nonPositive() {
        return chain((value, path) -> {
            if (Float.compare(value, 0.0f) > 0) {
                return Result.fail(path, ErrorCodes.OUT_OF_RANGE, "must be non-positive",
                        Map.of("max", 0.0f, "actual", value));
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to one of the specified allowed values.
     *
     * @param allowed the set of allowed float values
     * @return a new decoder that fails with {@link ErrorCodes#NOT_ALLOWED} if the value is not in the set
     */
    public FloatDecoder<I> oneOf(Float... allowed) {
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
     * {@inheritDoc}
     *
     * <p>The return type is narrowed to {@link FloatDecoder} so that float constraints can still be chained
     * after the refinement.
     */
    @Override
    public FloatDecoder<I> refine(Predicate<? super Float> ok, String code, String message) {
        return refine(ok, code, message, v -> Map.of());
    }

    /**
     * {@inheritDoc}
     *
     * <p>The return type is narrowed to {@link FloatDecoder} so that float constraints can still be chained
     * after the refinement.
     */
    @Override
    public FloatDecoder<I> refine(Predicate<? super Float> ok, String code, String message,
                                  Function<? super Float, ? extends Map<String, Object>> metaFn) {
        return refine(ok, (v, path) -> Result.failCustom(path, code, message, metaFn.apply(v)));
    }

    /**
     * {@inheritDoc}
     *
     * <p>The return type is narrowed to {@link FloatDecoder} so that float constraints can still be chained
     * after the refinement.
     */
    @Override
    public FloatDecoder<I> refine(Predicate<? super Float> ok,
                                  BiFunction<? super Float, ? super Path, ? extends Result<Float>> onFail) {
        return chain((value, path) -> ok.test(value) ? Result.ok(value) : onFail.apply(value, path));
    }

    private FloatDecoder<I> chain(Decoder<Float, Float> constraint) {
        return new FloatDecoder<>((in, path) ->
                this.decode(in, path).flatMap(value -> constraint.decode(value, path))
        );
    }
}
