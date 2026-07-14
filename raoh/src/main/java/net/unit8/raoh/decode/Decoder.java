package net.unit8.raoh.decode;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

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
public interface Decoder<I extends @Nullable Object, T extends @Nullable Object> {
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
    default <U> Decoder<I, U> map(Function<? super T, ? extends U> f) {
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
    default <U> Decoder<I, U> flatMap(Function<? super T, ? extends Result<U>> f) {
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
    default <U> Decoder<I, U> flatMapWithPath(BiFunction<? super T, ? super Path, ? extends Result<U>> f) {
        return (in, path) -> this.decode(in, path)
                .flatMap(t -> f.apply(t, path));
    }

    /**
     * Refines the decoded value with a predicate, keeping the value unchanged on success
     * and producing an {@link net.unit8.raoh.Issue Issue} at the current path on failure.
     *
     * <p>This is the ergonomic form of the common {@link #flatMapWithPath} idiom "keep the value,
     * or fail with a domain rule". The caller supplies the error {@code code} and {@code message};
     * no built-in error code is introduced, and the {@code message} is stored as a custom message
     * that {@link net.unit8.raoh.MessageResolver MessageResolver} will not overwrite.
     *
     * <pre>{@code
     * field("age", int_().refine(age -> age % 2 == 0, "must_be_even", "must be even"));
     * }</pre>
     *
     * <p>Refinement failures accumulate with sibling errors through
     * {@link Decoders#combine Decoders.combine}, exactly like any other decoder failure.
     *
     * <p><strong>Nullness:</strong> the predicate receives the decoded value as-is, including
     * {@code null} — consistent with {@link #map} and {@link #flatMap}. When decoding a nullable
     * value, prefer applying {@code refine} to the inner decoder and wrapping with
     * {@link ObjectDecoders#nullable nullable} on the outside, so that a {@code null} input
     * short-circuits before the predicate runs. Applied on the outside
     * ({@code nullable(dec).refine(...)}), the predicate will receive {@code null}.
     *
     * @param ok      the predicate the decoded value must satisfy
     * @param code    the error code to report on failure
     * @param message the error message to report on failure
     * @return a decoder that fails with the given code and message when {@code ok} rejects the value
     */
    default Decoder<I, T> refine(Predicate<? super T> ok, String code, String message) {
        return refine(ok, code, message, v -> Map.of());
    }

    /**
     * Like {@link #refine(Predicate, String, String)}, but attaches metadata derived from the
     * failing value — useful for templating the failing value into a localized message.
     *
     * <pre>{@code
     * int_().refine(n -> n % 2 == 0, "must_be_even", "must be even",
     *               n -> Map.of("actual", n));
     * }</pre>
     *
     * <p>{@code metaFn} must return a non-null map, and the map itself must not contain {@code null}
     * values — {@link Map#of} throws {@link NullPointerException} on a {@code null} value. When the
     * refined value may be {@code null} (see the nullness note on {@link #refine(Predicate, String, String)}),
     * guard the payload accordingly (e.g. return {@link Map#of()} for a {@code null} value).
     *
     * @param ok      the predicate the decoded value must satisfy
     * @param code    the error code to report on failure
     * @param message the error message to report on failure
     * @param metaFn  a function producing the issue metadata from the failing value; must be non-null
     *                and must not put {@code null} values into the map
     * @return a decoder that fails with the given code, message, and metadata when {@code ok} rejects the value
     */
    default Decoder<I, T> refine(Predicate<? super T> ok, String code, String message,
                                 Function<? super T, ? extends Map<String, Object>> metaFn) {
        return refine(ok, (v, path) -> Result.failCustom(path, code, message, metaFn.apply(v)));
    }

    /**
     * Like {@link #refine(Predicate, String, String)}, but the failure branch is fully caller-controlled:
     * when {@code ok} rejects the value, {@code onFail} builds the failing {@link Result} from the value
     * and the current path.
     *
     * <p>Use this overload when the code, message, or metadata depend on the value, or when the failure
     * path differs from the current path. On success the value is passed through unchanged.
     *
     * @param ok     the predicate the decoded value must satisfy
     * @param onFail builds the failing result from the rejected value and the current path
     * @return a decoder that delegates to {@code onFail} when {@code ok} rejects the value
     */
    default Decoder<I, T> refine(Predicate<? super T> ok,
                                 BiFunction<? super T, ? super Path, ? extends Result<T>> onFail) {
        return flatMapWithPath((t, path) -> ok.test(t) ? Result.ok(t) : onFail.apply(t, path));
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
