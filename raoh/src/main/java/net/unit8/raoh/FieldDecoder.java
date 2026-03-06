package net.unit8.raoh;

/**
 * A {@link Decoder} that is bound to a specific field name.
 *
 * <p>Returned by {@code field(name, dec)} factory methods. The field name is used by
 * {@link net.unit8.raoh.combinator.Combiner2#strict(java.util.function.BiFunction) Combiner#strict()}
 * to automatically collect the set of known fields, eliminating the need to enumerate
 * them manually in {@link Decoders#strict(Decoder, java.util.Set)}.
 *
 * @param <I> the input type
 * @param <T> the decoded output type
 */
public interface FieldDecoder<I, T> extends Decoder<I, T> {

    /** Returns the field name this decoder is bound to. */
    String fieldName();
}
