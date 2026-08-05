package net.unit8.raoh.decode.combinator;

import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;


/**
 * Combines 16 decoders for applicative-style validation with error accumulation.
 *
 * @param <I> the input type
 * @param <A> the first decoder's output type
 * @param <B> the second decoder's output type
 * @param <C> the third decoder's output type
 * @param <D> the fourth decoder's output type
 * @param <E> the fifth decoder's output type
 * @param <F> the sixth decoder's output type
 * @param <G> the seventh decoder's output type
 * @param <H> the eighth decoder's output type
 * @param <J> the ninth decoder's output type
 * @param <K> the tenth decoder's output type
 * @param <L> the eleventh decoder's output type
 * @param <M> the twelfth decoder's output type
 * @param <N> the thirteenth decoder's output type
 * @param <O> the fourteenth decoder's output type
 * @param <P> the fifteenth decoder's output type
 * @param <Q> the sixteenth decoder's output type
 * @param da  the first component
 * @param db  the second component
 * @param dc  the third component
 * @param dd  the fourth component
 * @param de  the fifth component
 * @param df  the sixth component
 * @param dg  the seventh component
 * @param dh  the eighth component
 * @param dj  the ninth component
 * @param dk  the tenth component
 * @param dl  the eleventh component
 * @param dm  the twelfth component
 * @param dn  the thirteenth component
 * @param do_  the fourteenth component
 * @param dp  the fifteenth component
 * @param dq  the sixteenth component
 */
public record Combiner16<I, A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, Q>(CombinePart<I, A> da, CombinePart<I, B> db, CombinePart<I, C> dc, CombinePart<I, D> dd, CombinePart<I, E> de, CombinePart<I, F> df, CombinePart<I, G> dg, CombinePart<I, H> dh, CombinePart<I, J> dj, CombinePart<I, K> dk, CombinePart<I, L> dl, CombinePart<I, M> dm, CombinePart<I, N> dn, CombinePart<I, O> do_, CombinePart<I, P> dp, CombinePart<I, Q> dq) {

    /**
     * Applies a constructor function to the decoded values with error accumulation.
     *
     * @param <T> the output type
     * @param f   the constructor function
     * @return a decoder that runs all decoders and accumulates errors
     */
    @SuppressWarnings("unchecked")
    public <T> Decoder<I, T> map(Function16<A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, Q, T> f) {
        return (in, path) -> {
            var va = Validated.fromResult(da.decode(in, path));
            var vb = Validated.fromResult(db.decode(in, path));
            var vc = Validated.fromResult(dc.decode(in, path));
            var vd = Validated.fromResult(dd.decode(in, path));
            var ve = Validated.fromResult(de.decode(in, path));
            var vf = Validated.fromResult(df.decode(in, path));
            var vg = Validated.fromResult(dg.decode(in, path));
            var vh = Validated.fromResult(dh.decode(in, path));
            var vj = Validated.fromResult(dj.decode(in, path));
            var vk = Validated.fromResult(dk.decode(in, path));
            var vl = Validated.fromResult(dl.decode(in, path));
            var vm = Validated.fromResult(dm.decode(in, path));
            var vn = Validated.fromResult(dn.decode(in, path));
            var vo = Validated.fromResult(do_.decode(in, path));
            var vp = Validated.fromResult(dp.decode(in, path));
            var vq = Validated.fromResult(dq.decode(in, path));
            return Validated.accumulate(
                    new Validated<?>[]{ va, vb, vc, vd, ve, vf, vg, vh, vj, vk, vl, vm, vn, vo, vp, vq },
                    args -> f.apply((A) args[0], (B) args[1], (C) args[2], (D) args[3], (E) args[4], (F) args[5], (G) args[6], (H) args[7], (J) args[8], (K) args[9], (L) args[10], (M) args[11], (N) args[12], (O) args[13], (P) args[14], (Q) args[15])
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
    public <T> Decoder<I, T> flatMap(Function16<A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, Q, Result<T>> f) {
        return (in, path) -> {
            var va = Validated.fromResult(da.decode(in, path));
            var vb = Validated.fromResult(db.decode(in, path));
            var vc = Validated.fromResult(dc.decode(in, path));
            var vd = Validated.fromResult(dd.decode(in, path));
            var ve = Validated.fromResult(de.decode(in, path));
            var vf = Validated.fromResult(df.decode(in, path));
            var vg = Validated.fromResult(dg.decode(in, path));
            var vh = Validated.fromResult(dh.decode(in, path));
            var vj = Validated.fromResult(dj.decode(in, path));
            var vk = Validated.fromResult(dk.decode(in, path));
            var vl = Validated.fromResult(dl.decode(in, path));
            var vm = Validated.fromResult(dm.decode(in, path));
            var vn = Validated.fromResult(dn.decode(in, path));
            var vo = Validated.fromResult(do_.decode(in, path));
            var vp = Validated.fromResult(dp.decode(in, path));
            var vq = Validated.fromResult(dq.decode(in, path));
            return Validated.<Result<T>>accumulate(
                    new Validated<?>[]{ va, vb, vc, vd, ve, vf, vg, vh, vj, vk, vl, vm, vn, vo, vp, vq },
                    args -> f.apply((A) args[0], (B) args[1], (C) args[2], (D) args[3], (E) args[4], (F) args[5], (G) args[6], (H) args[7], (J) args[8], (K) args[9], (L) args[10], (M) args[11], (N) args[12], (O) args[13], (P) args[14], (Q) args[15])
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
    public <T> Decoder<I, T> strict(Function16<A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, Q, T> f) {
        return CombinerSupport.strict(map(f), da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn, do_, dp, dq);
    }

    /**
     * Like {@link #flatMap}, but additionally rejects unknown fields.
     *
     * @param <T> the output type
     * @param f   a function returning a {@link Result}
     * @return a strict decoder that fails on unknown fields
     */
    public <T> Decoder<I, T> strictFlatMap(Function16<A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, Q, Result<T>> f) {
        return CombinerSupport.strict(flatMap(f), da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn, do_, dp, dq);
    }

}
