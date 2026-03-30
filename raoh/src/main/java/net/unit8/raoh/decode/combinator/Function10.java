package net.unit8.raoh.decode.combinator;

/**
 * A function that accepts 10 arguments and produces a result.
 *
 * @param <A> the type of the first argument
 * @param <B> the type of the second argument
 * @param <C> the type of the third argument
 * @param <D> the type of the fourth argument
 * @param <E> the type of the fifth argument
 * @param <F> the type of the sixth argument
 * @param <G> the type of the seventh argument
 * @param <H> the type of the eighth argument
 * @param <J> the type of the ninth argument
 * @param <K> the type of the tenth argument
 * @param <R> the type of the result
 */
@FunctionalInterface
public interface Function10<A, B, C, D, E, F, G, H, J, K, R> {
    /**
     * Applies this function to the given arguments.
     *
     * @param a the first argument
     * @param b the second argument
     * @param c the third argument
     * @param d the fourth argument
     * @param e the fifth argument
     * @param f the sixth argument
     * @param g the seventh argument
     * @param h the eighth argument
     * @param j the ninth argument
     * @param k the tenth argument
     * @return the result
     */
    R apply(A a, B b, C c, D d, E e, F f, G g, H h, J j, K k);
}
