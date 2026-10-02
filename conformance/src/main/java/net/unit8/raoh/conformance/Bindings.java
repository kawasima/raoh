package net.unit8.raoh.conformance;

import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Issues;
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
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Binds the forms of the specification's decoder language ({@code decoder-language.md}) to
 * raoh-java's API and builds the decoder or encoder a case names.
 *
 * <p>Every feature the runner binds is an entry of one of the tables here, and
 * {@link #boundFeatures()} lists exactly those entries, with the {@code .message} facet of every
 * form whose binding takes a message. What the runner can run and what it says it binds therefore
 * come from the same place.
 *
 * <p>A form is built in two phases. Planning walks the whole form, finds the binding of every
 * feature it needs and the type of every part, and calls nothing of raoh-java's; a feature no table
 * has throws {@link UnboundFeature} there. Only a form that planned completely is built: its value
 * arguments are read and raoh-java's decoders and encoders are constructed. So whether a case runs
 * depends on its features alone, never on how far building got before raoh-java refused something:
 * a case that needs an unbound feature is never run, and one that is run records whatever raoh-java
 * did. Planning itself fails otherwise only for a form that does not type-check, which the verifier
 * rejects, or for a defect of the runner.
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

        /**
         * Creates the exception. It has no stack trace: it says which feature a form needs, not
         * where the runner found that out.
         *
         * @param feature the feature's ID
         */
        UnboundFeature(String feature) {
            super(feature, null, false, false);
            this.feature = feature;
        }

        /**
         * The feature.
         *
         * @return its ID
         */
        String feature() {
            return feature;
        }
    }

    /**
     * A part of a form that has been planned: its type, and how to build it once the whole form
     * has been planned.
     *
     * @param type  the type of the part: of a decoder's result, or of what an encoder or property
     *              takes
     * @param build builds raoh-java's object for the part
     * @param <J>   the class of that object
     */
    private record Plan<J>(SpecType type, Supplier<J> build) {
    }

    private interface Constructor {
        Plan<Decoder<JsonNode, ?>> plan(List<JsonNode> args, @Nullable String message);
    }

    /**
     * A constructor: its required arguments, whether a message may follow them, and its planning.
     *
     * @param arity   the number of required arguments
     * @param message whether it takes a message
     * @param plan    plans the decoder
     */
    private record ConstructorBinding(int arity, boolean message, Constructor plan) {
    }

    private interface Operation {
        Plan<Decoder<JsonNode, ?>> plan(Plan<Decoder<JsonNode, ?>> receiver, List<JsonNode> values,
                                         @Nullable String message);
    }

    /**
     * An operation on one kind of receiver.
     *
     * @param arity       the number of value arguments
     * @param defaultLast the value a missing last argument stands for, when it is optional
     * @param message     whether a message may follow the values
     * @param plan        plans the decoder
     */
    private record OperationBinding(int arity, @Nullable JsonNode defaultLast, boolean message, Operation plan) {
    }

    /**
     * A field of an object, as a component of raoh-java's {@code combine}.
     *
     * @param part   the component
     * @param member the member it reads, or {@code null} for one that reads the whole input
     */
    private record Part(CombinePart<JsonNode, ?> part, @Nullable String member) {
    }

    private interface Field {
        Plan<Part> plan(@Nullable JsonNode name, Plan<Decoder<JsonNode, ?>> decoder);
    }

    private interface EncoderBinding {
        Plan<Encoder<Object, ?>> plan(List<JsonNode> args);
    }

    private interface Property {
        Plan<PropertyEncoder<Object>> plan(List<JsonNode> args);
    }

    /**
     * An operation on a receiver of raoh-java's decoder class {@code D}, giving another decoder.
     *
     * @param <D> the receiver's class
     */
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

    /** Creates the tables of every feature the runner binds. */
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
     * Plans the decoder a decoder form names, then builds it.
     *
     * @param form the form
     * @return the decoder and its result type
     * @throws UnboundFeature if the form needs a feature the runner does not bind, before anything
     *                        of raoh-java's is called
     */
    BoundDecoder decoder(JsonNode form) {
        Plan<Decoder<JsonNode, ?>> plan = planDecoder(form);
        return new BoundDecoder(plan.build().get(), plan.type());
    }

    /**
     * Plans the encoder an encoder form names, then builds it.
     *
     * @param form the form
     * @return the encoder and its input type
     * @throws UnboundFeature if the form needs a feature the runner does not bind, before anything
     *                        of raoh-java's is called
     */
    BoundEncoder encoder(JsonNode form) {
        Plan<Encoder<Object, ?>> plan = planEncoder(form);
        return new BoundEncoder(plan.build().get(), plan.type());
    }

    private Plan<Decoder<JsonNode, ?>> planDecoder(JsonNode form) {
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
        Plan<Decoder<JsonNode, ?>> plan = c.plan().plan(args, message);
        for (int i = next; i < form.size(); i++) {
            plan = planOperation(plan, form.get(i));
        }
        return plan;
    }

    private Plan<Decoder<JsonNode, ?>> planOperation(Plan<Decoder<JsonNode, ?>> receiver, JsonNode form) {
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
        return o.plan().plan(receiver, args, message);
    }

    private Plan<Encoder<Object, ?>> planEncoder(JsonNode form) {
        String id = "encoder." + formName(form);
        EncoderBinding e = encoders.get(id);
        if (e == null) {
            throw new UnboundFeature(id);
        }
        return e.plan(slice(form, 1, form.size()));
    }

    private Plan<Part> planField(JsonNode form) {
        String id = "field." + formName(form);
        Field f = fields.get(id);
        if (f == null) {
            throw new UnboundFeature(id);
        }
        // A field form is [kind, name, decoder], or [kind, decoder] for one that reads no member.
        return switch (form.size()) {
            case 3 -> f.plan(form.get(1), planDecoder(form.get(2)));
            case 2 -> f.plan(null, planDecoder(form.get(1)));
            default -> throw new IllegalArgumentException("not a field form: " + form);
        };
    }

    private Plan<PropertyEncoder<Object>> planProperty(JsonNode form) {
        String id = "property." + formName(form);
        Property p = properties.get(id);
        if (p == null) {
            throw new UnboundFeature(id);
        }
        return p.plan(slice(form, 1, form.size()));
    }

    // --- Constructors ---

    private void bindScalarConstructors() {
        constructor("string", 0, false, (a, m) -> new Plan<>(STRING, JsonDecoders::string));
        constructor("int", 0, false, (a, m) -> new Plan<>(INT32, JsonDecoders::int_));
        constructor("long", 0, false, (a, m) -> new Plan<>(SpecType.Scalar.INT64, JsonDecoders::long_));
        constructor("float", 0, false, (a, m) -> new Plan<>(SpecType.Scalar.FLOAT32, JsonDecoders::float_));
        constructor("double", 0, false, (a, m) -> new Plan<>(SpecType.Scalar.FLOAT64, JsonDecoders::double_));
        constructor("decimal", 0, false, (a, m) -> new Plan<>(SpecType.Scalar.DECIMAL, JsonDecoders::decimal));
        constructor("bool", 0, false, (a, m) -> new Plan<>(SpecType.Scalar.BOOL, JsonDecoders::bool));
    }

    private void bindStructuralConstructors() {
        constructor("list", 1, false, (a, m) -> {
            var element = planDecoder(a.get(0));
            return new Plan<>(new SpecType.ListOf(element.type()), () -> JsonDecoders.list(build(element)));
        });
        constructor("dict", 1, false, (a, m) -> {
            var value = planDecoder(a.get(0));
            return new Plan<>(new SpecType.MapOf(value.type()), () -> JsonDecoders.map(build(value)));
        });
        constructor("object", 1, false, (a, m) -> object(a.get(0), false));
        constructor("strictObject", 1, false, (a, m) -> object(a.get(0), true));
        constructor("strict", 2, false, (a, m) -> {
            var inner = planDecoder(a.get(0));
            return new Plan<>(inner.type(),
                    () -> JsonDecoders.strict(build(inner), new LinkedHashSet<>(strings(a.get(1)))));
        });
        constructor("nullable", 1, false, (a, m) -> {
            var inner = planDecoder(a.get(0));
            return new Plan<>(new SpecType.NullableOf(inner.type()), () -> JsonDecoders.nullable(build(inner)));
        });
        constructor("enum", 2, true, (a, m) -> {
            // The symbols are the type, so they are read while planning.
            List<String> symbols = strings(a.get(0));
            var string = planString(a.get(1));
            return new Plan<>(new SpecType.Symbol(symbols),
                    () -> enumOf(Symbols.enumOf(symbols), buildString(string), m));
        });
        constructor("literal", 2, true, (a, m) -> {
            var string = planString(a.get(1));
            return new Plan<>(STRING, () -> Decoders.literal(string(a.get(0)), buildString(string), m));
        });
        constructor("discriminate", 2, false, (a, m) -> {
            Map<String, Plan<Decoder<JsonNode, ?>>> variants = planVariants(a.get(1));
            return new Plan<>(common(variants.values()),
                    () -> JsonDecoders.discriminate(string(a.get(0)), buildVariants(variants)));
        });
        constructor("discriminateBy", 3, false, (a, m) -> {
            var tag = planString(a.get(1));
            Map<String, Plan<Decoder<JsonNode, ?>>> variants = planVariants(a.get(2));
            return new Plan<>(common(variants.values()),
                    () -> Decoders.discriminate(string(a.get(0)), buildString(tag), buildVariants(variants)));
        });
        constructor("oneOf", 1, false, (a, m) -> {
            List<Plan<Decoder<JsonNode, ?>>> candidates = new ArrayList<>();
            for (JsonNode candidate : a.get(0)) {
                candidates.add(planDecoder(candidate));
            }
            return new Plan<>(common(candidates), () -> {
                @SuppressWarnings("unchecked")
                Decoder<JsonNode, Object>[] decoders = candidates.stream().map(Bindings::build).toArray(Decoder[]::new);
                return Decoders.oneOf(decoders);
            });
        });
        constructor("withDefault", 2, false, (a, m) -> {
            var inner = planDecoder(a.get(0));
            return new Plan<>(inner.type(),
                    () -> JsonDecoders.withDefault(build(inner), ValueCodec.materialize(inner.type(), a.get(1))));
        });
        constructor("recover", 2, false, (a, m) -> {
            var inner = planDecoder(a.get(0));
            return new Plan<>(inner.type(), () -> {
                Object fallback = ValueCodec.materialize(inner.type(), a.get(1));
                return Decoders.recover(build(inner), fallback);
            });
        });
        constructor("recoverWith", 2, false, (a, m) -> {
            var inner = planDecoder(a.get(0));
            Fixtures.RecoverFixture recovery = fixture(a.get(1), Fixtures.RecoverFixture.class);
            requireType(recovery.output(), inner.type());
            return new Plan<>(inner.type(), () -> recoverWith(build(inner), recovery.fn()));
        });
    }

    /**
     * {@code object} and {@code strictObject}: every object is one {@code combine} over a list of
     * components, whatever the number of fields, giving the product of their values.
     *
     * @param fieldForms the field forms
     * @param strict     whether a member no field reads is refused
     * @return the plan
     */
    private Plan<Decoder<JsonNode, ?>> object(JsonNode fieldForms, boolean strict) {
        List<Plan<Part>> fieldPlans = new ArrayList<>();
        List<SpecType> types = new ArrayList<>();
        for (JsonNode fieldForm : fieldForms) {
            if (strict && fieldForm.size() == 2) {
                throw new IllegalArgumentException("a strictObject cannot have a field that reads no member");
            }
            Plan<Part> field = planField(fieldForm);
            fieldPlans.add(field);
            types.add(field.type());
        }
        return new Plan<>(new SpecType.Product(types), () -> {
            List<CombinePart<JsonNode, ?>> parts = new ArrayList<>();
            Set<String> members = new LinkedHashSet<>();
            for (Plan<Part> field : fieldPlans) {
                Part part = field.build().get();
                parts.add(part.part());
                if (part.member() != null) {
                    members.add(part.member());
                }
            }
            Decoder<JsonNode, List<@Nullable Object>> product = JsonDecoders.combine(parts).map(ValueCodec::product);
            return strict ? JsonDecoders.strict(product, members) : product;
        });
    }

    private Map<String, Plan<Decoder<JsonNode, ?>>> planVariants(JsonNode variantForms) {
        Map<String, Plan<Decoder<JsonNode, ?>>> variants = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> e : variantForms.properties()) {
            variants.put(e.getKey(), planDecoder(e.getValue()));
        }
        return variants;
    }

    private static Map<String, Decoder<JsonNode, ?>> buildVariants(Map<String, Plan<Decoder<JsonNode, ?>>> variants) {
        Map<String, Decoder<JsonNode, ?>> decoders = new LinkedHashMap<>();
        variants.forEach((tag, plan) -> decoders.put(tag, plan.build().get()));
        return decoders;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Decoder<JsonNode, ?> enumOf(Class<? extends Enum<?>> cls, Decoder<JsonNode, String> string,
                                               @Nullable String message) {
        return Decoders.enumOf((Class) cls, string, message);
    }

    /**
     * {@code recoverWith}, typed so that the function cannot be read as the fallback value of
     * {@link Decoders#recover(Decoder, Object)}.
     *
     * @param inner the decoder
     * @param fn    the function from the issues of a failure
     * @param <T>   the type of the decoder's result
     * @return the recovering decoder
     */
    private static <T> Decoder<JsonNode, T> recoverWith(Decoder<JsonNode, T> inner, Function<Issues, T> fn) {
        return Decoders.recover(inner, fn);
    }

    // --- Fields ---

    private void bindFields() {
        fields.put("field.field", (name, d) -> new Plan<>(d.type(), () -> {
            String member = string(member(name));
            return new Part(JsonDecoders.field(member, build(d)), member);
        }));
        fields.put("field.optionalField", (name, d) -> new Plan<>(new SpecType.OptionalOf(d.type()), () -> {
            String member = string(member(name));
            return new Part(JsonDecoders.optionalField(member, build(d)), member);
        }));
        fields.put("field.optionalNullableField", (name, d) -> new Plan<>(new SpecType.PresenceOf(d.type()), () -> {
            String member = string(member(name));
            return new Part(JsonDecoders.optionalNullableField(member, build(d)), member);
        }));
        fields.put("field.flat", (name, d) -> new Plan<>(d.type(), () -> new Part(JsonDecoders.flat(build(d)), null)));
    }

    private static JsonNode member(@Nullable JsonNode name) {
        if (name == null) {
            throw new IllegalArgumentException("this field reads a member and needs its name");
        }
        return name;
    }

    // --- Operations on strings ---

    private void bindStringOperations() {
        Class<StringDecoder<JsonNode>> s = decoderClass(StringDecoder.class);
        String kind = STRING.kind();
        op(kind, "trim", s, 0, false, (d, v, t, m) -> d.trim());
        op(kind, "toLowerCase", s, 0, false, (d, v, t, m) -> d.toLowerCase());
        op(kind, "toUpperCase", s, 0, false, (d, v, t, m) -> d.toUpperCase());
        // normalize's one argument is optional and stands for NFC when left out.
        op(kind, "normalize", s, 1, JsonNodeFactory.instance.stringNode("NFC"), false, UnaryOperator.identity(),
                (d, v, t, m) -> d.normalize(Normalizer.Form.valueOf(string(v.get(0)))));
        op(kind, "nonBlank", s, 0, true, (d, v, t, m) -> d.nonBlank(m));
        op(kind, "minLength", s, 1, true, (d, v, t, m) -> d.minLength(int32(v.get(0)), m));
        op(kind, "maxLength", s, 1, true, (d, v, t, m) -> d.maxLength(int32(v.get(0)), m));
        op(kind, "fixedLength", s, 1, true, (d, v, t, m) -> d.fixedLength(int32(v.get(0)), m));
        op(kind, "oneOf", s, 1, true, (d, v, t, m) -> d.oneOf(strings(v.get(0)), m));
        op(kind, "startsWith", s, 1, true, (d, v, t, m) -> d.startsWith(string(v.get(0)), m));
        op(kind, "endsWith", s, 1, true, (d, v, t, m) -> d.endsWith(string(v.get(0)), m));
        op(kind, "includes", s, 1, true, (d, v, t, m) -> d.includes(string(v.get(0)), m));
        op(kind, "pattern", s, 1, true, (d, v, t, m) -> d.pattern(string(v.get(0)), ErrorCodes.INVALID_FORMAT, m));
        op(kind, "email", s, 0, true, (d, v, t, m) -> d.email(m));
        op(kind, "ipv4", s, 0, true, (d, v, t, m) -> d.ipv4(m));
        op(kind, "ipv6", s, 0, true, (d, v, t, m) -> d.ipv6(m));
        op(kind, "ip", s, 0, true, (d, v, t, m) -> d.ip(m));
        op(kind, "ulid", s, 0, true, (d, v, t, m) -> d.ulid(m));
        op(kind, "cuid", s, 0, true, (d, v, t, m) -> d.cuid(m));
        conversion("uuid", SpecType.Scalar.UUID, (d, v, t, m) -> d.uuid(m));
        conversion("url", SpecType.Scalar.URI, (d, v, t, m) -> d.url(m));
        conversion("uri", SpecType.Scalar.URI, (d, v, t, m) -> d.uri(m));
        conversion("toInt", INT32, (d, v, t, m) -> d.toInt(m));
        conversion("toLong", SpecType.Scalar.INT64, (d, v, t, m) -> d.toLong(m));
        conversion("toDecimal", SpecType.Scalar.DECIMAL, (d, v, t, m) -> d.toDecimal(m));
        conversion("toBool", SpecType.Scalar.BOOL, (d, v, t, m) -> d.toBool(m));
        conversion("iso8601", SpecType.Scalar.INSTANT, (d, v, t, m) -> d.iso8601(m));
        conversion("date", SpecType.Scalar.DATE, (d, v, t, m) -> d.date(m));
        conversion("time", SpecType.Scalar.TIME, (d, v, t, m) -> d.time(m));
        conversion("dateTime", SpecType.Scalar.DATETIME, (d, v, t, m) -> d.dateTime(m));
        conversion("offsetDateTime", SpecType.Scalar.OFFSET_DATETIME, (d, v, t, m) -> d.offsetDateTime(m));
    }

    private void conversion(String name, SpecType result, TypedOperation<StringDecoder<JsonNode>> op) {
        op(STRING.kind(), name, decoderClass(StringDecoder.class), 0, null, true, t -> result, op);
    }

    // --- Operations on numbers ---

    private interface Bound<D> {
        Decoder<?, ?> apply(D receiver, Object bound, @Nullable String message);
    }

    private interface Range<D> {
        Decoder<?, ?> apply(D receiver, Object min, Object max, @Nullable String message);
    }

    private interface Sign<D> {
        Decoder<?, ?> apply(D receiver, @Nullable String message);
    }

    /**
     * How the bounds every numeric receiver has are called on one of raoh-java's numeric decoder
     * classes. They share no interface, so each class says once how it is called, and the bounds
     * are registered from that for every class alike.
     *
     * @param kind        the receiver kind
     * @param cls         the decoder class
     * @param min         {@code min}
     * @param max         {@code max}
     * @param range       {@code range}
     * @param positive    {@code positive}
     * @param negative    {@code negative}
     * @param nonNegative {@code nonNegative}
     * @param nonPositive {@code nonPositive}
     * @param <D>         the decoder class
     */
    private record Numeric<D>(SpecType.Scalar kind, Class<D> cls, Bound<D> min, Bound<D> max, Range<D> range,
                              Sign<D> positive, Sign<D> negative, Sign<D> nonNegative, Sign<D> nonPositive) {
    }

    private void bindNumericOperations() {
        Numeric<IntDecoder<JsonNode>> int32 = new Numeric<>(SpecType.Scalar.INT32, decoderClass(IntDecoder.class),
                (d, b, m) -> d.min((Integer) b, m), (d, b, m) -> d.max((Integer) b, m),
                (d, lo, hi, m) -> d.range((Integer) lo, (Integer) hi, m),
                IntDecoder::positive, IntDecoder::negative, IntDecoder::nonNegative, IntDecoder::nonPositive);
        Numeric<LongDecoder<JsonNode>> int64 = new Numeric<>(SpecType.Scalar.INT64, decoderClass(LongDecoder.class),
                (d, b, m) -> d.min((Long) b, m), (d, b, m) -> d.max((Long) b, m),
                (d, lo, hi, m) -> d.range((Long) lo, (Long) hi, m),
                LongDecoder::positive, LongDecoder::negative, LongDecoder::nonNegative, LongDecoder::nonPositive);
        Numeric<FloatDecoder<JsonNode>> float32 = new Numeric<>(SpecType.Scalar.FLOAT32, decoderClass(FloatDecoder.class),
                (d, b, m) -> d.min((Float) b, m), (d, b, m) -> d.max((Float) b, m),
                (d, lo, hi, m) -> d.range((Float) lo, (Float) hi, m),
                FloatDecoder::positive, FloatDecoder::negative, FloatDecoder::nonNegative, FloatDecoder::nonPositive);
        Numeric<DoubleDecoder<JsonNode>> float64 = new Numeric<>(SpecType.Scalar.FLOAT64, decoderClass(DoubleDecoder.class),
                (d, b, m) -> d.min((Double) b, m), (d, b, m) -> d.max((Double) b, m),
                (d, lo, hi, m) -> d.range((Double) lo, (Double) hi, m),
                DoubleDecoder::positive, DoubleDecoder::negative, DoubleDecoder::nonNegative, DoubleDecoder::nonPositive);
        Numeric<DecimalDecoder<JsonNode>> decimal = new Numeric<>(SpecType.Scalar.DECIMAL, decoderClass(DecimalDecoder.class),
                (d, b, m) -> d.min((BigDecimal) b, m), (d, b, m) -> d.max((BigDecimal) b, m),
                (d, lo, hi, m) -> d.range((BigDecimal) lo, (BigDecimal) hi, m),
                DecimalDecoder::positive, DecimalDecoder::negative, DecimalDecoder::nonNegative, DecimalDecoder::nonPositive);
        for (Numeric<?> n : List.of(int32, int64, float32, float64, decimal)) {
            bindNumeric(n);
        }

        op("int32", "oneOf", int32.cls(), 1, true, (d, v, t, m) -> d.oneOf(listOf(t, v.get(0), Integer.class), m));
        op("int32", "multipleOf", int32.cls(), 1, true, (d, v, t, m) -> d.multipleOf((Integer) value(t, v.get(0)), m));
        op("int64", "oneOf", int64.cls(), 1, true, (d, v, t, m) -> d.oneOf(listOf(t, v.get(0), Long.class), m));
        op("int64", "multipleOf", int64.cls(), 1, true, (d, v, t, m) -> d.multipleOf((Long) value(t, v.get(0)), m));
        op("float32", "oneOf", float32.cls(), 1, true, (d, v, t, m) -> d.oneOf(listOf(t, v.get(0), Float.class), m));
        op("float64", "oneOf", float64.cls(), 1, true, (d, v, t, m) -> d.oneOf(listOf(t, v.get(0), Double.class), m));
        op("decimal", "multipleOf", decimal.cls(), 1, true,
                (d, v, t, m) -> d.multipleOf((BigDecimal) value(t, v.get(0)), m));
        op("decimal", "scale", decimal.cls(), 1, true, (d, v, t, m) -> d.scale(int32(v.get(0)), m));
    }

    private <D> void bindNumeric(Numeric<D> n) {
        String kind = n.kind().kind();
        op(kind, "min", n.cls(), 1, true, (d, v, t, m) -> n.min().apply(d, value(t, v.get(0)), m));
        op(kind, "max", n.cls(), 1, true, (d, v, t, m) -> n.max().apply(d, value(t, v.get(0)), m));
        op(kind, "range", n.cls(), 2, true,
                (d, v, t, m) -> n.range().apply(d, value(t, v.get(0)), value(t, v.get(1)), m));
        op(kind, "positive", n.cls(), 0, true, (d, v, t, m) -> n.positive().apply(d, m));
        op(kind, "negative", n.cls(), 0, true, (d, v, t, m) -> n.negative().apply(d, m));
        op(kind, "nonNegative", n.cls(), 0, true, (d, v, t, m) -> n.nonNegative().apply(d, m));
        op(kind, "nonPositive", n.cls(), 0, true, (d, v, t, m) -> n.nonPositive().apply(d, m));
    }

    // --- Operations on booleans, lists, maps and temporal values ---

    private void bindBoolOperations() {
        Class<BoolDecoder<JsonNode>> bool = decoderClass(BoolDecoder.class);
        op("bool", "isTrue", bool, 0, true, (d, v, t, m) -> d.isTrue(m));
    }

    private interface Size<D> {
        Decoder<?, ?> apply(D receiver, int size, @Nullable String message);
    }

    /**
     * How the size checks lists and maps both have are called on one of raoh-java's decoder
     * classes, as {@link Numeric} does for the bounds.
     *
     * @param kind      the receiver kind
     * @param cls       the decoder class
     * @param nonempty  {@code nonempty}
     * @param minSize   {@code minSize}
     * @param maxSize   {@code maxSize}
     * @param fixedSize {@code fixedSize}
     * @param <D>       the decoder class
     */
    private record Sized<D>(String kind, Class<D> cls, Sign<D> nonempty, Size<D> minSize, Size<D> maxSize,
                            Size<D> fixedSize) {
    }

    private void bindCollectionOperations() {
        Class<ListDecoder<JsonNode, Object>> list = decoderClass(ListDecoder.class);
        Class<RecordDecoder<JsonNode, Object>> map = decoderClass(RecordDecoder.class);
        bindSized(new Sized<>("list", list, ListDecoder::nonempty, ListDecoder::minSize, ListDecoder::maxSize,
                ListDecoder::fixedSize));
        bindSized(new Sized<>("map", map, RecordDecoder::nonempty, RecordDecoder::minSize, RecordDecoder::maxSize,
                RecordDecoder::fixedSize));

        op("list", "unique", list, 0, true, (d, v, t, m) -> d.unique(m));
        op("list", "contains", list, 1, true, (d, v, t, m) -> d.contains(value(element(t), v.get(0)), m));
        op("list", "containsAll", list, 1, true,
                (d, v, t, m) -> d.containsAllOf(listOf(element(t), v.get(0), Object.class), m));
        op("list", "toSet", list, 0, null, false, t -> new SpecType.SetOf(element(t)), (d, v, t, m) -> d.toSet());
    }

    private <D> void bindSized(Sized<D> s) {
        op(s.kind(), "nonempty", s.cls(), 0, true, (d, v, t, m) -> s.nonempty().apply(d, m));
        op(s.kind(), "minSize", s.cls(), 1, true, (d, v, t, m) -> s.minSize().apply(d, int32(v.get(0)), m));
        op(s.kind(), "maxSize", s.cls(), 1, true, (d, v, t, m) -> s.maxSize().apply(d, int32(v.get(0)), m));
        op(s.kind(), "fixedSize", s.cls(), 1, true, (d, v, t, m) -> s.fixedSize().apply(d, int32(v.get(0)), m));
    }

    /**
     * The temporal bounds. A bound is read at the receiver's type, so its class is the one the
     * receiver's {@link TemporalDecoder} compares.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void bindTemporalOperations() {
        Class<TemporalDecoder> temporal = TemporalDecoder.class;
        for (SpecType.Scalar kind : List.of(SpecType.Scalar.INSTANT, SpecType.Scalar.DATE, SpecType.Scalar.TIME,
                SpecType.Scalar.DATETIME, SpecType.Scalar.OFFSET_DATETIME)) {
            op(kind.kind(), "before", temporal, 1, true,
                    (d, v, t, m) -> d.before((Comparable) value(t, v.get(0)), m));
            op(kind.kind(), "after", temporal, 1, true,
                    (d, v, t, m) -> d.after((Comparable) value(t, v.get(0)), m));
            op(kind.kind(), "between", temporal, 2, true,
                    (d, v, t, m) -> d.between((Comparable) value(t, v.get(0)), (Comparable) value(t, v.get(1)), m));
        }
    }

    // --- Operations on every type ---

    private void bindAnyOperations() {
        operations.put("operation.any.map", new OperationBinding(1, null, false, (r, v, m) -> {
            Fixtures.MapFixture f = fixture(v.get(0), Fixtures.MapFixture.class);
            return new Plan<>(f.output().apply(r.type()), () -> build(r).map(f.fn()));
        }));
        operations.put("operation.any.refine", new OperationBinding(1, null, false, (r, v, m) -> {
            Fixtures.RefineFixture f = fixture(v.get(0), Fixtures.RefineFixture.class);
            requireType(f.input(), r.type());
            return new Plan<>(r.type(), () -> build(r).refine(f.holds(), f.code(), f.message(), f.meta()));
        }));
        operations.put("operation.any.flatMap", new OperationBinding(1, null, false, (r, v, m) -> {
            Fixtures.FlatMapFixture f = fixture(v.get(0), Fixtures.FlatMapFixture.class);
            requireType(f.input(), r.type());
            return new Plan<>(f.output(), () -> build(r).flatMap(f.fn()));
        }));
    }

    // --- Encoders ---

    @SuppressWarnings("unchecked")
    private void bindEncoders() {
        encoders.put("encoder.string", a -> {
            requireArity("string", 0, a);
            return new Plan<>(STRING, () -> (Encoder<Object, ?>) (Encoder<?, ?>) ObjectEncoders.string());
        });
        encoders.put("encoder.object", a -> {
            requireArity("object", 1, a);
            List<Plan<PropertyEncoder<Object>>> props = new ArrayList<>();
            for (JsonNode propertyForm : a.get(0)) {
                props.add(planProperty(propertyForm));
            }
            return new Plan<>(common(props), () -> {
                @SuppressWarnings("rawtypes")
                PropertyEncoder<Object>[] array = props.stream().map(p -> p.build().get())
                        .toArray(PropertyEncoder[]::new);
                return (Encoder<Object, ?>) (Encoder<?, ?>) MapEncoders.object(array);
            });
        });
        properties.put("property.propertyWithDefault", a -> {
            requireArity("propertyWithDefault", 4, a);
            Fixtures.GetterFixture getter = fixture(a.get(1), Fixtures.GetterFixture.class);
            Plan<Encoder<Object, ?>> value = planEncoder(a.get(2));
            return new Plan<>(getter.input().apply(value.type()), () -> MapEncoders.propertyWithDefault(
                    string(a.get(0)), getter.fn(), (Encoder<Object, Object>) value.build().get(),
                    ValueCodec.materialize(value.type(), a.get(3))));
        });
    }

    private static void requireArity(String name, int arity, List<JsonNode> args) {
        if (args.size() != arity) {
            throw new IllegalArgumentException(name + " takes " + arity + " arguments, not " + args.size());
        }
    }

    // --- Helpers ---

    private void constructor(String name, int arity, boolean message, Constructor plan) {
        constructors.put("decoder." + name, new ConstructorBinding(arity, message, plan));
    }

    private <D> void op(String kind, String name, Class<D> cls, int arity, boolean message, TypedOperation<D> op) {
        op(kind, name, cls, arity, null, message, UnaryOperator.identity(), op);
    }

    /**
     * Registers an operation called on a receiver of one of raoh-java's decoder classes.
     *
     * @param kind        the receiver kind
     * @param name        the operation's name
     * @param cls         the decoder class the operation is a method of
     * @param arity       the number of value arguments
     * @param defaultLast the value a missing last argument stands for, or {@code null}
     * @param message     whether it takes a message
     * @param result      the result type for the receiver's type
     * @param op          calls the method
     * @param <D>         the decoder class
     */
    private <D> void op(String kind, String name, Class<D> cls, int arity, @Nullable JsonNode defaultLast,
                        boolean message, UnaryOperator<SpecType> result, TypedOperation<D> op) {
        String id = "operation." + kind + "." + name;
        operations.put(id, new OperationBinding(arity, defaultLast, message, (r, v, m) -> new Plan<>(
                result.apply(r.type()),
                () -> cast(op.apply(typed(cls, r.build().get(), id), v, r.type(), m)))));
    }

    /**
     * A decoder a typed operation gave, which reads the same input as its receiver.
     *
     * @param decoder the decoder
     * @return the same decoder
     */
    @SuppressWarnings("unchecked")
    private static Decoder<JsonNode, ?> cast(Decoder<?, ?> decoder) {
        return (Decoder<JsonNode, ?>) decoder;
    }

    /**
     * One of raoh-java's generic decoder classes, at the type arguments the runner uses.
     *
     * @param cls the raw class
     * @param <D> the parameterized class
     * @return the same class
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <D> Class<D> decoderClass(Class cls) {
        return (Class<D>) cls;
    }

    /**
     * The receiver as the decoder class raoh-java gives the operation on.
     *
     * @param cls     the class
     * @param decoder the receiver
     * @param id      the operation's feature
     * @param <D>     the class
     * @return the receiver
     * @throws IllegalStateException if raoh-java gives another class here, so that the operation
     *                               cannot be called on it
     */
    private static <D> D typed(Class<D> cls, Decoder<JsonNode, ?> decoder, String id) {
        if (!cls.isInstance(decoder)) {
            throw new IllegalStateException(id + " is a method of " + cls.getSimpleName()
                    + ", but raoh-java gives a " + decoder.getClass().getName() + " here");
        }
        return cls.cast(decoder);
    }

    @SuppressWarnings("unchecked")
    private static Decoder<JsonNode, Object> build(Plan<Decoder<JsonNode, ?>> plan) {
        return (Decoder<JsonNode, Object>) plan.build().get();
    }

    private Plan<Decoder<JsonNode, ?>> planString(JsonNode form) {
        Plan<Decoder<JsonNode, ?>> plan = planDecoder(form);
        requireType(STRING, plan.type());
        return plan;
    }

    @SuppressWarnings("unchecked")
    private static Decoder<JsonNode, String> buildString(Plan<Decoder<JsonNode, ?>> plan) {
        return (Decoder<JsonNode, String>) plan.build().get();
    }

    /**
     * The fixture a fixture argument names. Its name is not a value, so it is read while planning.
     *
     * @param name the argument
     * @param kind the kind of fixture the argument takes
     * @param <F>  that kind
     * @return the fixture
     * @throws UnboundFeature if the runner does not bind the fixture
     */
    private static <F extends Fixtures.Fixture> F fixture(JsonNode name, Class<F> kind) {
        if (!name.isString()) {
            throw new IllegalArgumentException("not a fixture name: " + name);
        }
        String id = "fixture." + name.stringValue();
        Fixtures.Fixture f = Fixtures.ALL.get(id);
        if (f == null) {
            throw new UnboundFeature(id);
        }
        if (!kind.isInstance(f)) {
            throw new IllegalArgumentException(id + " is not a " + kind.getSimpleName());
        }
        return kind.cast(f);
    }

    private static SpecType element(SpecType listType) {
        if (!(listType instanceof SpecType.ListOf l)) {
            throw new IllegalArgumentException("not a list: " + listType);
        }
        return l.element();
    }

    /**
     * A value argument, which raoh-java's methods take only when it is not null.
     *
     * @param type        the type to read it at
     * @param observation the argument
     * @return the value
     */
    private static Object value(SpecType type, JsonNode observation) {
        Object value = ValueCodec.materialize(type, observation);
        if (value == null) {
            throw new IllegalArgumentException("raoh-java takes no null here");
        }
        return value;
    }

    /**
     * A list argument whose elements have the given type, none of them null.
     *
     * @param elementType the type of the elements
     * @param observation the argument
     * @param element     the class of the elements
     * @param <T>         that class
     * @return the elements
     */
    private static <T> List<T> listOf(SpecType elementType, JsonNode observation, Class<T> element) {
        List<T> values = new ArrayList<>();
        for (Object v : (List<?>) value(new SpecType.ListOf(elementType), observation)) {
            if (v == null) {
                throw new IllegalArgumentException("raoh-java takes no null element here");
            }
            values.add(element.cast(v));
        }
        return values;
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

    private static SpecType common(Iterable<? extends Plan<?>> plans) {
        Set<SpecType> types = new LinkedHashSet<>();
        for (Plan<?> plan : plans) {
            types.add(plan.type());
        }
        if (types.size() != 1) {
            throw new IllegalArgumentException("the parts have different types: " + types);
        }
        return types.iterator().next();
    }

    private static void requireType(SpecType expected, SpecType actual) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("expected " + expected + ", got " + actual);
        }
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
