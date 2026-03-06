package net.unit8.raoh.combinator;

@FunctionalInterface
public interface Function8<A, B, C, D, E, F, G, H, R> {
    R apply(A a, B b, C c, D d, E e, F f, G g, H h);
}
