package net.unit8.raoh.combinator;

import net.unit8.raoh.Decoder;
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
 * @param da  the first decoder
 * @param db  the second decoder
 */
public record Combiner2<I, A, B>(Decoder<I, A> da, Decoder<I, B> db) {

    public <T> Decoder<I, T> apply(BiFunction<A, B, T> f) {
        return (in, path) -> {
            var va = Validated.fromResult(da.decode(in, path));
            var vb = Validated.fromResult(db.decode(in, path));
            return Validated.combine(va, vb, f).toResult();
        };
    }

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
}
