package net.unit8.raoh.combinator;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;
import net.unit8.raoh.Issues;

import java.util.function.BiFunction;
import java.util.function.Function;

sealed interface Validated<T> permits Valid, Invalid {

    <U> Validated<U> map(Function<T, U> f);

    Result<T> toResult();

    static <T> Validated<T> fromResult(Result<T> r) {
        return switch (r) {
            case Ok<T> ok -> new Valid<>(ok.value());
            case Err<T> err -> new Invalid<>(err.issues());
        };
    }

    static <A, B, T> Validated<T> combine(
            Validated<A> va, Validated<B> vb,
            BiFunction<A, B, T> f) {
        return switch (va) {
            case Valid<A> a -> switch (vb) {
                case Valid<B> b -> new Valid<>(f.apply(a.value(), b.value()));
                case Invalid<B> ib -> new Invalid<>(ib.issues());
            };
            case Invalid<A> ia -> switch (vb) {
                case Valid<B> _ -> new Invalid<>(ia.issues());
                case Invalid<B> ib -> new Invalid<>(ia.issues().merge(ib.issues()));
            };
        };
    }

    static <T> Validated<T> accumulate(Validated<?>[] vals, Function<Object[], T> f) {
        Issues merged = Issues.EMPTY;
        boolean hasError = false;
        for (var v : vals) {
            if (v instanceof Invalid<?> inv) {
                merged = merged.merge(inv.issues());
                hasError = true;
            }
        }
        if (hasError) {
            return new Invalid<>(merged);
        }
        var args = new Object[vals.length];
        for (int i = 0; i < vals.length; i++) {
            args[i] = ((Valid<?>) vals[i]).value();
        }
        return new Valid<>(f.apply(args));
    }
}
