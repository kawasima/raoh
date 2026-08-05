package net.unit8.raoh.decode.combinator;

import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.decode.InputFields;

import java.util.Optional;

/**
 * One component of a {@code combine(...)} schema.
 *
 * <p>A combine component is not a {@link Decoder}. That is the point: a component built by
 * {@link #named} declares the field it consumes, and an ordinary {@code Decoder} wrapper cannot
 * take one, so it cannot quietly erase that declaration. Wrapping a component means composing
 * inside it — {@code named(name, wrapped)} — or converting deliberately with {@link #asDecoder()}.
 *
 * <p>Two kinds exist. A <strong>named</strong> part reads one field: it appends its name to the
 * path before delegating, so a standalone {@code part.decode(input)} reports at the same place a
 * combined one does. A <strong>flat</strong> part wraps a decoder that reads the same whole input,
 * which is how a flat JOIN row is split across several decoders; it declares no fields, so a
 * combiner containing one cannot be made strict.
 *
 * @param <I> the input type
 * @param <T> the decoded output type
 */
public sealed interface CombinePart<I, T> permits NamedCombinePart, FlatCombinePart {

    /**
     * Decodes this component from {@code input}.
     *
     * @param input the input to decode
     * @param path  the enclosing path; a named part appends its own name to it
     * @return the decoding result
     */
    Result<T> decode(I input, Path path);

    /**
     * Decodes this component at the root path.
     *
     * @param input the input to decode
     * @return the decoding result
     */
    default Result<T> decode(I input) {
        return decode(input, Path.ROOT);
    }

    /**
     * Views this component as a plain {@link Decoder}, giving up the field declaration that
     * {@code strict()} depends on.
     *
     * @return a decoder with the same behaviour and none of the schema metadata
     */
    default Decoder<I, T> asDecoder() {
        return this::decode;
    }

    /**
     * Transforms the decoded value, staying the same component.
     *
     * <p>Composition happens inside the component rather than around it, which is why the field
     * declaration cannot be lost on the way through.
     *
     * @param <U> the new output type
     * @param f   the mapping function
     * @return a component producing {@code U}
     */
    default <U> CombinePart<I, U> map(java.util.function.Function<? super T, ? extends U> f) {
        return rebuild(inner -> inner.map(f));
    }

    /**
     * Transforms the decoded value with a function that may itself fail.
     *
     * @param <U> the new output type
     * @param f   the mapping function returning a {@link Result}
     * @return a component producing {@code U}
     */
    default <U> CombinePart<I, U> flatMap(java.util.function.Function<? super T, ? extends Result<U>> f) {
        return rebuild(inner -> inner.flatMap(f));
    }

    /**
     * Like {@link #flatMap}, but the mapping function also receives the path this component decodes
     * at — the field path, for a named component.
     *
     * @param <U> the new output type
     * @param f   the mapping function
     * @return a component producing {@code U}
     */
    default <U> CombinePart<I, U> flatMapWithPath(
            java.util.function.BiFunction<? super T, ? super Path, ? extends Result<U>> f) {
        return rebuild(inner -> inner.flatMapWithPath(f));
    }

    /**
     * Pipes the decoded value into another decoder.
     *
     * @param <U>  the new output type
     * @param next the decoder to apply to this component's output
     * @return a component producing {@code U}
     */
    default <U> CombinePart<I, U> pipe(Decoder<T, U> next) {
        return rebuild(inner -> inner.pipe(next));
    }

    /**
     * Refines the decoded value with a predicate, failing at this component's path.
     *
     * @param ok      the predicate the decoded value must satisfy
     * @param code    the error code to report on failure
     * @param message the error message to report on failure
     * @return a component that fails when {@code ok} rejects the value
     */
    default CombinePart<I, T> refine(java.util.function.Predicate<? super T> ok, String code, String message) {
        return rebuild(inner -> inner.refine(ok, code, message));
    }

    /**
     * Refines the decoded value, attaching metadata derived from the failing value.
     *
     * @param ok      the predicate the decoded value must satisfy
     * @param code    the error code to report on failure
     * @param message the error message to report on failure
     * @param metaFn  a function producing the issue metadata from the failing value
     * @return a component that fails when {@code ok} rejects the value
     */
    default CombinePart<I, T> refine(java.util.function.Predicate<? super T> ok, String code, String message,
                                     java.util.function.Function<? super T, ? extends java.util.Map<String, Object>> metaFn) {
        return rebuild(inner -> inner.refine(ok, code, message, metaFn));
    }

    /**
     * Refines the decoded value with a caller-controlled failure branch.
     *
     * @param ok     the predicate the decoded value must satisfy
     * @param onFail builds the failing result from the rejected value and this component's path
     * @return a component that delegates to {@code onFail} when {@code ok} rejects the value
     */
    default CombinePart<I, T> refine(java.util.function.Predicate<? super T> ok,
                                     java.util.function.BiFunction<? super T, ? super Path, ? extends Result<T>> onFail) {
        return rebuild(inner -> inner.refine(ok, onFail));
    }

    /**
     * Rebuilds this component around a transformed inner decoder, keeping whatever it declares.
     *
     * @param <U> the new output type
     * @param f   transforms the decoder this component delegates to
     * @return a component of the same kind, producing {@code U}
     */
    private <U> CombinePart<I, U> rebuild(java.util.function.Function<Decoder<I, T>, Decoder<I, U>> f) {
        return switch (this) {
            case NamedCombinePart<I, T> p ->
                    new NamedCombinePart<>(p.name(), f.apply(p.decoder()), p.inputFields());
            case FlatCombinePart<I, T> p -> new FlatCombinePart<>(f.apply(p.decoder()));
        };
    }

    /**
     * Creates a component that reads the field {@code name}, without saying how the input's fields
     * are enumerated — so a combiner built only from these cannot be made strict.
     *
     * @param <I>     the input type
     * @param <T>     the decoded output type
     * @param name    the field name this component consumes
     * @param decoder reads the field from the whole input, at the already-appended field path
     * @return a named component
     */
    static <I, T> CombinePart<I, T> named(String name, Decoder<I, T> decoder) {
        return new NamedCombinePart<>(name, decoder, Optional.empty());
    }

    /**
     * Creates a component that reads the field {@code name} on a known input boundary.
     *
     * @param <I>         the input type
     * @param <T>         the decoded output type
     * @param name        the field name this component consumes
     * @param decoder     reads the field from the whole input, at the already-appended field path
     * @param inputFields how to enumerate the field names present in the input
     * @return a named component that can take part in {@code strict()}
     */
    static <I, T> CombinePart<I, T> named(String name, Decoder<I, T> decoder, InputFields<I> inputFields) {
        return new NamedCombinePart<>(name, decoder, Optional.of(inputFields));
    }

    /**
     * Lifts a decoder that reads the same whole input into a combine component, for splitting a
     * flat row across several decoders.
     *
     * <p>The decoder is opaque, so the component declares no fields and a combiner containing one
     * cannot be made strict.
     *
     * @param <I>     the input type
     * @param <T>     the decoded output type
     * @param decoder reads the same input the other components read
     * @return a flat component
     */
    static <I, T> CombinePart<I, T> flat(Decoder<I, T> decoder) {
        return new FlatCombinePart<>(decoder);
    }
}
