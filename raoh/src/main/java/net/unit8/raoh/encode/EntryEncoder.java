package net.unit8.raoh.encode;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * A map-entry encoder that writes zero or more key/value pairs for a domain object into an
 * output map.
 *
 * <p>This generalizes {@link PropertyEncoder}, which always writes exactly one key. An
 * {@code EntryEncoder} may write:
 *
 * <ul>
 *   <li>exactly one entry (a plain {@link MapEncoders#property property}),</li>
 *   <li>zero or one entry (e.g. {@link MapEncoders#optionalProperty optionalProperty}, which
 *       omits the key when the value is absent), or</li>
 *   <li>zero, one, or a {@code null}-valued entry (e.g.
 *       {@link MapEncoders#presenceProperty presenceProperty}).</li>
 * </ul>
 *
 * <p>Consumed by {@link MapEncoders#object(EntryEncoder[])}, which applies each entry encoder in
 * order to a shared output map.
 *
 * @param <T> the domain type from which entries are extracted
 */
@FunctionalInterface
public interface EntryEncoder<T> {

    /**
     * Writes this encoder's key/value pair(s) for {@code value} into {@code out}.
     *
     * @param value the domain object being encoded
     * @param out   the output map to write entries into
     */
    void encodeTo(T value, Map<String, @Nullable Object> out);
}
