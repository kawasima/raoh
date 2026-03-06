package net.unit8.raoh.combinator;

import net.unit8.raoh.Decoder;
import net.unit8.raoh.Decoders;
import net.unit8.raoh.Err;
import net.unit8.raoh.FieldDecoder;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;

import java.util.LinkedHashSet;
import java.util.Set;

public record Combiner5<I, A, B, C, D, E>(Decoder<I, A> da, Decoder<I, B> db, Decoder<I, C> dc, Decoder<I, D> dd, Decoder<I, E> de) {

    @SuppressWarnings("unchecked")
    public <T> Decoder<I, T> apply(Function5<A, B, C, D, E, T> f) {
        return (in, path) -> {
            var va = Validated.fromResult(da.decode(in, path));
            var vb = Validated.fromResult(db.decode(in, path));
            var vc = Validated.fromResult(dc.decode(in, path));
            var vd = Validated.fromResult(dd.decode(in, path));
            var ve = Validated.fromResult(de.decode(in, path));
            return Validated.accumulate(
                    new Validated<?>[]{ va, vb, vc, vd, ve },
                    args -> f.apply((A) args[0], (B) args[1], (C) args[2], (D) args[3], (E) args[4])
            ).toResult();
        };
    }

    @SuppressWarnings("unchecked")
    public <T> Decoder<I, T> flatMap(Function5<A, B, C, D, E, Result<T>> f) {
        return (in, path) -> {
            var va = Validated.fromResult(da.decode(in, path));
            var vb = Validated.fromResult(db.decode(in, path));
            var vc = Validated.fromResult(dc.decode(in, path));
            var vd = Validated.fromResult(dd.decode(in, path));
            var ve = Validated.fromResult(de.decode(in, path));
            return Validated.<Result<T>>accumulate(
                    new Validated<?>[]{ va, vb, vc, vd, ve },
                    args -> f.apply((A) args[0], (B) args[1], (C) args[2], (D) args[3], (E) args[4])
            ).toResult().flatMap(r -> switch (r) {
                        case Ok<T> ok -> ok;
                        case Err<T> err -> Result.err(err.issues().rebase(path));
                    });
        };
    }

    public <T> Decoder<I, T> strict(Function5<A, B, C, D, E, T> f) {
        return Decoders.strict(apply(f), knownFields());
    }

    public <T> Decoder<I, T> strictFlatMap(Function5<A, B, C, D, E, Result<T>> f) {
        return Decoders.strict(flatMap(f), knownFields());
    }

    private Set<String> knownFields() {
        var fields = new LinkedHashSet<String>();
        if (da instanceof FieldDecoder<I, A> fd) fields.add(fd.fieldName());
        if (db instanceof FieldDecoder<I, B> fd) fields.add(fd.fieldName());
        if (dc instanceof FieldDecoder<I, C> fd) fields.add(fd.fieldName());
        if (dd instanceof FieldDecoder<I, D> fd) fields.add(fd.fieldName());
        if (de instanceof FieldDecoder<I, E> fd) fields.add(fd.fieldName());
        return Set.copyOf(fields);
    }
}
