package net.unit8.raoh.decode.combinator;

@FunctionalInterface
public interface Function5<A, B, C, D, E, R> {
    R apply(A a, B b, C c, D d, E e);
}
