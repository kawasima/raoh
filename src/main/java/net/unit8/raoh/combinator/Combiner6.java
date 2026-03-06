package net.unit8.raoh.combinator;

import net.unit8.raoh.Decoder;
import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;

public record Combiner6<I, A, B, C, D, E, F>(Decoder<I, A> da, Decoder<I, B> db, Decoder<I, C> dc, Decoder<I, D> dd, Decoder<I, E> de, Decoder<I, F> df) {

    @SuppressWarnings("unchecked")
    public <T> Decoder<I, T> apply(Function6<A, B, C, D, E, F, T> f) {
        return (in, path) -> {
            var va = Validated.fromResult(da.decode(in, path));
            var vb = Validated.fromResult(db.decode(in, path));
            var vc = Validated.fromResult(dc.decode(in, path));
            var vd = Validated.fromResult(dd.decode(in, path));
            var ve = Validated.fromResult(de.decode(in, path));
            var vf = Validated.fromResult(df.decode(in, path));
            return Validated.accumulate(
                    new Validated<?>[]{ va, vb, vc, vd, ve, vf },
                    args -> f.apply((A) args[0], (B) args[1], (C) args[2], (D) args[3], (E) args[4], (F) args[5])
            ).toResult();
        };
    }

    @SuppressWarnings("unchecked")
    public <T> Decoder<I, T> flatMap(Function6<A, B, C, D, E, F, Result<T>> f) {
        return (in, path) -> {
            var va = Validated.fromResult(da.decode(in, path));
            var vb = Validated.fromResult(db.decode(in, path));
            var vc = Validated.fromResult(dc.decode(in, path));
            var vd = Validated.fromResult(dd.decode(in, path));
            var ve = Validated.fromResult(de.decode(in, path));
            var vf = Validated.fromResult(df.decode(in, path));
            return Validated.<Result<T>>accumulate(
                    new Validated<?>[]{ va, vb, vc, vd, ve, vf },
                    args -> f.apply((A) args[0], (B) args[1], (C) args[2], (D) args[3], (E) args[4], (F) args[5])
            ).toResult().flatMap(r -> switch (r) {
                        case Ok<T> ok -> ok;
                        case Err<T> err -> Result.err(err.issues().rebase(path));
                    });
        };
    }
}
