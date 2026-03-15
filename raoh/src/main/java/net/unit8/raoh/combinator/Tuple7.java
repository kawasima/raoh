package net.unit8.raoh.combinator;

/**
 * A generic 7-element tuple for use with {@link Combiner7#map(Function7)}.
 *
 * @param <E1> type of the first element
 * @param <E2> type of the second element
 * @param <E3> type of the third element
 * @param <E4> type of the fourth element
 * @param <E5> type of the fifth element
 * @param <E6> type of the sixth element
 * @param <E7> type of the seventh element
 * @param _1 the first element
 * @param _2 the second element
 * @param _3 the third element
 * @param _4 the fourth element
 * @param _5 the fifth element
 * @param _6 the sixth element
 * @param _7 the seventh element
 * @see Tuple2
 */
public record Tuple7<E1, E2, E3, E4, E5, E6, E7>(E1 _1, E2 _2, E3 _3, E4 _4, E5 _5, E6 _6, E7 _7) {}
