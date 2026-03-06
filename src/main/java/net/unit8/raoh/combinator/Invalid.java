package net.unit8.raoh.combinator;

import net.unit8.raoh.Issues;
import net.unit8.raoh.Result;

import java.util.function.Function;

record Invalid<T>(Issues issues) implements Validated<T> {
    @SuppressWarnings("unchecked")
    @Override
    public <U> Validated<U> map(Function<T, U> f) {
        return (Invalid<U>) this;
    }

    @Override
    public Result<T> toResult() {
        return Result.err(issues);
    }
}
