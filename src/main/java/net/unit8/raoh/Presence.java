package net.unit8.raoh;

public sealed interface Presence<T> {
    record Absent<T>() implements Presence<T> {}
    record PresentNull<T>() implements Presence<T> {}
    record Present<T>(T value) implements Presence<T> {}
}
