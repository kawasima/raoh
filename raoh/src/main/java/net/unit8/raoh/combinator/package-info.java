/**
 * Applicative-style combinator types for accumulating validation errors across multiple decoders.
 *
 * <p>Use {@link net.unit8.raoh.Decoders#combine} to create combiners.
 *
 * <p>This package also provides generic tuple types ({@link net.unit8.raoh.combinator.Tuple2}
 * through {@link net.unit8.raoh.combinator.Tuple8}) for capturing combiner results without
 * defining a dedicated record. These are especially useful with Java 21+ record patterns:
 *
 * <pre>{@code
 * var dec = combine(field("name", string()), field("age", int_())).map(Tuple2::new);
 * switch (dec.decode(input)) {
 *     case Ok(Tuple2(var name, var age)) -> ...
 *     case Err(var issues) -> ...
 * }
 * }</pre>
 */
package net.unit8.raoh.combinator;
