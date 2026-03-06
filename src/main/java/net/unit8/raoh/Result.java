package net.unit8.raoh;

import java.util.Map;
import java.util.function.Function;

public sealed interface Result<T> permits Ok, Err {

    default <U> Result<U> map(Function<T, U> f) {
        return switch (this) {
            case Ok<T> ok -> Result.ok(f.apply(ok.value()));
            case Err<T> err -> err.coerce();
        };
    }

    default <U> Result<U> flatMap(Function<T, Result<U>> f) {
        return switch (this) {
            case Ok<T> ok -> f.apply(ok.value());
            case Err<T> err -> err.coerce();
        };
    }

    default <R> R fold(Function<T, R> onOk, Function<Issues, R> onErr) {
        return switch (this) {
            case Ok<T> ok -> onOk.apply(ok.value());
            case Err<T> err -> onErr.apply(err.issues());
        };
    }

    default T orElseThrow(Function<Issues, ? extends RuntimeException> exceptionMapper) {
        return switch (this) {
            case Ok<T> ok -> ok.value();
            case Err<T> err -> throw exceptionMapper.apply(err.issues());
        };
    }

    static <T> Result<T> ok(T value) {
        return new Ok<>(value);
    }

    static <T> Result<T> err(Issues issues) {
        return new Err<>(issues);
    }

    static <T> Result<T> fail(Path path, String code, String message, Map<String, Object> meta) {
        return new Err<>(Issues.EMPTY.add(Issue.of(path, code, message, meta)));
    }

    static <T> Result<T> fail(Path path, String code, String message) {
        return new Err<>(Issues.EMPTY.add(Issue.of(path, code, message)));
    }

    static <T> Result<T> failCustom(Path path, String code, String message, Map<String, Object> meta) {
        return new Err<>(Issues.EMPTY.add(new Issue(path, code, message, meta, true)));
    }
}
