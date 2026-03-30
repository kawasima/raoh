package net.unit8.raoh.encoder;

import java.util.function.Function;

/**
 * A function that encodes a domain object {@code T} into an external representation {@code O}.
 *
 * <p>This is the dual of {@link net.unit8.raoh.Decoder Decoder}: where a decoder converts
 * an untrusted external value into a validated domain object (and may fail), an encoder
 * converts a trusted domain object into an external representation and never fails.
 *
 * <p>Encoders compose via {@link #contramap} (pre-process the input) and
 * {@link #andThen} (post-process the output):
 *
 * <pre>{@code
 * // Pre-process: unwrap a value object before encoding
 * Encoder<UserId, Object> userIdEncoder = long_().contramap(UserId::value);
 *
 * // Post-process: convert to a different output type
 * Encoder<Long, String> longString = long_().andThen(o -> o.toString());
 * }</pre>
 *
 * @param <T> the domain type to encode from
 * @param <O> the external representation type to encode to
 */
@FunctionalInterface
public interface Encoder<T, O> {

    /**
     * Encodes the given domain value into an external representation.
     *
     * @param value the domain value to encode; must not be {@code null}
     * @return the encoded external representation
     */
    O encode(T value);

    /**
     * Returns an encoder that first applies {@code f} to its input, then encodes the result.
     *
     * <p>This is the standard {@code contramap} operation for encoders.
     * Use it to unwrap value objects before encoding:
     *
     * <pre>{@code
     * Encoder<UserId, Object> enc = long_().contramap(UserId::value);
     * }</pre>
     *
     * @param <S> the new input type
     * @param f   the function to apply to the input before encoding
     * @return a new encoder whose input type is {@code S}
     */
    default <S> Encoder<S, O> contramap(Function<S, T> f) {
        return value -> this.encode(f.apply(value));
    }

    /**
     * Returns an encoder that encodes with {@code this} and then transforms the output with
     * {@code next}.
     *
     * <pre>{@code
     * Encoder<Instant, String> enc = iso8601().andThen(o -> o.toString().replace("T", " "));
     * }</pre>
     *
     * @param <P>  the final output type
     * @param next the encoder to apply to the output of {@code this}
     * @return a composed encoder
     */
    default <P> Encoder<T, P> andThen(Encoder<O, P> next) {
        return value -> next.encode(this.encode(value));
    }
}
