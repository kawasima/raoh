package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A decoder for boolean values with optional value constraints.
 *
 * @param <I> the input type
 */
public class BoolDecoder<I extends @Nullable Object> implements Decoder<I, Boolean> {

    private final Decoder<I, Boolean> inner;

    /**
     * Creates a new BoolDecoder wrapping the given inner decoder.
     *
     * @param inner the underlying decoder that produces boolean values
     */
    public BoolDecoder(Decoder<I, Boolean> inner) {
        this.inner = inner;
    }

    /**
     * Decodes the input into a boolean value.
     *
     * @param in   the input to decode
     * @param path the current path for error reporting
     * @return a {@link Result} containing the decoded boolean or validation errors
     */
    @Override
    public Result<Boolean> decode(I in, Path path) {
        return inner.decode(in, path);
    }

    /**
     * Constrains the value to be {@code true}.
     *
     * <p>Produces an {@code invalid_value} error when the decoded value is {@code false}.
     * Useful for "must accept terms" or similar required-confirmation fields.
     *
     * @return a new decoder with the constraint applied
     */
    public BoolDecoder<I> isTrue() {
        return isTrue(null);
    }

    /**
     * Constrains the value to be {@code true}.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder with the constraint applied
     */
    public BoolDecoder<I> isTrue(@Nullable String message) {
        return booleanConstraint(true, message);
    }

    /**
     * Constrains the value to be {@code false}.
     *
     * <p>Produces an {@code invalid_value} error when the decoded value is {@code true}.
     *
     * @return a new decoder with the constraint applied
     */
    public BoolDecoder<I> isFalse() {
        return isFalse(null);
    }

    /**
     * Constrains the value to be {@code false}.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder with the constraint applied
     */
    public BoolDecoder<I> isFalse(@Nullable String message) {
        return booleanConstraint(false, message);
    }

    private BoolDecoder<I> booleanConstraint(boolean expected, @Nullable String message) {
        return chain((value, path) -> {
            if (value != expected) {
                var meta = Map.<String, Object>of("expected", expected, "actual", value);
                return message != null
                        ? Result.failCustom(path, ErrorCodes.INVALID_VALUE, message, meta)
                        : Result.fail(path, ErrorCodes.INVALID_VALUE,
                                "must be %s".formatted(expected), meta);
            }
            return Result.ok(value);
        });
    }


    /**
     * Refines the decoded value with a predicate, narrowing the inherited return type so that this
     * decoder's own constraints can still be chained after the refinement.
     *
     * @param ok      the predicate the decoded value must satisfy
     * @param code    the error code to report on failure
     * @param message the error message to report on failure
     * @return a decoder that fails with the given code and message when {@code ok} rejects the value
     */
    @Override
    public BoolDecoder<I> refine(Predicate<? super Boolean> ok, String code, String message) {
        return refine(ok, code, message, v -> Map.of());
    }

    /**
     * Refines the decoded value with a predicate, attaching metadata derived from the failing value.
     * Narrows the inherited return type so that this decoder's own constraints can still be chained.
     *
     * @param ok      the predicate the decoded value must satisfy
     * @param code    the error code to report on failure
     * @param message the error message to report on failure
     * @param metaFn  a function producing the issue metadata from the failing value
     * @return a decoder that fails with the given code, message, and metadata when {@code ok}
     *         rejects the value
     */
    @Override
    public BoolDecoder<I> refine(Predicate<? super Boolean> ok, String code, String message,
                                 Function<? super Boolean, ? extends Map<String, Object>> metaFn) {
        return refine(ok, (v, path) -> Result.failCustom(path, code, message, metaFn.apply(v)));
    }

    /**
     * Refines the decoded value with a predicate whose failure branch is caller-controlled.
     * Narrows the inherited return type so that this decoder's own constraints can still be chained.
     *
     * @param ok     the predicate the decoded value must satisfy
     * @param onFail builds the failing result from the rejected value and the current path
     * @return a decoder that delegates to {@code onFail} when {@code ok} rejects the value
     */
    @Override
    public BoolDecoder<I> refine(Predicate<? super Boolean> ok,
                                 BiFunction<? super Boolean, ? super Path, ? extends Result<Boolean>> onFail) {
        return chain((value, path) -> ok.test(value) ? Result.ok(value) : onFail.apply(value, path));
    }

    private BoolDecoder<I> chain(Decoder<Boolean, Boolean> constraint) {
        return new BoolDecoder<>((in, path) ->
                this.decode(in, path).flatMap(value -> constraint.decode(value, path))
        );
    }
}
