package net.unit8.raoh.encoder;

import java.util.function.Function;

/**
 * A named property encoder that extracts a field from a domain object and encodes its value.
 *
 * <p>Analogous to {@link net.unit8.raoh.FieldDecoder FieldDecoder} on the decoder side,
 * a {@code PropertyEncoder} binds together:
 *
 * <ul>
 *   <li>a map key (the column or JSON field name)</li>
 *   <li>a getter that extracts the field value from the domain object</li>
 *   <li>a value encoder that converts the extracted value to {@code Object}</li>
 * </ul>
 *
 * <p>Created via {@link MapEncoders#property(String, Function, Encoder)} and consumed by
 * {@link MapEncoders#object(PropertyEncoder[])}.
 *
 * @param <T> the domain type from which the property is extracted
 */
public final class PropertyEncoder<T> {

    private final String key;
    private final Function<T, Object> extractor;

    /**
     * Creates a property encoder.
     *
     * @param <V>          the property value type
     * @param key          the output map key
     * @param getter       extracts the property value from the domain object
     * @param valueEncoder encodes the extracted value to {@code Object}
     */
    <V> PropertyEncoder(String key, Function<T, V> getter, Encoder<V, Object> valueEncoder) {
        this.key = key;
        this.extractor = value -> valueEncoder.encode(getter.apply(value));
    }

    /**
     * Returns the map key for this property.
     *
     * @return the map key
     */
    public String key() {
        return key;
    }

    /**
     * Extracts and encodes the property value from the given domain object.
     *
     * @param value the domain object
     * @return the encoded property value
     */
    public Object encode(T value) {
        return extractor.apply(value);
    }
}
