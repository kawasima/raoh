package net.unit8.raoh.json;

import net.unit8.raoh.Err;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Issue;
import net.unit8.raoh.Issues;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Presence;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.decode.Decoders;
import net.unit8.raoh.decode.InputFields;
import net.unit8.raoh.decode.combinator.CombinePart;
import net.unit8.raoh.decode.builtin.BoolDecoder;
import net.unit8.raoh.decode.builtin.DecimalDecoder;
import net.unit8.raoh.decode.builtin.DoubleDecoder;
import net.unit8.raoh.decode.builtin.FloatDecoder;
import net.unit8.raoh.decode.builtin.IntDecoder;
import net.unit8.raoh.decode.builtin.ListDecoder;
import net.unit8.raoh.decode.builtin.LongDecoder;
import net.unit8.raoh.decode.builtin.RecordDecoder;
import net.unit8.raoh.decode.builtin.StringDecoder;
import net.unit8.raoh.decode.combinator.*;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.exc.JsonNodeException;

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
 *
 * <p><strong>Temporals.</strong> There are intentionally no temporal primitives here
 * (no {@code date()} / {@code dateTime()} / {@code iso8601()} factory). A JSON temporal is
 * always a string, so decode it through {@link #string()} and one of its temporal conversions —
 * {@code string().date()}, {@code string().time()}, {@code string().dateTime()},
 * {@code string().offsetDateTime()}, or {@code string().iso8601()} for an {@link java.time.Instant}:
 *
 * <pre>{@code
 * field("bornOn",    string().date())            // LocalDate
 * field("createdAt", string().iso8601())         // Instant
 * }</pre>
 */
public final class JsonDecoders {

    private JsonDecoders() {}

    /**
     * How to enumerate the property names of a JSON object input.
     *
     * <p>Every field factory here hands this to the {@link CombinePart} it builds, so a combiner
     * assembled from them can be made strict. A {@code null} node, or one that is not an object,
     * has no fields.
     */
    public static final InputFields<JsonNode> JSON_FIELDS =
            in -> in != null && in.isObject() ? in.propertyNames() : List.of();

    // --- Primitive decoders ---

    /**
     * Creates a string decoder.
     *
     * @return a decoder that extracts a string value from a JSON node
     */
    public static StringDecoder<JsonNode> string() {
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
     * Creates a double decoder.
     *
     * <p>Accepts any JSON number; a value that does not fit a finite {@code double}
     * (e.g. a very large integer literal) fails with {@code type_mismatch}, mirroring how
     * {@link #int_()} rejects a number that does not fit its target type.
     *
     * @return a decoder that extracts a double value from a JSON node
     */
    public static DoubleDecoder<JsonNode> double_() {
        return new DoubleDecoder<>((in, path) -> {
            if (in == null || in.isNull() || in.isMissingNode()) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (!in.isNumber()) {
                return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected double",
                        Map.of("expected", "double", "actual", in.getNodeType().name().toLowerCase()));
            }
            try {
                double v = in.doubleValue();
                // A very large integer node makes Jackson 3's doubleValue() throw (caught below); a
                // large decimal node instead yields an infinity. Reject both as out-of-range rather
                // than returning Infinity or letting the unchecked exception escape decode().
                if (Double.isInfinite(v)) {
                    return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected double",
                            Map.of("expected", "double"));
                }
                return Result.ok(v);
            } catch (JsonNodeException e) {
                return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected double",
                        Map.of("expected", "double"));
            }
        });
    }

    /**
     * Creates a float decoder.
     *
     * <p>Like {@link #double_()}, but produces a primitive {@code float}; a value that does not
     * fit a finite {@code float} (e.g. a magnitude above the {@code float} range) fails with
     * {@code type_mismatch}.
     *
     * @return a decoder that extracts a float value from a JSON node
     */
    public static FloatDecoder<JsonNode> float_() {
        return new FloatDecoder<>((in, path) -> {
            if (in == null || in.isNull() || in.isMissingNode()) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (!in.isNumber()) {
                return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected float",
                        Map.of("expected", "float", "actual", in.getNodeType().name().toLowerCase()));
            }
            try {
                float v = in.floatValue();
                // Jackson 3's floatValue() throws for most out-of-range nodes (caught below), but
                // enforce the finite contract explicitly too — a node that narrows to Infinity
                // without throwing must still be rejected, matching double_().
                if (Float.isInfinite(v)) {
                    return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected float",
                            Map.of("expected", "float"));
                }
                return Result.ok(v);
            } catch (JsonNodeException e) {
                return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected float",
                        Map.of("expected", "float"));
            }
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
    public static <T> CombinePart<JsonNode, T> field(String name, Decoder<JsonNode, T> dec) {
        return CombinePart.named(name, (in, fieldPath) -> {
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
        }, JSON_FIELDS);
    }

    /**
     * Extracts an optional field. Returns {@link Optional#empty()} if absent.
     *
     * <p>The returned {@link CombinePart} keeps its field declaration through {@code map},
     * {@code refine}, {@code pipe} and the other component combinators, so {@code strict()} still
     * sees the name after composition. Convert it with {@code asDecoder()} where a plain
     * {@link Decoder} is required, giving up that declaration deliberately.
     *
     * @param <T>  the decoded field type
     * @param name the field name
     * @param dec  the decoder for the field value
     * @return a decoder that produces an {@link Optional}
     */
    public static <T> CombinePart<JsonNode, Optional<T>> optionalField(String name, Decoder<JsonNode, T> dec) {
        return CombinePart.named(name, (in, fieldPath) -> {
            if (in == null || !in.isObject()) {
                return Result.ok(Optional.empty());
            }
            var node = in.get(name);
            if (node == null || node.isMissingNode()) {
                return Result.ok(Optional.empty());
            }
            return dec.decode(node, fieldPath).map(Optional::of);
        }, JSON_FIELDS);
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
    // The null branch deliberately produces a Result whose value is null (Decoder<..., @Nullable T>).
    // NullAway cannot verify the type-parameter variance of the lambda's Result<@Nullable T> return,
    // so this single honest nullness-widening site is suppressed (mirrors ObjectDecoders.nullable).
    @SuppressWarnings("NullAway")
    public static <T> Decoder<JsonNode, @Nullable T> nullable(Decoder<JsonNode, T> dec) {
        return (in, path) -> {
            if (in == null || in.isNull()) {
                return Result.<@Nullable T>ok(null);
            }
            return dec.decode(in, path);
        };
    }

    /**
     * Extracts a field with tri-state presence semantics (absent / null / present).
     *
     * <p>The returned {@link CombinePart} keeps its field declaration through {@code map},
     * {@code refine}, {@code pipe} and the other component combinators, so {@code strict()} still
     * sees the name after composition. Convert it with {@code asDecoder()} where a plain
     * {@link Decoder} is required, giving up that declaration deliberately.
     *
     * @param <T>  the decoded field type
     * @param name the field name
     * @param dec  the decoder for the field value
     * @return a decoder that produces a {@link Presence} value
     */
    public static <T> CombinePart<JsonNode, Presence<T>> optionalNullableField(String name, Decoder<JsonNode, T> dec) {
        return CombinePart.named(name, (in, fieldPath) -> {
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
        }, JSON_FIELDS);
    }

    /**
     * Creates a field decoder that lands directly on a {@code @Nullable T}: a missing key or a
     * JSON {@code null} value both decode to {@code null}, and any other value is decoded with
     * {@code dec}.
     *
     * <p>This is the {@code @Nullable}-target sibling of {@link #optionalField} (which targets
     * {@link Optional Optional&lt;T&gt;}) and {@link #optionalNullableField} (which targets
     * {@link Presence Presence&lt;T&gt;}). Unlike {@code optionalNullableField}, it does <em>not</em>
     * preserve the absent/present-null distinction — it collapses both to {@code null}. Use it to
     * populate a plain {@code @Nullable} domain field or constructor argument without an intermediate
     * {@code Optional} or {@code Presence}.
     *
     * <p>The returned {@link CombinePart} keeps its field declaration through {@code map},
     * {@code refine}, {@code pipe} and the other component combinators, so {@code strict()} still
     * sees the name after composition. Convert it with {@code asDecoder()} where a plain
     * {@link Decoder} is required, giving up that declaration deliberately.
     *
     * @param <T>  the decoded value type
     * @param name the field name
     * @param dec  the decoder for the field value when present and non-null
     * @return a decoder that produces the decoded value, or {@code null} when the key is absent or its
     *         value is JSON {@code null}
     */
    // Returns a Decoder<..., @Nullable T>. NullAway cannot verify the type-parameter nullness of the
    // @Nullable T return, the same honest widening already suppressed in JsonDecoders.nullable.
    @SuppressWarnings("NullAway")
    public static <T> CombinePart<JsonNode, @Nullable T> nullableField(String name, Decoder<JsonNode, T> dec) {
        return CombinePart.named(name, (in, fieldPath) -> {
            // A null / non-object input is treated as absent (-> null), consistent with optionalField /
            // optionalNullableField (field alone would reject it as a required error).
            if (in == null || !in.isObject()) {
                return Result.<@Nullable T>ok(null);
            }
            var node = in.get(name);
            // Collapse both a missing key and a JSON null to null; nullable(dec) alone would not,
            // since it only folds NullNode and leaves a MissingNode to reach the inner decoder.
            if (node == null || node.isMissingNode() || node.isNull()) {
                return Result.<@Nullable T>ok(null);
            }
            return dec.decode(node, fieldPath);
        }, JSON_FIELDS);
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
        return Decoders.<JsonNode, E>enumOf(cls, string());
    }

    /**
     * Decodes a JSON string and asserts it equals the expected value.
     *
     * @param expected the expected string value
     * @return a literal decoder
     */
    public static Decoder<JsonNode, String> literal(String expected) {
        return Decoders.<JsonNode>literal(expected, string());
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
        return Decoders.<JsonNode, T>discriminate(fieldName, field(fieldName, string()).asDecoder(), variants);
    }

    /**
     * Creates a {@link Decoders.Variant} for use with {@link #discriminate(String, Decoders.Variant[])},
     * with the input type pinned to {@link JsonNode}.
     *
     * @param <S>     the value type this variant decodes to
     * @param tag     the discriminator value that selects this variant
     * @param decoder the decoder producing the variant value
     * @return a variant for use with {@link #discriminate(String, Decoders.Variant[])}
     */
    public static <S> Decoders.Variant<JsonNode, S> variant(String tag, Decoder<JsonNode, S> decoder) {
        return Decoders.variant(tag, decoder);
    }

    /**
     * Typed, cast-free variant of {@link #discriminate(String, Map)}: dispatches on the string value
     * of the discriminator field. See {@link Decoders#discriminate(String, Decoder, Decoders.Variant[])}.
     *
     * @param <T>       the decoded (supertype) type
     * @param fieldName the discriminator field name (e.g., {@code "type"})
     * @param variants  the variants, each pairing a tag with its decoder
     * @return a discriminating decoder
     * @throws IllegalArgumentException if two variants share the same tag
     */
    @SafeVarargs
    public static <T> Decoder<JsonNode, T> discriminate(
            String fieldName, Decoders.Variant<JsonNode, ? extends T>... variants) {
        return Decoders.discriminate(fieldName, field(fieldName, string()).asDecoder(), variants);
    }

    // --- strict ---

    /**
     * Wraps a decoder to reject unknown fields not in the given set.
     *
     * <p>The JSON-boundary convenience over
     * {@link Decoders#strict(Decoder, Set, InputFields) Decoders.strict}: unknown properties in a
     * JSON object are reported as {@code unknown_field} issues.
     *
     * @param <T>         the decoded type
     * @param dec         the underlying decoder
     * @param knownFields the set of allowed field names
     * @return a strict decoder
     */
    public static <T> Decoder<JsonNode, T> strict(Decoder<JsonNode, T> dec, Set<String> knownFields) {
        return Decoders.strict(dec, knownFields, JSON_FIELDS);
    }

    /**
     * Lifts a decoder that reads the same whole node into a {@code combine} component, for
     * splitting one flat object across several decoders.
     *
     * <p>The decoder is opaque, so the component declares no fields and the combiner cannot be made
     * strict. Use {@link #field} for components whose field names are known.
     *
     * @param <T> the decoded value type
     * @param dec the decoder to read the same node with
     * @return a combine component reading the whole input
     */
    public static <T> CombinePart<JsonNode, T> flat(Decoder<JsonNode, T> dec) {
        return CombinePart.flat(dec);
    }

    // --- Delegate combine to Decoders ---

    /**
     * Combines 2 JSON decoders into an applicative builder.
     *
     * @param <A> the first decoder's output type
     * @param <B> the second decoder's output type
     * @param da  the first decoder
     * @param db  the second decoder
     * @return a combiner that can be applied with a function
     * @see Decoders#combine
     */
    public static <A, B> Combiner2<JsonNode, A, B> combine(
            CombinePart<JsonNode, A> da, CombinePart<JsonNode, B> db) {
        return Decoders.combine(da, db);
    }

    /**
     * Combines 3 JSON decoders into an applicative builder.
     *
     * @param <A> the first decoder's output type
     * @param <B> the second decoder's output type
     * @param <C> the third decoder's output type
     * @param da  the first decoder
     * @param db  the second decoder
     * @param dc  the third decoder
     * @return a combiner that can be applied with a function
     * @see Decoders#combine
     */
    public static <A, B, C> Combiner3<JsonNode, A, B, C> combine(
            CombinePart<JsonNode, A> da, CombinePart<JsonNode, B> db, CombinePart<JsonNode, C> dc) {
        return Decoders.combine(da, db, dc);
    }

    /**
     * Combines 4 JSON decoders into an applicative builder.
     *
     * @param <A> the first decoder's output type
     * @param <B> the second decoder's output type
     * @param <C> the third decoder's output type
     * @param <D> the fourth decoder's output type
     * @param da  the first decoder
     * @param db  the second decoder
     * @param dc  the third decoder
     * @param dd  the fourth decoder
     * @return a combiner that can be applied with a function
     * @see Decoders#combine
     */
    public static <A, B, C, D> Combiner4<JsonNode, A, B, C, D> combine(
            CombinePart<JsonNode, A> da, CombinePart<JsonNode, B> db, CombinePart<JsonNode, C> dc,
            CombinePart<JsonNode, D> dd) {
        return Decoders.combine(da, db, dc, dd);
    }

    /**
     * Combines 5 JSON decoders into an applicative builder.
     *
     * @param <A> the first decoder's output type
     * @param <B> the second decoder's output type
     * @param <C> the third decoder's output type
     * @param <D> the fourth decoder's output type
     * @param <E> the fifth decoder's output type
     * @param da  the first decoder
     * @param db  the second decoder
     * @param dc  the third decoder
     * @param dd  the fourth decoder
     * @param de  the fifth decoder
     * @return a combiner that can be applied with a function
     * @see Decoders#combine
     */
    public static <A, B, C, D, E> Combiner5<JsonNode, A, B, C, D, E> combine(
            CombinePart<JsonNode, A> da, CombinePart<JsonNode, B> db, CombinePart<JsonNode, C> dc,
            CombinePart<JsonNode, D> dd, CombinePart<JsonNode, E> de) {
        return Decoders.combine(da, db, dc, dd, de);
    }

    /**
     * Combines 6 JSON decoders into an applicative builder.
     *
     * @param <A> the first decoder's output type
     * @param <B> the second decoder's output type
     * @param <C> the third decoder's output type
     * @param <D> the fourth decoder's output type
     * @param <E> the fifth decoder's output type
     * @param <F> the sixth decoder's output type
     * @param da  the first decoder
     * @param db  the second decoder
     * @param dc  the third decoder
     * @param dd  the fourth decoder
     * @param de  the fifth decoder
     * @param df  the sixth decoder
     * @return a combiner that can be applied with a function
     * @see Decoders#combine
     */
    public static <A, B, C, D, E, F> Combiner6<JsonNode, A, B, C, D, E, F> combine(
            CombinePart<JsonNode, A> da, CombinePart<JsonNode, B> db, CombinePart<JsonNode, C> dc,
            CombinePart<JsonNode, D> dd, CombinePart<JsonNode, E> de, CombinePart<JsonNode, F> df) {
        return Decoders.combine(da, db, dc, dd, de, df);
    }

    /**
     * Combines 7 JSON decoders into an applicative builder.
     *
     * @param <A> the first decoder's output type
     * @param <B> the second decoder's output type
     * @param <C> the third decoder's output type
     * @param <D> the fourth decoder's output type
     * @param <E> the fifth decoder's output type
     * @param <F> the sixth decoder's output type
     * @param <G> the seventh decoder's output type
     * @param da  the first decoder
     * @param db  the second decoder
     * @param dc  the third decoder
     * @param dd  the fourth decoder
     * @param de  the fifth decoder
     * @param df  the sixth decoder
     * @param dg  the seventh decoder
     * @return a combiner that can be applied with a function
     * @see Decoders#combine
     */
    public static <A, B, C, D, E, F, G> Combiner7<JsonNode, A, B, C, D, E, F, G> combine(
            CombinePart<JsonNode, A> da, CombinePart<JsonNode, B> db, CombinePart<JsonNode, C> dc,
            CombinePart<JsonNode, D> dd, CombinePart<JsonNode, E> de, CombinePart<JsonNode, F> df,
            CombinePart<JsonNode, G> dg) {
        return Decoders.combine(da, db, dc, dd, de, df, dg);
    }

    /**
     * Combines 8 JSON decoders into an applicative builder.
     *
     * @param <A> the first decoder's output type
     * @param <B> the second decoder's output type
     * @param <C> the third decoder's output type
     * @param <D> the fourth decoder's output type
     * @param <E> the fifth decoder's output type
     * @param <F> the sixth decoder's output type
     * @param <G> the seventh decoder's output type
     * @param <H> the eighth decoder's output type
     * @param da  the first decoder
     * @param db  the second decoder
     * @param dc  the third decoder
     * @param dd  the fourth decoder
     * @param de  the fifth decoder
     * @param df  the sixth decoder
     * @param dg  the seventh decoder
     * @param dh  the eighth decoder
     * @return a combiner that can be applied with a function
     * @see Decoders#combine
     */
    public static <A, B, C, D, E, F, G, H> Combiner8<JsonNode, A, B, C, D, E, F, G, H> combine(
            CombinePart<JsonNode, A> da, CombinePart<JsonNode, B> db, CombinePart<JsonNode, C> dc,
            CombinePart<JsonNode, D> dd, CombinePart<JsonNode, E> de, CombinePart<JsonNode, F> df,
            CombinePart<JsonNode, G> dg, CombinePart<JsonNode, H> dh) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh);
    }

    /**
     * Combines 9 JSON decoders into an applicative builder.
     *
     * @param <A> the first decoder's output type
     * @param <B> the second decoder's output type
     * @param <C> the third decoder's output type
     * @param <D> the fourth decoder's output type
     * @param <E> the fifth decoder's output type
     * @param <F> the sixth decoder's output type
     * @param <G> the seventh decoder's output type
     * @param <H> the eighth decoder's output type
     * @param <J> the ninth decoder's output type
     * @param da  the first decoder
     * @param db  the second decoder
     * @param dc  the third decoder
     * @param dd  the fourth decoder
     * @param de  the fifth decoder
     * @param df  the sixth decoder
     * @param dg  the seventh decoder
     * @param dh  the eighth decoder
     * @param dj  the ninth decoder
     * @return a combiner that can be applied with a function
     * @see Decoders#combine
     */
    public static <A, B, C, D, E, F, G, H, J> Combiner9<JsonNode, A, B, C, D, E, F, G, H, J> combine(
            CombinePart<JsonNode, A> da, CombinePart<JsonNode, B> db, CombinePart<JsonNode, C> dc,
            CombinePart<JsonNode, D> dd, CombinePart<JsonNode, E> de, CombinePart<JsonNode, F> df,
            CombinePart<JsonNode, G> dg, CombinePart<JsonNode, H> dh, CombinePart<JsonNode, J> dj) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj);
    }

    /**
     * Combines 10 JSON decoders into an applicative builder.
     *
     * @param <A> the first decoder's output type
     * @param <B> the second decoder's output type
     * @param <C> the third decoder's output type
     * @param <D> the fourth decoder's output type
     * @param <E> the fifth decoder's output type
     * @param <F> the sixth decoder's output type
     * @param <G> the seventh decoder's output type
     * @param <H> the eighth decoder's output type
     * @param <J> the ninth decoder's output type
     * @param <K> the tenth decoder's output type
     * @param da  the first decoder
     * @param db  the second decoder
     * @param dc  the third decoder
     * @param dd  the fourth decoder
     * @param de  the fifth decoder
     * @param df  the sixth decoder
     * @param dg  the seventh decoder
     * @param dh  the eighth decoder
     * @param dj  the ninth decoder
     * @param dk  the tenth decoder
     * @return a combiner that can be applied with a function
     * @see Decoders#combine
     */
    public static <A, B, C, D, E, F, G, H, J, K> Combiner10<JsonNode, A, B, C, D, E, F, G, H, J, K> combine(
            CombinePart<JsonNode, A> da, CombinePart<JsonNode, B> db, CombinePart<JsonNode, C> dc,
            CombinePart<JsonNode, D> dd, CombinePart<JsonNode, E> de, CombinePart<JsonNode, F> df,
            CombinePart<JsonNode, G> dg, CombinePart<JsonNode, H> dh, CombinePart<JsonNode, J> dj,
            CombinePart<JsonNode, K> dk) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk);
    }

    /**
     * Combines 11 JSON decoders into an applicative builder.
     *
     * @param <A> the first decoder's output type
     * @param <B> the second decoder's output type
     * @param <C> the third decoder's output type
     * @param <D> the fourth decoder's output type
     * @param <E> the fifth decoder's output type
     * @param <F> the sixth decoder's output type
     * @param <G> the seventh decoder's output type
     * @param <H> the eighth decoder's output type
     * @param <J> the ninth decoder's output type
     * @param <K> the tenth decoder's output type
     * @param <L> the eleventh decoder's output type
     * @param da  the first decoder
     * @param db  the second decoder
     * @param dc  the third decoder
     * @param dd  the fourth decoder
     * @param de  the fifth decoder
     * @param df  the sixth decoder
     * @param dg  the seventh decoder
     * @param dh  the eighth decoder
     * @param dj  the ninth decoder
     * @param dk  the tenth decoder
     * @param dl  the eleventh decoder
     * @return a combiner that can be applied with a function
     * @see Decoders#combine
     */
    public static <A, B, C, D, E, F, G, H, J, K, L> Combiner11<JsonNode, A, B, C, D, E, F, G, H, J, K, L> combine(
            CombinePart<JsonNode, A> da, CombinePart<JsonNode, B> db, CombinePart<JsonNode, C> dc,
            CombinePart<JsonNode, D> dd, CombinePart<JsonNode, E> de, CombinePart<JsonNode, F> df,
            CombinePart<JsonNode, G> dg, CombinePart<JsonNode, H> dh, CombinePart<JsonNode, J> dj,
            CombinePart<JsonNode, K> dk, CombinePart<JsonNode, L> dl) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl);
    }

    /**
     * Combines 12 JSON decoders into an applicative builder.
     *
     * @param <A> the first decoder's output type
     * @param <B> the second decoder's output type
     * @param <C> the third decoder's output type
     * @param <D> the fourth decoder's output type
     * @param <E> the fifth decoder's output type
     * @param <F> the sixth decoder's output type
     * @param <G> the seventh decoder's output type
     * @param <H> the eighth decoder's output type
     * @param <J> the ninth decoder's output type
     * @param <K> the tenth decoder's output type
     * @param <L> the eleventh decoder's output type
     * @param <M> the twelfth decoder's output type
     * @param da  the first decoder
     * @param db  the second decoder
     * @param dc  the third decoder
     * @param dd  the fourth decoder
     * @param de  the fifth decoder
     * @param df  the sixth decoder
     * @param dg  the seventh decoder
     * @param dh  the eighth decoder
     * @param dj  the ninth decoder
     * @param dk  the tenth decoder
     * @param dl  the eleventh decoder
     * @param dm  the twelfth decoder
     * @return a combiner that can be applied with a function
     * @see Decoders#combine
     */
    public static <A, B, C, D, E, F, G, H, J, K, L, M> Combiner12<JsonNode, A, B, C, D, E, F, G, H, J, K, L, M> combine(
            CombinePart<JsonNode, A> da, CombinePart<JsonNode, B> db, CombinePart<JsonNode, C> dc,
            CombinePart<JsonNode, D> dd, CombinePart<JsonNode, E> de, CombinePart<JsonNode, F> df,
            CombinePart<JsonNode, G> dg, CombinePart<JsonNode, H> dh, CombinePart<JsonNode, J> dj,
            CombinePart<JsonNode, K> dk, CombinePart<JsonNode, L> dl, CombinePart<JsonNode, M> dm) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm);
    }

    /**
     * Combines 13 JSON decoders into an applicative builder.
     *
     * @param <A> the first decoder's output type
     * @param <B> the second decoder's output type
     * @param <C> the third decoder's output type
     * @param <D> the fourth decoder's output type
     * @param <E> the fifth decoder's output type
     * @param <F> the sixth decoder's output type
     * @param <G> the seventh decoder's output type
     * @param <H> the eighth decoder's output type
     * @param <J> the ninth decoder's output type
     * @param <K> the tenth decoder's output type
     * @param <L> the eleventh decoder's output type
     * @param <M> the twelfth decoder's output type
     * @param <N> the thirteenth decoder's output type
     * @param da  the first decoder
     * @param db  the second decoder
     * @param dc  the third decoder
     * @param dd  the fourth decoder
     * @param de  the fifth decoder
     * @param df  the sixth decoder
     * @param dg  the seventh decoder
     * @param dh  the eighth decoder
     * @param dj  the ninth decoder
     * @param dk  the tenth decoder
     * @param dl  the eleventh decoder
     * @param dm  the twelfth decoder
     * @param dn  the thirteenth decoder
     * @return a combiner that can be applied with a function
     * @see Decoders#combine
     */
    public static <A, B, C, D, E, F, G, H, J, K, L, M, N> Combiner13<JsonNode, A, B, C, D, E, F, G, H, J, K, L, M, N> combine(
            CombinePart<JsonNode, A> da, CombinePart<JsonNode, B> db, CombinePart<JsonNode, C> dc,
            CombinePart<JsonNode, D> dd, CombinePart<JsonNode, E> de, CombinePart<JsonNode, F> df,
            CombinePart<JsonNode, G> dg, CombinePart<JsonNode, H> dh, CombinePart<JsonNode, J> dj,
            CombinePart<JsonNode, K> dk, CombinePart<JsonNode, L> dl, CombinePart<JsonNode, M> dm,
            CombinePart<JsonNode, N> dn) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn);
    }

    /**
     * Combines 14 JSON decoders into an applicative builder.
     *
     * @param <A> the first decoder's output type
     * @param <B> the second decoder's output type
     * @param <C> the third decoder's output type
     * @param <D> the fourth decoder's output type
     * @param <E> the fifth decoder's output type
     * @param <F> the sixth decoder's output type
     * @param <G> the seventh decoder's output type
     * @param <H> the eighth decoder's output type
     * @param <J> the ninth decoder's output type
     * @param <K> the tenth decoder's output type
     * @param <L> the eleventh decoder's output type
     * @param <M> the twelfth decoder's output type
     * @param <N> the thirteenth decoder's output type
     * @param <O> the fourteenth decoder's output type
     * @param da  the first decoder
     * @param db  the second decoder
     * @param dc  the third decoder
     * @param dd  the fourth decoder
     * @param de  the fifth decoder
     * @param df  the sixth decoder
     * @param dg  the seventh decoder
     * @param dh  the eighth decoder
     * @param dj  the ninth decoder
     * @param dk  the tenth decoder
     * @param dl  the eleventh decoder
     * @param dm  the twelfth decoder
     * @param dn  the thirteenth decoder
     * @param do_ the fourteenth decoder
     * @return a combiner that can be applied with a function
     * @see Decoders#combine
     */
    public static <A, B, C, D, E, F, G, H, J, K, L, M, N, O> Combiner14<JsonNode, A, B, C, D, E, F, G, H, J, K, L, M, N, O> combine(
            CombinePart<JsonNode, A> da, CombinePart<JsonNode, B> db, CombinePart<JsonNode, C> dc,
            CombinePart<JsonNode, D> dd, CombinePart<JsonNode, E> de, CombinePart<JsonNode, F> df,
            CombinePart<JsonNode, G> dg, CombinePart<JsonNode, H> dh, CombinePart<JsonNode, J> dj,
            CombinePart<JsonNode, K> dk, CombinePart<JsonNode, L> dl, CombinePart<JsonNode, M> dm,
            CombinePart<JsonNode, N> dn, CombinePart<JsonNode, O> do_) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn, do_);
    }

    /**
     * Combines 15 JSON decoders into an applicative builder.
     *
     * @param <A> the first decoder's output type
     * @param <B> the second decoder's output type
     * @param <C> the third decoder's output type
     * @param <D> the fourth decoder's output type
     * @param <E> the fifth decoder's output type
     * @param <F> the sixth decoder's output type
     * @param <G> the seventh decoder's output type
     * @param <H> the eighth decoder's output type
     * @param <J> the ninth decoder's output type
     * @param <K> the tenth decoder's output type
     * @param <L> the eleventh decoder's output type
     * @param <M> the twelfth decoder's output type
     * @param <N> the thirteenth decoder's output type
     * @param <O> the fourteenth decoder's output type
     * @param <P> the fifteenth decoder's output type
     * @param da  the first decoder
     * @param db  the second decoder
     * @param dc  the third decoder
     * @param dd  the fourth decoder
     * @param de  the fifth decoder
     * @param df  the sixth decoder
     * @param dg  the seventh decoder
     * @param dh  the eighth decoder
     * @param dj  the ninth decoder
     * @param dk  the tenth decoder
     * @param dl  the eleventh decoder
     * @param dm  the twelfth decoder
     * @param dn  the thirteenth decoder
     * @param do_ the fourteenth decoder
     * @param dp  the fifteenth decoder
     * @return a combiner that can be applied with a function
     * @see Decoders#combine
     */
    public static <A, B, C, D, E, F, G, H, J, K, L, M, N, O, P> Combiner15<JsonNode, A, B, C, D, E, F, G, H, J, K, L, M, N, O, P> combine(
            CombinePart<JsonNode, A> da, CombinePart<JsonNode, B> db, CombinePart<JsonNode, C> dc,
            CombinePart<JsonNode, D> dd, CombinePart<JsonNode, E> de, CombinePart<JsonNode, F> df,
            CombinePart<JsonNode, G> dg, CombinePart<JsonNode, H> dh, CombinePart<JsonNode, J> dj,
            CombinePart<JsonNode, K> dk, CombinePart<JsonNode, L> dl, CombinePart<JsonNode, M> dm,
            CombinePart<JsonNode, N> dn, CombinePart<JsonNode, O> do_, CombinePart<JsonNode, P> dp) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn, do_, dp);
    }

    /**
     * Combines 16 JSON decoders into an applicative builder.
     *
     * @param <A> the first decoder's output type
     * @param <B> the second decoder's output type
     * @param <C> the third decoder's output type
     * @param <D> the fourth decoder's output type
     * @param <E> the fifth decoder's output type
     * @param <F> the sixth decoder's output type
     * @param <G> the seventh decoder's output type
     * @param <H> the eighth decoder's output type
     * @param <J> the ninth decoder's output type
     * @param <K> the tenth decoder's output type
     * @param <L> the eleventh decoder's output type
     * @param <M> the twelfth decoder's output type
     * @param <N> the thirteenth decoder's output type
     * @param <O> the fourteenth decoder's output type
     * @param <P> the fifteenth decoder's output type
     * @param <Q> the sixteenth decoder's output type
     * @param da  the first decoder
     * @param db  the second decoder
     * @param dc  the third decoder
     * @param dd  the fourth decoder
     * @param de  the fifth decoder
     * @param df  the sixth decoder
     * @param dg  the seventh decoder
     * @param dh  the eighth decoder
     * @param dj  the ninth decoder
     * @param dk  the tenth decoder
     * @param dl  the eleventh decoder
     * @param dm  the twelfth decoder
     * @param dn  the thirteenth decoder
     * @param do_ the fourteenth decoder
     * @param dp  the fifteenth decoder
     * @param dq  the sixteenth decoder
     * @return a combiner that can be applied with a function
     * @see Decoders#combine
     */
    public static <A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, Q> Combiner16<JsonNode, A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, Q> combine(
            CombinePart<JsonNode, A> da, CombinePart<JsonNode, B> db, CombinePart<JsonNode, C> dc,
            CombinePart<JsonNode, D> dd, CombinePart<JsonNode, E> de, CombinePart<JsonNode, F> df,
            CombinePart<JsonNode, G> dg, CombinePart<JsonNode, H> dh, CombinePart<JsonNode, J> dj,
            CombinePart<JsonNode, K> dk, CombinePart<JsonNode, L> dl, CombinePart<JsonNode, M> dm,
            CombinePart<JsonNode, N> dn, CombinePart<JsonNode, O> do_, CombinePart<JsonNode, P> dp,
            CombinePart<JsonNode, Q> dq) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn, do_, dp, dq);
    }

    /**
     * Returns a {@link CombinerList} for combining more than 16 decoders.
     *
     * @param parts the components to combine
     * @return a combiner on which {@code .map(f)} or {@code .flatMap(f)} can be called
     * @see Decoders#combine(List)
     */
    public static CombinerList<JsonNode> combine(List<CombinePart<JsonNode, ?>> parts) {
        return Decoders.combine(parts);
    }
}
