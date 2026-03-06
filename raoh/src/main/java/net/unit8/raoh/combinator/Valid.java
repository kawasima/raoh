package net.unit8.raoh.combinator;

import net.unit8.raoh.Result;

import java.util.function.Function;

record Valid<T>(T value) implements Validated<T> {
    @Override
    public <U> Validated<U> map(Function<T, U> f) {
        return new Valid<>(f.apply(value));
    }

    @Override
    public Result<T> toResult() {
        return Result.ok(value);
    }
}
