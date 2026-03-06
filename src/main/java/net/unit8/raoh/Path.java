package net.unit8.raoh;

import java.util.ArrayList;
import java.util.List;

/**
 * An immutable path representing a location in the input structure.
 * Used for error reporting in validation issues.
 *
 * @param segments the path segments (e.g., field names or array indices)
 */
public record Path(List<String> segments) {
    /** The root path (empty segments). */
    public static final Path ROOT = new Path(List.of());

    /**
     * Appends a segment to this path.
     *
     * @param segment the segment to append (e.g., a field name or array index)
     * @return a new path with the segment appended
     */
    public Path append(String segment) {
        var copy = new ArrayList<>(segments);
        copy.add(segment);
        return new Path(List.copyOf(copy));
    }

    /**
     * Appends all segments of another path to this path.
     *
     * @param other the path to append
     * @return a new combined path
     */
    public Path append(Path other) {
        var copy = new ArrayList<>(segments);
        copy.addAll(other.segments());
        return new Path(List.copyOf(copy));
    }

    /**
     * Converts this path to a JSON Pointer string (RFC 6901).
     *
     * @return the JSON Pointer (e.g., {@code "/address/city"}), or empty string for root
     */
    public String toJsonPointer() {
        return segments.isEmpty() ? "" : "/" + String.join("/", segments);
    }

    @Override
    public String toString() {
        return toJsonPointer();
    }
}
