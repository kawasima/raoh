package net.unit8.raoh.decode.combinator;

import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.decode.InputFields;

import java.util.Optional;
import java.util.Set;

/**
 * A component that consumes one named field.
 *
 * <p>Appending the field name lives here rather than in the boundary factories, so that a
 * standalone decode and a combined one produce the same paths. Extraction stays in {@code decoder},
 * which is the only part that knows whether the input is a map, a JSON object or a record.
 *
 * @param <I>         the input type
 * @param <T>         the decoded output type
 * @param name        the field name consumed
 * @param decoder     reads the field, receiving the path with {@code name} already appended
 * @param inputFields how to enumerate the input's fields, when the boundary is known
 */
record NamedCombinePart<I, T>(String name, Decoder<I, T> decoder, Optional<InputFields<I>> inputFields)
        implements CombinePart<I, T>, CombinePartMetadata<I> {

    @Override
    public Result<T> decode(I input, Path path) {
        return decoder.decode(input, path.append(name));
    }

    @Override
    public DeclaredFields declaredFields() {
        return new DeclaredFields.Known(Set.of(name));
    }
}
