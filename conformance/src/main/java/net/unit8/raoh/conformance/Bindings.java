package net.unit8.raoh.conformance;

import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.decode.Decoders;
import net.unit8.raoh.decode.builtin.BoolDecoder;
import net.unit8.raoh.decode.builtin.DecimalDecoder;
import net.unit8.raoh.decode.builtin.DoubleDecoder;
import net.unit8.raoh.decode.builtin.FloatDecoder;
import net.unit8.raoh.decode.builtin.IntDecoder;
import net.unit8.raoh.decode.builtin.ListDecoder;
import net.unit8.raoh.decode.builtin.LongDecoder;
import net.unit8.raoh.decode.builtin.RecordDecoder;
import net.unit8.raoh.decode.builtin.StringDecoder;
import net.unit8.raoh.decode.builtin.TemporalDecoder;
import net.unit8.raoh.decode.combinator.CombinePart;
import net.unit8.raoh.encode.Encoder;
import net.unit8.raoh.encode.MapEncoders;
import net.unit8.raoh.encode.ObjectEncoders;
import net.unit8.raoh.encode.PropertyEncoder;
import net.unit8.raoh.json.JsonDecoders;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Binds the forms of the specification's decoder language ({@code decoder-language.md}) to
 * raoh-java's API and builds the decoder or encoder a case names.
 *
 * <p>Every feature the runner binds is an entry of one of the tables here, and
 * {@link #boundFeatures()} lists exactly those entries, with the {@code .message} facet of every
 * form whose binding takes a message. What the runner can run and what it says it binds therefore
 * come from the same place.
 *
 * <p>Building a form that needs a feature no table has throws {@link UnboundFeature}, and the case
 * is not run. Anything else that goes wrong while building is raoh-java refusing the decoder, or a
 * defect, and the case records an error.
 */
final class Bindings {

    /**
     * A decoder built from a form, with the type of what it gives.
     *
     * @param decoder the decoder
     * @param type    the type of its result
     */
    record BoundDecoder(Decoder<JsonNode, ?> decoder, SpecType type) {
    }

    /**
     * An encoder built from a form, with the type of what it takes.
     *
     * @param encoder the encoder
     * @param input   the type of its input
     */
    record BoundEncoder(Encoder<Object, ?> encoder, SpecType input) {
    }

    /** A form needs a feature the runner does not bind. */
    static final class UnboundFeature extends RuntimeException {
        private final String feature;

        UnboundFeature(String feature) {
            super(feature, null, false, false);
            this.feature = feature;
        }

        String feature() {
            return feature;
        }
    }

    @FunctionalInterface
    private interface Constructor {
        BoundDecoder build(List<JsonNode> args, @Nullable String message);
    }

    /** A constructor: its required arguments, and whether a message may follow them. */
    private record ConstructorBinding(int arity, boolean message, Constructor build) {
    }

    @FunctionalInterface
    private interface Operation {
        BoundDecoder apply(BoundDecoder receiver, List<JsonNode> values, @Nullable String message);
    }

    /**
     * An operation on one kind of receiver: how many value arguments it takes, the value a missing
     * optional one stands for, and whether a message may follow them.
     */
    private record OperationBinding(int arity, @Nullable JsonNode defaultLast, boolean message, Operation apply) {
    }

    @FunctionalInterface
    private interface Field {
        Part build(@Nullable String name, BoundDecoder decoder);
    }

    /** A field of an object, as a component of raoh-java's {@code combine}. */
    private record Part(CombinePart<JsonNode, ?> part, SpecType type, @Nullable String member) {
    }

    @FunctionalInterface
    private interface EncoderBinding {
        BoundEncoder build(List<JsonNode> args);
    }

    @FunctionalInterface
    private interface Property {
        BoundProperty build(List<JsonNode> args);
    }

    private record BoundProperty(PropertyEncoder<Object> property, SpecType input) {
    }

    /** An operation on a receiver of raoh-java's decoder class {@code D}, giving another decoder. */
    @FunctionalInterface
    private interface TypedOperation<D> {
        Decoder<?, ?> apply(D receiver, List<JsonNode> values, SpecType type, @Nullable String message);
    }

    private static final SpecType STRING = SpecType.Scalar.STRING;
    private static final SpecType INT32 = SpecType.Scalar.INT32;
    private static final SpecType LIST_OF_STRING = new SpecType.ListOf(STRING);

    private final Map<String, ConstructorBinding> constructors = new LinkedHashMap<>();
    private final Map<String, OperationBinding> operations = new LinkedHashMap<>();
    private final Map<String, Field> fields = new LinkedHashMap<>();
    private final Map<String, EncoderBinding> encoders = new LinkedHashMap<>();
    private final Map<String, Property> properties = new LinkedHashMap<>();

    Bindings() {
        bindScalarConstructors();
        bindStructuralConstructors();
        bindFields();
        bindStringOperations();
        bindNumericOperations();
        bindBoolOperations();
        bindCollectionOperations();
        bindTemporalOperations();
        bindAnyOperations();
        bindEncoders();
    }

    /**
     * The features the runner binds, sorted.
     *
     * @return the feature IDs
     */
    List<String> boundFeatures() {
        Set<String> features = new TreeSet<>();
        constructors.forEach((id, c) -> {
            features.add(id);
            if (c.message()) {
                features.add(id + ".message");
            }
        });
        operations.forEach((id, o) -> {
            features.add(id);
            if (o.message()) {
                features.add(id + ".message");
            }
        });
        features.addAll(fields.keySet());
        features.addAll(Fixtures.ALL.keySet());
        features.addAll(encoders.keySet());
        features.addAll(properties.keySet());
        return List.copyOf(features);
    }

    // --- Interpreting forms ---

    /**
     * Builds the decoder a decoder form names.
     *
     * @param form the form
     * @return the decoder and its result type
     * @throws UnboundFeature if the form needs a feature the runner does not bind
     */
    BoundDecoder decoder(JsonNode form) {
        String name = formName(form);
        String id = "decoder." + name;
        ConstructorBinding c = constructors.get(id);
        if (c == null) {
            throw new UnboundFeature(id);
        }
        int next = 1 + c.arity();
        if (form.size() < next) {
            throw new IllegalArgumentException(name + " takes " + c.arity() + " arguments: " + form);
        }
        List<JsonNode> args = slice(form, 1, next);
        String message = null;
        if (c.message() && form.size() > next && form.get(next).isString()) {
            message = form.get(next).stringValue();
            next++;
        }
        BoundDecoder bound = c.build().build(args, message);
        for (int i = next; i < form.size(); i++) {
            bound = operation(bound, form.get(i));
        }
        return bound;
    }

    private BoundDecoder operation(BoundDecoder receiver, JsonNode form) {
        String name = formName(form);
        String id = "operation." + receiver.type().kind() + "." + name;
        OperationBinding o = operations.get(id);
        if (o == null) {
            id = "operation.any." + name;
            o = operations.get(id);
            if (o == null) {
                throw new UnboundFeature("operation." + receiver.type().kind() + "." + name);
            }
        }
        List<JsonNode> args = slice(form, 1, form.size());
        String message = null;
        if (args.size() == o.arity() + 1 && args.getLast().isString()) {
            // The form gives its message, which needs the facet as well as the operation.
            if (!o.message()) {
                throw new UnboundFeature(id + ".message");
            }
            message = args.getLast().stringValue();
            args = args.subList(0, o.arity());
        }
        if (args.size() == o.arity() - 1 && o.defaultLast() != null) {
            args = new ArrayList<>(args);
            args.add(o.defaultLast());
        }
        if (args.size() != o.arity()) {
            throw new IllegalArgumentException(name + " takes " + o.arity() + " arguments: " + form);
        }
        return o.apply().apply(receiver, args, message);
    }

    /**
     * Builds the encoder an encoder form names.
     *
     * @param form the form
     * @return the encoder and its input type
     * @throws UnboundFeature if the form needs a feature the runner does not bind
     */
    BoundEncoder encoder(JsonNode form) {
        String id = "encoder." + formName(form);
        EncoderBinding e = encoders.get(id);
        if (e == null) {
            throw new UnboundFeature(id);
        }
        return e.build(slice(form, 1, form.size()));
    }

    // --- Constructors ---

    private void bindScalarConstructors() {
        constructor("string", 0, false, (a, m) -> new BoundDecoder(JsonDecoders.string(), STRING));
        constructor("int", 0, false, (a, m) -> new BoundDecoder(JsonDecoders.int_(), INT32));
        constructor("long", 0, false, (a, m) -> new BoundDecoder(JsonDecoders.long_(), SpecType.Scalar.INT64));
        constructor("float", 0, false, (a, m) -> new BoundDecoder(JsonDecoders.float_(), SpecType.Scalar.FLOAT32));
        constructor("double", 0, false, (a, m) -> new BoundDecoder(JsonDecoders.double_(), SpecType.Scalar.FLOAT64));
        constructor("decimal", 0, false, (a, m) -> new BoundDecoder(JsonDecoders.decimal(), SpecType.Scalar.DECIMAL));
        constructor("bool", 0, false, (a, m) -> new BoundDecoder(JsonDecoders.bool(), SpecType.Scalar.BOOL));
    }

    private void bindStructuralConstructors() {
        constructor("list", 1, false, (a, m) -> {
            BoundDecoder element = decoder(a.get(0));
            return new BoundDecoder(JsonDecoders.list(of(element)), new SpecType.ListOf(element.type()));
        });
        constructor("dict", 1, false, (a, m) -> {
            BoundDecoder value = decoder(a.get(0));
            return new BoundDecoder(JsonDecoders.map(of(value)), new SpecType.MapOf(value.type()));
        });
        constructor("object", 1, false, (a, m) -> object(a.get(0), false));
        constructor("strictObject", 1, false, (a, m) -> object(a.get(0), true));
        constructor("strict", 2, false, (a, m) -> {
            BoundDecoder inner = decoder(a.get(0));
            Set<String> known = new LinkedHashSet<>(strings(a.get(1)));
            return new BoundDecoder(JsonDecoders.strict(of(inner), known), inner.type());
        });
        constructor("nullable", 1, false, (a, m) -> {
            BoundDecoder inner = decoder(a.get(0));
            return new BoundDecoder(JsonDecoders.nullable(of(inner)), new SpecType.NullableOf(inner.type()));
        });
        constructor("enum", 2, true, (a, m) -> {
            List<String> symbols = strings(a.get(0));
            Decoder<JsonNode, String> string = stringDecoder(decoder(a.get(1)));
            return new BoundDecoder(enumOf(Symbols.enumOf(symbols), string, m), new SpecType.Symbol(symbols));
        });
        constructor("literal", 2, true, (a, m) -> {
            String literal = (String) ValueCodec.materialize(STRING, a.get(0));
            Decoder<JsonNode, String> string = stringDecoder(decoder(a.get(1)));
            return new BoundDecoder(Decoders.literal(literal, string, m), STRING);
        });
        constructor("discriminate", 2, false, (a, m) -> {
            String field = (String) ValueCodec.materialize(STRING, a.get(0));
            Variants variants = variants(a.get(1));
            return new BoundDecoder(JsonDecoders.discriminate(field, variants.decoders()), variants.type());
        });
        constructor("discriminateBy", 3, false, (a, m) -> {
            String field = (String) ValueCodec.materialize(STRING, a.get(0));
            Decoder<JsonNode, String> tag = stringDecoder(decoder(a.get(1)));
            Variants variants = variants(a.get(2));
            return new BoundDecoder(Decoders.discriminate(field, tag, variants.decoders()), variants.type());
        });
        constructor("oneOf", 1, false, (a, m) -> {
            List<BoundDecoder> candidates = new ArrayList<>();
            for (JsonNode candidate : a.get(0)) {
                candidates.add(decoder(candidate));
            }
            SpecType type = common(candidates.stream().map(BoundDecoder::type).toList());
            @SuppressWarnings("unchecked")
            Decoder<JsonNode, Object>[] decoders = candidates.stream().map(Bindings::of).toArray(Decoder[]::new);
            return new BoundDecoder(Decoders.oneOf(decoders), type);
        });
        constructor("withDefault", 2, false, (a, m) -> {
            BoundDecoder inner = decoder(a.get(0));
            Object fallback = ValueCodec.materialize(inner.type(), a.get(1));
            return new BoundDecoder(JsonDecoders.withDefault(of(inner), fallback), inner.type());
        });
        constructor("recover", 2, false, (a, m) -> {
            BoundDecoder inner = decoder(a.get(0));
            Object fallback = ValueCodec.materialize(inner.type(), a.get(1));
            return new BoundDecoder(Decoders.recover(of(inner), fallback), inner.type());
        });
        constructor("recoverWith", 2, false, (a, m) -> {
            BoundDecoder inner = decoder(a.get(0));
            Fixtures.RecoverFixture recovery = fixture(a.get(1), Fixtures.RecoverFixture.class);
            requireType(recovery.output(), inner.type());
            return new BoundDecoder(recoverWith(of(inner), recovery.fn()), inner.type());
        });
    }

    /**
     * {@code object} and {@code strictObject}: every object is one {@code combine} over a list of
     * components, whatever the number of fields, giving the product of their values.
     */
    private BoundDecoder object(JsonNode fieldForms, boolean strict) {
        List<CombinePart<JsonNode, ?>> parts = new ArrayList<>();
        List<SpecType> types = new ArrayList<>();
        Set<String> members = new LinkedHashSet<>();
        for (JsonNode fieldForm : fieldForms) {
            Part part = field(fieldForm);
            parts.add(part.part());
            types.add(part.type());
            if (part.member() == null) {
                if (strict) {
                    throw new IllegalArgumentException("a strictObject cannot have a flat field");
                }
            } else {
                members.add(part.member());
            }
        }
        Decoder<JsonNode, List<@Nullable Object>> product = JsonDecoders.combine(parts).map(ValueCodec::product);
        return new BoundDecoder(strict ? JsonDecoders.strict(product, members) : product, new SpecType.Product(types));
    }

    private record Variants(Map<String, Decoder<JsonNode, ?>> decoders, SpecType type) {
    }

    private Variants variants(JsonNode variantForms) {
        Map<String, Decoder<JsonNode, ?>> decoders = new LinkedHashMap<>();
        List<SpecType> types = new ArrayList<>();
        for (Map.Entry<String, JsonNode> e : variantForms.properties()) {
            BoundDecoder variant = decoder(e.getValue());
            decoders.put(e.getKey(), variant.decoder());
            types.add(variant.type());
        }
        return new Variants(decoders, common(types));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Decoder<JsonNode, ?> enumOf(Class<? extends Enum<?>> cls, Decoder<JsonNode, String> string,
                                               @Nullable String message) {
        return Decoders.enumOf((Class) cls, string, message);
    }

    // --- Fields ---

    private void bindFields() {
        fields.put("field.field", (name, d) -> new Part(JsonDecoders.field(member(name), of(d)), d.type(), name));
        fields.put("field.optionalField", (name, d) ->
                new Part(JsonDecoders.optionalField(member(name), of(d)), new SpecType.OptionalOf(d.type()), name));
        fields.put("field.optionalNullableField", (name, d) ->
                new Part(JsonDecoders.optionalNullableField(member(name), of(d)), new SpecType.PresenceOf(d.type()), name));
        fields.put("field.flat", (name, d) -> new Part(JsonDecoders.flat(of(d)), d.type(), null));
    }

    private Part field(JsonNode form) {
        String kind = formName(form);
        String id = "field." + kind;
        Field f = fields.get(id);
        if (f == null) {
            throw new UnboundFeature(id);
        }
        // A field form is [kind, name, decoder], or [kind, decoder] for one that reads no member.
        if (form.size() == 3) {
            return f.build((String) ValueCodec.materialize(STRING, form.get(1)), decoder(form.get(2)));
        }
        if (form.size() == 2) {
            return f.build(null, decoder(form.get(1)));
        }
        throw new IllegalArgumentException("not a field form: " + form);
    }

    private static String member(@Nullable String name) {
        if (name == null) {
            throw new IllegalArgumentException("this field reads a member and needs its name");
        }
        return name;
    }

    // --- Operations on strings ---

    private void bindStringOperations() {
        stringOp("trim", 0, false, (d, v, t, m) -> d.trim());
        stringOp("toLowerCase", 0, false, (d, v, t, m) -> d.toLowerCase());
        stringOp("toUpperCase", 0, false, (d, v, t, m) -> d.toUpperCase());
        // normalize's one argument is optional and stands for NFC when left out.
        operations.put("operation.string.normalize", new OperationBinding(1,
                JsonNodeFactory.instance.stringNode("NFC"), false,
                (r, v, m) -> new BoundDecoder(
                        typed(stringClass(), r, "operation.string.normalize")
                                .normalize(Normalizer.Form.valueOf(string(v.get(0)))),
                        r.type())));
        stringOp("nonBlank", 0, true, (d, v, t, m) -> d.nonBlank(m));
        stringOp("minLength", 1, true, (d, v, t, m) -> d.minLength(int32(v.get(0)), m));
        stringOp("maxLength", 1, true, (d, v, t, m) -> d.maxLength(int32(v.get(0)), m));
        stringOp("fixedLength", 1, true, (d, v, t, m) -> d.fixedLength(int32(v.get(0)), m));
        stringOp("oneOf", 1, true, (d, v, t, m) -> d.oneOf(strings(v.get(0)), m));
        stringOp("startsWith", 1, true, (d, v, t, m) -> d.startsWith(string(v.get(0)), m));
        stringOp("endsWith", 1, true, (d, v, t, m) -> d.endsWith(string(v.get(0)), m));
        stringOp("includes", 1, true, (d, v, t, m) -> d.includes(string(v.get(0)), m));
        stringOp("pattern", 1, true, (d, v, t, m) -> d.pattern(string(v.get(0)), ErrorCodes.INVALID_FORMAT, m));
        stringOp("email", 0, true, (d, v, t, m) -> d.email(m));
        stringOp("ipv4", 0, true, (d, v, t, m) -> d.ipv4(m));
        stringOp("ipv6", 0, true, (d, v, t, m) -> d.ipv6(m));
        stringOp("ip", 0, true, (d, v, t, m) -> d.ip(m));
        stringOp("ulid", 0, true, (d, v, t, m) -> d.ulid(m));
        stringOp("cuid", 0, true, (d, v, t, m) -> d.cuid(m));
        convertingStringOp("uuid", SpecType.Scalar.UUID, (d, m) -> d.uuid(m));
        convertingStringOp("url", SpecType.Scalar.URI, (d, m) -> d.url(m));
        convertingStringOp("uri", SpecType.Scalar.URI, (d, m) -> d.uri(m));
        convertingStringOp("toInt", INT32, (d, m) -> d.toInt(m));
        convertingStringOp("toLong", SpecType.Scalar.INT64, (d, m) -> d.toLong(m));
        convertingStringOp("toDecimal", SpecType.Scalar.DECIMAL, (d, m) -> d.toDecimal(m));
        convertingStringOp("toBool", SpecType.Scalar.BOOL, (d, m) -> d.toBool(m));
        convertingStringOp("iso8601", SpecType.Scalar.INSTANT, (d, m) -> d.iso8601(m));
        convertingStringOp("date", SpecType.Scalar.DATE, (d, m) -> d.date(m));
        convertingStringOp("time", SpecType.Scalar.TIME, (d, m) -> d.time(m));
        convertingStringOp("dateTime", SpecType.Scalar.DATETIME, (d, m) -> d.dateTime(m));
        convertingStringOp("offsetDateTime", SpecType.Scalar.OFFSET_DATETIME, (d, m) -> d.offsetDateTime(m));
    }

    private void stringOp(String name, int arity, boolean message, TypedOperation<StringDecoder<JsonNode>> op) {
        String id = "operation.string." + name;
        operations.put(id, new OperationBinding(arity, null, message,
                (r, v, m) -> new BoundDecoder(cast(op.apply(typed(stringClass(), r, id), v, r.type(), m)), r.type())));
    }

    @FunctionalInterface
    private interface Conversion {
        Decoder<JsonNode, ?> apply(StringDecoder<JsonNode> receiver, @Nullable String message);
    }

    private void convertingStringOp(String name, SpecType result, Conversion conversion) {
        String id = "operation.string." + name;
        operations.put(id, new OperationBinding(0, null, true,
                (r, v, m) -> new BoundDecoder(conversion.apply(typed(stringClass(), r, id), m), result)));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Class<StringDecoder<JsonNode>> stringClass() {
        return (Class) StringDecoder.class;
    }

    // --- Operations on numbers ---

    private void bindNumericOperations() {
        numericOps("int32", IntDecoder.class);
        operations.put("operation.int32.oneOf", typedOp("operation.int32.oneOf", IntDecoder.class, 1,
                (d, v, t, m) -> ((IntDecoder<JsonNode>) d).oneOf(listOf(t, v.get(0), Integer.class), m)));
        operations.put("operation.int32.multipleOf", typedOp("operation.int32.multipleOf", IntDecoder.class, 1,
                (d, v, t, m) -> ((IntDecoder<JsonNode>) d).multipleOf((Integer) value(t, v.get(0)), m)));

        numericOps("int64", LongDecoder.class);
        operations.put("operation.int64.oneOf", typedOp("operation.int64.oneOf", LongDecoder.class, 1,
                (d, v, t, m) -> ((LongDecoder<JsonNode>) d).oneOf(listOf(t, v.get(0), Long.class), m)));
        operations.put("operation.int64.multipleOf", typedOp("operation.int64.multipleOf", LongDecoder.class, 1,
                (d, v, t, m) -> ((LongDecoder<JsonNode>) d).multipleOf((Long) value(t, v.get(0)), m)));

        numericOps("float32", FloatDecoder.class);
        operations.put("operation.float32.oneOf", typedOp("operation.float32.oneOf", FloatDecoder.class, 1,
                (d, v, t, m) -> ((FloatDecoder<JsonNode>) d).oneOf(listOf(t, v.get(0), Float.class), m)));

        numericOps("float64", DoubleDecoder.class);
        operations.put("operation.float64.oneOf", typedOp("operation.float64.oneOf", DoubleDecoder.class, 1,
                (d, v, t, m) -> ((DoubleDecoder<JsonNode>) d).oneOf(listOf(t, v.get(0), Double.class), m)));

        numericOps("decimal", DecimalDecoder.class);
        operations.put("operation.decimal.multipleOf", typedOp("operation.decimal.multipleOf", DecimalDecoder.class, 1,
                (d, v, t, m) -> ((DecimalDecoder<JsonNode>) d).multipleOf((BigDecimal) value(t, v.get(0)), m)));
        operations.put("operation.decimal.scale", typedOp("operation.decimal.scale", DecimalDecoder.class, 1,
                (d, v, t, m) -> ((DecimalDecoder<JsonNode>) d).scale(int32(v.get(0)), m)));
    }

    /**
     * The bounds every numeric receiver has. raoh-java gives each numeric type a decoder class of
     * its own with the same methods, overloaded on the primitive, so each is called on its class.
     */
    private void numericOps(String kind, Class<?> cls) {
        bound(kind, cls, "min", 1, (d, v, t, m) -> switch (d) {
            case IntDecoder<?> i -> i.min((Integer) value(t, v.get(0)), m);
            case LongDecoder<?> l -> l.min((Long) value(t, v.get(0)), m);
            case FloatDecoder<?> f -> f.min((Float) value(t, v.get(0)), m);
            case DoubleDecoder<?> f -> f.min((Double) value(t, v.get(0)), m);
            case DecimalDecoder<?> b -> b.min((BigDecimal) value(t, v.get(0)), m);
            default -> throw unexpected(d);
        });
        bound(kind, cls, "max", 1, (d, v, t, m) -> switch (d) {
            case IntDecoder<?> i -> i.max((Integer) value(t, v.get(0)), m);
            case LongDecoder<?> l -> l.max((Long) value(t, v.get(0)), m);
            case FloatDecoder<?> f -> f.max((Float) value(t, v.get(0)), m);
            case DoubleDecoder<?> f -> f.max((Double) value(t, v.get(0)), m);
            case DecimalDecoder<?> b -> b.max((BigDecimal) value(t, v.get(0)), m);
            default -> throw unexpected(d);
        });
        bound(kind, cls, "range", 2, (d, v, t, m) -> switch (d) {
            case IntDecoder<?> i -> i.range((Integer) value(t, v.get(0)), (Integer) value(t, v.get(1)), m);
            case LongDecoder<?> l -> l.range((Long) value(t, v.get(0)), (Long) value(t, v.get(1)), m);
            case FloatDecoder<?> f -> f.range((Float) value(t, v.get(0)), (Float) value(t, v.get(1)), m);
            case DoubleDecoder<?> f -> f.range((Double) value(t, v.get(0)), (Double) value(t, v.get(1)), m);
            case DecimalDecoder<?> b -> b.range((BigDecimal) value(t, v.get(0)), (BigDecimal) value(t, v.get(1)), m);
            default -> throw unexpected(d);
        });
        bound(kind, cls, "positive", 0, (d, v, t, m) -> switch (d) {
            case IntDecoder<?> i -> i.positive(m);
            case LongDecoder<?> l -> l.positive(m);
            case FloatDecoder<?> f -> f.positive(m);
            case DoubleDecoder<?> f -> f.positive(m);
            case DecimalDecoder<?> b -> b.positive(m);
            default -> throw unexpected(d);
        });
        bound(kind, cls, "negative", 0, (d, v, t, m) -> switch (d) {
            case IntDecoder<?> i -> i.negative(m);
            case LongDecoder<?> l -> l.negative(m);
            case FloatDecoder<?> f -> f.negative(m);
            case DoubleDecoder<?> f -> f.negative(m);
            case DecimalDecoder<?> b -> b.negative(m);
            default -> throw unexpected(d);
        });
        bound(kind, cls, "nonNegative", 0, (d, v, t, m) -> switch (d) {
            case IntDecoder<?> i -> i.nonNegative(m);
            case LongDecoder<?> l -> l.nonNegative(m);
            case FloatDecoder<?> f -> f.nonNegative(m);
            case DoubleDecoder<?> f -> f.nonNegative(m);
            case DecimalDecoder<?> b -> b.nonNegative(m);
            default -> throw unexpected(d);
        });
        bound(kind, cls, "nonPositive", 0, (d, v, t, m) -> switch (d) {
            case IntDecoder<?> i -> i.nonPositive(m);
            case LongDecoder<?> l -> l.nonPositive(m);
            case FloatDecoder<?> f -> f.nonPositive(m);
            case DoubleDecoder<?> f -> f.nonPositive(m);
            case DecimalDecoder<?> b -> b.nonPositive(m);
            default -> throw unexpected(d);
        });
    }

    private void bound(String kind, Class<?> cls, String name, int arity, TypedOperation<Object> op) {
        String id = "operation." + kind + "." + name;
        operations.put(id, typedOp(id, cls, arity, op));
    }

    // --- Operations on booleans, lists, maps and temporal values ---

    private void bindBoolOperations() {
        operations.put("operation.bool.isTrue", typedOp("operation.bool.isTrue", BoolDecoder.class, 0,
                (d, v, t, m) -> ((BoolDecoder<JsonNode>) d).isTrue(m)));
    }

    @SuppressWarnings("unchecked")
    private void bindCollectionOperations() {
        for (String kind : List.of("list", "map")) {
            bound(kind, kind.equals("list") ? ListDecoder.class : RecordDecoder.class, "nonempty", 0,
                    (d, v, t, m) -> switch (d) {
                        case ListDecoder<?, ?> l -> l.nonempty(m);
                        case RecordDecoder<?, ?> r -> r.nonempty(m);
                        default -> throw unexpected(d);
                    });
            bound(kind, kind.equals("list") ? ListDecoder.class : RecordDecoder.class, "minSize", 1,
                    (d, v, t, m) -> switch (d) {
                        case ListDecoder<?, ?> l -> l.minSize(int32(v.get(0)), m);
                        case RecordDecoder<?, ?> r -> r.minSize(int32(v.get(0)), m);
                        default -> throw unexpected(d);
                    });
            bound(kind, kind.equals("list") ? ListDecoder.class : RecordDecoder.class, "maxSize", 1,
                    (d, v, t, m) -> switch (d) {
                        case ListDecoder<?, ?> l -> l.maxSize(int32(v.get(0)), m);
                        case RecordDecoder<?, ?> r -> r.maxSize(int32(v.get(0)), m);
                        default -> throw unexpected(d);
                    });
            bound(kind, kind.equals("list") ? ListDecoder.class : RecordDecoder.class, "fixedSize", 1,
                    (d, v, t, m) -> switch (d) {
                        case ListDecoder<?, ?> l -> l.fixedSize(int32(v.get(0)), m);
                        case RecordDecoder<?, ?> r -> r.fixedSize(int32(v.get(0)), m);
                        default -> throw unexpected(d);
                    });
        }
        bound("list", ListDecoder.class, "unique", 0, (d, v, t, m) -> ((ListDecoder<JsonNode, ?>) d).unique(m));
        bound("list", ListDecoder.class, "contains", 1, (d, v, t, m) -> ((ListDecoder<JsonNode, Object>) d)
                .contains(nonNull(value(((SpecType.ListOf) t).element(), v.get(0))), m));
        // raoh-java's containsAll takes no message, so the facet is not bound.
        operations.put("operation.list.containsAll", new OperationBinding(1, null, false,
                (r, v, m) -> new BoundDecoder(
                        ((ListDecoder<JsonNode, Object>) typed(ListDecoder.class, r, "operation.list.containsAll"))
                                .containsAll(listOf(((SpecType.ListOf) r.type()).element(), v.get(0), Object.class).toArray()),
                        r.type())));
        operations.put("operation.list.toSet", new OperationBinding(0, null, false,
                (r, v, m) -> new BoundDecoder(
                        ((ListDecoder<JsonNode, ?>) typed(ListDecoder.class, r, "operation.list.toSet")).toSet(),
                        new SpecType.SetOf(((SpecType.ListOf) r.type()).element()))));
    }

    private void bindTemporalOperations() {
        for (SpecType.Scalar kind : List.of(SpecType.Scalar.INSTANT, SpecType.Scalar.DATE, SpecType.Scalar.TIME,
                SpecType.Scalar.DATETIME, SpecType.Scalar.OFFSET_DATETIME)) {
            bound(kind.kind(), TemporalDecoder.class, "before", 1,
                    (d, v, t, m) -> temporal(d).before(comparable(t, v.get(0)), m));
            bound(kind.kind(), TemporalDecoder.class, "after", 1,
                    (d, v, t, m) -> temporal(d).after(comparable(t, v.get(0)), m));
            bound(kind.kind(), TemporalDecoder.class, "between", 2,
                    (d, v, t, m) -> temporal(d).between(comparable(t, v.get(0)), comparable(t, v.get(1)), m));
        }
    }

    /**
     * {@code recoverWith}, typed so that the function cannot be read as the fallback value of
     * {@link Decoders#recover(Decoder, Object)}.
     */
    private static <T> Decoder<JsonNode, T> recoverWith(Decoder<JsonNode, T> inner,
                                                         java.util.function.Function<net.unit8.raoh.Issues, T> fn) {
        return Decoders.recover(inner, fn);
    }

    /** The receiver of a temporal operation, whose bound the materialized value matches. */
    @SuppressWarnings("rawtypes")
    private static TemporalDecoder temporal(Object d) {
        return (TemporalDecoder) d;
    }

    // --- Operations on every type ---

    @SuppressWarnings("unchecked")
    private void bindAnyOperations() {
        operations.put("operation.any.map", new OperationBinding(1, null, false, (r, v, m) -> {
            Fixtures.MapFixture f = fixture(v.get(0), Fixtures.MapFixture.class);
            SpecType output = f.output().apply(r.type());
            return new BoundDecoder(of(r).map(f.fn()), output);
        }));
        operations.put("operation.any.refine", new OperationBinding(1, null, false, (r, v, m) -> {
            Fixtures.RefineFixture f = fixture(v.get(0), Fixtures.RefineFixture.class);
            requireType(f.input(), r.type());
            return new BoundDecoder(of(r).refine(f.holds(), f.code(), f.message(), f.meta()), r.type());
        }));
        operations.put("operation.any.flatMap", new OperationBinding(1, null, false, (r, v, m) -> {
            Fixtures.FlatMapFixture f = fixture(v.get(0), Fixtures.FlatMapFixture.class);
            requireType(f.input(), r.type());
            return new BoundDecoder(of(r).flatMap(f.fn()), f.output());
        }));
    }

    // --- Encoders ---

    @SuppressWarnings("unchecked")
    private void bindEncoders() {
        encoders.put("encoder.string", a -> {
            requireArity("string", 0, a);
            return new BoundEncoder((Encoder<Object, ?>) (Encoder<?, ?>) ObjectEncoders.string(), STRING);
        });
        encoders.put("encoder.object", a -> {
            requireArity("object", 1, a);
            List<PropertyEncoder<Object>> props = new ArrayList<>();
            List<SpecType> inputs = new ArrayList<>();
            for (JsonNode propertyForm : a.get(0)) {
                BoundProperty p = property(propertyForm);
                props.add(p.property());
                inputs.add(p.input());
            }
            @SuppressWarnings("rawtypes")
            PropertyEncoder<Object>[] array = props.toArray(new PropertyEncoder[0]);
            return new BoundEncoder((Encoder<Object, ?>) (Encoder<?, ?>) MapEncoders.object(array), common(inputs));
        });
        properties.put("property.propertyWithDefault", a -> {
            requireArity("propertyWithDefault", 4, a);
            String name = string(a.get(0));
            Fixtures.GetterFixture getter = fixture(a.get(1), Fixtures.GetterFixture.class);
            BoundEncoder value = encoder(a.get(2));
            Object fallback = ValueCodec.materialize(value.input(), a.get(3));
            PropertyEncoder<Object> property = MapEncoders.propertyWithDefault(
                    name, getter.fn(), (Encoder<Object, Object>) (Encoder<?, ?>) value.encoder(), fallback);
            return new BoundProperty(property, getter.input().apply(value.input()));
        });
    }

    private BoundProperty property(JsonNode form) {
        String id = "property." + formName(form);
        Property p = properties.get(id);
        if (p == null) {
            throw new UnboundFeature(id);
        }
        return p.build(slice(form, 1, form.size()));
    }

    private static void requireArity(String name, int arity, List<JsonNode> args) {
        if (args.size() != arity) {
            throw new IllegalArgumentException(name + " takes " + arity + " arguments, not " + args.size());
        }
    }

    // --- Helpers ---

    private void constructor(String name, int arity, boolean message, Constructor build) {
        constructors.put("decoder." + name, new ConstructorBinding(arity, message, build));
    }

    private OperationBinding typedOp(String id, Class<?> cls, int arity, TypedOperation<Object> op) {
        return new OperationBinding(arity, null, true, (r, v, m) -> {
            Object receiver = typed(cls, r, id);
            return new BoundDecoder(cast(op.apply(receiver, v, r.type(), m)), r.type());
        });
    }

    /** A decoder a typed operation gave, which reads the same input as its receiver. */
    @SuppressWarnings("unchecked")
    private static Decoder<JsonNode, ?> cast(Decoder<?, ?> decoder) {
        return (Decoder<JsonNode, ?>) decoder;
    }

    /**
     * The receiver as the decoder class raoh-java gives the operation on.
     *
     * @throws IllegalStateException if raoh-java gives another class here, so that the operation
     *                               cannot be called on it
     */
    private static <D> D typed(Class<D> cls, BoundDecoder receiver, String id) {
        if (!cls.isInstance(receiver.decoder())) {
            throw new IllegalStateException(id + " is a method of " + cls.getSimpleName()
                    + ", but raoh-java gives a " + receiver.decoder().getClass().getName() + " here");
        }
        return cls.cast(receiver.decoder());
    }

    @SuppressWarnings("unchecked")
    private static Decoder<JsonNode, Object> of(BoundDecoder bound) {
        return (Decoder<JsonNode, Object>) bound.decoder();
    }

    @SuppressWarnings("unchecked")
    private static Decoder<JsonNode, String> stringDecoder(BoundDecoder bound) {
        requireType(STRING, bound.type());
        return (Decoder<JsonNode, String>) bound.decoder();
    }

    private <F extends Fixtures.Fixture> F fixture(JsonNode name, Class<F> kind) {
        String id = "fixture." + string(name);
        Fixtures.Fixture f = Fixtures.ALL.get(id);
        if (f == null) {
            throw new UnboundFeature(id);
        }
        if (!kind.isInstance(f)) {
            throw new IllegalArgumentException(id + " is not a " + kind.getSimpleName());
        }
        return kind.cast(f);
    }

    private static Object value(SpecType type, JsonNode observation) {
        return nonNull(ValueCodec.materialize(type, observation));
    }

    /** A list argument whose elements have the given type, none of them null. */
    private static <T> List<T> listOf(SpecType elementType, JsonNode observation, Class<T> element) {
        List<T> values = new ArrayList<>();
        for (Object v : (List<?>) ValueCodec.materialize(new SpecType.ListOf(elementType), observation)) {
            values.add(element.cast(nonNull(v)));
        }
        return values;
    }

    @SuppressWarnings("rawtypes")
    private static Comparable comparable(SpecType type, JsonNode observation) {
        return (Comparable) value(type, observation);
    }

    private static Object nonNull(@Nullable Object value) {
        if (value == null) {
            throw new IllegalArgumentException("raoh-java takes no null here");
        }
        return value;
    }

    private static String string(JsonNode node) {
        return (String) value(STRING, node);
    }

    private static int int32(JsonNode node) {
        return (Integer) value(INT32, node);
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(JsonNode node) {
        return (List<String>) value(LIST_OF_STRING, node);
    }

    private static SpecType common(List<SpecType> types) {
        if (types.isEmpty() || types.stream().distinct().count() != 1) {
            throw new IllegalArgumentException("the decoders give different types: " + types);
        }
        return types.getFirst();
    }

    private static void requireType(SpecType expected, SpecType actual) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("expected " + expected + ", got " + actual);
        }
    }

    private static IllegalStateException unexpected(Object decoder) {
        return new IllegalStateException("unexpected decoder " + decoder.getClass().getName());
    }

    private static String formName(JsonNode form) {
        if (!form.isArray() || form.isEmpty() || !form.get(0).isString()) {
            throw new IllegalArgumentException("not a form: " + form);
        }
        return form.get(0).stringValue();
    }

    private static List<JsonNode> slice(JsonNode array, int from, int to) {
        List<JsonNode> nodes = new ArrayList<>();
        for (int i = from; i < to; i++) {
            nodes.add(array.get(i));
        }
        return nodes;
    }
}
