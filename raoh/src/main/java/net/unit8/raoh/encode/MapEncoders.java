package net.unit8.raoh.encode;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Factory for encoders that produce {@code Map<String, Object>} — the format consumed by
 * Spring JDBC's {@code JdbcClient} named-parameter binding.
 *
 * <p>This is the encoding counterpart of
 * {@link net.unit8.raoh.decode.map.MapDecoders MapDecoders}. The API mirrors the decoder side
 * deliberately so that decoder and encoder definitions can be written side by side:
 *
 * <pre>{@code
 * import static net.unit8.raoh.decode.map.MapDecoders.*;
 * import static net.unit8.raoh.decode.ObjectDecoders.*;
 * import static net.unit8.raoh.encode.MapEncoders.*;
 * import static net.unit8.raoh.encode.ObjectEncoders.*;
 *
 * // Decoder: Map → Table
 * static final Decoder<Map<String, Object>, Table> TABLE_DECODER = combine(
 *     field("id",           long_()).map(TableId::new),
 *     field("table_number", int_()),
 *     field("capacity",     int_())
 * ).map(Table::new);
 *
 * // Encoder: Table → Map  (mirror image)
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
     * {@link net.unit8.raoh.decode.Decoders#withDefault(net.unit8.raoh.decode.Decoder, Object)
     * Decoders.withDefault}. Like {@link #property}, the value encoder is a plain
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
     * Creates an encoder that builds a {@code Map<String, Object>} from a domain object
     * by applying each property encoder in order.
     *
     * <p>The resulting map preserves insertion order (backed by {@link LinkedHashMap}).
     * Corresponds to {@code combine(...).map(Constructor::new)} on the decoder side.
     *
     * @param <T>        the domain type to encode
     * @param properties the property encoders that define the output map entries
     * @return an encoder producing {@code Map<String, Object>}
     */
    @SafeVarargs
    public static <T> Encoder<T, Map<String, @Nullable Object>> object(PropertyEncoder<T>... properties) {
        return value -> {
            var map = new LinkedHashMap<String, @Nullable Object>(properties.length * 2);
            for (var prop : properties) {
                map.put(prop.key(), prop.encode(value));
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
}
