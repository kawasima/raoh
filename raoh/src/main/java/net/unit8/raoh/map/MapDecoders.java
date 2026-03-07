package net.unit8.raoh.map;

import net.unit8.raoh.*;
import net.unit8.raoh.combinator.*;
import net.unit8.raoh.builtin.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Boundary factory for {@code Map<String, Object>} input.
 * <p>
 * Usage: {@code import static net.unit8.raoh.map.MapDecoders.*;}
 * <p>
 * Primitive decoders (string(), int_(), etc.) receive values extracted by field().
 * At runtime, field() passes the raw Object value via an unchecked cast to
 * {@code Map<String, Object>}, and each primitive decoder casts back to Object
 * for instanceof checking. This is safe because the decoders never actually
 * call Map methods on primitive values.
 */
public final class MapDecoders {

    private MapDecoders() {}

    // --- Primitive decoders (input type is Object to accept raw field values) ---

    public static StringDecoder<Object> string() {
        Decoder<Object, String> base = allowBlankBase();
        return new StringDecoder<>(base, base);
    }

    public static StringDecoder<Object> allowBlankString() {
        return new StringDecoder<>(allowBlankBase());
    }

    private static Decoder<Object, String> allowBlankBase() {
        return (in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (in instanceof String s) {
                return Result.ok(s);
            }
            return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected string",
                    Map.of("expected", "string", "actual", in.getClass().getSimpleName()));
        };
    }

    public static IntDecoder<Object> int_() {
        return new IntDecoder<>((in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (in instanceof Integer i) {
                return Result.ok(i);
            }
            if (in instanceof Number n) {
                return Result.ok(n.intValue());
            }
            return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected integer",
                    Map.of("expected", "integer", "actual", in.getClass().getSimpleName()));
        });
    }

    public static LongDecoder<Object> long_() {
        return new LongDecoder<>((in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (in instanceof Long l) {
                return Result.ok(l);
            }
            if (in instanceof Number n) {
                return Result.ok(n.longValue());
            }
            return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected long",
                    Map.of("expected", "long", "actual", in.getClass().getSimpleName()));
        });
    }

    public static BoolDecoder<Object> bool() {
        return new BoolDecoder<>((in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (in instanceof Boolean b) {
                return Result.ok(b);
            }
            return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected boolean",
                    Map.of("expected", "boolean", "actual", in.getClass().getSimpleName()));
        });
    }

    public static DecimalDecoder<Object> decimal() {
        return new DecimalDecoder<>((in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (in instanceof BigDecimal bd) {
                return Result.ok(bd);
            }
            if (in instanceof Number n) {
                return Result.ok(new BigDecimal(n.toString()));
            }
            return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected number",
                    Map.of("expected", "number", "actual", in.getClass().getSimpleName()));
        });
    }

    // --- field / optionalField / nullable ---

    public static <T> FieldDecoder<Map<String, Object>, T> field(String name, Decoder<Object, T> dec) {
        return new FieldDecoder<>() {
            @Override
            public String fieldName() { return name; }

            @Override
            public Result<T> decode(Map<String, Object> in, Path path) {
                var fieldPath = path.append(name);
                if (in == null) {
                    return Result.fail(fieldPath, ErrorCodes.REQUIRED, "is required");
                }
                return dec.decode(in.get(name), fieldPath);
            }
        };
    }

    public static <T> Decoder<Map<String, Object>, Optional<T>> optionalField(String name, Decoder<Object, T> dec) {
        return (in, path) -> {
            var fieldPath = path.append(name);
            if (in == null || !in.containsKey(name)) {
                return Result.ok(Optional.empty());
            }
            return dec.decode(in.get(name), fieldPath).map(Optional::of);
        };
    }

    /**
     * Adapts a {@code Decoder<Map<String, Object>, T>} for use as a field value decoder.
     *
     * <p><strong>Why this is needed</strong></p>
     * <p>{@link #field} and {@link #list} accept a {@code Decoder<Object, T>} for the value,
     * because field values arrive as raw {@code Object} from the enclosing map.
     * But a decoder built with {@link #combine} + {@link #field} internally produces a
     * {@code Decoder<Map<String, Object>, T>} — there is a type gap between {@code Object}
     * and {@code Map<String, Object>} that {@code nested()} bridges.
     *
     * <p><strong>Usage example</strong></p>
     * <pre>{@code
     * // Address decoder expects Map<String, Object> as its input
     * Decoder<Map<String, Object>, Address> addressDecoder = combine(
     *     field("zip",  string().minLength(3)),
     *     field("city", string())
     * ).apply(Address::new);
     *
     * // nested() lets you embed it inside an outer field() call
     * Decoder<Map<String, Object>, User> userDecoder = combine(
     *     field("name",    string()),
     *     field("address", nested(addressDecoder))  // ← nested() required here
     * ).apply(User::new);
     * }</pre>
     *
     * <p>Without {@code nested()}, the compiler rejects the second {@code field()} call because
     * {@code addressDecoder} has type {@code Decoder<Map<String,Object>, Address>} but
     * {@code field()} requires {@code Decoder<Object, Address>}.
     *
     * <p><strong>Note:</strong> {@code net.unit8.raoh.json.JsonDecoders} does not need a
     * {@code nested()} equivalent because all decoders there share the same input type
     * ({@code tools.jackson.databind.JsonNode}).
     *
     * @param <T> the decoded output type
     * @param dec the inner decoder that expects a {@code Map<String, Object>}
     * @return a decoder whose input type is {@code Object}, suitable for use with {@link #field}
     */
    @SuppressWarnings("unchecked")
    public static <T> Decoder<Object, T> nested(Decoder<Map<String, Object>, T> dec) {
        return (in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            // Use Map<String, ?> pattern to ensure keys are Strings before casting.
            if (!(in instanceof Map<?, ?> rawMap) || !isStringKeyedMap(rawMap)) {
                return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected object",
                        Map.of("expected", "object", "actual", in.getClass().getSimpleName()));
            }
            return dec.decode((Map<String, Object>) rawMap, path);
        };
    }

    private static boolean isStringKeyedMap(Map<?, ?> map) {
        if (map.isEmpty()) return true;
        // Check only the first key; a heterogeneous map would be a programming error upstream.
        return map.keySet().iterator().next() instanceof String;
    }

    public static <T> Decoder<Object, T> nullable(Decoder<Object, T> dec) {
        return (in, path) -> {
            if (in == null) {
                return Result.ok(null);
            }
            return dec.decode(in, path);
        };
    }

    public static <T> Decoder<Map<String, Object>, Presence<T>> optionalNullableField(String name, Decoder<Object, T> dec) {
        return (in, path) -> {
            var fieldPath = path.append(name);
            if (in == null || !in.containsKey(name)) {
                return Result.ok(new Presence.Absent<>());
            }
            var value = in.get(name);
            if (value == null) {
                return Result.ok(new Presence.PresentNull<>());
            }
            return dec.decode(value, fieldPath)
                    .map(v -> (Presence<T>) new Presence.Present<>(v));
        };
    }

    // --- list / map ---

    public static <T> ListDecoder<Object, T> list(Decoder<Object, T> elementDec) {
        return new ListDecoder<>((in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (!(in instanceof List<?> rawList)) {
                return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected array",
                        Map.of("expected", "array", "actual", in.getClass().getSimpleName()));
            }
            var issues = Issues.EMPTY;
            var results = new ArrayList<T>();
            for (int i = 0; i < rawList.size(); i++) {
                var elemPath = path.append(String.valueOf(i));
                var r = elementDec.decode(rawList.get(i), elemPath);
                switch (r) {
                    case Ok<T> ok -> results.add(ok.value());
                    case Err<T> err -> issues = issues.merge(err.issues());
                }
            }
            if (!issues.asList().isEmpty()) {
                return Result.err(issues);
            }
            return Result.ok(List.copyOf(results));
        });
    }

    public static <V> RecordDecoder<Object, V> map(Decoder<Object, V> valDec) {
        return new RecordDecoder<>((in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (!(in instanceof Map<?, ?> rawMap)) {
                return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected object",
                        Map.of("expected", "object", "actual", in.getClass().getSimpleName()));
            }
            var issues = Issues.EMPTY;
            var results = new LinkedHashMap<String, V>();
            for (var entry : rawMap.entrySet()) {
                var keyPath = path.append(String.valueOf(entry.getKey()));
                var r = valDec.decode(entry.getValue(), keyPath);
                switch (r) {
                    case Ok<V> ok -> results.put(String.valueOf(entry.getKey()), ok.value());
                    case Err<V> err -> issues = issues.merge(err.issues());
                }
            }
            if (!issues.asList().isEmpty()) {
                return Result.err(issues);
            }
            return Result.ok(Map.copyOf(results));
        });
    }

    // --- enumOf / literal ---

    public static <E extends Enum<E>> Decoder<Object, E> enumOf(Class<E> cls) {
        return Decoders.enumOf(cls, allowBlankString());
    }

    public static Decoder<Object, String> literal(String expected) {
        return Decoders.literal(expected, allowBlankString());
    }

    public static <T> Decoder<Map<String, Object>, T> strict(Decoder<Map<String, Object>, T> dec, java.util.Set<String> knownFields) {
        return Decoders.strict(dec, knownFields);
    }

    // --- Delegate combine to Decoders ---

    public static <A, B> Combiner2<Map<String, Object>, A, B> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db) {
        return Decoders.combine(da, db);
    }

    public static <A, B, C> Combiner3<Map<String, Object>, A, B, C> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc) {
        return Decoders.combine(da, db, dc);
    }

    public static <A, B, C, D> Combiner4<Map<String, Object>, A, B, C, D> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc, Decoder<Map<String, Object>, D> dd) {
        return Decoders.combine(da, db, dc, dd);
    }

    public static <A, B, C, D, E> Combiner5<Map<String, Object>, A, B, C, D, E> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc, Decoder<Map<String, Object>, D> dd,
            Decoder<Map<String, Object>, E> de) {
        return Decoders.combine(da, db, dc, dd, de);
    }

    public static <A, B, C, D, E, F> Combiner6<Map<String, Object>, A, B, C, D, E, F> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc, Decoder<Map<String, Object>, D> dd,
            Decoder<Map<String, Object>, E> de, Decoder<Map<String, Object>, F> df) {
        return Decoders.combine(da, db, dc, dd, de, df);
    }

    public static <A, B, C, D, E, F, G> Combiner7<Map<String, Object>, A, B, C, D, E, F, G> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc, Decoder<Map<String, Object>, D> dd,
            Decoder<Map<String, Object>, E> de, Decoder<Map<String, Object>, F> df,
            Decoder<Map<String, Object>, G> dg) {
        return Decoders.combine(da, db, dc, dd, de, df, dg);
    }

    public static <A, B, C, D, E, F, G, H> Combiner8<Map<String, Object>, A, B, C, D, E, F, G, H> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc, Decoder<Map<String, Object>, D> dd,
            Decoder<Map<String, Object>, E> de, Decoder<Map<String, Object>, F> df,
            Decoder<Map<String, Object>, G> dg, Decoder<Map<String, Object>, H> dh) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh);
    }

    public static <A, B, C, D, E, F, G, H, J> Combiner9<Map<String, Object>, A, B, C, D, E, F, G, H, J> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc, Decoder<Map<String, Object>, D> dd,
            Decoder<Map<String, Object>, E> de, Decoder<Map<String, Object>, F> df,
            Decoder<Map<String, Object>, G> dg, Decoder<Map<String, Object>, H> dh,
            Decoder<Map<String, Object>, J> dj) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj);
    }

    public static <A, B, C, D, E, F, G, H, J, K> Combiner10<Map<String, Object>, A, B, C, D, E, F, G, H, J, K> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc, Decoder<Map<String, Object>, D> dd,
            Decoder<Map<String, Object>, E> de, Decoder<Map<String, Object>, F> df,
            Decoder<Map<String, Object>, G> dg, Decoder<Map<String, Object>, H> dh,
            Decoder<Map<String, Object>, J> dj, Decoder<Map<String, Object>, K> dk) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk);
    }

    public static <A, B, C, D, E, F, G, H, J, K, L> Combiner11<Map<String, Object>, A, B, C, D, E, F, G, H, J, K, L> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc, Decoder<Map<String, Object>, D> dd,
            Decoder<Map<String, Object>, E> de, Decoder<Map<String, Object>, F> df,
            Decoder<Map<String, Object>, G> dg, Decoder<Map<String, Object>, H> dh,
            Decoder<Map<String, Object>, J> dj, Decoder<Map<String, Object>, K> dk,
            Decoder<Map<String, Object>, L> dl) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl);
    }

    public static <A, B, C, D, E, F, G, H, J, K, L, M> Combiner12<Map<String, Object>, A, B, C, D, E, F, G, H, J, K, L, M> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc, Decoder<Map<String, Object>, D> dd,
            Decoder<Map<String, Object>, E> de, Decoder<Map<String, Object>, F> df,
            Decoder<Map<String, Object>, G> dg, Decoder<Map<String, Object>, H> dh,
            Decoder<Map<String, Object>, J> dj, Decoder<Map<String, Object>, K> dk,
            Decoder<Map<String, Object>, L> dl, Decoder<Map<String, Object>, M> dm) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm);
    }

    public static <A, B, C, D, E, F, G, H, J, K, L, M, N> Combiner13<Map<String, Object>, A, B, C, D, E, F, G, H, J, K, L, M, N> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc, Decoder<Map<String, Object>, D> dd,
            Decoder<Map<String, Object>, E> de, Decoder<Map<String, Object>, F> df,
            Decoder<Map<String, Object>, G> dg, Decoder<Map<String, Object>, H> dh,
            Decoder<Map<String, Object>, J> dj, Decoder<Map<String, Object>, K> dk,
            Decoder<Map<String, Object>, L> dl, Decoder<Map<String, Object>, M> dm,
            Decoder<Map<String, Object>, N> dn) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn);
    }

    public static <A, B, C, D, E, F, G, H, J, K, L, M, N, O> Combiner14<Map<String, Object>, A, B, C, D, E, F, G, H, J, K, L, M, N, O> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc, Decoder<Map<String, Object>, D> dd,
            Decoder<Map<String, Object>, E> de, Decoder<Map<String, Object>, F> df,
            Decoder<Map<String, Object>, G> dg, Decoder<Map<String, Object>, H> dh,
            Decoder<Map<String, Object>, J> dj, Decoder<Map<String, Object>, K> dk,
            Decoder<Map<String, Object>, L> dl, Decoder<Map<String, Object>, M> dm,
            Decoder<Map<String, Object>, N> dn, Decoder<Map<String, Object>, O> do_) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn, do_);
    }

    public static <A, B, C, D, E, F, G, H, J, K, L, M, N, O, P> Combiner15<Map<String, Object>, A, B, C, D, E, F, G, H, J, K, L, M, N, O, P> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc, Decoder<Map<String, Object>, D> dd,
            Decoder<Map<String, Object>, E> de, Decoder<Map<String, Object>, F> df,
            Decoder<Map<String, Object>, G> dg, Decoder<Map<String, Object>, H> dh,
            Decoder<Map<String, Object>, J> dj, Decoder<Map<String, Object>, K> dk,
            Decoder<Map<String, Object>, L> dl, Decoder<Map<String, Object>, M> dm,
            Decoder<Map<String, Object>, N> dn, Decoder<Map<String, Object>, O> do_,
            Decoder<Map<String, Object>, P> dp) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn, do_, dp);
    }

    public static <A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, Q> Combiner16<Map<String, Object>, A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, Q> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc, Decoder<Map<String, Object>, D> dd,
            Decoder<Map<String, Object>, E> de, Decoder<Map<String, Object>, F> df,
            Decoder<Map<String, Object>, G> dg, Decoder<Map<String, Object>, H> dh,
            Decoder<Map<String, Object>, J> dj, Decoder<Map<String, Object>, K> dk,
            Decoder<Map<String, Object>, L> dl, Decoder<Map<String, Object>, M> dm,
            Decoder<Map<String, Object>, N> dn, Decoder<Map<String, Object>, O> do_,
            Decoder<Map<String, Object>, P> dp, Decoder<Map<String, Object>, Q> dq) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn, do_, dp, dq);
    }
}
