package net.unit8.raoh.combinator;

import net.unit8.raoh.Decoder;
import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;

import java.util.List;
import java.util.function.Function;

/**
 * Holds an arbitrary number of decoders for applicative-style validation with error accumulation.
 * Returned by {@link net.unit8.raoh.Decoders#combine(List)} as a fallback for more than 16 fields.
 *
 * <p>Because the arity is not statically known, {@link #map} and {@link #flatMap}
 * receive decoded values as an untyped {@code Object[]}. To use a constructor reference
 * ({@code MyRow::new}), add an {@code Object[]}-accepting constructor to your record:
 *
 * <pre>{@code
 * record MyRow(String name, Long id, ...) {
 *     MyRow(Object[] args) {
 *         this((String) args[0], (Long) args[1], ...);
 *     }
 * }
 * combine(List.of(
 *     field("name", string()),
 *     field("id",   long_()),
 *     // 17 or more fields
 * )).map(MyRow::new)
 * }</pre>
 *
 * @param <I>      the input type
 * @param decoders the decoders to combine
 */
public record CombinerList<I>(List<Decoder<I, ?>> decoders) {

    /**
     * Applies a constructor function to the decoded values.
     *
     * <p>All decoders run independently; errors are accumulated rather than short-circuited.
     *
     * @param <T> the output type
     * @param f   a function receiving all decoded values as an untyped {@code Object[]}
     * @return a decoder that runs all decoders and accumulates errors
     */
    public <T> Decoder<I, T> map(Function<Object[], T> f) {
        return (in, path) -> {
            var vals = new Validated<?>[decoders.size()];
            for (int i = 0; i < decoders.size(); i++) {
                vals[i] = Validated.fromResult(decoders.get(i).decode(in, path));
            }
            return Validated.accumulate(vals, f).toResult();
        };
    }

    /**
     * Like {@link #map}, but the constructor function may itself return a {@link Result}.
     *
     * <p>If the combined decoding succeeds, the result of {@code f} is flat-mapped;
     * any issues it produces are rebased to the current path.
     *
     * @param <T> the output type
     * @param f   a function returning a {@link Result}
     * @return a decoder that runs all decoders, accumulates errors, and flat-maps the result
     */
    public <T> Decoder<I, T> flatMap(Function<Object[], Result<T>> f) {
        return (in, path) -> {
            var vals = new Validated<?>[decoders.size()];
            for (int i = 0; i < decoders.size(); i++) {
                vals[i] = Validated.fromResult(decoders.get(i).decode(in, path));
            }
            return Validated.accumulate(vals, f).toResult()
                    .flatMap(r -> switch (r) {
                        case Ok<T> ok -> ok;
                        case Err<T> err -> Result.err(err.issues().rebase(path));
                    });
        };
    }
}
