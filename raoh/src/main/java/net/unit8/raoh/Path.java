package net.unit8.raoh;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable path representing a location in the input structure.
 * Used for error reporting in validation issues.
 *
 * <p>Internally stored as a persistent cons-list so that {@link #append(String)} is O(1).
 * {@link #segments()} builds the flat list on each call, in time proportional to the depth.
 * {@link #ROOT} is the only path with no segments.
 */
public final class Path {
    /** The root path (empty segments). */
    public static final Path ROOT = new Path(null, null);

    private final @Nullable Path parent;
    private final @Nullable String head;

    private Path(@Nullable Path parent, @Nullable String head) {
        this.parent = parent;
        this.head = head;
    }

    /**
     * Creates a path from one or more segments.
     *
     * <p>This is a shorthand for {@code Path.ROOT.append(first).append(...)}.
     *
     * @param first the first segment (e.g., a field name)
     * @param rest  additional segments
     * @return a path with all segments appended to root
     */
    public static Path of(String first, String... rest) {
        Path p = ROOT.append(first);
        for (String segment : rest) {
            p = p.append(segment);
        }
        return p;
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
     * <p>Each segment is written as a reference token: {@code ~} becomes {@code ~0} and {@code /}
     * becomes {@code ~1}. A segment {@code "a/b"} is therefore {@code "/a~1b"}, distinct from the
     * two segments {@code "a"} and {@code "b"} ({@code "/a/b"}), so two different paths never share
     * a pointer. {@link #segments()} keeps the raw, unescaped names.
     *
     * @return the JSON Pointer (e.g., {@code "/address/city"}), or empty string for root
     */
    public String toJsonPointer() {
        if (parent == null) return "";
        var sb = new StringBuilder();
        for (var seg : segments()) {
            sb.append('/').append(escapeReferenceToken(seg));
        }
        return sb.toString();
    }

    private static String escapeReferenceToken(String segment) {
        // '~' first: escaping '/' first would turn the '~' of the '~1' it produces into '~0'.
        return segment.replace("~", "~0").replace("/", "~1");
    }

    @Override
    public String toString() {
        return toJsonPointer();
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof Path other)) return false;
        return segments().equals(other.segments());
    }

    @Override
    public int hashCode() {
        return segments().hashCode();
    }
}
