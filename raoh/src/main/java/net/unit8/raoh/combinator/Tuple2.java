package net.unit8.raoh.combinator;

/**
 * A generic 2-element tuple for use with {@link Combiner2#map(java.util.function.BiFunction)}.
 *
 * <p>Pass {@code Tuple2::new} as the mapping function to capture decoded values
 * without defining a dedicated record, then destructure with pattern matching:
 *
 * <pre>{@code
 * Decoder<Map<String, Object>, Tuple2<String, Integer>> dec = combine(
 *     field("name", string()),
 *     field("age", int_())
 * ).map(Tuple2::new);
 *
 * switch (dec.decode(input)) {
 *     case Ok(Tuple2(var name, var age)) -> ...
 *     case Err(var issues) -> ...
 * }
 * }</pre>
 *
 * @param <E1> type of the first element
 * @param <E2> type of the second element
 * @param _1 the first element
 * @param _2 the second element
 */
public record Tuple2<E1, E2>(E1 _1, E2 _2) {}
