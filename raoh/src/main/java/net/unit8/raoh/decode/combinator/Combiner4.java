package net.unit8.raoh.decode.combinator;

import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;


/**
 * Combines 4 decoders for applicative-style validation with error accumulation.
 *
 * @param <I> the input type
 * @param <A> the first decoder's output type
 * @param <B> the second decoder's output type
 * @param <C> the third decoder's output type
 * @param <D> the fourth decoder's output type
 * @param da  the first component
 * @param db  the second component
 * @param dc  the third component
 * @param dd  the fourth component
 */
public record Combiner4<I, A, B, C, D>(CombinePart<I, A> da, CombinePart<I, B> db, CombinePart<I, C> dc, CombinePart<I, D> dd) {

    /**
     * Applies a constructor function to the decoded values with error accumulation.
     *
     * @param <T> the output type
     * @param f   the constructor function
     * @return a decoder that runs all decoders and accumulates errors
     */
    @SuppressWarnings("unchecked")
    public <T> Decoder<I, T> map(Function4<A, B, C, D, T> f) {
        return (in, path) -> {
            var va = Validated.fromResult(da.decode(in, path));
            var vb = Validated.fromResult(db.decode(in, path));
            var vc = Validated.fromResult(dc.decode(in, path));
            var vd = Validated.fromResult(dd.decode(in, path));
            return Validated.accumulate(
                    new Validated<?>[]{ va, vb, vc, vd },
                    args -> f.apply((A) args[0], (B) args[1], (C) args[2], (D) args[3])
            ).toResult();
        };
    }

    /**
     * Like {@link #map}, but the constructor function may itself return a {@link Result}.
     *
     * @param <T> the output type
     * @param f   a function returning a {@link Result}
     * @return a decoder that runs all decoders, accumulates errors, and flat-maps the result
     */
    @SuppressWarnings("unchecked")
    public <T> Decoder<I, T> flatMap(Function4<A, B, C, D, Result<T>> f) {
        return (in, path) -> {
            var va = Validated.fromResult(da.decode(in, path));
            var vb = Validated.fromResult(db.decode(in, path));
            var vc = Validated.fromResult(dc.decode(in, path));
            var vd = Validated.fromResult(dd.decode(in, path));
            return Validated.<Result<T>>accumulate(
                    new Validated<?>[]{ va, vb, vc, vd },
                    args -> f.apply((A) args[0], (B) args[1], (C) args[2], (D) args[3])
            ).toResult().flatMap(r -> switch (r) {
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
    public <T> Decoder<I, T> strict(Function4<A, B, C, D, T> f) {
        return CombinerSupport.strict(map(f), da, db, dc, dd);
    }

    /**
     * Like {@link #flatMap}, but additionally rejects unknown fields.
     *
     * @param <T> the output type
     * @param f   a function returning a {@link Result}
     * @return a strict decoder that fails on unknown fields
     */
    public <T> Decoder<I, T> strictFlatMap(Function4<A, B, C, D, Result<T>> f) {
        return CombinerSupport.strict(flatMap(f), da, db, dc, dd);
    }

}
