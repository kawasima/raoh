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
 * Combines 3 decoders for applicative-style validation with error accumulation.
 *
 * @param <I> the input type
 * @param <A> the first decoder's output type
 * @param <B> the second decoder's output type
 * @param <C> the third decoder's output type
 * @param da  the first decoder
 * @param db  the second decoder
 * @param dc  the third decoder
 */
public record Combiner3<I, A, B, C>(Decoder<I, A> da, Decoder<I, B> db, Decoder<I, C> dc) {

    /**
     * Applies a constructor function to the decoded values with error accumulation.
     *
     * @param <T> the output type
     * @param f   the constructor function
     * @return a decoder that runs all decoders and accumulates errors
     */
    @SuppressWarnings("unchecked")
    public <T> Decoder<I, T> apply(Function3<A, B, C, T> f) {
        return (in, path) -> {
            var va = Validated.fromResult(da.decode(in, path));
            var vb = Validated.fromResult(db.decode(in, path));
            var vc = Validated.fromResult(dc.decode(in, path));
            return Validated.accumulate(
                    new Validated<?>[]{ va, vb, vc },
                    args -> f.apply((A) args[0], (B) args[1], (C) args[2])
            ).toResult();
        };
    }

    /**
     * Like {@link #apply}, but the constructor function may itself return a {@link Result}.
     *
     * @param <T> the output type
     * @param f   a function returning a {@link Result}
     * @return a decoder that runs all decoders, accumulates errors, and flat-maps the result
     */
    @SuppressWarnings("unchecked")
    public <T> Decoder<I, T> flatMap(Function3<A, B, C, Result<T>> f) {
        return (in, path) -> {
            var va = Validated.fromResult(da.decode(in, path));
            var vb = Validated.fromResult(db.decode(in, path));
            var vc = Validated.fromResult(dc.decode(in, path));
            return Validated.<Result<T>>accumulate(
                    new Validated<?>[]{ va, vb, vc },
                    args -> f.apply((A) args[0], (B) args[1], (C) args[2])
            ).toResult().flatMap(r -> switch (r) {
                        case Ok<T> ok -> ok;
                        case Err<T> err -> Result.err(err.issues().rebase(path));
                    });
        };
    }

    /**
     * Like {@link #apply}, but additionally rejects unknown fields.
     *
     * @param <T> the output type
     * @param f   the constructor function
     * @return a strict decoder that fails on unknown fields
     */
    public <T> Decoder<I, T> strict(Function3<A, B, C, T> f) {
        return Decoders.strict(apply(f), knownFields());
    }

    /**
     * Like {@link #flatMap}, but additionally rejects unknown fields.
     *
     * @param <T> the output type
     * @param f   a function returning a {@link Result}
     * @return a strict decoder that fails on unknown fields
     */
    public <T> Decoder<I, T> strictFlatMap(Function3<A, B, C, Result<T>> f) {
        return Decoders.strict(flatMap(f), knownFields());
    }

    private Set<String> knownFields() {
        var fields = new LinkedHashSet<String>();
        if (da instanceof FieldDecoder<I, A> fd) fields.add(fd.fieldName());
        if (db instanceof FieldDecoder<I, B> fd) fields.add(fd.fieldName());
        if (dc instanceof FieldDecoder<I, C> fd) fields.add(fd.fieldName());
        return Set.copyOf(fields);
    }
}
