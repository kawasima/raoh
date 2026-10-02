package net.unit8.raoh.conformance;

import net.unit8.raoh.Presence;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Converts between raoh-java's values and their observations (the specification's
 * {@code observation.md}), directed by the value's {@link SpecType}.
 *
 * <p>The Java value a type has is fixed here and in {@link SpecType.Scalar}, nowhere else:
 * {@code int32} is an {@link Integer}, {@code decimal} a {@link BigDecimal}, {@code product} the
 * list {@link #product} makes, {@code nullable<T>} a {@code T} or {@code null},
 * {@code presence<T>} a {@link Presence}. A value that is not what its type says is a defect of the
 * runner or of raoh-java, never something to write down: {@link #observe} throws on one.
 *
 * <p>{@link #materialize} reads observations the suite gives, and does not check that they are
 * observations: whether one is (a float in its type's range and written as its canonical decimal,
 * a set with no element twice) is what {@code raoh-verify check-suite} decides, and a run on a suite
 * that fails it is invalid whatever the runner wrote. Checking again here would be a second reading
 * of {@code observation.md}.
 */
final class ValueCodec {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private ValueCodec() {
    }

    /**
     * The product of the values an object decoder gives, which may hold {@code null}.
     *
     * @param values the values of the fields, in order
     * @return an unmodifiable list of them
     */
    static List<@Nullable Object> product(@Nullable Object[] values) {
        return Collections.unmodifiableList(Arrays.asList(values.clone()));
    }

    /**
     * Writes a value as the observation of its type.
     *
     * @param type  the value's type
     * @param value the value
     * @return the observation
     * @throws IllegalStateException if the value is not one of the type
     */
    static JsonNode observe(SpecType type, @Nullable Object value) {
        return switch (type) {
            case SpecType.NullableOf n -> value == null ? NODES.nullNode() : observe(n.value(), value);
            case SpecType.OptionalOf o -> {
                Optional<?> optional = cast(type, value, Optional.class);
                yield optional.isPresent() ? observe(o.value(), optional.get()) : NODES.nullNode();
            }
            case SpecType.PresenceOf p -> switch (cast(type, value, Presence.class)) {
                case Presence.Absent<?> a -> NODES.stringNode("absent");
                case Presence.PresentNull<?> n -> NODES.stringNode("null");
                case Presence.Present<?> present -> {
                    ObjectNode node = NODES.objectNode();
                    node.set("present", observe(p.value(), present.value()));
                    yield node;
                }
            };
            case SpecType.ListOf l -> elements(l.element(), cast(type, value, List.class));
            case SpecType.SetOf s -> elements(s.element(), cast(type, value, Set.class));
            case SpecType.MapOf m -> {
                ObjectNode node = NODES.objectNode();
                Map<?, ?> map = cast(type, value, Map.class);
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    node.set(cast(SpecType.Scalar.STRING, e.getKey(), String.class), observe(m.value(), e.getValue()));
                }
                yield node;
            }
            case SpecType.Product p -> {
                List<?> elements = cast(type, value, List.class);
                if (elements.size() != p.elements().size()) {
                    throw new IllegalStateException("a product of " + p.elements().size()
                            + " elements has " + elements.size());
                }
                ArrayNode node = NODES.arrayNode();
                for (int i = 0; i < elements.size(); i++) {
                    node.add(observe(p.elements().get(i), elements.get(i)));
                }
                yield node;
            }
            case SpecType.Symbol s -> {
                Enum<?> constant = cast(type, value, Enum.class);
                if (!s.alternatives().contains(constant.name())) {
                    throw new IllegalStateException(constant.name() + " is not one of " + s.alternatives());
                }
                yield NODES.stringNode(constant.name());
            }
            case SpecType.Scalar s -> scalar(s, value);
        };
    }

    private static JsonNode elements(SpecType element, Collection<?> values) {
        ArrayNode node = NODES.arrayNode();
        for (Object v : values) {
            node.add(observe(element, v));
        }
        return node;
    }

    private static JsonNode scalar(SpecType.Scalar type, @Nullable Object value) {
        Object v = cast(type, value, type.javaType());
        return switch (type) {
            case BOOL -> NODES.booleanNode((Boolean) v);
            case INT32 -> NODES.numberNode((Integer) v);
            case INT64 -> NODES.numberNode((Long) v);
            case FLOAT32 -> float32((Float) v);
            case FLOAT64 -> float64((Double) v);
            case DECIMAL -> NODES.stringNode(((BigDecimal) v).toString());
            // Each of these is written as its toString: a string itself, the lower-case UUID, the
            // URI as written, and the ISO 8601 forms observation.md lists for the temporal types.
            case STRING, UUID, URI, DATE, TIME, DATETIME, OFFSET_DATETIME, INSTANT -> NODES.stringNode(v.toString());
        };
    }

    /**
     * The observation of a float32. {@link Float#toString} writes the shortest decimal that rounds
     * to the value, two digits where one would be far from it, which is the specification's
     * canonical decimal; the values a JSON number cannot carry are written as tags.
     *
     * @param f the value
     * @return its observation
     */
    static JsonNode float32(float f) {
        if (Float.isNaN(f)) {
            return floatTag("NaN");
        }
        if (Float.isInfinite(f)) {
            return floatTag(f > 0 ? "+Infinity" : "-Infinity");
        }
        if (f == 0 && Float.floatToRawIntBits(f) != 0) {
            return floatTag("-0");
        }
        return NODES.numberNode(new BigDecimal(Float.toString(f)));
    }

    /**
     * The observation of a float64, as {@link #float32} writes a float32.
     *
     * @param d the value
     * @return its observation
     */
    static JsonNode float64(double d) {
        if (Double.isNaN(d)) {
            return floatTag("NaN");
        }
        if (Double.isInfinite(d)) {
            return floatTag(d > 0 ? "+Infinity" : "-Infinity");
        }
        if (d == 0 && Double.doubleToRawLongBits(d) != 0) {
            return floatTag("-0");
        }
        return NODES.numberNode(new BigDecimal(Double.toString(d)));
    }

    private static JsonNode floatTag(String tag) {
        ObjectNode node = NODES.objectNode();
        node.put("float", tag);
        return node;
    }

    /**
     * Builds the value an observation denotes, at the given type.
     *
     * @param type        the type to read the observation at
     * @param observation the observation
     * @return the value
     * @throws IllegalArgumentException if the observation is not one of the type
     */
    static @Nullable Object materialize(SpecType type, JsonNode observation) {
        return switch (type) {
            case SpecType.NullableOf n -> observation.isNull() ? null : materialize(n.value(), observation);
            case SpecType.OptionalOf o -> observation.isNull()
                    ? Optional.empty()
                    : Optional.of(nonNull(o.value(), materialize(o.value(), observation)));
            case SpecType.PresenceOf p -> {
                if (observation.isString() && observation.stringValue().equals("absent")) {
                    yield new Presence.Absent<>();
                }
                if (observation.isString() && observation.stringValue().equals("null")) {
                    yield new Presence.PresentNull<>();
                }
                if (observation.isObject() && observation.size() == 1 && observation.get("present") != null) {
                    yield new Presence.Present<>(nonNull(p.value(), materialize(p.value(), observation.get("present"))));
                }
                throw notAnObservation(type, observation);
            }
            case SpecType.ListOf l -> Collections.unmodifiableList(elements(l, l.element(), observation));
            case SpecType.SetOf s -> Collections.unmodifiableSet(new LinkedHashSet<>(elements(s, s.element(), observation)));
            case SpecType.MapOf m -> {
                if (!observation.isObject()) {
                    throw notAnObservation(type, observation);
                }
                Map<String, @Nullable Object> map = new LinkedHashMap<>();
                for (Map.Entry<String, JsonNode> e : observation.properties()) {
                    map.put(e.getKey(), materialize(m.value(), e.getValue()));
                }
                yield Collections.unmodifiableMap(map);
            }
            case SpecType.Product p -> {
                if (!observation.isArray() || observation.size() != p.elements().size()) {
                    throw notAnObservation(type, observation);
                }
                Object[] values = new Object[observation.size()];
                for (int i = 0; i < values.length; i++) {
                    values[i] = materialize(p.elements().get(i), observation.get(i));
                }
                yield product(values);
            }
            case SpecType.Symbol s -> {
                String name = text(type, observation);
                if (!s.alternatives().contains(name)) {
                    throw notAnObservation(type, observation);
                }
                yield constant(Symbols.enumOf(s.alternatives()), name);
            }
            case SpecType.Scalar s -> scalar(s, observation);
        };
    }

    private static List<@Nullable Object> elements(SpecType type, SpecType element, JsonNode observation) {
        if (!observation.isArray()) {
            throw notAnObservation(type, observation);
        }
        List<@Nullable Object> values = new ArrayList<>();
        for (JsonNode e : observation) {
            values.add(materialize(element, e));
        }
        return values;
    }

    private static Object scalar(SpecType.Scalar type, JsonNode observation) {
        return switch (type) {
            case BOOL -> {
                if (!observation.isBoolean()) {
                    throw notAnObservation(type, observation);
                }
                yield observation.booleanValue();
            }
            case INT32 -> {
                if (!observation.isIntegralNumber() || !observation.canConvertToInt()) {
                    throw notAnObservation(type, observation);
                }
                yield observation.intValue();
            }
            case INT64 -> {
                if (!observation.isIntegralNumber() || !observation.canConvertToLong()) {
                    throw notAnObservation(type, observation);
                }
                yield observation.longValue();
            }
            case FLOAT32 -> {
                String tag = floatTagOf(observation);
                yield switch (tag) {
                    case "NaN" -> Float.NaN;
                    case "+Infinity" -> Float.POSITIVE_INFINITY;
                    case "-Infinity" -> Float.NEGATIVE_INFINITY;
                    case "-0" -> -0.0f;
                    case "" -> Float.parseFloat(number(type, observation).toString());
                    default -> throw notAnObservation(type, observation);
                };
            }
            case FLOAT64 -> {
                String tag = floatTagOf(observation);
                yield switch (tag) {
                    case "NaN" -> Double.NaN;
                    case "+Infinity" -> Double.POSITIVE_INFINITY;
                    case "-Infinity" -> Double.NEGATIVE_INFINITY;
                    case "-0" -> -0.0d;
                    case "" -> Double.parseDouble(number(type, observation).toString());
                    default -> throw notAnObservation(type, observation);
                };
            }
            case DECIMAL -> new BigDecimal(text(type, observation));
            case STRING -> text(type, observation);
            case UUID -> java.util.UUID.fromString(text(type, observation));
            case URI -> java.net.URI.create(text(type, observation));
            case DATE -> LocalDate.parse(text(type, observation));
            case TIME -> LocalTime.parse(text(type, observation));
            case DATETIME -> LocalDateTime.parse(text(type, observation));
            case OFFSET_DATETIME -> OffsetDateTime.parse(text(type, observation));
            case INSTANT -> Instant.parse(text(type, observation));
        };
    }

    /**
     * The tag of a float observation.
     *
     * @param observation the observation
     * @return its tag, or the empty string for a number
     */
    private static String floatTagOf(JsonNode observation) {
        if (observation.isObject() && observation.size() == 1 && observation.get("float") != null
                && observation.get("float").isString()) {
            return observation.get("float").stringValue();
        }
        return "";
    }

    private static BigDecimal number(SpecType type, JsonNode observation) {
        if (!observation.isNumber()) {
            throw notAnObservation(type, observation);
        }
        return observation.decimalValue();
    }

    private static String text(SpecType type, JsonNode observation) {
        if (!observation.isString()) {
            throw notAnObservation(type, observation);
        }
        return observation.stringValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object constant(Class<? extends Enum<?>> enumClass, String name) {
        return Enum.valueOf((Class) enumClass, name);
    }

    private static Object nonNull(SpecType type, @Nullable Object value) {
        if (value == null) {
            throw new IllegalArgumentException("a value of " + type + " cannot be null here");
        }
        return value;
    }

    private static <T> T cast(SpecType type, @Nullable Object value, Class<T> javaType) {
        if (!javaType.isInstance(value)) {
            throw new IllegalStateException("expected a " + javaType.getSimpleName() + " for " + type
                    + ", got " + (value == null ? "null" : value.getClass().getName()));
        }
        return javaType.cast(value);
    }

    private static IllegalArgumentException notAnObservation(SpecType type, JsonNode observation) {
        return new IllegalArgumentException(observation + " is not an observation of " + type);
    }
}
