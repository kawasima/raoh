package net.unit8.raoh.decode;

import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * Enumerates the field names present in an input of type {@code I}.
 *
 * <p>This is what lets {@link Decoders#strict(Decoder, java.util.Set, InputFields) strict} reject
 * unknown fields without knowing anything about the input representation. Each boundary module
 * supplies one shared instance — {@code MapDecoders} for {@code Map<String, Object>},
 * {@code JsonDecoders} for Jackson's {@code JsonNode} — and its field factories hand it to every
 * {@link FieldDecoder} they build, so a combiner can recover it from its components.
 *
 * <p>Implement this to bring {@code strict} to an input representation the library does not cover.
 * Return an empty collection for an input that has no field structure at all, including
 * {@code null}; that is not an error, it simply means there is nothing to reject.
 *
 * <p><strong>An implementation must return every field present in the input.</strong> This
 * describes a boundary, not a policy: it answers "what does this input contain", not "what should
 * be checked". Returning a subset would make {@code strict} accept the fields it left out, which is
 * the failure this abstraction exists to prevent. To exempt fields from the check, add them to the
 * known-field set instead.
 *
 * <p>It follows that a given input type has one correct implementation, so a combiner requires all
 * of its components to carry the same instance — see {@code Combiner#strict}.
 *
 * @param <I> the input type
 */
@FunctionalInterface
public interface InputFields<I extends @Nullable Object> {

    /**
     * Returns the field names present in {@code in}.
     *
     * @param in the input to inspect
     * @return every field name present, or an empty collection when {@code in} has no fields
     */
    Collection<String> fieldNames(I in);
}
