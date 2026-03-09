package net.unit8.raoh.map;

import net.unit8.raoh.*;
import net.unit8.raoh.combinator.*;

import java.util.Map;
import java.util.Optional;

/**
 * Boundary factory for {@code Map<String, Object>} input.
 *
 * <p>Usage: {@code import static net.unit8.raoh.map.MapDecoders.*;}
 *
 * <p>For primitive decoders ({@code string()}, {@code int_()}, etc.) that work on raw
 * {@code Object} values, see {@link net.unit8.raoh.ObjectDecoders}.
 */
public final class MapDecoders {

    private MapDecoders() {}

    // --- field / optionalField / optionalNullableField ---

    /**
     * Creates a required field decoder that extracts a value from a map by key
     * and decodes it with the given decoder.
     *
     * <p>The field path is appended to the current path for error reporting.
     *
     * @param <T>  the decoded value type
     * @param name the field name (map key)
     * @param dec  the decoder for the field value
     * @return a field decoder for {@code Map<String, Object>} input
     */
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

    /**
     * Creates an optional field decoder. If the field is absent from the map,
     * the result is {@code Optional.empty()} rather than an error.
     *
     * @param <T>  the decoded value type
     * @param name the field name (map key)
     * @param dec  the decoder for the field value when present
     * @return a decoder that produces {@code Optional<T>}
     */
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
     * <p>{@link #field} and {@link net.unit8.raoh.ObjectDecoders#list(Decoder) ObjectDecoders.list} accept a {@code Decoder<Object, T>} for the value,
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

    /**
     * Creates a three-state field decoder that distinguishes between absent, present-null,
     * and present-with-value.
     *
     * <p>Returns {@link Presence.Absent} if the key is missing from the map,
     * {@link Presence.PresentNull} if the key is present with a {@code null} value,
     * and {@link Presence.Present} with the decoded value otherwise.
     * This is useful for PATCH-style updates where the three states have different semantics.
     *
     * @param <T>  the decoded value type
     * @param name the field name (map key)
     * @param dec  the decoder for the field value when present and non-null
     * @return a decoder that produces {@link Presence Presence&lt;T&gt;}
     */
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

    /**
     * Wraps a decoder to reject maps containing keys not in the given set.
     *
     * @param <T>         the decoded value type
     * @param dec         the inner decoder
     * @param knownFields the set of allowed field names
     * @return a decoder that fails with {@code unknown_field} for unrecognized keys
     */
    public static <T> Decoder<Map<String, Object>, T> strict(Decoder<Map<String, Object>, T> dec, java.util.Set<String> knownFields) {
        return Decoders.strict(dec, knownFields);
    }

    // --- Delegate combine to Decoders ---

    /**
     * Combines two decoders so that all fields are decoded in parallel with error accumulation.
     *
     * <p>Overloads for 2–16 decoders are provided. Call {@code .apply(MyRecord::new)} on the
     * returned combiner to produce the final {@code Decoder<Map<String, Object>, T>}.
     *
     * @param <A> the first decoded type
     * @param <B> the second decoded type
     * @param da  the first decoder
     * @param db  the second decoder
     * @return a combiner that can be finished with {@code apply}
     */
    public static <A, B> Combiner2<Map<String, Object>, A, B> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db) {
        return Decoders.combine(da, db);
    }

