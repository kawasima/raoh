package net.unit8.raoh;

import net.unit8.raoh.combinator.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Core combinators and utility decoders that work with any input type.
 *
 * <p>For input-specific factories, see {@code net.unit8.raoh.json.JsonDecoders}
 * and {@link net.unit8.raoh.map.MapDecoders}.
 */
public final class Decoders {

    private Decoders() {}

    // --- combine overloads ---

    /**
     * Combines two decoders for applicative-style validation.
     * All decoders run independently and errors are accumulated.
     *
     * @param <I> the input type
     * @param <A> the first decoder's output type
     * @param <B> the second decoder's output type
     * @param da  the first decoder
     * @param db  the second decoder
     * @return a combiner that can be applied with a function
     */
    public static <I, A, B> Combiner2<I, A, B> combine(
            Decoder<I, A> da, Decoder<I, B> db) {
        return new Combiner2<>(da, db);
    }

    public static <I, A, B, C> Combiner3<I, A, B, C> combine(
            Decoder<I, A> da, Decoder<I, B> db, Decoder<I, C> dc) {
        return new Combiner3<>(da, db, dc);
    }

    public static <I, A, B, C, D> Combiner4<I, A, B, C, D> combine(
            Decoder<I, A> da, Decoder<I, B> db, Decoder<I, C> dc, Decoder<I, D> dd) {
        return new Combiner4<>(da, db, dc, dd);
    }

    public static <I, A, B, C, D, E> Combiner5<I, A, B, C, D, E> combine(
            Decoder<I, A> da, Decoder<I, B> db, Decoder<I, C> dc, Decoder<I, D> dd,
            Decoder<I, E> de) {
        return new Combiner5<>(da, db, dc, dd, de);
    }

    public static <I, A, B, C, D, E, F> Combiner6<I, A, B, C, D, E, F> combine(
            Decoder<I, A> da, Decoder<I, B> db, Decoder<I, C> dc, Decoder<I, D> dd,
            Decoder<I, E> de, Decoder<I, F> df) {
        return new Combiner6<>(da, db, dc, dd, de, df);
    }

    public static <I, A, B, C, D, E, F, G> Combiner7<I, A, B, C, D, E, F, G> combine(
            Decoder<I, A> da, Decoder<I, B> db, Decoder<I, C> dc, Decoder<I, D> dd,
            Decoder<I, E> de, Decoder<I, F> df, Decoder<I, G> dg) {
        return new Combiner7<>(da, db, dc, dd, de, df, dg);
    }

    public static <I, A, B, C, D, E, F, G, H> Combiner8<I, A, B, C, D, E, F, G, H> combine(
            Decoder<I, A> da, Decoder<I, B> db, Decoder<I, C> dc, Decoder<I, D> dd,
            Decoder<I, E> de, Decoder<I, F> df, Decoder<I, G> dg, Decoder<I, H> dh) {
        return new Combiner8<>(da, db, dc, dd, de, df, dg, dh);
    }

    public static <I, A, B, C, D, E, F, G, H, J> Combiner9<I, A, B, C, D, E, F, G, H, J> combine(
            Decoder<I, A> da, Decoder<I, B> db, Decoder<I, C> dc, Decoder<I, D> dd,
            Decoder<I, E> de, Decoder<I, F> df, Decoder<I, G> dg, Decoder<I, H> dh,
            Decoder<I, J> dj) {
        return new Combiner9<>(da, db, dc, dd, de, df, dg, dh, dj);
    }

    public static <I, A, B, C, D, E, F, G, H, J, K> Combiner10<I, A, B, C, D, E, F, G, H, J, K> combine(
            Decoder<I, A> da, Decoder<I, B> db, Decoder<I, C> dc, Decoder<I, D> dd,
            Decoder<I, E> de, Decoder<I, F> df, Decoder<I, G> dg, Decoder<I, H> dh,
            Decoder<I, J> dj, Decoder<I, K> dk) {
        return new Combiner10<>(da, db, dc, dd, de, df, dg, dh, dj, dk);
    }

