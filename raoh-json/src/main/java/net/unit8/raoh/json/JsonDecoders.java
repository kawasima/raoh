package net.unit8.raoh.json;

import net.unit8.raoh.*;
import net.unit8.raoh.combinator.*;
import net.unit8.raoh.builtin.*;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Factory methods for creating decoders that operate on Jackson {@link JsonNode} input.
 *
 * <p>This is the primary entry point for building JSON decoders.
 * Use {@code import static net.unit8.raoh.json.JsonDecoders.*;} to access all factories.
 *
 * <pre>{@code
 * var userDecoder = combine(
 *     field("name", string().minLength(1)),
 *     field("age", int_().range(0, 150))
 * ).map(User::new);
 * }</pre>
 */
public final class JsonDecoders {

    private JsonDecoders() {}

    // --- Primitive decoders ---

    /**
     * Creates a string decoder.
     *
     * @return a decoder that extracts a string value from a JSON node
     */
    public static StringDecoder<JsonNode> string() {
        Decoder<JsonNode, String> base = allowBlankBase();
        return new StringDecoder<>(base, base);
    }

    /**
     * Creates a string decoder that preserves blank values (no trim or nonBlank validation).
     *
     * @return a string decoder that allows blank strings
     */
    public static StringDecoder<JsonNode> allowBlankString() {
        return new StringDecoder<>(allowBlankBase());
    }