    /** 3-arity variant of {@link #combine(Decoder, Decoder)}. See that method for details.
     *
     * @param <A> the first decoded type
     * @param <B> the second decoded type
     * @param <C> the third decoded type
     * @param da  the first decoder
     * @param db  the second decoder
     * @param dc  the third decoder
     * @return a combiner that can be finished with {@code apply}
     */
    public static <A, B, C> Combiner3<Map<String, Object>, A, B, C> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc) {
        return Decoders.combine(da, db, dc);
    }

    /** 4-arity variant of {@link #combine(Decoder, Decoder)}. @return a combiner */
    public static <A, B, C, D> Combiner4<Map<String, Object>, A, B, C, D> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc, Decoder<Map<String, Object>, D> dd) {
        return Decoders.combine(da, db, dc, dd);
    }

    /** 5-arity variant of {@link #combine(Decoder, Decoder)}. @return a combiner */
    public static <A, B, C, D, E> Combiner5<Map<String, Object>, A, B, C, D, E> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc, Decoder<Map<String, Object>, D> dd,
            Decoder<Map<String, Object>, E> de) {
        return Decoders.combine(da, db, dc, dd, de);
    }

    /** 6-arity variant of {@link #combine(Decoder, Decoder)}. @return a combiner */
    public static <A, B, C, D, E, F> Combiner6<Map<String, Object>, A, B, C, D, E, F> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc, Decoder<Map<String, Object>, D> dd,
            Decoder<Map<String, Object>, E> de, Decoder<Map<String, Object>, F> df) {
        return Decoders.combine(da, db, dc, dd, de, df);
    }

    /** 7-arity variant of {@link #combine(Decoder, Decoder)}. @return a combiner */
    public static <A, B, C, D, E, F, G> Combiner7<Map<String, Object>, A, B, C, D, E, F, G> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc, Decoder<Map<String, Object>, D> dd,
            Decoder<Map<String, Object>, E> de, Decoder<Map<String, Object>, F> df,
            Decoder<Map<String, Object>, G> dg) {
        return Decoders.combine(da, db, dc, dd, de, df, dg);
    }

    /** 8-arity variant of {@link #combine(Decoder, Decoder)}. @return a combiner */
    public static <A, B, C, D, E, F, G, H> Combiner8<Map<String, Object>, A, B, C, D, E, F, G, H> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc, Decoder<Map<String, Object>, D> dd,
            Decoder<Map<String, Object>, E> de, Decoder<Map<String, Object>, F> df,
            Decoder<Map<String, Object>, G> dg, Decoder<Map<String, Object>, H> dh) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh);
    }

    /** 9-arity variant of {@link #combine(Decoder, Decoder)}. @return a combiner */
    public static <A, B, C, D, E, F, G, H, J> Combiner9<Map<String, Object>, A, B, C, D, E, F, G, H, J> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc, Decoder<Map<String, Object>, D> dd,
            Decoder<Map<String, Object>, E> de, Decoder<Map<String, Object>, F> df,
            Decoder<Map<String, Object>, G> dg, Decoder<Map<String, Object>, H> dh,
            Decoder<Map<String, Object>, J> dj) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj);
    }

    /** 10-arity variant of {@link #combine(Decoder, Decoder)}. @return a combiner */
    public static <A, B, C, D, E, F, G, H, J, K> Combiner10<Map<String, Object>, A, B, C, D, E, F, G, H, J, K> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc, Decoder<Map<String, Object>, D> dd,
            Decoder<Map<String, Object>, E> de, Decoder<Map<String, Object>, F> df,
            Decoder<Map<String, Object>, G> dg, Decoder<Map<String, Object>, H> dh,
            Decoder<Map<String, Object>, J> dj, Decoder<Map<String, Object>, K> dk) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk);
    }

    /** 11-arity variant of {@link #combine(Decoder, Decoder)}. @return a combiner */
    public static <A, B, C, D, E, F, G, H, J, K, L> Combiner11<Map<String, Object>, A, B, C, D, E, F, G, H, J, K, L> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc, Decoder<Map<String, Object>, D> dd,
            Decoder<Map<String, Object>, E> de, Decoder<Map<String, Object>, F> df,
            Decoder<Map<String, Object>, G> dg, Decoder<Map<String, Object>, H> dh,
            Decoder<Map<String, Object>, J> dj, Decoder<Map<String, Object>, K> dk,
            Decoder<Map<String, Object>, L> dl) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl);
    }

    /** 12-arity variant of {@link #combine(Decoder, Decoder)}. @return a combiner */
    public static <A, B, C, D, E, F, G, H, J, K, L, M> Combiner12<Map<String, Object>, A, B, C, D, E, F, G, H, J, K, L, M> combine(
            Decoder<Map<String, Object>, A> da, Decoder<Map<String, Object>, B> db,
            Decoder<Map<String, Object>, C> dc, Decoder<Map<String, Object>, D> dd,
            Decoder<Map<String, Object>, E> de, Decoder<Map<String, Object>, F> df,
            Decoder<Map<String, Object>, G> dg, Decoder<Map<String, Object>, H> dh,
            Decoder<Map<String, Object>, J> dj, Decoder<Map<String, Object>, K> dk,
            Decoder<Map<String, Object>, L> dl, Decoder<Map<String, Object>, M> dm) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm);
    }

    /** 13-arity variant of {@link #combine(Decoder, Decoder)}. @return a combiner */
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

    /** 14-arity variant of {@link #combine(Decoder, Decoder)}. @return a combiner */
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

    /** 15-arity variant of {@link #combine(Decoder, Decoder)}. @return a combiner */
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

    /** 16-arity variant of {@link #combine(Decoder, Decoder)}. @return a combiner */
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
