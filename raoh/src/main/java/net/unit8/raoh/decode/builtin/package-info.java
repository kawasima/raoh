/**
 * Built-in typed decoders with fluent constraint APIs.
 *
 * <p>These decoders are typically created via factory methods in
 * {@link net.unit8.raoh.decode.ObjectDecoders} or {@code net.unit8.raoh.json.JsonDecoders}.
 * For map-structure utilities ({@code field()}, {@code combine()}, etc.),
 * see {@link net.unit8.raoh.decode.map.MapDecoders}.
 *
 * <p><strong>These classes are {@code final}: extend them by composition, not by subclassing.</strong>
 * Each one wraps an inner {@link net.unit8.raoh.decode.Decoder} and builds every constraint through
 * a private {@code chain} helper, so a subclass could neither add a constraint in the same style nor
 * intercept the ones already there — overriding {@code flatMapWithPath} would not have caught
 * {@code minLength()}, {@code email()} or any other built-in constraint.
 *
 * <p>To wrap or instrument a decoder, take the inner one and hand a new one back. Every class here
 * has a public constructor for exactly that, and {@link
 * net.unit8.raoh.decode.builtin.StringDecoder#from(net.unit8.raoh.decode.Decoder) StringDecoder.from}
 * is the same route spelled as a factory:
 *
 * <pre>{@code
 * StringDecoder<I> traced(StringDecoder<I> dec) {
 *     return StringDecoder.from((in, path) -> {
 *         log(path);
 *         return dec.decode(in, path);
 *     });
 * }
 * }</pre>
 *
 * <p>To add a domain rule, use {@link net.unit8.raoh.decode.Decoder#refine(java.util.function.Predicate,
 * String, String) refine}: it returns the same decoder type, so it composes with the built-in
 * constraints in either order.
 */
@NullMarked
package net.unit8.raoh.decode.builtin;

import org.jspecify.annotations.NullMarked;
