package net.unit8.raoh;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static net.unit8.raoh.map.MapDecoders.*;
import static net.unit8.raoh.ObjectDecoders.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultToStringTest {

    @Test
    void okToString() {
        Result<String> result = Result.ok("hello");
        assertEquals("Ok[hello]", result.toString());
    }

    @Test
    void okToStringNull() {
        Result<String> result = Result.ok(null);
        assertEquals("Ok[null]", result.toString());
    }

    @Test
    void errToStringRootPath() {
        Result<String> result = string().decode(null, Path.ROOT);
        assertEquals("Err[/: is required]", result.toString());
    }

    @Test
    void errToStringFieldPath() {
        Result<String> result = field("name", string()).decode(Map.of("name", 42), Path.ROOT);
        assertEquals("Err[/name: expected string]", result.toString());
    }

    @Test
    void errToStringMultipleIssues() {
        Decoder<Map<String, Object>, ?> dec = Decoders.combine(
                field("a", string()),
                field("b", string())
        ).map((a, b) -> a + b);
        Result<?> result = dec.decode(Map.of(), Path.ROOT);
        assertEquals("Err[/a: is required, /b: is required]", result.toString());
    }
}
