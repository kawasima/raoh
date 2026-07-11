package net.unit8.raoh.decode.combinator;

import net.unit8.raoh.Result;

import java.util.function.Function;

record Valid<T>(T value) implements Validated<T> {
    @Override
    public <U> Validated<U> map(Function<? super T, ? extends U> f) {
        return new Valid<>(f.apply(value));
    }

    @Override
    public Result<T> toResult() {
        return Result.ok(value);
    }
}
