package net.unit8.raoh;

import java.util.ArrayList;
import java.util.List;

public record Path(List<String> segments) {
    public static final Path ROOT = new Path(List.of());

    public Path append(String segment) {
        var copy = new ArrayList<>(segments);
        copy.add(segment);
        return new Path(List.copyOf(copy));
    }

    public Path append(Path other) {
        var copy = new ArrayList<>(segments);
        copy.addAll(other.segments());
        return new Path(List.copyOf(copy));
    }

    public String toJsonPointer() {
        return segments.isEmpty() ? "" : "/" + String.join("/", segments);
    }

    @Override
    public String toString() {
        return toJsonPointer();
    }
}
