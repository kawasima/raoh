package net.unit8.raoh.builtin;

import net.unit8.raoh.*;

/**
 * A decoder for boolean values.
 *
 * @param <I> the input type
 */
public class BoolDecoder<I> implements Decoder<I, Boolean> {

    private final Decoder<I, Boolean> inner;

    /**
     * Creates a new BoolDecoder wrapping the given inner decoder.
     *
     * @param inner the underlying decoder that produces boolean values
     */
    public BoolDecoder(Decoder<I, Boolean> inner) {
        this.inner = inner;
    }

    /**
     * Decodes the input into a boolean value.
     *
     * @param in   the input to decode
     * @param path the current path for error reporting
     * @return a {@link Result} containing the decoded boolean or validation errors
     */
    @Override
    public Result<Boolean> decode(I in, Path path) {
        return inner.decode(in, path);
    }
}
