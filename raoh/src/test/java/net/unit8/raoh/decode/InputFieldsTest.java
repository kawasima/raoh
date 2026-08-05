package net.unit8.raoh.decode;

import net.unit8.raoh.Err;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.unit8.raoh.decode.ObjectDecoders.int_;
import static net.unit8.raoh.decode.ObjectDecoders.string;
import static net.unit8.raoh.decode.map.MapDecoders.combine;
import static net.unit8.raoh.decode.map.MapDecoders.field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link InputFields} is how {@code strict} reaches an input boundary it knows nothing about.
 * A {@link FieldDecoder} carries one, composition preserves it, and a combiner with no boundary at
 * all refuses to be made strict rather than silently accepting everything.
 */
class InputFieldsTest {

    private static final Map<String, Object> INPUT = Map.of("name", "Taro", "age", 20);

    @Test
    void mapFieldsEnumeratesTheKeys() {
        assertEquals(Set.of("name", "age"),
                Set.copyOf(net.unit8.raoh.decode.map.MapDecoders.MAP_FIELDS.fieldNames(INPUT)));
    }

    @Test
    void mapFieldsTreatsANullMapAsHavingNoFields() {
        assertEquals(List.of(), net.unit8.raoh.decode.map.MapDecoders.MAP_FIELDS.fieldNames(null));
    }

    @Test
    void aFieldFactoryCarriesTheBoundary() {
        var dec = field("age", int_());
        assertTrue(dec.inputFields().isPresent());
    }

    @Test
    void compositionPreservesTheBoundary() {
        var refined = field("age", int_()).refine(a -> a >= 0, "must_be_positive", "must be positive");
        var mapped = field("age", int_()).map(a -> a + 1);
        var piped = field("age", int_()).pipe((a, path) -> Result.ok(a.toString()));

        assertTrue(refined.inputFields().isPresent());
        assertTrue(mapped.inputFields().isPresent());
        assertTrue(piped.inputFields().isPresent());
    }

    @Test
    void theTwoArgumentBinderCarriesNoBoundary() {
        var dec = FieldDecoder.named("age", (Map<String, Object> in, Path path) -> Result.ok(1));
        assertTrue(dec.inputFields().isEmpty());
    }

    @Test
    void theThreeArgumentBinderCarriesOne() {
        InputFields<Map<String, Object>> fields = in -> List.copyOf(in.keySet());
        var dec = FieldDecoder.named("age", (Map<String, Object> in, Path path) -> Result.ok(1), fields);
        assertEquals(fields, dec.inputFields().orElseThrow());
    }

    /**
     * A combiner whose components carry no boundary cannot tell an unknown field from a known one.
     * Failing at assembly makes that a visible programming error instead of a decoder that quietly
     * checks nothing.
     */
    @Test
    void strictRefusesACombinerWithNoBoundary() {
        var nameless = combine(
                (Decoder<Map<String, Object>, String>) (in, path) -> Result.ok("Taro"),
                (Decoder<Map<String, Object>, Integer>) (in, path) -> Result.ok(20));

        var thrown = assertThrows(IllegalStateException.class,
                () -> nameless.strict((name, age) -> name + age));
        assertTrue(thrown.getMessage().contains("input boundary"), thrown.getMessage());

        assertThrows(IllegalStateException.class,
                () -> nameless.strictFlatMap((name, age) -> Result.ok(name + age)));
    }

    @Test
    void oneBoundaryAwareComponentIsEnough() {
        var mixed = combine(
                field("name", string()),
                FieldDecoder.named("age", (Map<String, Object> in, Path path) ->
                        Result.ok((Integer) in.get("age"))));

        var dec = mixed.strict((name, age) -> name + age);
        switch (dec.decode(INPUT)) {
            case Ok(_) -> { }
            case Err(var issues) -> fail("Expected Ok, got: " + issues.asList());
        }

        switch (dec.decode(Map.of("name", "Taro", "age", 20, "extra", true))) {
            case Ok(var v) -> fail("Expected Err, got Ok: " + v);
            case Err(var issues) -> assertEquals(ErrorCodes.UNKNOWN_FIELD,
                    issues.asList().getFirst().code());
        }
    }

    @Test
    void strictAcceptsACustomBoundary() {
        // Only "name" is visible to this scan, so "age" is never reported even though it is present.
        InputFields<Map<String, Object>> nameOnly =
                in -> in.containsKey("name") ? List.of("name") : List.of();

        var dec = Decoders.strict(
                combine(field("name", string()), field("age", int_())).map((n, a) -> n + a),
                Set.of("name"),
                nameOnly);

        switch (dec.decode(INPUT)) {
            case Ok(_) -> { }
            case Err(var issues) -> fail("Expected Ok, got: " + issues.asList());
        }
    }
}
