package net.unit8.raoh.map;

import net.unit8.raoh.*;
import net.unit8.raoh.combinator.*;
import net.unit8.raoh.builtin.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
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

    /**
     * Creates a string decoder that trims whitespace by default.
     *
     * <p>Returns {@code required} if the value is {@code null}, and {@code type_mismatch}
     * if the value is not a {@link String}.
     *
     * @return a string decoder for {@code Object} input
     */
    public static StringDecoder<Object> string() {
        Decoder<Object, String> base = allowBlankBase();
        return new StringDecoder<>(base, base);
    }

    /**
     * Creates a string decoder that preserves blank values (no trim or nonBlank validation).
     *
     * @return a string decoder for {@code Object} input that allows blank strings
     */
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

    /**
     * Creates an integer decoder.
     *
     * <p>Accepts {@link Integer} values directly, and narrows other {@link Number} subtypes
     * via {@link Number#intValue()}. Returns {@code required} if the value is {@code null},
     * and {@code type_mismatch} if the value is not a number.
     *
     * @return an integer decoder for {@code Object} input
     */
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

    /**
     * Creates a long decoder.
     *
     * <p>Accepts {@link Long} values directly, and narrows other {@link Number} subtypes
     * via {@link Number#longValue()}. Returns {@code required} if the value is {@code null},
     * and {@code type_mismatch} if the value is not a number.
     *
     * @return a long decoder for {@code Object} input
     */
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

    /**
     * Creates a boolean decoder.
     *
     * <p>Returns {@code required} if the value is {@code null},
     * and {@code type_mismatch} if the value is not a {@link Boolean}.
     *
     * @return a boolean decoder for {@code Object} input
     */
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

    /**
     * Creates a {@link java.math.BigDecimal} decoder.
     *
     * <p>Accepts {@link java.math.BigDecimal} values directly, and converts other {@link Number}
     * subtypes via their string representation. Returns {@code required} if the value is
     * {@code null}, and {@code type_mismatch} if the value is not a number.
     *
     * @return a decimal decoder for {@code Object} input
     */
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

    // --- Temporal decoders ---

    /**
     * Creates a {@link LocalDate} decoder.
     *
     * <p>Returns {@code required} if the value is {@code null}, and {@code type_mismatch}
     * if the value is not a {@link LocalDate}.
     *
     * @return a temporal decoder for {@code Object} input producing {@link LocalDate}
     */
    public static TemporalDecoder<Object, LocalDate> date() {
        return temporalOf(LocalDate.class, "date");
    }

    /**
     * Creates a {@link LocalTime} decoder.
     *
     * <p>Returns {@code required} if the value is {@code null}, and {@code type_mismatch}
     * if the value is not a {@link LocalTime}.
     *
     * @return a temporal decoder for {@code Object} input producing {@link LocalTime}
     */
    public static TemporalDecoder<Object, LocalTime> time() {
        return temporalOf(LocalTime.class, "time");
    }

    /**
     * Creates a {@link LocalDateTime} decoder.
     *
     * <p>Returns {@code required} if the value is {@code null}, and {@code type_mismatch}
     * if the value is not a {@link LocalDateTime}.
     *
     * @return a temporal decoder for {@code Object} input producing {@link LocalDateTime}
     */
    public static TemporalDecoder<Object, LocalDateTime> dateTime() {
        return temporalOf(LocalDateTime.class, "date-time");
    }

    /**
     * Creates an {@link Instant} decoder.
     *
     * <p>Returns {@code required} if the value is {@code null}, and {@code type_mismatch}
     * if the value is not an {@link Instant}.
     *
     * @return a temporal decoder for {@code Object} input producing {@link Instant}
     */
    public static TemporalDecoder<Object, Instant> iso8601() {
        return temporalOf(Instant.class, "instant");
    }

    /**
     * Creates an {@link OffsetDateTime} decoder.
     *
     * <p>Returns {@code required} if the value is {@code null}, and {@code type_mismatch}
     * if the value is not an {@link OffsetDateTime}.
     *
     * @return a temporal decoder for {@code Object} input producing {@link OffsetDateTime}
     */
    public static TemporalDecoder<Object, OffsetDateTime> offsetDateTime() {
        return temporalOf(OffsetDateTime.class, "offset-date-time");
    }

    private static <T extends Comparable<? super T>> TemporalDecoder<Object, T> temporalOf(
            Class<T> type, String typeName) {
        return new TemporalDecoder<>((in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (type.isInstance(in)) {
                return Result.ok(type.cast(in));
            }
            return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected " + typeName,
                    Map.of("expected", typeName, "actual", in.getClass().getSimpleName()));
        });
    }

    // --- field / optionalField / nullable ---

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

    /**
     * Wraps a decoder to accept {@code null} input, returning {@code null} as the decoded value
     * instead of a {@code required} error.
     *
     * @param <T> the decoded value type
     * @param dec the inner decoder to apply when the value is non-null
     * @return a decoder that passes through {@code null} without error
     */
    public static <T> Decoder<Object, T> nullable(Decoder<Object, T> dec) {
        return (in, path) -> {
            if (in == null) {
                return Result.ok(null);
            }
            return dec.decode(in, path);
        };
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

    // --- list / map ---

    /**
     * Creates a list decoder that decodes each element of a {@link List} with the given decoder,
     * accumulating all errors rather than short-circuiting on the first failure.
     *
     * <p>Returns {@code required} if the value is {@code null},
     * and {@code type_mismatch} if the value is not a {@link List}.
     *
     * @param <T>        the decoded element type
     * @param elementDec the decoder for each list element
     * @return a list decoder for {@code Object} input
     */
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
            if (!issues.isEmpty()) {
                return Result.err(issues);
            }
            return Result.ok(List.copyOf(results));
        });
    }

    /**
     * Creates a map decoder that decodes each value of a {@code Map<String, ?>} with the given
     * decoder, accumulating all errors. Keys are preserved as-is.
     *
     * <p>Returns {@code required} if the value is {@code null},
     * and {@code type_mismatch} if the value is not a {@link Map}.
     *
     * @param <V>    the decoded value type
     * @param valDec the decoder for each map value
     * @return a record decoder for {@code Object} input
     */
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
            if (!issues.isEmpty()) {
                return Result.err(issues);
            }
            return Result.ok(Map.copyOf(results));
        });
    }

    // --- enumOf / literal ---

    /**
     * Creates an enum decoder that parses a string value into the given enum type.
     *
     * @param <E> the enum type
     * @param cls the enum class
     * @return a decoder that produces enum constants from string input
     */
    public static <E extends Enum<E>> Decoder<Object, E> enumOf(Class<E> cls) {
        return Decoders.enumOf(cls, allowBlankString());
    }

    /**
     * Creates a decoder that accepts only the given literal string value.
     *
     * @param expected the expected string value
     * @return a decoder that succeeds only when the input matches {@code expected}
     */
    public static Decoder<Object, String> literal(String expected) {
        return Decoders.literal(expected, allowBlankString());
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