    public static <I, A, B, C, D, E, F, G, H, J, K, L> Combiner11<I, A, B, C, D, E, F, G, H, J, K, L> combine(
            Decoder<I, A> da, Decoder<I, B> db, Decoder<I, C> dc, Decoder<I, D> dd,
            Decoder<I, E> de, Decoder<I, F> df, Decoder<I, G> dg, Decoder<I, H> dh,
            Decoder<I, J> dj, Decoder<I, K> dk, Decoder<I, L> dl) {
        return new Combiner11<>(da, db, dc, dd, de, df, dg, dh, dj, dk, dl);
    }

    public static <I, A, B, C, D, E, F, G, H, J, K, L, M> Combiner12<I, A, B, C, D, E, F, G, H, J, K, L, M> combine(
            Decoder<I, A> da, Decoder<I, B> db, Decoder<I, C> dc, Decoder<I, D> dd,
            Decoder<I, E> de, Decoder<I, F> df, Decoder<I, G> dg, Decoder<I, H> dh,
            Decoder<I, J> dj, Decoder<I, K> dk, Decoder<I, L> dl, Decoder<I, M> dm) {
        return new Combiner12<>(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm);
    }

    public static <I, A, B, C, D, E, F, G, H, J, K, L, M, N> Combiner13<I, A, B, C, D, E, F, G, H, J, K, L, M, N> combine(
            Decoder<I, A> da, Decoder<I, B> db, Decoder<I, C> dc, Decoder<I, D> dd,
            Decoder<I, E> de, Decoder<I, F> df, Decoder<I, G> dg, Decoder<I, H> dh,
            Decoder<I, J> dj, Decoder<I, K> dk, Decoder<I, L> dl, Decoder<I, M> dm,
            Decoder<I, N> dn) {
        return new Combiner13<>(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn);
    }

    public static <I, A, B, C, D, E, F, G, H, J, K, L, M, N, O> Combiner14<I, A, B, C, D, E, F, G, H, J, K, L, M, N, O> combine(
            Decoder<I, A> da, Decoder<I, B> db, Decoder<I, C> dc, Decoder<I, D> dd,
            Decoder<I, E> de, Decoder<I, F> df, Decoder<I, G> dg, Decoder<I, H> dh,
            Decoder<I, J> dj, Decoder<I, K> dk, Decoder<I, L> dl, Decoder<I, M> dm,
            Decoder<I, N> dn, Decoder<I, O> do_) {
        return new Combiner14<>(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn, do_);
    }

    public static <I, A, B, C, D, E, F, G, H, J, K, L, M, N, O, P> Combiner15<I, A, B, C, D, E, F, G, H, J, K, L, M, N, O, P> combine(
            Decoder<I, A> da, Decoder<I, B> db, Decoder<I, C> dc, Decoder<I, D> dd,
            Decoder<I, E> de, Decoder<I, F> df, Decoder<I, G> dg, Decoder<I, H> dh,
            Decoder<I, J> dj, Decoder<I, K> dk, Decoder<I, L> dl, Decoder<I, M> dm,
            Decoder<I, N> dn, Decoder<I, O> do_, Decoder<I, P> dp) {
        return new Combiner15<>(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn, do_, dp);
    }

    public static <I, A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, Q> Combiner16<I, A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, Q> combine(
            Decoder<I, A> da, Decoder<I, B> db, Decoder<I, C> dc, Decoder<I, D> dd,
            Decoder<I, E> de, Decoder<I, F> df, Decoder<I, G> dg, Decoder<I, H> dh,
            Decoder<I, J> dj, Decoder<I, K> dk, Decoder<I, L> dl, Decoder<I, M> dm,
            Decoder<I, N> dn, Decoder<I, O> do_, Decoder<I, P> dp, Decoder<I, Q> dq) {
        return new Combiner16<>(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn, do_, dp, dq);
    }

    // --- fallback for 17+ fields ---

