package net.unit8.raoh.builtin;

import net.unit8.raoh.*;

public class BoolDecoder<I> implements Decoder<I, Boolean> {

    private final Decoder<I, Boolean> inner;

    public BoolDecoder(Decoder<I, Boolean> inner) {
        this.inner = inner;
    }

    @Override
    public Result<Boolean> decode(I in, Path path) {
        return inner.decode(in, path);
    }
}
