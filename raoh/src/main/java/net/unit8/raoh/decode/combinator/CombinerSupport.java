package net.unit8.raoh.decode.combinator;

import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.decode.Decoders;
import net.unit8.raoh.decode.FieldDecoder;
import net.unit8.raoh.decode.InputFields;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Shared strict-decoder assembly for the {@code Combiner*} records.
 *
 * <p>Every combiner needs the same two things from its components: the set of field names they
 * cover, and how to enumerate the field names actually present in the input. Both come from the
 * components that are {@link FieldDecoder}s, so the logic lives here once rather than in each of
 * the sixteen records.
 */
final class CombinerSupport {

    private CombinerSupport() {}

    /**
     * Builds a strict decoder from {@code dec}, taking the known field names and the input's field
     * enumeration from whichever {@code components} are {@link FieldDecoder}s.
     *
     * <p>Components that carry an {@link InputFields} must all carry the <em>same instance</em>.
     * An {@code InputFields} describes a boundary rather than a policy — it must enumerate every
     * field present — so a given input type has one correct implementation, and each boundary
     * module hands the same shared instance to every field decoder it builds. Two different
     * instances therefore mean two contradictory claims about what the input contains, and picking
     * either one would make the result depend on the order the components were written in. Note
     * that this is a check on identity, not on behaviour: it holds implementations to the shared
     * instance rather than trying to decide whether two of them agree, which is not decidable.
     *
     * <p>A component carrying no {@code InputFields} is not a conflict; it simply contributes its
     * field name and nothing else.
     *
     * @param <I>        the input type
     * @param <T>        the output type
     * @param dec        the decoder to wrap
     * @param components the combiner's component decoders
     * @return a strict decoder that rejects unknown fields
     * @throws IllegalStateException if no component carries an {@link InputFields}, so the input
     *                               boundary is unknown and unknown fields cannot be detected, or
     *                               if two components carry different ones
     */
    @SafeVarargs
    static <I, T> Decoder<I, T> strict(Decoder<I, T> dec, Decoder<I, ?>... components) {
        var knownFields = new LinkedHashSet<String>();
        InputFields<I> inputFields = null;

        for (var component : components) {
            if (component instanceof FieldDecoder<I, ?> fd) {
                knownFields.add(fd.fieldName());
                var candidate = fd.inputFields().orElse(null);
                if (candidate == null) {
                    continue;
                }
                if (inputFields == null) {
                    inputFields = candidate;
                } else if (inputFields != candidate) {
                    throw new IllegalStateException(
                            "strict() got two different InputFields from the components of one "
                                    + "combiner, so what counts as an unknown field would depend on "
                                    + "the order they were written in; every field of one schema must "
                                    + "share a single InputFields instance");
                }
            }
        }

        if (inputFields == null) {
            throw new IllegalStateException(
                    "strict() needs at least one component bound to a known input boundary; "
                            + "build the fields with MapDecoders.field / JsonDecoders.field, or with "
                            + "FieldDecoder.named(name, decoder, inputFields)");
        }

        return Decoders.strict(dec, Set.copyOf(knownFields), inputFields);
    }
}
