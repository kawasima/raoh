package net.unit8.raoh;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable path representing a location in the input structure.
 * Used for error reporting in validation issues.
 *
 * <p>Internally stored as a persistent cons-list so that {@link #append(String)} is O(1).
 * The flat {@link #segments()} list is materialised lazily on first access.
 */
public final class Path {
    /** The root path (empty segments). */
    public static final Path ROOT = new Path(null, null);

    private final Path parent;
    private final String head;

    private Path(Path parent, String head) {
        this.parent = parent;
        this.head = head;
    }

    /**
     * Appends a segment to this path in O(1).
     *
     * @param segment the segment to append (e.g., a field name or array index)
     * @return a new path with the segment appended
     */
    public Path append(String segment) {
        return new Path(this, segment);
    }

    /**
     * Appends all segments of another path to this path.
     *
     * @param other the path to append
     * @return a new combined path
     */
    public Path append(Path other) {
        var result = this;
        for (var seg : other.segments()) {
            result = result.append(seg);
        }
        return result;
    }

    /**
     * Returns the path segments as an immutable list.
     * This operation is O(depth) and allocates a list.
     *
     * @return the path segments
     */
    public List<String> segments() {
        if (parent == null) return List.of();
        var segs = new ArrayList<String>();
        var cur = this;
        while (cur.parent != null) {
            segs.add(cur.head);
            cur = cur.parent;
        }
        Collections.reverse(segs);
        return Collections.unmodifiableList(segs);
    }

    /**
     * Converts this path to a JSON Pointer string (RFC 6901).
     *
     * @return the JSON Pointer (e.g., {@code "/address/city"}), or empty string for root
     */
    public String toJsonPointer() {
        if (parent == null) return "";
        return "/" + String.join("/", segments());
    }

    @Override
    public String toString() {
        return toJsonPointer();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Path other)) return false;
        return segments().equals(other.segments());
    }

    @Override
    public int hashCode() {
        return segments().hashCode();
    }
}
