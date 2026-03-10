package net.unit8.raoh;

import org.junit.jupiter.api.Test;

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
}
