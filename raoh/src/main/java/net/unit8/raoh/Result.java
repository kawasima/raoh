package net.unit8.raoh;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
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
     * Returns {@code true} if this result is {@link Ok}.
     *
     * @return {@code true} for success, {@code false} for failure
     */
    default boolean isOk() {
        return this instanceof Ok<T>;
    }

    /**
     * Returns {@code true} if this result is {@link Err}.
     *
     * @return {@code true} for failure, {@code false} for success
     */
    default boolean isErr() {
        return this instanceof Err<T>;
    }

    /**
     * Returns the decoded value if this result is {@link Ok}, or throws if it is {@link Err}.
     *
     * <p>Prefer {@link #orElseThrow(java.util.function.Function)} when you want a domain-specific
     * exception type. Use this method only when a failure is truly unexpected.
     *
     * @return the decoded value
     * @throws IllegalStateException if this result is {@link Err}
     */
    default T getOrThrow() {
        return switch (this) {
            case Ok<T> ok -> ok.value();
            case Err<T> err -> throw new IllegalStateException(
                    "Decode failed: " + err.issues().asList().stream()
                            .map(Issue::message)
                            .reduce((a, b) -> a + "; " + b)
                            .orElse("(no issues)"));
        };
    }

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
     * <p><strong>Path propagation note:</strong> The mapping function {@code f} receives only the
     * decoded value — not the current {@link Path}. If {@code f} needs to produce an error with a
     * correct path (e.g., for cross-field validation), wrap the entire logic in a {@link Decoder}
     * lambda so that {@code path} is available via closure:
     * <pre>{@code
     * Decoder<JsonNode, Money> moneyDecoder = (in, path) ->
     *     baseDecoder.decode(in, path).flatMap(money -> {
     *         if (money.amount().signum() < 0)
     *             return Result.fail(path.append("amount"), "out_of_range", "must be positive");
     *         return Result.ok(money);
     *     });
     * }</pre>
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
     * Creates a failed result with a single issue at the root path (no metadata).
     *
     * <p>Equivalent to {@code Result.fail(Path.ROOT, code, message)}.
     * Use this only for errors that apply to the input as a whole, not to a specific field.
     * For field-level errors, always pass an explicit {@link Path}.
     *
     * @param <T>     the value type
     * @param code    the error code
     * @param message the error message
     * @return an {@link Err} result at {@link Path#ROOT}
     */
    static <T> Result<T> fail(String code, String message) {
        return fail(Path.ROOT, code, message);
    }

    /**
     * Creates a failed result with a single issue at the root path.
     *
     * <p>Equivalent to {@code Result.fail(Path.ROOT, code, message, meta)}.
     * Use this only for errors that apply to the input as a whole, not to a specific field.
     * For field-level errors, always pass an explicit {@link Path}.
     *
     * @param <T>     the value type
     * @param code    the error code
     * @param message the error message
     * @param meta    additional metadata
     * @return an {@link Err} result at {@link Path#ROOT}
     */
    static <T> Result<T> fail(String code, String message, Map<String, Object> meta) {
        return fail(Path.ROOT, code, message, meta);
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

    /**
     * Combines two independent results, accumulating errors from both if either fails.
     *
     * <p>Unlike {@link #flatMap}, both {@code ra} and {@code rb} are evaluated regardless of
     * whether the other succeeds. If both fail, the issues from both are merged.
     *
     * <p>Use this when the two results come from independent sources (e.g., different input types
     * or different database tables). When both results share the same input type, prefer
     * {@link Decoders#combine} which composes decoders before decoding.
     *
     * @param <A> the value type of the first result
     * @param <B> the value type of the second result
     * @param <C> the combined output type
     * @param ra  the first result
     * @param rb  the second result
     * @param f   the function to combine the two values if both succeed
     * @return {@link Ok} with the combined value, or {@link Err} with all accumulated issues
     */
    static <A, B, C> Result<C> map2(Result<A> ra, Result<B> rb, BiFunction<A, B, C> f) {
        if (ra instanceof Ok<A> oa && rb instanceof Ok<B> ob) {
            return Result.ok(f.apply(oa.value(), ob.value()));
        }
        Issues issues = Issues.EMPTY;
        if (ra instanceof Err<A> e) issues = issues.merge(e.issues());
        if (rb instanceof Err<B> e) issues = issues.merge(e.issues());
        return Result.err(issues);
    }

    /**
     * Decodes every element of a list, accumulating all errors rather than short-circuiting
     * on the first failure.
     *
     * <p>Each element at index {@code i} is decoded with the path {@code basePath/i}.
     * If all elements succeed the method returns {@code Ok<List<T>>}; if any element
     * fails the method returns {@code Err} containing the merged issues from every
     * failing element.
     *
     * @param <I>      the element input type
     * @param <T>      the decoded element type
     * @param items    the list of inputs to decode
     * @param f        the decoding function applied to each element together with its path
     * @param basePath the path prefix prepended to each element index
     * @return a result containing all decoded values, or all accumulated errors
     */
    static <I, T> Result<List<T>> traverse(
            List<I> items,
            BiFunction<I, Path, Result<T>> f,
            Path basePath) {
        Issues accumulated = Issues.EMPTY;
        List<T> values = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            Path itemPath = basePath.append(String.valueOf(i));
            Result<T> r = f.apply(items.get(i), itemPath);
            switch (r) {
                case Ok<T> ok -> values.add(ok.value());
                case Err<T> err -> accumulated = accumulated.merge(err.issues());
            }
        }
        return accumulated.asList().isEmpty()
                ? Result.ok(Collections.unmodifiableList(values))
                : Result.err(accumulated);
    }

    /**
     * Decodes every element of a list at the root path, accumulating all errors.
     *
     * <p>Equivalent to {@link #traverse(List, BiFunction, Path)} with {@link Path#ROOT}.
     *
     * @param <I>   the element input type
     * @param <T>   the decoded element type
     * @param items the list of inputs to decode
     * @param f     the decoding function applied to each element together with its path
     * @return a result containing all decoded values, or all accumulated errors
     */
    static <I, T> Result<List<T>> traverse(List<I> items, BiFunction<I, Path, Result<T>> f) {
        return traverse(items, f, Path.ROOT);
    }

    /**
     * Maps every element of a list through a function that returns a {@link Result},
     * accumulating all errors rather than short-circuiting on the first failure.
     *
     * <p>Unlike {@link #traverse(List, BiFunction)}, this overload does not pass a
     * {@link Path} to the mapping function. Errors produced by {@code f} retain whatever
     * path they already have — no index-based path is prepended.
     *
     * <p>Use this when the mapping function does not need path context — for example,
     * when calling a service method that returns a {@link Result}.
     *
     * @param <I>   the element input type
     * @param <T>   the decoded element type
     * @param items the list of inputs to process
     * @param f     the mapping function applied to each element
     * @return a result containing all mapped values, or all accumulated errors
     */
    static <I, T> Result<List<T>> traverseResults(List<I> items, Function<I, Result<T>> f) {
        Issues accumulated = Issues.EMPTY;
        List<T> values = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            Result<T> r = f.apply(items.get(i));
            switch (r) {
                case Ok<T> ok -> values.add(ok.value());
                case Err<T> err -> accumulated = accumulated.merge(err.issues());
            }
        }
        return accumulated.asList().isEmpty()
                ? Result.ok(Collections.unmodifiableList(values))
                : Result.err(accumulated);
    }
}
