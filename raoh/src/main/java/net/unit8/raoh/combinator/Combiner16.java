package net.unit8.raoh.combinator;

import net.unit8.raoh.Decoder;
import net.unit8.raoh.Decoders;
import net.unit8.raoh.Err;
import net.unit8.raoh.FieldDecoder;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;

import java.util.LinkedHashSet;
import java.util.Set;

public record Combiner16<I, A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, Q>(Decoder<I, A> da, Decoder<I, B> db, Decoder<I, C> dc, Decoder<I, D> dd, Decoder<I, E> de, Decoder<I, F> df, Decoder<I, G> dg, Decoder<I, H> dh, Decoder<I, J> dj, Decoder<I, K> dk, Decoder<I, L> dl, Decoder<I, M> dm, Decoder<I, N> dn, Decoder<I, O> do_, Decoder<I, P> dp, Decoder<I, Q> dq) {

    @SuppressWarnings("unchecked")
    public <T> Decoder<I, T> apply(Function16<A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, Q, T> f) {
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

    public <T> Decoder<I, T> strict(Function16<A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, Q, T> f) {
        return Decoders.strict(apply(f), knownFields());
    }

    public <T> Decoder<I, T> strictFlatMap(Function16<A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, Q, Result<T>> f) {
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
        if (dq instanceof FieldDecoder<I, Q> fd) fields.add(fd.fieldName());
        return Set.copyOf(fields);
    }
}
