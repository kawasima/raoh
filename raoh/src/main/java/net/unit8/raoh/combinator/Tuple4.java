package net.unit8.raoh.combinator;

/**
 * A generic 4-element tuple for use with {@link Combiner4#map(Function4)}.
 *
 * @param <E1> type of the first element
 * @param <E2> type of the second element
 * @param <E3> type of the third element
 * @param <E4> type of the fourth element
 * @param _1 the first element
 * @param _2 the second element
 * @param _3 the third element
 * @param _4 the fourth element
 * @see Tuple2
 */
public record Tuple4<E1, E2, E3, E4>(E1 _1, E2 _2, E3 _3, E4 _4) {}
