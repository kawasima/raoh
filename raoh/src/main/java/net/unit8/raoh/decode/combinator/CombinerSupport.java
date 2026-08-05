package net.unit8.raoh.decode.combinator;

import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.decode.Decoders;
import net.unit8.raoh.decode.InputFields;

import java.util.Set;

/**
 * Shared strict-decoder assembly for the {@code Combiner*} records.
 *
 * <p>{@code strict()} needs two independent facts, and they come from different places: what the
 * schema declares it consumes, and how the input's own fields can be enumerated. Neither implies
 * the other — jOOQ knows its field names but has no scanner — so they are collected separately and
 * their absence is reported separately.
 */
final class CombinerSupport {

    private CombinerSupport() {}

    /**
     * Builds a strict decoder from {@code dec}, taking the declared field set and the input's field
     * enumeration from {@code parts}.
     *
     * <p>Every permitted implementation of the sealed {@link CombinePart} also implements
     * {@link CombinePartMetadata}, so the cast below cannot fail.
     *
     * @param <I>   the input type
     * @param <T>   the output type
     * @param dec   the decoder to wrap
     * @param parts the combiner's components
     * @return a strict decoder that rejects fields the schema does not declare
     * @throws IllegalStateException if any component's declared field set is unknown, or if no
     *                               component can enumerate the input's fields
     */
    @SafeVarargs
    static <I, T> Decoder<I, T> strict(Decoder<I, T> dec, CombinePart<I, ?>... parts) {
        DeclaredFields declared = new DeclaredFields.Known(Set.of());
        InputFields<I> inputFields = null;

        for (var part : parts) {
            @SuppressWarnings("unchecked")
            var metadata = (CombinePartMetadata<I>) part;
            declared = DeclaredFields.merge(declared, metadata.declaredFields());

            var candidate = metadata.inputFields().orElse(null);
            if (candidate == null) {
                continue;
            }
            if (inputFields == null) {
                inputFields = candidate;
            } else if (inputFields != candidate) {
                throw new IllegalStateException(
                        "strict() got two different InputFields from the components of one "
                                + "combiner, so what counts as an unknown field would depend on the "
                                + "order they were written in; every field of one schema must share "
                                + "a single InputFields instance");
            }
        }

        // Checked before the scanner so that a flat component is always reported as such, rather
        // than as a missing scanner it was never going to supply.
        if (!(declared instanceof DeclaredFields.Known(var names))) {
            throw new IllegalStateException(
                    "strict() cannot be applied because one or more combine components do not "
                            + "declare the fields they consume; a flat(...) component reads the "
                            + "whole input opaquely, so the schema's field set is unknown");
        }

        if (inputFields == null) {
            throw new IllegalStateException(
                    "strict() cannot be applied because no combine component provides input-field "
                            + "enumeration; build the fields with MapDecoders.field / "
                            + "JsonDecoders.field, or with CombinePart.named(name, decoder, inputFields)");
        }

        return Decoders.strict(dec, names, inputFields);
    }
}
