package net.unit8.raoh.encode;

import net.unit8.raoh.Presence;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One property of an object encoder: the map key it owns, and how a domain object decides what
 * is written under that key.
 *
 * <p>Analogous to {@link net.unit8.raoh.decode.combinator.CombinePart CombinePart} on the decoder side,
 * a {@code PropertyEncoder} binds together:
 *
 * <ul>
 *   <li>a map key (the column or JSON field name), fixed when the property is made</li>
 *   <li>a getter that extracts the field value from the domain object</li>
 *   <li>a value encoder that converts the extracted value to {@code Object}</li>
 * </ul>
 *
 * <p>A property owns exactly one key and writes it zero or one times: depending on the value it
 * writes the encoded value, writes {@code null}, or leaves the key out (as
 * {@link MapEncoders#optionalProperty optionalProperty} and
 * {@link MapEncoders#presenceProperty presenceProperty} do). It never writes any other key, so
 * {@link MapEncoders#object(PropertyEncoder[])} can tell from the properties alone which keys an
 * object may contain, and rejects two properties that own the same key.
 *
 * <p>Properties are made by the factories on {@link MapEncoders}; there is no way to make one
 * that writes a key other than its own.
 *
 * @param <T> the domain type from which the property is extracted
 */
public final class PropertyEncoder<T> {

    /** Returned by an emission to leave the key out. Never escapes this class. */
    private static final Object OMIT = new Object();

    private final String key;
    /** The encoded value to write under {@link #key}, {@code null}, or {@link #OMIT}. */
    private final Function<T, @Nullable Object> emission;

    private PropertyEncoder(String key, Function<T, @Nullable Object> emission) {
        this.key = key;
        this.emission = emission;
    }

    /**
     * Creates a property encoder for a non-null property value.
     *
     * @param <T>          the domain type
     * @param <V>          the property value type
     * @param key          the output map key
     * @param getter       extracts the (non-null) property value from the domain object
     * @param valueEncoder encodes the extracted value to {@code Object}
     * @return a property encoder that always writes its key
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
     * @return a property encoder that always writes its key
     */
    static <T, V> PropertyEncoder<T> ofNullable(String key, Function<? super T, ? extends @Nullable V> getter, Encoder<V, Object> valueEncoder) {
        return new PropertyEncoder<>(key, value -> {
            @Nullable V v = getter.apply(value);
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
     * @return a property encoder that always writes its key
     */
    static <T, V> PropertyEncoder<T> ofDefault(String key, Function<? super T, ? extends @Nullable V> getter,
                                               Encoder<V, Object> valueEncoder, V defaultValue) {
        return new PropertyEncoder<>(key, value -> {
            @Nullable V v = getter.apply(value);
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
     * @return a property encoder that always writes its key
     */
    static <T, V> PropertyEncoder<T> ofDefault(String key, Function<? super T, ? extends @Nullable V> getter,
                                               Encoder<V, Object> valueEncoder, Supplier<V> defaultValue) {
        return new PropertyEncoder<>(key, value -> {
            @Nullable V v = getter.apply(value);
            return valueEncoder.encode(v == null ? defaultValue.get() : v);
        });
    }

    /**
     * Creates a property encoder that leaves its key out when the getter returns {@code null}.
     *
     * @param <T>          the domain type
     * @param <V>          the property value type
     * @param key          the output map key
     * @param getter       extracts the property value; when it returns {@code null} the key is omitted
     * @param valueEncoder encodes the extracted value (only invoked when non-null) to {@code Object}
     * @return a property encoder that writes its key zero or one times
     */
    static <T, V> PropertyEncoder<T> ofOptional(String key, Function<? super T, ? extends @Nullable V> getter,
                                                Encoder<V, Object> valueEncoder) {
        return new PropertyEncoder<>(key, value -> {
            @Nullable V v = getter.apply(value);
            return v == null ? OMIT : valueEncoder.encode(v);
        });
    }

    /**
     * Creates a property encoder for a tri-state {@link Presence} value: {@link Presence.Absent}
     * leaves the key out, {@link Presence.PresentNull} writes {@code null}, and
     * {@link Presence.Present} writes the encoded value ({@code null} if it carries {@code null}).
     *
     * @param <T>          the domain type
     * @param <V>          the present value type
     * @param key          the output map key
     * @param getter       extracts the {@link Presence} field from the domain object
     * @param valueEncoder encodes the present value (only invoked for a non-null
     *                     {@link Presence.Present}) to {@code Object}
     * @return a property encoder that writes its key zero or one times
     */
    static <T, V> PropertyEncoder<T> ofPresence(String key, Function<? super T, ? extends Presence<V>> getter,
                                                Encoder<V, Object> valueEncoder) {
        return new PropertyEncoder<>(key, value -> switch (getter.apply(value)) {
            case Presence.Absent<V> _ -> OMIT;
            case Presence.PresentNull<V> _ -> null;
            case Presence.Present<V> present -> {
                @Nullable V v = present.value();
                yield v == null ? null : valueEncoder.encode(v);
            }
        });
    }

    /**
     * Returns the map key this property owns.
     *
     * @return the map key
     */
    String key() {
        return key;
    }

    /**
     * Writes this property's key into {@code out}, unless the value says to leave it out. This is
     * the only place a property writes to the output map, so it writes no key but its own.
     *
     * @param value the domain object being encoded
     * @param out   the output map to write the entry into
     */
    void encodeTo(T value, Map<String, @Nullable Object> out) {
        @Nullable Object emitted = emission.apply(value);
        if (emitted != OMIT) {
            out.put(key, emitted);
        }
    }
}