    /**
     * Returns a {@link CombinerList} for combining more than 16 decoders.
     *
     * <p>Use this when the typed {@code combine(da, db, ...)} overloads (up to 16 arguments)
     * are not sufficient. See {@link CombinerList} for usage examples.
     *
     * @param <I>      the input type
     * @param decoders the decoders to combine; must not be empty
     * @return a combiner on which {@code .map(f)} or {@code .flatMap(f)} can be called
     */
    public static <I> CombinerList<I> combine(List<Decoder<I, ?>> decoders) {
        return new CombinerList<>(decoders);
    }

    // --- Utility combinators ---

    /**
     * Creates a lazily-evaluated decoder. Useful for recursive decoder definitions.
     *
     * @param <I>      the input type
     * @param <T>      the output type
     * @param supplier supplies the decoder on each invocation
     * @return a lazy decoder
     */
    public static <I, T> Decoder<I, T> lazy(Supplier<Decoder<I, T>> supplier) {
        return (in, path) -> supplier.get().decode(in, path);
    }

    /**
     * Wraps a decoder to use a default value when the field is absent (i.e., all issues are "required").
     * If the decoder fails with a non-required error, the error is preserved.
     *
     * @param <I>      the input type
     * @param <T>      the output type
     * @param dec      the underlying decoder
     * @param fallback the default value
     * @return a decoder with default-value behavior
     */
    public static <I, T> Decoder<I, T> withDefault(Decoder<I, T> dec, T fallback) {
        return (in, path) -> {
            var r = dec.decode(in, path);
            return switch (r) {
                case Ok<T> ok -> ok;
                case Err<T> err -> shouldUseDefault(err.issues())
                        ? Result.ok(fallback)
                        : err.coerce();
            };
        };
    }

    /**
     * Like {@link #withDefault(Decoder, Object)}, but the default is lazily computed.
     *
     * @param <I>      the input type
     * @param <T>      the output type
     * @param dec      the underlying decoder
     * @param fallback supplier for the default value
     * @return a decoder with default-value behavior
     */
    public static <I, T> Decoder<I, T> withDefault(Decoder<I, T> dec, Supplier<T> fallback) {
        return (in, path) -> {
            var r = dec.decode(in, path);
            return switch (r) {
                case Ok<T> ok -> ok;
                case Err<T> err -> shouldUseDefault(err.issues())
                        ? Result.ok(fallback.get())
                        : err.coerce();
            };
        };
    }

    /**
     * Wraps a decoder to recover from any error with a fixed fallback value.
     *
     * @param <I>      the input type
     * @param <T>      the output type
     * @param dec      the underlying decoder
     * @param fallback the recovery value
     * @return a decoder that never fails
     */
    public static <I, T> Decoder<I, T> recover(Decoder<I, T> dec, T fallback) {
        return (in, path) -> {
            var r = dec.decode(in, path);
            return switch (r) {
                case Ok<T> ok -> ok;
                case Err<T> _ -> Result.ok(fallback);
            };
        };
    }

    /**
     * Like {@link #recover(Decoder, Object)}, but computes the fallback from the issues.
     *
     * @param <I>      the input type
     * @param <T>      the output type
     * @param dec      the underlying decoder
     * @param fallback function to compute the recovery value from issues
     * @return a decoder that never fails
     */
    public static <I, T> Decoder<I, T> recover(Decoder<I, T> dec, Function<Issues, T> fallback) {
        return (in, path) -> {
            var r = dec.decode(in, path);
            return switch (r) {
                case Ok<T> ok -> ok;
                case Err<T> err -> Result.ok(fallback.apply(err.issues()));
            };
        };
    }

