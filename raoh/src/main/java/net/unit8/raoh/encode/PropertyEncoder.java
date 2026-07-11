package net.unit8.raoh.encode;

import org.jspecify.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A named property encoder that extracts a field from a domain object and encodes its value.
 *
 * <p>Analogous to {@link net.unit8.raoh.decode.FieldDecoder FieldDecoder} on the decoder side,
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
    private final Function<T, @Nullable Object> extractor;

    private PropertyEncoder(String key, Function<T, @Nullable Object> extractor) {
        this.key = key;
        this.extractor = extractor;
    }

    /**
     * Creates a property encoder for a non-null property value.
     *
     * @param <T>          the domain type
     * @param <V>          the property value type
     * @param key          the output map key
     * @param getter       extracts the (non-null) property value from the domain object
     * @param valueEncoder encodes the extracted value to {@code Object}
     * @return a property encoder
     */
    static <T, V> PropertyEncoder<T> of(String key, Function<? super T, ? extends V> getter, Encoder<V, Object> valueEncoder) {
        return new PropertyEncoder<>(key, value -> valueEncoder.encode(getter.apply(value)));
    }

    /**
     * Creates a property encoder for a property whose value may be {@code null}.
     *
     * <p>The {@code null} branch is handled here: when the getter returns {@code null}, the value
     * encoder is not invoked and {@code null} is written to the map. This keeps {@code null}
     * handling in the property layer, so value encoders only ever see non-null values.
     *
     * @param <T>          the domain type
     * @param <V>          the property value type
     * @param key          the output map key
     * @param getter       extracts the property value, which may be {@code null}
     * @param valueEncoder encodes the extracted value (only invoked when non-null) to {@code Object}
     * @return a property encoder
     */
    static <T, V> PropertyEncoder<T> ofNullable(String key, Function<? super T, ? extends @Nullable V> getter, Encoder<V, Object> valueEncoder) {
        return new PropertyEncoder<>(key, value -> {
            V v = getter.apply(value);
            return v == null ? null : valueEncoder.encode(v);
        });
    }

    /**
     * Creates a property encoder that substitutes a default value when the getter returns
     * {@code null}. The value encoder only ever sees a non-null value (the actual value or the
     * default), so the encoded map entry is never {@code null}.
     *
     * @param <T>          the domain type
     * @param <V>          the property value type
     * @param key          the output map key
     * @param getter       extracts the property value, which may be {@code null}
     * @param valueEncoder encodes the value (or the default) to {@code Object}
     * @param defaultValue the value to encode when the getter returns {@code null}
     * @return a property encoder
     */
    static <T, V> PropertyEncoder<T> ofDefault(String key, Function<? super T, ? extends @Nullable V> getter,
                                               Encoder<V, Object> valueEncoder, V defaultValue) {
        return new PropertyEncoder<>(key, value -> {
            V v = getter.apply(value);
            return valueEncoder.encode(v == null ? defaultValue : v);
        });
    }

    /**
     * Like {@link #ofDefault(String, Function, Encoder, Object)}, but the default is computed
     * lazily by the given supplier.
     *
     * @param <T>          the domain type
     * @param <V>          the property value type
     * @param key          the output map key
     * @param getter       extracts the property value, which may be {@code null}
     * @param valueEncoder encodes the value (or the supplied default) to {@code Object}
     * @param defaultValue supplies the value to encode when the getter returns {@code null}
     * @return a property encoder
     */
    static <T, V> PropertyEncoder<T> ofDefault(String key, Function<? super T, ? extends @Nullable V> getter,
                                               Encoder<V, Object> valueEncoder, Supplier<V> defaultValue) {
        return new PropertyEncoder<>(key, value -> {
            V v = getter.apply(value);
            return valueEncoder.encode(v == null ? defaultValue.get() : v);
        });
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
    public @Nullable Object encode(T value) {
        return extractor.apply(value);
    }
}
