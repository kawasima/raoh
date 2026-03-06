package net.unit8.raoh.combinator;

import net.unit8.raoh.Decoder;
import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;

public record Combiner12<I, A, B, C, D, E, F, G, H, J, K, L, M>(Decoder<I, A> da, Decoder<I, B> db, Decoder<I, C> dc, Decoder<I, D> dd, Decoder<I, E> de, Decoder<I, F> df, Decoder<I, G> dg, Decoder<I, H> dh, Decoder<I, J> dj, Decoder<I, K> dk, Decoder<I, L> dl, Decoder<I, M> dm) {

    @SuppressWarnings("unchecked")
    public <T> Decoder<I, T> apply(Function12<A, B, C, D, E, F, G, H, J, K, L, M, T> f) {
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
            return Validated.accumulate(
                    new Validated<?>[]{ va, vb, vc, vd, ve, vf, vg, vh, vj, vk, vl, vm },
                    args -> f.apply((A) args[0], (B) args[1], (C) args[2], (D) args[3], (E) args[4], (F) args[5], (G) args[6], (H) args[7], (J) args[8], (K) args[9], (L) args[10], (M) args[11])
            ).toResult();
        };
    }

    @SuppressWarnings("unchecked")
    public <T> Decoder<I, T> flatMap(Function12<A, B, C, D, E, F, G, H, J, K, L, M, Result<T>> f) {
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
            return Validated.<Result<T>>accumulate(
                    new Validated<?>[]{ va, vb, vc, vd, ve, vf, vg, vh, vj, vk, vl, vm },
                    args -> f.apply((A) args[0], (B) args[1], (C) args[2], (D) args[3], (E) args[4], (F) args[5], (G) args[6], (H) args[7], (J) args[8], (K) args[9], (L) args[10], (M) args[11])
            ).toResult().flatMap(r -> switch (r) {
                        case Ok<T> ok -> ok;
                        case Err<T> err -> Result.err(err.issues().rebase(path));
                    });
        };
    }
}
