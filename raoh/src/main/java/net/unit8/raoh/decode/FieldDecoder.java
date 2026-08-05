package net.unit8.raoh.decode;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A {@link Decoder} that is bound to a specific field name.
 *
 * <p>Returned by {@code field(name, dec)} factory methods. The field name is used by
 * {@link net.unit8.raoh.decode.combinator.Combiner2#strict(java.util.function.BiFunction) Combiner#strict()}
 * to automatically collect the set of known fields, eliminating the need to enumerate
 * them manually in {@link Decoders#strict(Decoder, java.util.Set)}.
 *
 * <p>The combinators that keep a decoder bound to the same field — {@link #map}, {@link #flatMap},
 * {@link #flatMapWithPath}, {@link #pipe}, and {@link #refine} — are overridden here with a
 * covariant return type so that the name survives composition. Without them
 * {@code field("age", int_()).refine(...)} would decay to a plain {@link Decoder},
 * {@code strict()} would not see {@code age} among the known fields, and a valid payload
 * would be rejected with {@code unknown_field}.
 *
 * <p>{@link #list()} is deliberately not overridden: it changes the input type to
 * {@code List<I>}, so a single field name no longer describes what it decodes.
 *
 * <p><strong>Path contract:</strong> a {@code FieldDecoder} decodes its value at
 * {@code path.append(fieldName())}, and the overrides here report failures there rather than at
 * the enclosing path. Without that, a single decoder would report two different paths depending on
 * which check failed — {@code field("age", int_()).refine(...)} put a type mismatch at
 * {@code /age} but a refinement failure on the enclosing object. Anything handed to
 * {@link #named} is expected to honour the same contract; the {@code field}, {@code optionalField},
 * {@code optionalNullableField} and {@code nullableField} factories in {@code MapDecoders} and
 * {@code JsonDecoders} all do.
 *
 * @param <I> the input type
 * @param <T> the decoded output type
 */
public interface FieldDecoder<I extends @Nullable Object, T extends @Nullable Object> extends Decoder<I, T> {

    /**
     * Returns the field name this decoder is bound to.
     *
     * @return the field name
     */
    String fieldName();

    /**
     * Binds {@code dec} to {@code name}, producing a {@link FieldDecoder} that delegates every
     * decode to it.
     *
     * @param <I>  the input type
     * @param <T>  the decoded output type
     * @param name the field name to bind to
     * @param dec  the decoder to delegate to
     * @return a field decoder bound to {@code name}
     */
    static <I extends @Nullable Object, T extends @Nullable Object> FieldDecoder<I, T> named(String name, Decoder<I, T> dec) {
        return new FieldDecoder<>() {
            @Override
            public String fieldName() {
                return name;
            }

            @Override
            public Result<T> decode(I in, Path path) {
                return dec.decode(in, path);
            }
        };
    }

    /**
     * Transforms the decoded value, staying bound to the same field.
     *
     * @param <U> the new output type
     * @param f   the mapping function
     * @return a field decoder for the same field, producing {@code U}
     */
    @Override
    default <U> FieldDecoder<I, U> map(Function<? super T, ? extends U> f) {
        return named(fieldName(), Decoder.super.map(f));
    }

    /**
     * Transforms the decoded value with a function that may itself fail, staying bound to the
     * same field. Issues produced by {@code f} are rebased onto the field's path.
     *
     * @param <U> the new output type
     * @param f   the mapping function returning a {@link Result}
     * @return a field decoder for the same field, producing {@code U}
     */
    @Override
    default <U> FieldDecoder<I, U> flatMap(Function<? super T, ? extends Result<U>> f) {
        return named(fieldName(), (in, path) -> this.decode(in, path).flatMap(t -> {
            Result<U> r = f.apply(t);
            return switch (r) {
                case Ok<U> ok -> ok;
                case Err<U> err -> Result.err(err.issues().rebase(path.append(fieldName())));
            };
        }));
    }

    /**
     * Like {@link #flatMap}, but the mapping function also receives the field's path. Stays bound
     * to the same field.
     *
     * @param <U> the new output type
     * @param f   the mapping function receiving both the decoded value and the field's path
     * @return a field decoder for the same field, producing {@code U}
     */
    @Override
    default <U> FieldDecoder<I, U> flatMapWithPath(BiFunction<? super T, ? super Path, ? extends Result<U>> f) {
        return named(fieldName(), (in, path) -> this.decode(in, path)
                .flatMap(t -> f.apply(t, path.append(fieldName()))));
    }

    /**
     * Pipes the decoded value into another decoder, staying bound to the same field. The next
     * decoder runs at the field's path, so its failures land there.
     *
     * @param <U>  the new output type
     * @param next the decoder to apply to this decoder's output
     * @return a field decoder for the same field, producing {@code U}
     */
    @Override
    default <U> FieldDecoder<I, U> pipe(Decoder<T, U> next) {
        return named(fieldName(), (in, path) -> this.decode(in, path)
                .flatMap(t -> next.decode(t, path.append(fieldName()))));
    }

    /**
     * Refines the decoded value with a predicate, staying bound to the same field. The failure is
     * reported at the field's path.
     *
     * @param ok      the predicate the decoded value must satisfy
     * @param code    the error code to report on failure
     * @param message the error message to report on failure
     * @return a field decoder for the same field
     */
    @Override
    default FieldDecoder<I, T> refine(Predicate<? super T> ok, String code, String message) {
        return refine(ok, code, message, v -> Map.of());
    }

    /**
     * Refines the decoded value with a predicate, attaching metadata derived from the failing
     * value. Stays bound to the same field.
     *
     * @param ok      the predicate the decoded value must satisfy
     * @param code    the error code to report on failure
     * @param message the error message to report on failure
     * @param metaFn  a function producing the issue metadata from the failing value
     * @return a field decoder for the same field
     */
    @Override
    default FieldDecoder<I, T> refine(Predicate<? super T> ok, String code, String message,
                                      Function<? super T, ? extends Map<String, Object>> metaFn) {
        return refine(ok, (v, path) -> Result.failCustom(path, code, message, metaFn.apply(v)));
    }

    /**
     * Refines the decoded value with a predicate whose failure branch is caller-controlled.
     * Stays bound to the same field; {@code onFail} receives the field's path.
     *
     * @param ok     the predicate the decoded value must satisfy
     * @param onFail builds the failing result from the rejected value and the current path
     * @return a field decoder for the same field
     */
    @Override
    default FieldDecoder<I, T> refine(Predicate<? super T> ok,
                                      BiFunction<? super T, ? super Path, ? extends Result<T>> onFail) {
        return flatMapWithPath((t, path) -> ok.test(t) ? Result.ok(t) : onFail.apply(t, path));
    }
}
