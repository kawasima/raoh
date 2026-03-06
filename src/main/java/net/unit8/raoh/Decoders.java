package net.unit8.raoh;

import net.unit8.raoh.combinator.*;
import net.unit8.raoh.builtin.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public final class Decoders {

    private Decoders() {}

    // --- combine overloads ---

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

    // --- Utility combinators ---

    public static <I, T> Decoder<I, T> lazy(Supplier<Decoder<I, T>> supplier) {
        return (in, path) -> supplier.get().decode(in, path);
    }

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

    public static <I, T> Decoder<I, T> recover(Decoder<I, T> dec, T fallback) {
        return (in, path) -> {
            var r = dec.decode(in, path);
            return switch (r) {
                case Ok<T> ok -> ok;
                case Err<T> ignored -> Result.ok(fallback);
            };
        };
    }

    public static <I, T> Decoder<I, T> recover(Decoder<I, T> dec, Function<Issues, T> fallback) {
        return (in, path) -> {
            var r = dec.decode(in, path);
            return switch (r) {
                case Ok<T> ok -> ok;
                case Err<T> err -> Result.ok(fallback.apply(err.issues()));
            };
        };
    }

    @SafeVarargs
    public static <I, T> Decoder<I, T> oneOf(Decoder<I, ? extends T>... candidates) {
        return (in, path) -> {
            var candidateIssues = new java.util.ArrayList<Map<String, Object>>();
            for (int i = 0; i < candidates.length; i++) {
                var r = candidates[i].decode(in, path);
                if (r instanceof Ok<?>) {
                    @SuppressWarnings("unchecked")
                    var ok = (Result<T>) r;
                    return ok;
                }
                if (r instanceof Err<?> err) {
                    candidateIssues.add(Map.of(
                            "candidate", i,
                            "issues", err.issues().toJsonList()));
                }
            }
            return Result.fail(path, "one_of_failed", "no variant matched",
                    Map.of("candidates", List.copyOf(candidateIssues)));
        };
    }

    public static <I, T> Decoder<I, T> strict(Decoder<I, T> dec, Set<String> knownFields) {
        return (in, path) -> {
            var issues = Issues.EMPTY;
            if (in instanceof Map<?, ?> rawMap) {
                for (var key : rawMap.keySet()) {
                    var name = String.valueOf(key);
                    if (!knownFields.contains(name)) {
                        issues = issues.add(Issue.of(path.append(name), "unknown_field",
                                "unknown field", Map.of("field", name)));
                    }
                }
            } else if (in instanceof com.fasterxml.jackson.databind.JsonNode node && node.isObject()) {
                var fields = node.fieldNames();
                while (fields.hasNext()) {
                    var name = fields.next();
                    if (!knownFields.contains(name)) {
                        issues = issues.add(Issue.of(path.append(name), "unknown_field",
                                "unknown field", Map.of("field", name)));
                    }
                }
            }

            if (issues.list().isEmpty()) {
                return dec.decode(in, path);
            }

            return switch (dec.decode(in, path)) {
                case Ok<T> ignored -> Result.err(issues);
                case Err<T> err -> Result.err(err.issues().merge(issues));
            };
        };
    }

    public static <I, E extends Enum<E>> Decoder<I, E> enumOf(Class<E> cls, Decoder<I, String> stringDec) {
        return (in, path) -> {
            var r = stringDec.decode(in, path);
            return switch (r) {
                case Err<String> err -> err.coerce();
                case Ok<String> ok -> {
                    var value = ok.value();
                    for (var constant : cls.getEnumConstants()) {
                        if (constant.name().equalsIgnoreCase(value)) {
                            yield Result.ok(constant);
                        }
                    }
                    var allowed = java.util.Arrays.stream(cls.getEnumConstants())
                            .map(e -> e.name().toLowerCase())
                            .toList();
                    yield Result.fail(path, "invalid_format",
                            "unknown value: " + value,
                            Map.of("allowed", allowed));
                }
            };
        };
    }

    public static <I> Decoder<I, String> literal(String expected, Decoder<I, String> stringDec) {
        return (in, path) -> {
            var r = stringDec.decode(in, path);
            return switch (r) {
                case Err<String> err -> err.coerce();
                case Ok<String> ok -> {
                    if (!expected.equals(ok.value())) {
                        yield Result.fail(path, "invalid_format",
                                "expected \"%s\"".formatted(expected),
                                Map.of("expected", expected, "actual", ok.value()));
                    }
                    yield Result.ok(ok.value());
                }
            };
        };
    }

    private static boolean shouldUseDefault(Issues issues) {
        return !issues.list().isEmpty()
                && issues.list().stream().allMatch(issue -> issue.code().equals("required"));
    }
}