    /**
     * Tries each candidate decoder in order and returns the first successful result.
     * If all candidates fail, returns an error with all candidate-specific issues.
     *
     * @param <I>        the input type
     * @param <T>        the output type
     * @param candidates the candidate decoders to try
     * @return a decoder that succeeds if any candidate succeeds
     */
    @SafeVarargs
    public static <I, T> Decoder<I, T> oneOf(Decoder<I, ? extends T>... candidates) {
        return (in, path) -> {
            // Accumulate raw Issues; defer toJsonList() until the caller accesses meta.
            var failedIssues = new java.util.ArrayList<Issues>(candidates.length);
            for (int i = 0; i < candidates.length; i++) {
                var r = candidates[i].decode(in, path);
                if (r instanceof Ok<?>) {
                    @SuppressWarnings("unchecked")
                    var ok = (Result<T>) r;
                    return ok;
                }
                if (r instanceof Err<?> err) {
                    failedIssues.add(err.issues());
                }
            }
            // Build the meta lazily at error construction time (once, not per candidate).
            var candidateMeta = new java.util.ArrayList<Map<String, Object>>(failedIssues.size());
            for (int i = 0; i < failedIssues.size(); i++) {
                candidateMeta.add(Map.of("candidate", i, "issues", failedIssues.get(i).toJsonList()));
            }
            return Result.fail(path, ErrorCodes.ONE_OF_FAILED, "no variant matched",
                    Map.of("candidates", List.copyOf(candidateMeta)));
        };
    }

    /**
     * Wraps a decoder to reject unknown fields not in the given set.
     *
     * @param <I>         the input type
     * @param <T>         the output type
     * @param dec         the underlying decoder
     * @param knownFields the set of allowed field names
     * @return a strict decoder that fails on unknown fields
     */
    public static <I, T> Decoder<I, T> strict(Decoder<I, T> dec, Set<String> knownFields) {
        return (in, path) -> {
            var issues = Issues.EMPTY;
            if (in instanceof Map<?, ?> rawMap) {
                for (var key : rawMap.keySet()) {
                    var name = String.valueOf(key);
                    if (!knownFields.contains(name)) {
                        issues = issues.add(Issue.of(path.append(name), ErrorCodes.UNKNOWN_FIELD,
                                "unknown field", Map.of("field", name)));
                    }
                }
            }

            var decResult = dec.decode(in, path);
            if (issues.isEmpty()) {
                return decResult;
            }

            return switch (decResult) {
                case Ok<T> _ -> Result.err(issues);
                case Err<T> err -> Result.err(err.issues().merge(issues));
            };
        };
    }

    /**
     * Decodes a string into an enum constant (case-insensitive).
     *
     * @param <I>       the input type
     * @param <E>       the enum type
     * @param cls       the enum class
     * @param stringDec the string decoder to use
     * @return a decoder that produces enum constants
     */
    public static <I, E extends Enum<E>> Decoder<I, E> enumOf(Class<E> cls, Decoder<I, String> stringDec) {
        // Build lookup table and allowed-list once at decoder construction time.
        var lookup = new HashMap<String, E>();
        for (var c : cls.getEnumConstants()) {
            lookup.put(c.name().toLowerCase(), c);
        }
        var allowed = List.copyOf(lookup.keySet());
        return (in, path) -> {
            var r = stringDec.decode(in, path);
            return switch (r) {
                case Err<String> err -> err.coerce();
                case Ok<String> ok -> {
                    var constant = lookup.get(ok.value().toLowerCase());
                    if (constant != null) yield Result.ok(constant);
                    yield Result.fail(path, ErrorCodes.INVALID_FORMAT,
                            "invalid value",
                            Map.of("allowed", allowed));
                }
            };
        };
    }

    /**
     * Decodes a string and asserts it equals the expected value.
     *
     * @param <I>       the input type
     * @param expected  the expected string value
     * @param stringDec the string decoder to use
     * @return a decoder that succeeds only when the string matches
     */
    public static <I> Decoder<I, String> literal(String expected, Decoder<I, String> stringDec) {
        return (in, path) -> {
            var r = stringDec.decode(in, path);
            return switch (r) {
                case Err<String> err -> err.coerce();
                case Ok<String> ok -> {
                    if (!expected.equals(ok.value())) {
                        yield Result.fail(path, ErrorCodes.INVALID_FORMAT,
                                "invalid value",
                                Map.of("expected", expected));
                    }
                    yield Result.ok(ok.value());
                }
            };
        };
    }

    private static boolean shouldUseDefault(Issues issues) {
        return !issues.isEmpty()
                && issues.asList().stream().allMatch(issue -> issue.code().equals(ErrorCodes.REQUIRED));
    }
}
