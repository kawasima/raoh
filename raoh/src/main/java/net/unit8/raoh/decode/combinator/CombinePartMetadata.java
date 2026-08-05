package net.unit8.raoh.decode.combinator;

import net.unit8.raoh.decode.InputFields;

import java.util.Optional;

/**
 * The schema facts {@code strict()} needs from a combine component.
 *
 * <p>Deliberately not part of {@link CombinePart}: callers have no use for these, and every
 * permitted implementation of the sealed {@code CombinePart} also implements this, so
 * {@link CombinerSupport} can always recover them. Interface methods are implicitly public, so it
 * is the type rather than the methods that is hidden here.
 *
 * @param <I> the input type
 */
interface CombinePartMetadata<I> {

    /** @return the fields this component declares it consumes */
    DeclaredFields declaredFields();

    /** @return how to enumerate the input's field names, when this component's boundary knows */
    Optional<InputFields<I>> inputFields();
}
