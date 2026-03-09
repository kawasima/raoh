package net.unit8.raoh.combinator;

import net.unit8.raoh.Decoder;
import net.unit8.raoh.Decoders;
import net.unit8.raoh.Err;
import net.unit8.raoh.FieldDecoder;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Combines 15 decoders for applicative-style validation with error accumulation.
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
 * @param da  the first decoder
 * @param db  the second decoder
 * @param dc  the third decoder
 * @param dd  the fourth decoder
 * @param de  the fifth decoder
 * @param df  the sixth decoder
 * @param dg  the seventh decoder
 * @param dh  the eighth decoder
 * @param dj  the ninth decoder
 * @param dk  the tenth decoder
 * @param dl  the eleventh decoder
 * @param dm  the twelfth decoder
 * @param dn  the thirteenth decoder
 * @param do_ the fourteenth decoder
 * @param dp  the fifteenth decoder
 */
public record Combiner15<I, A, B, C, D, E, F, G, H, J, K, L, M, N, O, P>(Decoder<I, A> da, Decoder<I, B> db, Decoder<I, C> dc, Decoder<I, D> dd, Decoder<I, E> de, Decoder<I, F> df, Decoder<I, G> dg, Decoder<I, H> dh, Decoder<I, J> dj, Decoder<I, K> dk, Decoder<I, L> dl, Decoder<I, M> dm, Decoder<I, N> dn, Decoder<I, O> do_, Decoder<I, P> dp) {

    /**
     * Applies a constructor function to the decoded values with error accumulation.
     *
     * @param <T> the output type
     * @param f   the constructor function
     * @return a decoder that runs all decoders and accumulates errors
     */
    @SuppressWarnings("unchecked")
    public <T> Decoder<I, T> map(Function15<A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, T> f) {
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
            return Validated.accumulate(
                    new Validated<?>[]{ va, vb, vc, vd, ve, vf, vg, vh, vj, vk, vl, vm, vn, vo, vp },
                    args -> f.apply((A) args[0], (B) args[1], (C) args[2], (D) args[3], (E) args[4], (F) args[5], (G) args[6], (H) args[7], (J) args[8], (K) args[9], (L) args[10], (M) args[11], (N) args[12], (O) args[13], (P) args[14])
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
    public <T> Decoder<I, T> flatMap(Function15<A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, Result<T>> f) {
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
            return Validated.<Result<T>>accumulate(
                    new Validated<?>[]{ va, vb, vc, vd, ve, vf, vg, vh, vj, vk, vl, vm, vn, vo, vp },
                    args -> f.apply((A) args[0], (B) args[1], (C) args[2], (D) args[3], (E) args[4], (F) args[5], (G) args[6], (H) args[7], (J) args[8], (K) args[9], (L) args[10], (M) args[11], (N) args[12], (O) args[13], (P) args[14])
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
    public <T> Decoder<I, T> strict(Function15<A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, T> f) {
        return Decoders.strict(map(f), knownFields());
    }

    /**
     * Like {@link #flatMap}, but additionally rejects unknown fields.
     *
     * @param <T> the output type
     * @param f   a function returning a {@link Result}
     * @return a strict decoder that fails on unknown fields
     */
    public <T> Decoder<I, T> strictFlatMap(Function15<A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, Result<T>> f) {
        return Decoders.strict(flatMap(f), knownFields());
    }

    private Set<String> knownFields() {
        var fields = new LinkedHashSet<String>();
        if (da instanceof FieldDecoder<I, A> fd) fields.add(fd.fieldName());
        if (db instanceof FieldDecoder<I, B> fd) fields.add(fd.fieldName());
        if (dc instanceof FieldDecoder<I, C> fd) fields.add(fd.fieldName());
        if (dd instanceof FieldDecoder<I, D> fd) fields.add(fd.fieldName());
        if (de instanceof FieldDecoder<I, E> fd) fields.add(fd.fieldName());
        if (df instanceof FieldDecoder<I, F> fd) fields.add(fd.fieldName());
        if (dg instanceof FieldDecoder<I, G> fd) fields.add(fd.fieldName());
        if (dh instanceof FieldDecoder<I, H> fd) fields.add(fd.fieldName());
        if (dj instanceof FieldDecoder<I, J> fd) fields.add(fd.fieldName());
        if (dk instanceof FieldDecoder<I, K> fd) fields.add(fd.fieldName());
        if (dl instanceof FieldDecoder<I, L> fd) fields.add(fd.fieldName());
        if (dm instanceof FieldDecoder<I, M> fd) fields.add(fd.fieldName());
        if (dn instanceof FieldDecoder<I, N> fd) fields.add(fd.fieldName());
        if (do_ instanceof FieldDecoder<I, O> fd) fields.add(fd.fieldName());
        if (dp instanceof FieldDecoder<I, P> fd) fields.add(fd.fieldName());
        return Set.copyOf(fields);
    }
}
