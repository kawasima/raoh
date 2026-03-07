package net.unit8.raoh;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A composable decoder that transforms input of type {@code I} into a validated value of type {@code T}.
 *
 * <p>Decoders are the core building block of Raoh. They validate and transform input data,
 * accumulating all errors rather than failing on the first one.
 *
 * @param <I> the input type (e.g., {@code JsonNode}, {@code Map<String, Object>})
 * @param <T> the decoded output type
 */
@FunctionalInterface
public interface Decoder<I, T> {
    /**
     * Decodes the given input, returning a {@link Result} that is either
     * {@link Ok} with the decoded value or {@link Err} with validation issues.
     *
     * @param in   the input to decode
     * @param path the current path in the input structure, used for error reporting
     * @return the decoding result
     */
    Result<T> decode(I in, Path path);

    /**
     * Decodes the given input at the root path.
     *
     * <p>Equivalent to {@code decode(in, Path.ROOT)}.
     *
     * @param in the input to decode
     * @return the decoding result
     */
    default Result<T> decode(I in) {
        return decode(in, Path.ROOT);
    }

    /**
     * Transforms the decoded value using the given function.
     *
     * @param <U> the new output type
     * @param f   the mapping function
     * @return a new decoder that applies {@code f} to successful results
     */
    default <U> Decoder<I, U> map(Function<T, U> f) {
        return (in, path) -> this.decode(in, path).map(f);
    }

    /**
     * Transforms the decoded value using a function that may itself fail.
     * Issues produced by {@code f} are rebased to the current path.
     *
     * @param <U> the new output type
     * @param f   the mapping function returning a {@link Result}
     * @return a new decoder that flat-maps successful results through {@code f}
     */
    default <U> Decoder<I, U> flatMap(Function<T, Result<U>> f) {
        return (in, path) -> this.decode(in, path).flatMap(t -> {
            Result<U> r = f.apply(t);
            return switch (r) {
                case Ok<U> ok -> ok;
                case Err<U> err -> Result.err(err.issues().rebase(path));
            };
        });
    }

    /**
     * Like {@link #flatMap}, but the mapping function also receives the current path.
     *
     * @param <U> the new output type
     * @param f   the mapping function receiving both the decoded value and the path
     * @return a new decoder
     */
    default <U> Decoder<I, U> flatMapWithPath(BiFunction<T, Path, Result<U>> f) {
        return (in, path) -> this.decode(in, path)
                .flatMap(t -> f.apply(t, path));
    }

    /**
     * Pipes the output of this decoder into another decoder.
     * Useful for multi-stage parsing (e.g., string to integer).
     *
     * @param <U>  the new output type
     * @param next the decoder to apply to this decoder's output
     * @return a new composed decoder
     */
    default <U> Decoder<I, U> pipe(Decoder<T, U> next) {
        return (in, path) -> this.decode(in, path)
                .flatMap(t -> next.decode(t, path));
    }

    /**
     * Returns a decoder that decodes a {@code List<I>} by applying this decoder to every element,
     * accumulating all errors rather than short-circuiting on the first failure.
     *
     * <p>Equivalent to {@code (items, path) -> Result.traverse(items, this::decode, path)}.
     *
     * @return a decoder from {@code List<I>} to {@code List<T>}
     */
    default Decoder<List<I>, List<T>> list() {
        return (items, path) -> Result.traverse(items, this::decode, path);
    }
}
