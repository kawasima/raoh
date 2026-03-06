package net.unit8.raoh;

import java.util.function.BiFunction;
import java.util.function.Function;

@FunctionalInterface
public interface Decoder<I, T> {
    Result<T> decode(I in, Path path);

    default <U> Decoder<I, U> map(Function<T, U> f) {
        return (in, path) -> this.decode(in, path).map(f);
    }

    default <U> Decoder<I, U> flatMap(Function<T, Result<U>> f) {
        return (in, path) -> this.decode(in, path).flatMap(t -> {
            Result<U> r = f.apply(t);
            return switch (r) {
                case Ok<U> ok -> ok;
                case Err<U> err -> Result.err(err.issues().rebase(path));
            };
        });
    }

    default <U> Decoder<I, U> flatMapWithPath(BiFunction<T, Path, Result<U>> f) {
        return (in, path) -> this.decode(in, path)
                .flatMap(t -> f.apply(t, path));
    }

    default <U> Decoder<I, U> pipe(Decoder<T, U> next) {
        return (in, path) -> this.decode(in, path)
                .flatMap(t -> next.decode(t, path));
    }
}
