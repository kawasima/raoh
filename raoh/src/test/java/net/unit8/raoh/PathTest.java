package net.unit8.raoh;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
    void pointerDecodesBackToTheSegments() {
        // Path -> pointer must be injective. The test domain is lists of raw segment names compared
        // with List.equals, not Path.equals, so a regression in Path's own equality cannot hide a
        // collision. Decoding every pointer back to its segments proves no two lists share one.
        for (var segments : segmentLists()) {
            var path = Path.ROOT;
            for (var seg : segments) {
                path = path.append(seg);
            }
            var pointer = path.toJsonPointer();
            assertEquals(segments, decodeJsonPointer(pointer), () -> "pointer " + pointer);
        }
    }

    /** Every list of depth 0..2 over names that include the separator and the escape character. */
    private static List<List<String>> segmentLists() {
        var corpus = List.of("", "a", "b", "/", "~", "a/b", "~0", "~1", "~01", "/~");
        var lists = new ArrayList<List<String>>();
        lists.add(List.of());
        for (var first : corpus) {
            lists.add(List.of(first));
            for (var second : corpus) {
                lists.add(List.of(first, second));
            }
        }
        return lists;
    }

    /** RFC 6901 section 3/4 decoding, written independently of {@link Path}. */
    private static List<String> decodeJsonPointer(String pointer) {
        if (pointer.isEmpty()) return List.of();
        assertEquals('/', pointer.charAt(0), () -> "pointer must start with '/': " + pointer);
        var tokens = new ArrayList<String>();
        for (var token : pointer.substring(1).split("/", -1)) {
            // '~1' first, then '~0': the reverse order would turn "~01" into "/" instead of "~1".
            tokens.add(token.replace("~1", "/").replace("~0", "~"));
        }
        return tokens;
    }

    /** CandidateFailures tells an empty prefix by identity with ROOT, so no other path is empty. */
    @Test
    void rootIsTheOnlyEmptyPath() {
        assertSame(Path.ROOT, Path.ROOT.append(Path.ROOT));
        assertFalse(Path.of("a").segments().isEmpty());
        assertFalse(Path.ROOT.append("").segments().isEmpty());
        assertNotSame(Path.ROOT, Path.ROOT.append(""));
    }
}
