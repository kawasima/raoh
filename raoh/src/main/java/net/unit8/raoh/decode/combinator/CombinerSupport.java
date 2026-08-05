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
     * <p>The first component that carries an {@link InputFields} wins. Two different enumerations
     * over the same input type are legal — they might scan different subsets of the same map — and
     * equivalence between them is not decidable in general, so no attempt is made to compare them.
     * In practice the order is unobservable: each boundary module hands the same shared instance to
     * every field decoder it builds.
     *
     * @param <I>        the input type
     * @param <T>        the output type
     * @param dec        the decoder to wrap
     * @param components the combiner's component decoders
     * @return a strict decoder that rejects unknown fields
     * @throws IllegalStateException if no component carries an {@link InputFields}, which means the
     *                               input boundary is unknown and unknown fields cannot be detected
     */
    @SafeVarargs
    static <I, T> Decoder<I, T> strict(Decoder<I, T> dec, Decoder<I, ?>... components) {
        var knownFields = new LinkedHashSet<String>();
        InputFields<I> inputFields = null;

        for (var component : components) {
            if (component instanceof FieldDecoder<I, ?> fd) {
                knownFields.add(fd.fieldName());
                if (inputFields == null) {
                    inputFields = fd.inputFields().orElse(null);
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
