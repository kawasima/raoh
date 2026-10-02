package net.unit8.raoh.encode;

import net.unit8.raoh.Presence;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Factory for encoders that produce {@code Map<String, Object>} — the format consumed by
 * Spring JDBC's {@code JdbcClient} named-parameter binding.
 *
 * <p>The one exception is {@link #lazy}, which is boundary-agnostic (it defers any
 * {@code Encoder<T, O>}) and so does not produce a map itself. It lives here deliberately: there is
 * no encode-side counterpart to the decoder's boundary-agnostic
 * {@link net.unit8.raoh.decode.Decoders Decoders} class, and there is no reason to add one. Most of
 * what fills {@code Decoders} — {@code recover}, {@code oneOf}, {@code strict} —
 * exists to handle failure, and an encoder is a total function that cannot fail; the value-mapping
 * operations ({@link Encoder#contramap contramap}, {@link Encoder#andThen andThen}) live on the
 * interface. An {@code Encoders} class would therefore hold {@code lazy} and nothing else. Since
 * recursion is reached through {@link #nested} / {@link #list} in practice, {@code lazy} is kept
 * next to them.
 *
 * <p>This is the encoding counterpart of
 * {@link net.unit8.raoh.decode.map.MapDecoders MapDecoders}. The API mirrors the decoder side
 * deliberately so that a decoder and its encoder read as mirror images. Each is written in a class
 * of its own, since the decoder and encoder classes share names ({@code int_()},
 * {@code discriminate}) and cannot all be imported on demand into one:
 *
 * <pre>{@code
 * // Decoder: Map → Table
 * import static net.unit8.raoh.decode.map.MapDecoders.*;
 * import static net.unit8.raoh.decode.ObjectDecoders.*;
 *
 * static final Decoder<Map<String, Object>, Table> TABLE_DECODER = combine(
 *     field("id",           long_()).map(TableId::new),
 *     field("table_number", int_()),
 *     field("capacity",     int_())
 * ).map(Table::new);
 * }</pre>
 *
 * <pre>{@code
 * // Encoder: Table → Map  (mirror image)
 * import static net.unit8.raoh.encode.MapEncoders.*;
 * import static net.unit8.raoh.encode.ObjectEncoders.*;
 *
 * static final Encoder<Table, Map<String, @Nullable Object>> TABLE_ENCODER = object(
 *     property("id",           Table::id,          long_().contramap(TableId::value)),
 *     property("table_number", Table::tableNumber, int_()),
 *     property("capacity",     Table::capacity,    int_())
 * );
 * }</pre>
 *
 * <p>The {@code Map<String, Object>} output integrates naturally with other libraries:
 * <ul>
 *   <li><strong>Spring JDBC</strong> — pass directly to {@code JdbcClient} named-parameter
 *       binding via {@code .params(map)}</li>
 *   <li><strong>jOOQ</strong> — populate a typed record via
 *       {@code record.fromMap(map)} (note: key matching is case-sensitive per jOOQ convention)</li>
 *   <li><strong>Jackson</strong> — convert to {@code ObjectNode} via
 *       {@code objectMapper.valueToTree(map)}</li>
 * </ul>
 *
 * <p>Usage: {@code import static net.unit8.raoh.encode.MapEncoders.*;}
 */
public final class MapEncoders {

    private MapEncoders() {}

    /**
     * Creates a {@link PropertyEncoder} that binds a map key to a getter and a value encoder.
     *
     * <p>Corresponds to {@link net.unit8.raoh.decode.map.MapDecoders#field(String, net.unit8.raoh.Decoder)
     * MapDecoders.field()} on the decoder side.
     *
     * <p>The property value is non-null. For a getter that may return {@code null}, use
     * {@link #nullableProperty(String, Function, Encoder)} instead.
     *
     * @param <T>          the domain type
     * @param <V>          the (non-null) property value type
     * @param key          the output map key (e.g., column name)
     * @param getter       extracts the property value from the domain object
     * @param valueEncoder encodes the extracted value to {@code Object}
     * @return a property encoder for use with {@link #object(PropertyEncoder[])}
     */
    public static <T, V> PropertyEncoder<T> property(
            String key,
            Function<? super T, ? extends V> getter,
            Encoder<V, Object> valueEncoder) {
        return PropertyEncoder.of(key, getter, valueEncoder);
    }

    /**
     * Creates a {@link PropertyEncoder} for a property whose value may be {@code null}.
     *
     * <p>The getter may return {@code null}. The {@code null} branch is handled inside this
     * method — when the getter returns {@code null}, the value encoder is not invoked and
     * {@code null} is written to the map. The value encoder therefore only ever sees non-null
     * values, and is the same {@code Encoder<V, Object>} used with {@link #property}. This is the
     * nullable counterpart of {@link #property(String, Function, Encoder)}; it is kept separate
     * because the two cannot be overloaded (their erasures are identical).
     *
     * @param <T>          the domain type
     * @param <V>          the property value type (the getter may return a {@code null} {@code V})
     * @param key          the output map key (e.g., column name)
     * @param getter       extracts the property value, which may be {@code null}
     * @param valueEncoder encodes the extracted value (only invoked when non-null) to {@code Object}
     * @return a property encoder for use with {@link #object(PropertyEncoder[])}
     */
    public static <T, V> PropertyEncoder<T> nullableProperty(
            String key,
            Function<? super T, ? extends @Nullable V> getter,
            Encoder<V, Object> valueEncoder) {
        return PropertyEncoder.ofNullable(key, getter, valueEncoder);
    }

    /**
     * Creates a {@link PropertyEncoder} that substitutes {@code defaultValue} when the getter
     * returns {@code null}, then encodes the result. The map entry is therefore never {@code null}.
     *
     * <p>This is the encoder-side counterpart of
     * {@link net.unit8.raoh.decode.ObjectDecoders#withDefault(net.unit8.raoh.decode.Decoder, Object)
     * ObjectDecoders.withDefault}. Like {@link #property}, the value encoder is a plain
     * {@code Encoder<V, Object>}; the {@code null}-to-default substitution happens in this property
     * layer rather than inside the encoder.
     *
     * @param <T>          the domain type
     * @param <V>          the property value type
     * @param key          the output map key (e.g., column name)
     * @param getter       extracts the property value, which may be {@code null}
     * @param valueEncoder encodes the value (or the default) to {@code Object}
     * @param defaultValue the value to encode when the getter returns {@code null}
     * @return a property encoder for use with {@link #object(PropertyEncoder[])}
     */
    public static <T, V> PropertyEncoder<T> propertyWithDefault(
            String key,
            Function<? super T, ? extends @Nullable V> getter,
            Encoder<V, Object> valueEncoder,
            V defaultValue) {
        return PropertyEncoder.ofDefault(key, getter, valueEncoder, defaultValue);
    }

    /**
     * Like {@link #propertyWithDefault(String, Function, Encoder, Object)}, but the default is
     * computed lazily by the given supplier.
     *
     * <p>Note: when passing a lambda directly, the compiler may not distinguish this overload from
     * {@link #propertyWithDefault(String, Function, Encoder, Object)}. In that case, assign the
     * lambda to a typed {@link Supplier} variable first.
     *
     * @param <T>          the domain type
     * @param <V>          the property value type
     * @param key          the output map key (e.g., column name)
     * @param getter       extracts the property value, which may be {@code null}
     * @param valueEncoder encodes the value (or the supplied default) to {@code Object}
     * @param defaultValue supplies the value to encode when the getter returns {@code null}
     * @return a property encoder for use with {@link #object(PropertyEncoder[])}
     */
    public static <T, V> PropertyEncoder<T> propertyWithDefault(
            String key,
            Function<? super T, ? extends @Nullable V> getter,
            Encoder<V, Object> valueEncoder,
            Supplier<V> defaultValue) {
        return PropertyEncoder.ofDefault(key, getter, valueEncoder, defaultValue);
    }

    /**
     * Creates a property encoder that omits the key entirely when the getter returns {@code null}.
     *
     * <p>This is the encode counterpart of
     * {@link net.unit8.raoh.decode.map.MapDecoders#optionalField optionalField} (which produces
     * {@code Optional.empty()} for an absent key). Contrast with {@link #nullableProperty}, which
     * <em>writes</em> the key with a {@code null} value; {@code optionalProperty} leaves the key out.
     *
     * @param <T>          the domain type
     * @param <V>          the property value type
     * @param key          the output map key
     * @param getter       extracts the property value; when it returns {@code null} the key is omitted
     * @param valueEncoder encodes the extracted value (only invoked when non-null) to {@code Object}
     * @return a property encoder that writes its key zero or one times
     */
    public static <T, V> PropertyEncoder<T> optionalProperty(
            String key,
            Function<? super T, ? extends @Nullable V> getter,
            Encoder<V, Object> valueEncoder) {
        return PropertyEncoder.ofOptional(key, getter, valueEncoder);
    }

    /**
     * Creates a property encoder that round-trips a tri-state {@link Presence} value:
     *
     * <ul>
     *   <li>{@link Presence.Absent} — the key is omitted entirely;</li>
     *   <li>{@link Presence.PresentNull} — the key is written with a {@code null} value;</li>
     *   <li>{@link Presence.Present} — the key is written with the encoded value (a {@code Present}
     *       carrying a {@code null} value is written as a {@code null} entry, never passed to the
     *       value encoder).</li>
     * </ul>
     *
     * <p>This is the exact encode counterpart of
     * {@link net.unit8.raoh.decode.map.MapDecoders#optionalNullableField optionalNullableField}, so a
     * {@link Presence}-carrying domain field can be decoded in and encoded back out without loss.
     *
     * @param <T>          the domain type
     * @param <V>          the present value type
     * @param key          the output map key
     * @param getter       extracts the {@link Presence} field from the domain object
     * @param valueEncoder encodes the present value (only invoked for {@link Presence.Present}) to {@code Object}
     * @return a property encoder that writes its key, possibly with a {@code null} value, zero or one times
     */
    public static <T, V> PropertyEncoder<T> presenceProperty(
            String key,
            Function<? super T, ? extends Presence<V>> getter,
            Encoder<V, Object> valueEncoder) {
        return PropertyEncoder.ofPresence(key, getter, valueEncoder);
    }

    /**
     * Creates an encoder that builds a {@code Map<String, Object>} from a domain object by applying
     * each property encoder in order.
     *
     * <p>Each property owns one key and writes it at most once: a plain {@link #property} always
     * writes it, {@link #optionalProperty} and {@link #presenceProperty} may leave it out. The
     * resulting map preserves the order the properties are given in (backed by
     * {@link LinkedHashMap}). Corresponds to {@code combine(...).map(Constructor::new)} on the
     * decoder side.
     *
     * <p>Two properties may not own the same key. That is a mistake in the definition, whatever
     * the values turn out to be, so it is rejected here rather than letting the later property's
     * value replace the earlier one's at encode time.
     *
     * @param <T>        the domain type to encode
     * @param properties the property encoders that define the output map entries
     * @return an encoder producing {@code Map<String, Object>}
     * @throws IllegalArgumentException if two properties own the same key
     */
    @SafeVarargs
    public static <T> Encoder<T, Map<String, @Nullable Object>> object(PropertyEncoder<T>... properties) {
        // A copy, so changing the array passed in afterwards does not change the encoder, and the
        // properties checked below are the ones that run.
        List<PropertyEncoder<T>> owned = List.of(properties);
        var firstByKey = HashMap.<String, Integer>newHashMap(owned.size());
        for (int i = 0; i < owned.size(); i++) {
            String key = owned.get(i).key();
            Integer previous = firstByKey.put(key, i);
            if (previous != null) {
                int first = previous;
                throw new IllegalArgumentException("object key '" + key + "' is owned by properties "
                        + first + " and " + i);
            }
        }
        int size = owned.size();
        return value -> {
            // Each property writes at most one key, so the property count bounds the output size.
            var map = LinkedHashMap.<String, @Nullable Object>newLinkedHashMap(size);
            for (var property : owned) {
                property.encodeTo(value, map);
            }
            return map;
        };
    }

    /**
     * Adapts an {@code Encoder<T, Map<String, Object>>} for use as a value encoder
     * (i.e., as the third argument to {@link #property}).
     *
     * <p>Corresponds to {@link net.unit8.raoh.decode.map.MapDecoders#nested(net.unit8.raoh.Decoder)
     * MapDecoders.nested()} on the decoder side. Use it to embed a structured encoder
     * inside a parent {@link #object}:
     *
     * <pre>{@code
     * static final Encoder<Restaurant, Map<String, @Nullable Object>> RESTAURANT_ENCODER = object(
     *     property("id",     Restaurant::id,     long_().contramap(RestaurantId::value)),
     *     property("name",   Restaurant::name,   string()),
     *     property("tables", Restaurant::tables, list(nested(TABLE_ENCODER)))
     * );
     * }</pre>
     *
     * @param <T> the domain type of the nested encoder
     * @param enc the encoder to adapt
     * @return an encoder whose output type is {@code Object}
     */
    public static <T> Encoder<T, Object> nested(Encoder<T, Map<String, @Nullable Object>> enc) {
        return enc::encode;
    }

    /**
     * Creates an encoder that applies an element encoder to every item in a {@link List}.
     *
     * <p>Corresponds to {@link net.unit8.raoh.decode.Decoder#list() Decoder.list()} on the decoder
     * side. Commonly used with {@link #nested} to encode a list of nested objects:
     *
     * <pre>{@code
     * property("tables", Restaurant::tables, list(nested(TABLE_ENCODER)))
     * }</pre>
     *
     * @param <T>            the element domain type
     * @param elementEncoder the encoder for each list element
     * @return an encoder that produces {@code List<Object>}
     */
    public static <T> Encoder<List<T>, Object> list(Encoder<T, Object> elementEncoder) {
        return values -> values.stream()
                .map(elementEncoder::encode)
                .toList();
    }

    /**
     * Creates a lazily-evaluated encoder, resolving the underlying encoder on each invocation.
     *
     * <p>The encode counterpart of
     * {@link net.unit8.raoh.decode.Decoders#lazy(java.util.function.Supplier) Decoders.lazy()}.
     * Use it to define self-referential (recursive) encoders, which would otherwise reference their
     * own {@code static final} field before its initializer completes:
     *
     * <pre>{@code
     * final class Schema {
     *     static final Encoder<Node, Map<String, @Nullable Object>> NODE = object(
     *             property("value",    Node::value,    int_()),
     *             property("children", Node::children, list(nested(lazy(() -> Schema.NODE)))));
     * }
     * }</pre>
     *
     * <p>The supplier must qualify the self-reference ({@code Schema.NODE} rather than a bare
     * {@code NODE}), because a simple name would be an illegal forward reference inside the field's
     * own initializer.
     *
     * @param <T>      the domain type to encode from
     * @param <O>      the external representation type to encode to
     * @param supplier supplies the encoder on each invocation
     * @return a lazy encoder that delegates to the supplied encoder
     */
    public static <T, O> Encoder<T, O> lazy(Supplier<Encoder<T, O>> supplier) {
        return value -> supplier.get().encode(value);
    }

    /**
     * Creates an encoder that applies a value encoder to every value of a homogeneous
     * {@code Map<String, V>}, preserving the keys.
     *
     * <p>The encode counterpart of
     * {@link net.unit8.raoh.decode.ObjectDecoders#map(net.unit8.raoh.decode.Decoder) ObjectDecoders.map()}
     * on the decoder side, so a value-map decoded in can be encoded back out. The result preserves
     * insertion order (backed by {@link LinkedHashMap}). Like {@link #list}, this is the map sibling
     * for a homogeneous collection; use it with {@link #nested} to encode a map of nested objects:
     *
     * <pre>{@code
     * property("prices", Order::prices, mapOf(decimal()))
     * }</pre>
     *
     * @param <V>          the value domain type
     * @param valueEncoder the encoder for each map value
     * @return an encoder that produces {@code Map<String, Object>}
     */
    public static <V> Encoder<Map<String, V>, Object> mapOf(Encoder<V, Object> valueEncoder) {
        return values -> {
            // Output size is exactly the input size (one entry per value), so pre-size to avoid rehashes.
            var out = LinkedHashMap.<String, Object>newLinkedHashMap(values.size());
            values.forEach((key, value) -> out.put(key, valueEncoder.encode(value)));
            return out;
        };
    }

    /**
     * A single tagged variant of a discriminated (tagged-union) encoder: the concrete subtype,
     * the discriminator tag written for it, and the encoder that produces its body.
     *
     * <p>Created via {@link #variant(Class, String, Encoder)} and consumed by
     * {@link #discriminate(String, Variant[])}. The {@code type} is matched against
     * {@link Object#getClass()} of the value being encoded, so it must be the value's exact
     * runtime class (see {@link #discriminate(String, Variant[])} for the implications).
     *
     * @param <S>     the concrete subtype this variant encodes
     * @param type    the exact runtime class dispatched on
     * @param tag     the discriminator value written under the discriminator key
     * @param encoder the encoder producing the variant body (must not itself write the tag)
     */
    public record Variant<S>(
            Class<S> type,
            String tag,
            Encoder<S, Map<String, @Nullable Object>> encoder) {}

    /**
     * Creates a {@link Variant} binding a concrete subtype to its discriminator tag and encoder.
     *
     * <p>The type parameter {@code S} ties the class and the encoder together so a mismatched
     * pair fails to compile. It deliberately does not mention the union supertype {@code T} of
     * {@link #discriminate(String, Variant[])}: that keeps type inference working when several
     * {@code variant(...)} calls of different subtypes are passed to the same {@code discriminate}.
     *
     * @param <S>     the concrete subtype this variant encodes
     * @param type    the exact runtime class to dispatch on
     * @param tag     the discriminator value to write for this subtype
     * @param encoder the encoder producing the variant body
     * @return a variant for use with {@link #discriminate(String, Variant[])}
     */
    public static <S> Variant<S> variant(
            Class<S> type, String tag, Encoder<S, Map<String, @Nullable Object>> encoder) {
        return new Variant<>(type, tag, encoder);
    }

    /**
     * Creates an encoder for a tagged union (typically a {@code sealed} interface) that selects a
     * variant encoder by the value's runtime class and writes the discriminator tag into the output.
     *
     * <p>This is the encode-side mirror of
     * {@link net.unit8.raoh.decode.map.MapDecoders#discriminate(String, Map)
     * MapDecoders.discriminate}. The two are asymmetric by nature: the decoder reads the tag from
     * the input data to choose a variant, whereas this encoder chooses a variant from the value's
     * concrete type and then writes the tag. The discriminator entry is injected here, so variant
     * encoders must not write it themselves.
     *
     * <p>The tag is placed first in the resulting {@link LinkedHashMap} and is authoritative: if a
     * variant encoder also emits the discriminator key, that entry is discarded in favour of the
     * injected tag. The return type matches {@link #object(PropertyEncoder[])}, so the result can be
     * embedded via {@link #nested(Encoder)} or used as a top-level encoder.
     *
     * <p><strong>Exact-class dispatch.</strong> Variants are matched against
     * {@link Object#getClass()}, i.e. by exact runtime class, not {@code instanceof}. This fits a
     * {@code sealed} hierarchy whose permitted types are {@code record}s or {@code final} classes.
     * A value whose class is a subclass of a registered non-final variant type will not match.
     *
     * <p><strong>Never fails for well-formed definitions.</strong> Consistent with {@link Encoder}
     * being a total function, a correct definition covering every permitted subtype never throws at
     * encode time. The runtime {@link IllegalArgumentException} below is a guard against a
     * definition that omits a subtype — a programming error, not a data error (unlike the decoder,
     * which returns an error result for an unknown tag).
     *
     * @param <T>       the union (supertype) being encoded
     * @param fieldName the discriminator key written into the output map
     * @param variants  the variants, each pairing a concrete class with its tag and encoder
     * @return an encoder that dispatches on runtime class and injects the discriminator tag
     * @throws IllegalArgumentException if two variants register the same class or the same tag
     *         (both detected here), or, from the returned encoder, if it is given a value whose
     *         class has no registered variant
     */
    @SafeVarargs
    public static <T> Encoder<T, Map<String, @Nullable Object>> discriminate(
            String fieldName, Variant<? extends T>... variants) {
        var byClass = new LinkedHashMap<Class<?>, Variant<? extends T>>(variants.length * 2);
        var seenTags = new HashSet<String>(variants.length * 2);
        for (var v : variants) {
            if (byClass.put(v.type(), v) != null) {
                throw new IllegalArgumentException("duplicate variant for " + v.type().getName());
            }
            // Reject duplicate tags too: two classes sharing a tag would emit a non-unique
            // discriminator that the tag-keyed decoder side cannot round-trip.
            if (!seenTags.add(v.tag())) {
                throw new IllegalArgumentException("duplicate variant tag '" + v.tag() + "'");
            }
        }
        return value -> {
            var v = byClass.get(value.getClass());
            if (v == null) {
                throw new IllegalArgumentException(
                        "no encoder registered for " + value.getClass().getName()
                        + "; known variants: "
                        + byClass.keySet().stream().map(Class::getName).sorted().toList());
            }
            // value.getClass() == v.type() is verified above, so applying the variant encoder to
            // this value is runtime-safe. Mirrors the unchecked cast in Decoders.discriminate.
            @SuppressWarnings("unchecked")
            var enc = (Encoder<T, Map<String, @Nullable Object>>) (Encoder<?, ?>) v.encoder();
            var body = enc.encode(value);
            var out = new LinkedHashMap<String, @Nullable Object>(body.size() + 2);
            out.put(fieldName, v.tag());
            body.forEach((k, val) -> {
                // fieldName is non-null; call equals on it so a variant that emits a null key
                // (keys are non-null by contract, but the encoder is arbitrary) drops through
                // as a normal entry instead of throwing.
                if (!fieldName.equals(k)) {
                    out.put(k, val);
                }
            });
            return out;
        };
    }
}