    private static Decoder<JsonNode, String> allowBlankBase() {
        return (in, path) -> {
            if (in == null || in.isNull() || in.isMissingNode()) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (!in.isString()) {
                return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected string",
                        Map.of("expected", "string", "actual", in.getNodeType().name().toLowerCase()));
            }
            return Result.ok(in.asString());
        };
    }

    /**
     * Creates an integer decoder.
     *
     * @return a decoder that extracts an integer value from a JSON node
     */
    public static IntDecoder<JsonNode> int_() {
        return new IntDecoder<>((in, path) -> {
            if (in == null || in.isNull() || in.isMissingNode()) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (!in.isInt() && !in.isLong() && !in.isShort()) {
                if (in.isNumber()) {
                    return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected integer",
                            Map.of("expected", "integer"));
                }
                return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected integer",
                        Map.of("expected", "integer", "actual", in.getNodeType().name().toLowerCase()));
            }
            return Result.ok(in.intValue());
        });
    }

    /**
     * Creates a long integer decoder.
     *
     * @return a decoder that extracts a long value from a JSON node
     */
    public static LongDecoder<JsonNode> long_() {
        return new LongDecoder<>((in, path) -> {
            if (in == null || in.isNull() || in.isMissingNode()) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (!in.isInt() && !in.isLong() && !in.isShort()) {
                return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected long",
                        Map.of("expected", "long", "actual", in.getNodeType().name().toLowerCase()));
            }
            return Result.ok(in.longValue());
        });
    }

    /**
     * Creates a boolean decoder.
     *
     * @return a decoder that extracts a boolean value from a JSON node
     */
    public static BoolDecoder<JsonNode> bool() {
        return new BoolDecoder<>((in, path) -> {
            if (in == null || in.isNull() || in.isMissingNode()) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (!in.isBoolean()) {
                return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected boolean",
                        Map.of("expected", "boolean", "actual", in.getNodeType().name().toLowerCase()));
            }
            return Result.ok(in.booleanValue());
        });
    }

    /**
     * Creates a decimal (BigDecimal) decoder.
     *
     * @return a decoder that extracts a decimal value from a JSON node
     */
    public static DecimalDecoder<JsonNode> decimal() {
        return new DecimalDecoder<>((in, path) -> {
            if (in == null || in.isNull() || in.isMissingNode()) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (!in.isNumber()) {
                return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected number",
                        Map.of("expected", "number", "actual", in.getNodeType().name().toLowerCase()));
            }
            return Result.ok(in.decimalValue());
        });
    }

    // --- field / optionalField / nullable ---

    /**
     * Extracts a required field from a JSON object and decodes it.
     *
     * @param <T>  the decoded field type
     * @param name the field name
     * @param dec  the decoder for the field value
     * @return a decoder for the named field
     */
    public static <T> FieldDecoder<JsonNode, T> field(String name, Decoder<JsonNode, T> dec) {
        return new FieldDecoder<>() {
            @Override
            public String fieldName() { return name; }

            @Override
            public Result<T> decode(JsonNode in, Path path) {
                var fieldPath = path.append(name);
                if (in == null || !in.isObject()) {
                    return Result.fail(fieldPath, ErrorCodes.TYPE_MISMATCH, "expected object",
                            Map.of("expected", "object", "actual",
                                    in == null ? "null" : in.getNodeType().name().toLowerCase()));
                }
                var node = in.get(name);
                if (node == null) {
                    node = tools.jackson.databind.node.MissingNode.getInstance();
                }
                return dec.decode(node, fieldPath);
            }
        };
    }

    /**
     * Extracts an optional field. Returns {@link Optional#empty()} if absent.
     *
     * @param <T>  the decoded field type
     * @param name the field name
     * @param dec  the decoder for the field value
     * @return a decoder that produces an {@link Optional}
     */
    public static <T> Decoder<JsonNode, Optional<T>> optionalField(String name, Decoder<JsonNode, T> dec) {
        return (in, path) -> {
            var fieldPath = path.append(name);
            if (in == null || !in.isObject()) {
                return Result.ok(Optional.empty());
            }
            var node = in.get(name);
            if (node == null || node.isMissingNode()) {
                return Result.ok(Optional.empty());
            }
            return dec.decode(node, fieldPath).map(Optional::of);
        };
    }

    /**
     * Wraps a decoder to accept {@code null} JSON values, returning {@code null} instead of an error.
     *
     * <p><strong>Note:</strong> The returned decoder produces {@code Ok(null)} when the input is
     * absent or JSON null. Callers must handle the {@code null} value explicitly; the type system
     * cannot enforce non-nullness here. Prefer {@link #optionalField} for optional semantics.
     *
     * @param <T> the decoded type
     * @param dec the underlying decoder
     * @return a nullable decoder whose {@code Ok} value may be {@code null}
     */
    public static <T> Decoder<JsonNode, @Nullable T> nullable(Decoder<JsonNode, T> dec) {
        return (in, path) -> {
            if (in == null || in.isNull()) {
                return Result.ok(null);
            }
            return dec.decode(in, path);
        };
    }

    /**
     * Extracts a field with tri-state presence semantics (absent / null / present).
     *
     * @param <T>  the decoded field type
     * @param name the field name
     * @param dec  the decoder for the field value
     * @return a decoder that produces a {@link Presence} value
     */
    public static <T> Decoder<JsonNode, Presence<T>> optionalNullableField(String name, Decoder<JsonNode, T> dec) {
        return (in, path) -> {
            var fieldPath = path.append(name);
            if (in == null || !in.isObject()) {
                return Result.ok(new Presence.Absent<>());
            }
            var node = in.get(name);
            if (node == null || node.isMissingNode()) {
                return Result.ok(new Presence.Absent<>());
            }
            if (node.isNull()) {
                return Result.ok(new Presence.PresentNull<>());
            }
            return dec.decode(node, fieldPath).map(v -> (Presence<T>) new Presence.Present<>(v));
        };
    }

    // --- list / map ---

    /**
     * Creates a decoder for JSON arrays, decoding each element with the given decoder.
     * Element errors are accumulated with path indices (e.g., {@code /items/0}).
     *
     * @param <T>        the element type
     * @param elementDec the decoder for each element
     * @return a list decoder
     */
    public static <T> ListDecoder<JsonNode, T> list(Decoder<JsonNode, T> elementDec) {
        return new ListDecoder<>((in, path) -> {
            if (in == null || in.isNull() || in.isMissingNode()) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (!in.isArray()) {
                return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected array",
                        Map.of("expected", "array", "actual", in.getNodeType().name().toLowerCase()));
            }
            var issues = Issues.EMPTY;
            var results = new ArrayList<T>();
            for (int i = 0; i < in.size(); i++) {
                var elemPath = path.append(String.valueOf(i));
                var r = elementDec.decode(in.get(i), elemPath);
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
     * Creates a decoder for JSON objects as string-keyed maps.
     *
     * @param <V>    the value type
     * @param valDec the decoder for each value
     * @return a record (map) decoder
     */
    public static <V> RecordDecoder<JsonNode, V> map(Decoder<JsonNode, V> valDec) {
        return new RecordDecoder<>((in, path) -> {
            if (in == null || in.isNull() || in.isMissingNode()) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (!in.isObject()) {
                return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected object",
                        Map.of("expected", "object", "actual", in.getNodeType().name().toLowerCase()));
            }
            var issues = Issues.EMPTY;
            var results = new LinkedHashMap<String, V>();
            for (var entry : in.properties()) {
                var keyPath = path.append(entry.getKey());
                var r = valDec.decode(entry.getValue(), keyPath);
                switch (r) {
                    case Ok<V> ok -> results.put(entry.getKey(), ok.value());
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
     * Decodes a JSON string into an enum constant (case-insensitive).
     *
     * @param <E> the enum type
     * @param cls the enum class
     * @return an enum decoder
     */
    public static <E extends Enum<E>> Decoder<JsonNode, E> enumOf(Class<E> cls) {
        return Decoders.enumOf(cls, allowBlankString());
    }

    /**
     * Decodes a JSON string and asserts it equals the expected value.
     *
     * @param expected the expected string value
     * @return a literal decoder
     */
    public static Decoder<JsonNode, String> literal(String expected) {
        return Decoders.literal(expected, allowBlankString());
    }

    // --- discriminate ---

    /**
     * Creates a decoder that dispatches to different decoders based on a discriminator field.
     *
     * @param <T>       the decoded type
     * @param fieldName the discriminator field name (e.g., {@code "type"})
     * @param variants  a map from discriminator values to decoders
     * @return a discriminating decoder
     */
    public static <T> Decoder<JsonNode, T> discriminate(
            String fieldName,
            Map<String, Decoder<JsonNode, ? extends T>> variants) {
        return (in, path) -> {
            var tag = field(fieldName, allowBlankString()).decode(in, path);
            return switch (tag) {
                case Err<String> err -> err.coerce();
                case Ok<String> ok -> {
                    var dec = variants.get(ok.value());
                    if (dec == null) {
                        yield Result.fail(path.append(fieldName),
                                ErrorCodes.INVALID_FORMAT, "invalid value",
                                Map.of("allowed", variants.keySet()));
                    }
                    @SuppressWarnings("unchecked")
                    var result = (Result<T>) dec.decode(in, path);
                    yield result;
                }
            };
        };
    }

    // --- strict ---

    /**
     * Wraps a decoder to reject unknown fields not in the given set.
     *
     * <p>This overrides the core {@link Decoders#strict} to add JsonNode object support:
     * unknown properties in a JSON object are reported as {@code unknown_field} issues.
     *
     * @param <T>         the decoded type
     * @param dec         the underlying decoder
     * @param knownFields the set of allowed field names
     * @return a strict decoder
     */
    public static <T> Decoder<JsonNode, T> strict(Decoder<JsonNode, T> dec, Set<String> knownFields) {
        return (in, path) -> {
            var issues = Issues.EMPTY;
            if (in != null && in.isObject()) {
                for (var name : in.propertyNames()) {
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

    // --- Delegate combine to Decoders ---

    /**
     * Combines 2 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder)
     */
    public static <A, B> Combiner2<JsonNode, A, B> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db) {
        return Decoders.combine(da, db);
    }

    /**
     * Combines 3 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder)
     */
    public static <A, B, C> Combiner3<JsonNode, A, B, C> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc) {
        return Decoders.combine(da, db, dc);
    }

    /**
     * Combines 4 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D> Combiner4<JsonNode, A, B, C, D> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd) {
        return Decoders.combine(da, db, dc, dd);
    }

    /**
     * Combines 5 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E> Combiner5<JsonNode, A, B, C, D, E> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de) {
        return Decoders.combine(da, db, dc, dd, de);
    }

    /**
     * Combines 6 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E, F> Combiner6<JsonNode, A, B, C, D, E, F> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de, Decoder<JsonNode, F> df) {
        return Decoders.combine(da, db, dc, dd, de, df);
    }

    /**
     * Combines 7 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E, F, G> Combiner7<JsonNode, A, B, C, D, E, F, G> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de, Decoder<JsonNode, F> df,
            Decoder<JsonNode, G> dg) {
        return Decoders.combine(da, db, dc, dd, de, df, dg);
    }

    /**
     * Combines 8 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E, F, G, H> Combiner8<JsonNode, A, B, C, D, E, F, G, H> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de, Decoder<JsonNode, F> df,
            Decoder<JsonNode, G> dg, Decoder<JsonNode, H> dh) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh);
    }

    /**
     * Combines 9 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E, F, G, H, J> Combiner9<JsonNode, A, B, C, D, E, F, G, H, J> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de, Decoder<JsonNode, F> df,
            Decoder<JsonNode, G> dg, Decoder<JsonNode, H> dh, Decoder<JsonNode, J> dj) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj);
    }

    /**
     * Combines 10 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E, F, G, H, J, K> Combiner10<JsonNode, A, B, C, D, E, F, G, H, J, K> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de, Decoder<JsonNode, F> df,
            Decoder<JsonNode, G> dg, Decoder<JsonNode, H> dh, Decoder<JsonNode, J> dj,
            Decoder<JsonNode, K> dk) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk);
    }

    /**
     * Combines 11 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E, F, G, H, J, K, L> Combiner11<JsonNode, A, B, C, D, E, F, G, H, J, K, L> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de, Decoder<JsonNode, F> df,
            Decoder<JsonNode, G> dg, Decoder<JsonNode, H> dh, Decoder<JsonNode, J> dj,
            Decoder<JsonNode, K> dk, Decoder<JsonNode, L> dl) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl);
    }

    /**
     * Combines 12 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E, F, G, H, J, K, L, M> Combiner12<JsonNode, A, B, C, D, E, F, G, H, J, K, L, M> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de, Decoder<JsonNode, F> df,
            Decoder<JsonNode, G> dg, Decoder<JsonNode, H> dh, Decoder<JsonNode, J> dj,
            Decoder<JsonNode, K> dk, Decoder<JsonNode, L> dl, Decoder<JsonNode, M> dm) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm);
    }

    /**
     * Combines 13 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E, F, G, H, J, K, L, M, N> Combiner13<JsonNode, A, B, C, D, E, F, G, H, J, K, L, M, N> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de, Decoder<JsonNode, F> df,
            Decoder<JsonNode, G> dg, Decoder<JsonNode, H> dh, Decoder<JsonNode, J> dj,
            Decoder<JsonNode, K> dk, Decoder<JsonNode, L> dl, Decoder<JsonNode, M> dm,
            Decoder<JsonNode, N> dn) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn);
    }

    /**
     * Combines 14 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E, F, G, H, J, K, L, M, N, O> Combiner14<JsonNode, A, B, C, D, E, F, G, H, J, K, L, M, N, O> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de, Decoder<JsonNode, F> df,
            Decoder<JsonNode, G> dg, Decoder<JsonNode, H> dh, Decoder<JsonNode, J> dj,
            Decoder<JsonNode, K> dk, Decoder<JsonNode, L> dl, Decoder<JsonNode, M> dm,
            Decoder<JsonNode, N> dn, Decoder<JsonNode, O> do_) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn, do_);
    }

    /**
     * Combines 15 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E, F, G, H, J, K, L, M, N, O, P> Combiner15<JsonNode, A, B, C, D, E, F, G, H, J, K, L, M, N, O, P> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de, Decoder<JsonNode, F> df,
            Decoder<JsonNode, G> dg, Decoder<JsonNode, H> dh, Decoder<JsonNode, J> dj,
            Decoder<JsonNode, K> dk, Decoder<JsonNode, L> dl, Decoder<JsonNode, M> dm,
            Decoder<JsonNode, N> dn, Decoder<JsonNode, O> do_, Decoder<JsonNode, P> dp) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn, do_, dp);
    }

    /**
     * Combines 16 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, Q> Combiner16<JsonNode, A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, Q> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de, Decoder<JsonNode, F> df,
            Decoder<JsonNode, G> dg, Decoder<JsonNode, H> dh, Decoder<JsonNode, J> dj,
            Decoder<JsonNode, K> dk, Decoder<JsonNode, L> dl, Decoder<JsonNode, M> dm,
            Decoder<JsonNode, N> dn, Decoder<JsonNode, O> do_, Decoder<JsonNode, P> dp,
            Decoder<JsonNode, Q> dq) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn, do_, dp, dq);
    }

    /**
     * Returns a {@link CombinerList} for combining more than 16 decoders.
     *
     * @param decoders the decoders to combine; must not be empty
     * @return a combiner on which {@code .map(f)} or {@code .flatMap(f)} can be called
     * @see Decoders#combine(List)
     */
    public static CombinerList<JsonNode> combine(List<Decoder<JsonNode, ?>> decoders) {
        return Decoders.combine(decoders);
    }
}
