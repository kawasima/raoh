package net.unit8.raoh.encoder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Factory for encoders that produce {@code Map<String, Object>} — the format consumed by
 * Spring JDBC's {@code JdbcClient} named-parameter binding.
 *
 * <p>This is the encoding counterpart of
 * {@link net.unit8.raoh.map.MapDecoders MapDecoders}. The API mirrors the decoder side
 * deliberately so that decoder and encoder definitions can be written side by side:
 *
 * <pre>{@code
 * import static net.unit8.raoh.map.MapDecoders.*;
 * import static net.unit8.raoh.ObjectDecoders.*;
 * import static net.unit8.raoh.encoder.MapEncoders.*;
 * import static net.unit8.raoh.encoder.ObjectEncoders.*;
 *
 * // Decoder: Map → Table
 * static final Decoder<Map<String, Object>, Table> TABLE_DECODER = combine(
 *     field("id",           long_()).map(TableId::new),
 *     field("table_number", int_()),
 *     field("capacity",     int_())
 * ).map(Table::new);
 *
 * // Encoder: Table → Map  (mirror image)
 * static final Encoder<Table, Map<String, Object>> TABLE_ENCODER = object(
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
 * <p>Usage: {@code import static net.unit8.raoh.encoder.MapEncoders.*;}
 */
public final class MapEncoders {

    private MapEncoders() {}

    /**
     * Creates a {@link PropertyEncoder} that binds a map key to a getter and a value encoder.
     *
     * <p>Corresponds to {@link net.unit8.raoh.map.MapDecoders#field(String, net.unit8.raoh.Decoder)
     * MapDecoders.field()} on the decoder side.
     *
     * @param <T>          the domain type
     * @param <V>          the property value type
     * @param key          the output map key (e.g., column name)
     * @param getter       extracts the property value from the domain object
     * @param valueEncoder encodes the extracted value to {@code Object}
     * @return a property encoder for use with {@link #object(PropertyEncoder[])}
     */
    public static <T, V> PropertyEncoder<T> property(
            String key,
            Function<T, V> getter,
            Encoder<V, Object> valueEncoder) {
        return new PropertyEncoder<>(key, getter, valueEncoder);
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
    public static <T> Encoder<T, Map<String, Object>> object(PropertyEncoder<T>... properties) {
        return value -> {
            var map = new LinkedHashMap<String, Object>(properties.length * 2);
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
     * <p>Corresponds to {@link net.unit8.raoh.map.MapDecoders#nested(net.unit8.raoh.Decoder)
     * MapDecoders.nested()} on the decoder side. Use it to embed a structured encoder
     * inside a parent {@link #object}:
     *
     * <pre>{@code
     * static final Encoder<Restaurant, Map<String, Object>> RESTAURANT_ENCODER = object(
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
    public static <T> Encoder<T, Object> nested(Encoder<T, Map<String, Object>> enc) {
        return enc::encode;
    }

    /**
     * Creates an encoder that applies an element encoder to every item in a {@link List}.
     *
     * <p>Corresponds to {@link net.unit8.raoh.Decoder#list() Decoder.list()} on the decoder
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
