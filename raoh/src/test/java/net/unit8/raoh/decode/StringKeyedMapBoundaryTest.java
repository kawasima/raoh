package net.unit8.raoh.decode;

import net.unit8.raoh.Err;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.map.MapDecoders;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The two places where an untyped {@code Object} becomes a {@code Map<String, ?>} —
 * {@link ObjectDecoders#map} and {@link MapDecoders#nested} — must enforce the same invariant:
 * every key is a non-null {@link String}, checked in full before any inner decoder runs, and a
 * violation is {@code type_mismatch} at the map's own path. Both are run against the same cases so
 * that neither can drift from the other.
 */
class StringKeyedMapBoundaryTest {

    private static final Path AT = Path.of("m");

    /** A boundary under test, built around a counter of how often its inner decoder ran. */
    record Boundary(String name, Function<AtomicInteger, Decoder<@Nullable Object, ?>> build) {
        @Override
        public String toString() {
            return name;
        }
    }

    static List<Boundary> boundaries() {
        return List.of(
                new Boundary("ObjectDecoders.map", calls -> ObjectDecoders.map((in, path) -> {
                    calls.incrementAndGet();
                    return Result.ok(in);
                })),
                new Boundary("MapDecoders.nested", calls -> MapDecoders.nested((in, path) -> {
                    calls.incrementAndGet();
                    return Result.ok(Map.copyOf(in));
                })));
    }

    @ParameterizedTest
    @MethodSource("boundaries")
    void acceptsStringKeysThatLookLikeOtherValues(Boundary boundary) {
        var input = Map.of("1", "a", "null", "b", "", "c");
        switch (boundary.build().apply(new AtomicInteger()).decode(input, AT)) {
            case Ok(var value) -> assertEquals(input, value);
            case Err(var issues) -> fail("unexpected " + issues);
        }
    }

    @ParameterizedTest
    @MethodSource("boundaries")
    void rejectsIntegerKey(Boundary boundary) {
        assertRejected(boundary, Map.of(1, "a"), "Integer key");
    }

    @ParameterizedTest
    @MethodSource("boundaries")
    void rejectsNonStringKeyAfterStringKeys(Boundary boundary) {
        // The first key is a String: a check that looks only at the first key lets this through.
        var input = new LinkedHashMap<Object, Object>();
        input.put("name", "a");
        input.put("age", "b");
        input.put(1, "c");
        assertRejected(boundary, input, "Integer key");
    }

    @ParameterizedTest
    @MethodSource("boundaries")
    void rejectsNullKey(Boundary boundary) {
        var input = new HashMap<Object, Object>();
        input.put(null, "a");
        assertRejected(boundary, input, "null key");
    }

    @ParameterizedTest
    @MethodSource("boundaries")
    void rejectsKeysThatWouldCollideAsStrings(Boundary boundary) {
        // String.valueOf(1) equals "1"; converting keys would silently drop one of the two values.
        var input = new LinkedHashMap<Object, Object>();
        input.put("1", "a");
        input.put(1, "b");
        assertRejected(boundary, input, "Integer key");
    }

    private static void assertRejected(Boundary boundary, Map<?, ?> input, String actual) {
        var calls = new AtomicInteger();
        switch (boundary.build().apply(calls).decode(input, AT)) {
            case Ok(var value) -> fail("expected Err, got " + value);
            case Err(var issues) -> {
                assertEquals(1, issues.asList().size(), () -> issues.toString());
                var issue = issues.asList().getFirst();
                // Reported at the map itself: a key that is not a String has no pointer of its own.
                assertEquals(AT, issue.path());
                assertEquals(ErrorCodes.TYPE_MISMATCH, issue.code());
                assertEquals(Map.of("expected", "object", "actual", actual), issue.meta());
                assertEquals("expected object with string keys, got a " + actual, issue.message());
            }
        }
        assertEquals(0, calls.get(), "keys must be checked before the inner decoder runs");
    }
}
