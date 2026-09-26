package net.unit8.raoh;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PathTest {

    @Test
    void ofSingleSegment() {
        Path path = Path.of("name");
        assertEquals("/name", path.toString());
        assertEquals(List.of("name"), path.segments());
    }

    @Test
    void ofMultipleSegments() {
        Path path = Path.of("address", "city");
        assertEquals("/address/city", path.toString());
        assertEquals(List.of("address", "city"), path.segments());
    }

    @Test
    void ofEqualsAppendChain() {
        Path fromOf = Path.of("orders", "items", "0");
        Path fromAppend = Path.ROOT.append("orders").append("items").append("0");
        assertEquals(fromAppend, fromOf);
    }

    @Test
    void rootIsEmptyPointer() {
        assertEquals("", Path.ROOT.toJsonPointer());
    }

    @Test
    void emptySegmentIsKeptAsAnEmptyToken() {
        assertEquals("/", Path.of("").toJsonPointer());
    }

    @Test
    void escapesSlashAndTildeInSegments() {
        // RFC 6901: '~' is written '~0' and '/' is written '~1' inside a reference token.
        assertEquals("/a~1b", Path.of("a/b").toJsonPointer());
        assertEquals("/~0c", Path.of("~c").toJsonPointer());
        assertEquals("/~01", Path.of("~1").toJsonPointer());
        assertEquals("/~0~1x", Path.of("~/x").toJsonPointer());
    }

    @Test
    void segmentsStayUnescaped() {
        assertEquals(List.of("a/b", "~c"), Path.of("a/b", "~c").segments());
    }

    @Test
    void segmentContainingSlashDoesNotCollideWithTwoSegments() {
        assertNotEquals(Path.of("a", "b"), Path.of("a/b"));
        assertNotEquals(Path.of("a", "b").toJsonPointer(), Path.of("a/b").toJsonPointer());
    }

    @Test
    void distinctPathsHaveDistinctPointers() {
        // Every path of depth 0..2 over segments that include the separator and the escape character.
        var corpus = List.of("", "a", "/", "~", "a/b", "~1", "~0", "b");
        var paths = new ArrayList<Path>();
        paths.add(Path.ROOT);
        for (var first : corpus) {
            paths.add(Path.of(first));
            for (var second : corpus) {
                paths.add(Path.of(first, second));
            }
        }
        var distinctPaths = new HashSet<>(paths);
        var distinctPointers = new HashSet<String>();
        for (var p : distinctPaths) {
            distinctPointers.add(p.toJsonPointer());
        }
        assertEquals(distinctPaths.size(), distinctPointers.size());
    }
}
