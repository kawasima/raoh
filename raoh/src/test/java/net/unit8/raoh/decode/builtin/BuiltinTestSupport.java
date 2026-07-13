package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.Err;
import net.unit8.raoh.Issue;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;

import org.jspecify.annotations.Nullable;

/**
 * Test-only helpers for exercising builtin decoders directly at the root path,
 * independent of the {@code Map}/JSON boundary.
 */
final class BuiltinTestSupport {

    private BuiltinTestSupport() {}

    /**
     * Decodes {@code input} at {@link Path#ROOT} and returns the decoded value,
     * throwing an {@link AssertionError} if the decoder failed.
     *
     * @param <T>     the decoded value type
     * @param decoder the decoder under test
     * @param input   the raw input value
     * @return the successfully decoded value
     */
    static <T extends @Nullable Object> T decodeOk(Decoder<@Nullable Object, T> decoder, @Nullable Object input) {
        Result<T> result = decoder.decode(input, Path.ROOT);
        if (result instanceof Ok<T> ok) {
            return ok.value();
        }
        throw new AssertionError("expected Ok but got: " + result);
    }

    /**
     * Decodes {@code input} at {@link Path#ROOT} and returns the first {@link Issue},
     * throwing an {@link AssertionError} if the decoder succeeded.
     *
     * @param decoder the decoder under test
     * @param input   the raw input value
     * @return the first issue reported by the decoder
     */
    static Issue decodeErr(Decoder<@Nullable Object, ?> decoder, @Nullable Object input) {
        Result<?> result = decoder.decode(input, Path.ROOT);
        if (result instanceof Err<?> err) {
            return err.issues().asList().getFirst();
        }
        throw new AssertionError("expected Err but got: " + result);
    }
}
