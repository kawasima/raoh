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
import net.unit8.raoh.decode.ObjectDecoders;
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
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.JsonNode;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
 * Result<User> user = userDecoder.decode(readTree(json));
 * }</pre>
 *
 * <p><strong>Reading the input.</strong> The decoders accept any {@link JsonNode}, but read the
 * input with {@link #readTree(String)} or one of its overloads: it keeps each number as written,
 * which a tree from Jackson's {@code ObjectMapper} does not, and refuses a member name written
 * twice. On a mapper's tree the numeric decoders see the value the mapper converted each number
 * to.
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

    // --- Reading JSON ---

    /**
     * Reads a JSON document into the tree the decoders here are specified on.
     *
     * <p>A tree from Jackson's own {@code ObjectMapper} has its numbers converted before any decoder
     * sees them: a number with a fraction or an exponent becomes a {@code double} unless the mapper
     * is configured otherwise, which loses the scale of {@code 0.0001}, digits past the
     * seventeenth, and the sign of {@code -0}. This method keeps the number as written. An integer
     * becomes an integer node; any other number becomes a decimal node holding the exact
     * {@link java.math.BigDecimal} it writes, scale included; and a zero written with a minus sign
     * ({@code -0}, {@code -0.0}, {@code -0e5}) keeps its sign, so {@link #double_()} and
     * {@link #float_()} read it as {@code -0.0} while {@link #int_()} still reads {@code -0} as
     * {@code 0}. Each decoder then rounds or checks the value once, as its own documentation says.
     *
     * <p>What the tree cannot hold is refused, not dropped: an object with the same member name
     * twice, and a number whose exponent is beyond what a {@code BigDecimal} holds (such as
     * {@code 1e3000000000}). The text must hold exactly one JSON value.
     *
     * <p>The parser is Jackson's, with its built-in {@code StreamReadConstraints}: limits on the
     * length of a number, a string or a name and on nesting depth. These bound the resources
     * parsing takes and are not Raoh decoder constraints; to read under other limits, pass a parser
     * configured with them to {@link #readTree(JsonParser)}.
     *
     * @param content the JSON text
     * @return the root node
     * @throws JacksonException if the text is not one JSON value, exceeds a read constraint, or
     *         holds something the tree cannot (see above)
     */
    public static JsonNode readTree(String content) {
        return JsonTreeReader.read(content);
    }

    /**
     * Reads a JSON document from {@code reader} into the tree the decoders here are specified on,
     * as {@link #readTree(String)} does, reading to the end of the input. The reader is not closed.
     *
     * @param reader the JSON text
     * @return the root node
     * @throws JacksonException if the text is not one JSON value, exceeds a read constraint, holds
     *         something the tree cannot (see {@link #readTree(String)}), or cannot be read
     */
    public static JsonNode readTree(Reader reader) {
        return JsonTreeReader.read(reader);
    }

    /**
     * Reads a JSON document from {@code in} into the tree the decoders here are specified on, as
     * {@link #readTree(String)} does, reading to the end of the input. The encoding (UTF-8, UTF-16
     * or UTF-32) is detected from the first bytes. The stream is not closed.
     *
     * @param in the JSON bytes
     * @return the root node
     * @throws JacksonException if the input is not one JSON value, exceeds a read constraint, holds
     *         something the tree cannot (see {@link #readTree(String)}), or cannot be read
     */
    public static JsonNode readTree(InputStream in) {
        return JsonTreeReader.read(in);
    }

    /**
     * Reads one JSON value from a parser the caller owns, into the tree the decoders here are
     * specified on, as {@link #readTree(String)} does.
     *
     * <p>The value starts at the parser's current token, or at its next token if the parser has not
     * read one yet. The parser is left on the value's last token and is not closed, so a stream of
     * values can be read one call at a time:
     *
     * <pre>{@code
     * while (parser.nextToken() != null) {
     *     Result<Order> order = orderDecoder.decode(JsonDecoders.readTree(parser));
     * }
     * }</pre>
     *
     * <p>The parser's own configuration decides what it reads: its {@code StreamReadConstraints}, its
     * input, and its features. Its numbers are taken from the text it holds for them, so the tree is
     * the same whichever of its accessors were called before. A member name that repeats is refused
     * whatever the parser's {@code STRICT_DUPLICATE_DETECTION} says, since the tree has room for only
     * one of them. The parser must read JSON text.
     *
     * @param parser the parser, on the first token of a value or before any token
     * @return the value's node
     * @throws JacksonException if the input ends before the value does, exceeds the parser's read
     *         constraints, or holds something the tree cannot (see {@link #readTree(String)})
     * @throws IllegalArgumentException if the parser is on a token that does not start a value
     */
    public static JsonNode readTree(JsonParser parser) {
        return JsonTreeReader.readValue(parser);
    }

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
                return typeMismatch(in, path, "string");
            }
            return Result.ok(in.asString());
        };
    }

    // --- Numeric decoders ---
    // Each decoder works in two steps. First it decides which JSON numbers it admits: any number,
    // or for int_() and long_() only an integer literal. That decision is JSON's own and stays
    // here. Then it reads the admitted node's value with numberValue() and hands it to the
    // ObjectDecoders decoder, which owns the conversion to the target type: range checks,
    // rounding and each source-to-target rule are defined there only.
    //
    // A number value cannot say that a zero was written with a minus sign, so the nodes readTree
    // builds for such a zero are NegativeZero, and double_() and float_() hand ObjectDecoders
    // -0.0 for them instead of the node's value. That is the only place the sign is read.
    //
    // int_() and long_() must not leave a fractional literal to ObjectDecoders, which accepts an
    // integral BigDecimal: whether 1.0 arrives as a Double or a BigDecimal depends on the
    // mapper's USE_BIG_DECIMAL_FOR_FLOATS setting, and the result must not.
    // JsonDecoderTest#intRejectsFractionalLiteralWhateverTheMapperConfiguration guards this.
    //
    // Jackson's target-typed accessors (intValue(), doubleValue(), decimalValue(), ...) are not
    // used: they decide range and rounding by Jackson's rules, and in Jackson 3 throw
    // JsonNodeException out of decode() for a value the target cannot hold.

    /**
     * Creates an integer decoder.
     *
     * <p>Accepts a JSON integer that fits {@code int}. Returns {@code required} for {@code null}
     * or a missing node, and {@code type_mismatch} for a number with a fraction or exponent part
     * (even {@code 1.0}), an integer outside the {@code int} range, and any other node type.
     *
     * @return a decoder that extracts an integer value from a JSON node
     */
    public static IntDecoder<JsonNode> int_() {
        var base = ObjectDecoders.int_();
        return new IntDecoder<>((in, path) -> {
            if (in == null || in.isNull() || in.isMissingNode()) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (!in.isIntegralNumber()) {
                return typeMismatch(in, path, "integer");
            }
            return base.decode(in.numberValue(), path);
        });
    }

    /**
     * Creates a long integer decoder.
     *
     * <p>Accepts a JSON integer that fits {@code long}. Returns {@code required} for {@code null}
     * or a missing node, and {@code type_mismatch} for a number with a fraction or exponent part
     * (even {@code 1.0}), an integer outside the {@code long} range, and any other node type.
     *
     * @return a decoder that extracts a long value from a JSON node
     */
    public static LongDecoder<JsonNode> long_() {
        var base = ObjectDecoders.long_();
        return new LongDecoder<>((in, path) -> {
            if (in == null || in.isNull() || in.isMissingNode()) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (!in.isIntegralNumber()) {
                return typeMismatch(in, path, "long");
            }
            return base.decode(in.numberValue(), path);
        });
    }

    /**
     * Creates a double decoder.
     *
     * <p>Accepts any JSON number, rounded to the nearest {@code double} as
     * {@link ObjectDecoders#double_()} does. Returns {@code required} for {@code null} or a
     * missing node, and {@code type_mismatch} for a number whose magnitude is beyond the
     * {@code double} range (e.g. {@code 1e400}) and any other node type.
     *
     * <p>On a tree from {@link #readTree(String)} the number is rounded once, from the decimal it
     * was written as, and a zero written with a minus sign ({@code -0}, {@code -0.0}) gives
     * {@code -0.0}. On a tree from Jackson's {@code ObjectMapper} it is whatever value the mapper
     * already converted the number to.
     *
     * @return a decoder that extracts a double value from a JSON node
     */
    public static DoubleDecoder<JsonNode> double_() {
        var base = ObjectDecoders.double_();
        return new DoubleDecoder<>((in, path) -> {
            if (in == null || in.isNull() || in.isMissingNode()) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (!in.isNumber()) {
                return typeMismatch(in, path, "double");
            }
            if (in instanceof NegativeZero) {
                return base.decode(-0.0d, path);
            }
            return base.decode(in.numberValue(), path);
        });
    }

    /**
     * Creates a float decoder.
     *
     * <p>Like {@link #double_()}, but rounds to the nearest {@code float} as
     * {@link ObjectDecoders#float_()} does; a number whose magnitude is beyond the {@code float}
     * range (e.g. {@code 1e40}) fails with {@code type_mismatch}.
     *
     * <p>On a tree from {@link #readTree(String)} the number is rounded to {@code float} once, from
     * the decimal it was written as, not through a {@code double} first, and a zero written with a
     * minus sign gives {@code -0.0f}. On a tree from Jackson's {@code ObjectMapper} a number with a
     * fraction or an exponent has usually been rounded to {@code double} already, and is rounded a
     * second time here.
     *
     * @return a decoder that extracts a float value from a JSON node
     */
    public static FloatDecoder<JsonNode> float_() {
        var base = ObjectDecoders.float_();
        return new FloatDecoder<>((in, path) -> {
            if (in == null || in.isNull() || in.isMissingNode()) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (!in.isNumber()) {
                return typeMismatch(in, path, "float");
            }
            if (in instanceof NegativeZero) {
                return base.decode(-0.0f, path);
            }
            return base.decode(in.numberValue(), path);
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
                return typeMismatch(in, path, "boolean");
            }
            return Result.ok(in.booleanValue());
        });
    }

    /**
     * Creates a decimal (BigDecimal) decoder.
     *
     * <p>Converts a JSON number as {@link ObjectDecoders#decimal()} does. On a tree from
     * {@link #readTree(String)} the result is exactly the number as written, scale included:
     * {@code 0.0001} gives {@code 0.0001} and {@code 1.50} gives {@code 1.50}. On a tree from
     * Jackson's {@code ObjectMapper} a JSON integer is exact, and so is a fractional number when the
     * mapper keeps it as {@code BigDecimal} ({@code DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS});
     * otherwise Jackson has already parsed it
     * as a {@code double}, and the result is the decimal that {@code double} prints. Returns
     * {@code required} for {@code null} or a missing node, and {@code type_mismatch} for any other
     * node type.
     *
     * @return a decoder that extracts a decimal value from a JSON node
     */
    public static DecimalDecoder<JsonNode> decimal() {
        var base = ObjectDecoders.decimal();
        return new DecimalDecoder<>((in, path) -> {
            if (in == null || in.isNull() || in.isMissingNode()) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (!in.isNumber()) {
                return typeMismatch(in, path, "number");
            }
            return base.decode(in.numberValue(), path);
        });
    }

    /**
     * The {@code type_mismatch} failure for a node of the wrong JSON type, naming that type as
     * {@code actual} ({@code "string"}, {@code "number"}, {@code "null"}, ...).
     *
     * @param in the node, or {@code null} when there is none
     * @param path the path of the node
     * @param expected the type the decoder expected
     * @param <T> the decoder's value type
     * @return a {@code type_mismatch} failure
     */
    private static <T> Result<T> typeMismatch(@Nullable JsonNode in, Path path, String expected) {
        return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected " + expected,
                Map.of("expected", expected,
                        "actual", in == null ? "null" : in.getNodeType().name().toLowerCase(Locale.ROOT)));
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
                return typeMismatch(in, fieldPath, "object");
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
     * <p>The decoded list is unmodifiable and holds every element in input order, including
     * {@code null} when {@code elementDec} decodes an element to {@code null}.
     *
     * @param <T>        the element type
     * @param elementDec the decoder for each element
     * @return a list decoder
     */
    public static <T extends @Nullable Object> ListDecoder<JsonNode, T> list(Decoder<JsonNode, T> elementDec) {
        return new ListDecoder<>((in, path) -> {
            if (in == null || in.isNull() || in.isMissingNode()) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (!in.isArray()) {
                return typeMismatch(in, path, "array");
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
            return Result.ok(Collections.unmodifiableList(results));
        });
    }

    /**
     * Creates a decoder for JSON objects as string-keyed maps.
     *
     * <p>The decoded map is unmodifiable, iterates in the input's key order, and keeps a
     * {@code null} value when {@code valDec} decodes a value to {@code null}.
     *
     * @param <V>    the value type
     * @param valDec the decoder for each value
     * @return a record (map) decoder
     */
    public static <V extends @Nullable Object> RecordDecoder<JsonNode, V> map(Decoder<JsonNode, V> valDec) {
        return new RecordDecoder<>((in, path) -> {
            if (in == null || in.isNull() || in.isMissingNode()) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (!in.isObject()) {
                return typeMismatch(in, path, "object");
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
            return Result.ok(Collections.unmodifiableMap(results));
        });
    }

    // --- enumOf / literal ---

    /**
     * Decodes a JSON string into an enum constant, matching the constant name ASCII
     * case-insensitively.
     *
     * <p>{@code A}-{@code Z} are equivalent to {@code a}-{@code z}; every other character must
     * match exactly. See {@link Decoders#enumOf} for the full contract.
     *
     * @param <E> the enum type
     * @param cls the enum class
     * @return an enum decoder
     * @throws IllegalArgumentException if two enum constant names are equal under ASCII
     *                                  case-insensitive matching
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
