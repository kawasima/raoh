package net.unit8.raoh.builtin;

import net.unit8.raoh.*;

import java.util.Map;

/**
 * A decoder for boolean values with optional value constraints.
 *
 * @param <I> the input type
 */
public class BoolDecoder<I> implements Decoder<I, Boolean> {

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
    public BoolDecoder<I> isTrue(String message) {
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
    public BoolDecoder<I> isFalse(String message) {
        return booleanConstraint(false, message);
    }

    private BoolDecoder<I> booleanConstraint(boolean expected, String message) {
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

    private BoolDecoder<I> chain(Decoder<Boolean, Boolean> constraint) {
        return new BoolDecoder<>((in, path) ->
                this.decode(in, path).flatMap(value -> constraint.decode(value, path))
        );
    }
}
