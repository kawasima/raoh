package net.unit8.raoh;

import java.util.Map;
import java.util.function.Function;

/**
 * The result of a decoding operation — either {@link Ok} (success) or {@link Err} (failure with issues).
 *
 * <p>Use pattern matching to handle the result:
 * <pre>{@code
 * switch (result) {
 *     case Ok(var value)  -> // use value
 *     case Err(var issues) -> // handle errors
 * }
 * }</pre>
 *
 * @param <T> the type of the successfully decoded value
 */
public sealed interface Result<T> permits Ok, Err {

    /**
     * Transforms the value if this result is {@link Ok}.
     *
     * @param <U> the new value type
     * @param f   the mapping function
     * @return a new result with the transformed value, or the original error
     */
    default <U> Result<U> map(Function<T, U> f) {
        return switch (this) {
            case Ok<T> ok -> Result.ok(f.apply(ok.value()));
            case Err<T> err -> err.coerce();
        };
    }

    /**
     * Transforms the value using a function that may itself produce a failure.
     *
     * @param <U> the new value type
     * @param f   the mapping function returning a {@link Result}
     * @return the result of applying {@code f}, or the original error
     */
    default <U> Result<U> flatMap(Function<T, Result<U>> f) {
        return switch (this) {
            case Ok<T> ok -> f.apply(ok.value());
            case Err<T> err -> err.coerce();
        };
    }

    /**
     * Folds this result into a single value by applying one of the given functions.
     *
     * @param <R>   the return type
     * @param onOk  function to apply if this is {@link Ok}
     * @param onErr function to apply if this is {@link Err}
     * @return the folded value
     */
    default <R> R fold(Function<T, R> onOk, Function<Issues, R> onErr) {
        return switch (this) {
            case Ok<T> ok -> onOk.apply(ok.value());
            case Err<T> err -> onErr.apply(err.issues());
        };
    }

    /**
     * Returns the value if this is {@link Ok}, or throws an exception mapped from the issues.
     *
     * @param exceptionMapper function to create an exception from issues
     * @return the decoded value
     * @throws RuntimeException if this is {@link Err}
     */
    default T orElseThrow(Function<Issues, ? extends RuntimeException> exceptionMapper) {
        return switch (this) {
            case Ok<T> ok -> ok.value();
            case Err<T> err -> throw exceptionMapper.apply(err.issues());
        };
    }

    /**
     * Creates a successful result.
     *
     * @param <T>   the value type
     * @param value the decoded value
     * @return an {@link Ok} result
     */
    static <T> Result<T> ok(T value) {
        return new Ok<>(value);
    }

    /**
     * Creates a failed result with the given issues.
     *
     * @param <T>    the value type
     * @param issues the validation issues
     * @return an {@link Err} result
     */
    static <T> Result<T> err(Issues issues) {
        return new Err<>(issues);
    }

    /**
     * Creates a failed result with a single issue.
     *
     * @param <T>     the value type
     * @param path    the path where the error occurred
     * @param code    the error code
     * @param message the error message
     * @param meta    additional metadata
     * @return an {@link Err} result
     */
    static <T> Result<T> fail(Path path, String code, String message, Map<String, Object> meta) {
        return new Err<>(Issues.EMPTY.add(Issue.of(path, code, message, meta)));
    }

    /**
     * Creates a failed result with a single issue (no metadata).
     *
     * @param <T>     the value type
     * @param path    the path where the error occurred
     * @param code    the error code
     * @param message the error message
     * @return an {@link Err} result
     */
    static <T> Result<T> fail(Path path, String code, String message) {
        return new Err<>(Issues.EMPTY.add(Issue.of(path, code, message)));
    }

    /**
     * Creates a failed result with a custom message that will not be overridden by {@link MessageResolver}.
     *
     * @param <T>     the value type
     * @param path    the path where the error occurred
     * @param code    the error code
     * @param message the custom error message
     * @param meta    additional metadata
     * @return an {@link Err} result
     */
    static <T> Result<T> failCustom(Path path, String code, String message, Map<String, Object> meta) {
        return new Err<>(Issues.EMPTY.add(new Issue(path, code, message, meta, true)));
    }
}
