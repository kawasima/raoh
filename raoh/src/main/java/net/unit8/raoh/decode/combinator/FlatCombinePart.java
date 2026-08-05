package net.unit8.raoh.decode.combinator;

import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.decode.InputFields;

import java.util.Optional;

/**
 * A component that reads the same whole input as its siblings, rather than one field of it.
 *
 * <p>The decoder is opaque: there is no way to ask it which fields it will read, so the declared
 * set is {@link DeclaredFields.Unknown} and any combiner containing one is refused by
 * {@code strict()}. Carrying no {@link InputFields} costs nothing, because that refusal happens
 * first.
 *
 * @param <I>     the input type
 * @param <T>     the decoded output type
 * @param decoder reads the same input the sibling components read
 */
record FlatCombinePart<I, T>(Decoder<I, T> decoder)
        implements CombinePart<I, T>, CombinePartMetadata<I> {

    @Override
    public Result<T> decode(I input, Path path) {
        return decoder.decode(input, path);
    }

    @Override
    public DeclaredFields declaredFields() {
        return new DeclaredFields.Unknown();
    }

    @Override
    public Optional<InputFields<I>> inputFields() {
        return Optional.empty();
    }
}
