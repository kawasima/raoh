package net.unit8.raoh.combinator;

/**
 * A generic 8-element tuple for use with {@link Combiner8#map(Function8)}.
 *
 * @param <E1> type of the first element
 * @param <E2> type of the second element
 * @param <E3> type of the third element
 * @param <E4> type of the fourth element
 * @param <E5> type of the fifth element
 * @param <E6> type of the sixth element
 * @param <E7> type of the seventh element
 * @param <E8> type of the eighth element
 * @param _1 the first element
 * @param _2 the second element
 * @param _3 the third element
 * @param _4 the fourth element
 * @param _5 the fifth element
 * @param _6 the sixth element
 * @param _7 the seventh element
 * @param _8 the eighth element
 * @see Tuple2
 */
public record Tuple8<E1, E2, E3, E4, E5, E6, E7, E8>(E1 _1, E2 _2, E3 _3, E4 _4, E5 _5, E6 _6, E7 _7, E8 _8) {}
