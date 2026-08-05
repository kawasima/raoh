package net.unit8.raoh.decode.combinator;

import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;

import java.util.function.BiFunction;

/**
 * Combines 2 decoders for applicative-style validation with error accumulation.
 *
 * @param <I> the input type
 * @param <A> the first decoder's output type
 * @param <B> the second decoder's output type
 * @param da  the first component
 * @param db  the second component
 */
public record Combiner2<I, A, B>(CombinePart<I, A> da, CombinePart<I, B> db) {

    /**
     * Applies a constructor function to the decoded values with error accumulation.
     *
     * @param <T> the output type
     * @param f   the constructor function
     * @return a decoder that runs all decoders and accumulates errors
     */
    public <T> Decoder<I, T> map(BiFunction<A, B, T> f) {
        return (in, path) -> {
            var va = Validated.fromResult(da.decode(in, path));
            var vb = Validated.fromResult(db.decode(in, path));
            return Validated.combine(va, vb, f).toResult();
        };
    }

    /**
     * Like {@link #map}, but the constructor function may itself return a {@link Result}.
     *
     * @param <T> the output type
     * @param f   a function returning a {@link Result}
     * @return a decoder that runs all decoders, accumulates errors, and flat-maps the result
     */
    public <T> Decoder<I, T> flatMap(BiFunction<A, B, Result<T>> f) {
        return (in, path) -> {
            var va = Validated.fromResult(da.decode(in, path));
            var vb = Validated.fromResult(db.decode(in, path));
            return Validated.combine(va, vb, f).toResult()
                    .flatMap(r -> switch (r) {
                        case Ok<T> ok -> ok;
                        case Err<T> err -> Result.err(err.issues().rebase(path));
                    });
        };
    }

    /**
     * Like {@link #map}, but additionally rejects unknown fields.
     *
     * @param <T> the output type
     * @param f   the constructor function
     * @return a strict decoder that fails on unknown fields
     */
    public <T> Decoder<I, T> strict(BiFunction<A, B, T> f) {
        return CombinerSupport.strict(map(f), da, db);
    }

    /**
     * Like {@link #flatMap}, but additionally rejects unknown fields.
     *
     * @param <T> the output type
     * @param f   a function returning a {@link Result}
     * @return a strict decoder that fails on unknown fields
     */
    public <T> Decoder<I, T> strictFlatMap(BiFunction<A, B, Result<T>> f) {
        return CombinerSupport.strict(flatMap(f), da, db);
    }

}
