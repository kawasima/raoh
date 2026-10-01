package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.MessageKeys;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A decoder for {@link BigDecimal} values with a fluent API for numeric constraints.
 *
 * @param <I> the input type
 */
public final class DecimalDecoder<I extends @Nullable Object> implements Decoder<I, BigDecimal> {

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
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_MINIMUM, message,
                        String.format(Locale.ROOT, "must be at least %s", n), meta);
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
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_MAXIMUM, message,
                        String.format(Locale.ROOT, "must be at most %s", n), meta);
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
            throw new IllegalArgumentException(String.format(Locale.ROOT, "min (%s) must not be greater than max (%s)", min, max));
        }
        return chain((value, path) -> {
            if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
                var meta = Map.<String, Object>of("min", min, "max", max, "actual", value);
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_RANGE, message,
                        String.format(Locale.ROOT, "must be between %s and %s", min, max), meta);
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
                return Result.failWith(path, ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_NON_POSITIVE, message, "must be non-positive", meta);
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
     * <p>Decided for any two decimals, however far apart their scales are: {@code 1E+100} is not a
     * multiple of {@code 7E-2147483647}, and that is found without building the power of ten their
     * scales differ by.
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
            if (!isMultiple(value, n)) {
                var meta = Map.<String, Object>of("divisor", n, "actual", value);
                return Result.failWith(path, ErrorCodes.NOT_MULTIPLE_OF, message,
                        String.format(Locale.ROOT, "must be a multiple of %s", n), meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Whether {@code value} is an integer multiple of {@code divisor}, which is not zero.
     *
     * <p>With {@code value = u_v × 10^-s_v} and {@code divisor = u_d × 10^-s_d}, the quotient is
     * {@code (u_v / u_d) × 10^d} with {@code d = s_d - s_v}. Where {@code d} is not negative it is
     * an integer when {@code u_d} divides {@code u_v × 10^d}, which is asked modulo {@code |u_d|}
     * with {@code 10^d} worked out by modular exponentiation. Where it is negative, {@code u_v} has
     * to end in at least {@code -d} decimal zeros, which are counted and not built, and what is
     * left of it has to be a multiple of {@code u_d}. {@link BigDecimal#remainder} instead aligns
     * the two scales, building a power of ten as large as {@code d}, which for scales far apart is
     * more than a {@code BigInteger} holds.
     *
     * <p>{@code d} is a {@code long}: the scales span the whole {@code int} range, and so does
     * their difference twice over.
     *
     * @param value   the value
     * @param divisor the divisor, not zero
     * @return whether {@code value / divisor} is an integer
     */
    private static boolean isMultiple(BigDecimal value, BigDecimal divisor) {
        BigInteger unscaled = value.unscaledValue();
        if (unscaled.signum() == 0) {
            return true;
        }
        BigInteger modulus = divisor.unscaledValue().abs();
        long d = (long) divisor.scale() - value.scale();
        if (d >= 0) {
            BigInteger power = BigInteger.TEN.modPow(BigInteger.valueOf(d), modulus);
            return unscaled.mod(modulus).multiply(power).mod(modulus).signum() == 0;
        }
        long zeros = -(long) new BigDecimal(unscaled).stripTrailingZeros().scale();
        if (-d > zeros) {
            return false;
        }
        // -d is at most the zeros u_v ends in, so this power is no longer than u_v.
        return unscaled.divide(BigInteger.TEN.pow((int) -d)).mod(modulus).signum() == 0;
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
                        String.format(Locale.ROOT, "too many decimal places (max %d)", s), meta);
            }
            return Result.ok(value);
        });
    }


    /**
     * {@inheritDoc}
     *
     * <p>The return type is narrowed to {@link DecimalDecoder} so that decimal constraints can still be chained
     * after the refinement.
     */
    @Override
    public DecimalDecoder<I> refine(Predicate<? super BigDecimal> ok, String code, String message) {
        return refine(ok, code, message, v -> Map.of());
    }

    /**
     * {@inheritDoc}
     *
     * <p>The return type is narrowed to {@link DecimalDecoder} so that decimal constraints can still be chained
     * after the refinement.
     */
    @Override
    public DecimalDecoder<I> refine(Predicate<? super BigDecimal> ok, String code, String message,
                                    Function<? super BigDecimal, ? extends Map<String, Object>> metaFn) {
        return refine(ok, (v, path) -> Result.failCustom(path, code, message, metaFn.apply(v)));
    }

    /**
     * {@inheritDoc}
     *
     * <p>The return type is narrowed to {@link DecimalDecoder} so that decimal constraints can still be chained
     * after the refinement.
     */
    @Override
    public DecimalDecoder<I> refine(Predicate<? super BigDecimal> ok,
                                    BiFunction<? super BigDecimal, ? super Path, ? extends Result<BigDecimal>> onFail) {
        return chain((value, path) -> ok.test(value) ? Result.ok(value) : onFail.apply(value, path));
    }

    private DecimalDecoder<I> chain(Decoder<BigDecimal, BigDecimal> constraint) {
        return new DecimalDecoder<>((in, path) ->
                this.decode(in, path).flatMap(value -> constraint.decode(value, path))
        );
    }
}
