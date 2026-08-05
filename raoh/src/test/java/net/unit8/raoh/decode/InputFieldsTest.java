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

    /**
     * The extension point: a representation the library knows nothing about. {@code Row} enumerates
     * its own columns, which is all {@code strict} needs to police it.
     */
    record Row(Map<String, Object> cells) {}

    private static final InputFields<Row> ROW_FIELDS =
            row -> row == null ? List.of() : List.copyOf(row.cells().keySet());

    private static FieldDecoder<Row, Object> rowField(String name) {
        return FieldDecoder.named(name,
                (Row in, Path path) -> in.cells().containsKey(name)
                        ? Result.ok(in.cells().get(name))
                        : Result.fail(path, ErrorCodes.REQUIRED, "is required"),
                ROW_FIELDS);
    }

    @Test
    void strictWorksOnACustomBoundary() {
        var dec = Decoders.combine(rowField("name"), rowField("age"))
                .strict((name, age) -> name + ":" + age);

        switch (dec.decode(new Row(Map.of("name", "Taro", "age", 20)))) {
            case Ok(_) -> { }
            case Err(var issues) -> fail("Expected Ok, got: " + issues.asList());
        }

        switch (dec.decode(new Row(Map.of("name", "Taro", "age", 20, "extra", true)))) {
            case Ok(var v) -> fail("Expected Err, got Ok: " + v);
            case Err(var issues) -> {
                var issue = issues.asList().getFirst();
                assertEquals(ErrorCodes.UNKNOWN_FIELD, issue.code());
                assertEquals("/extra", issue.path().toJsonPointer());
            }
        }
    }

    /**
     * Two {@link InputFields} over the same input type are two contradictory claims about what that
     * input contains. Taking either one would make strictness depend on the order the fields were
     * written in, so assembly fails instead.
     */
    @Test
    void mixedInputFieldsDoNotMakeStrictnessDependOnComponentOrder() {
        InputFields<Map<String, Object>> nameOnly =
                in -> in.containsKey("name") ? List.of("name") : List.of();

        var withOwnScan = FieldDecoder.named("name",
                (Map<String, Object> in, Path path) -> Result.ok((String) in.get("name")), nameOnly);
        var fromFactory = field("age", int_());

        var forward = assertThrows(IllegalStateException.class,
                () -> combine(withOwnScan, fromFactory).strict((name, age) -> name + age));
        var reversed = assertThrows(IllegalStateException.class,
                () -> combine(fromFactory, withOwnScan).strict((age, name) -> age + name));

        assertTrue(forward.getMessage().contains("two different InputFields"), forward.getMessage());
        assertEquals(forward.getMessage(), reversed.getMessage(),
                "both orders must fail the same way");
    }

    @Test
    void componentsSharingOneInstanceAreOrderIndependent() {
        var input = Map.<String, Object>of("name", "Taro", "age", 20, "extra", true);

        var forward = combine(field("name", string()), field("age", int_()))
                .strict((name, age) -> name + age);
        var reversed = combine(field("age", int_()), field("name", string()))
                .strict((age, name) -> age + name);

        assertEquals(unknownFieldPointers(forward.decode(input)),
                unknownFieldPointers(reversed.decode(input)));
        assertEquals(List.of("/extra"), unknownFieldPointers(forward.decode(input)));
    }

    /**
     * The sixteen combiner records were rewired mechanically, and a component left out of the
     * {@code CombinerSupport.strict} call would show up as a field going unrecognised. Sixteen is
     * the widest, so it is the one worth exercising: every field must be known, and the extra one
     * must still be rejected.
     */
    @Test
    void theWidestCombinerPassesEveryComponentToStrict() {
        var names = List.of("f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8",
                "f9", "f10", "f11", "f12", "f13", "f14", "f15", "f16");

        var dec = Decoders.combine(
                field(names.get(0), string()), field(names.get(1), string()),
                field(names.get(2), string()), field(names.get(3), string()),
                field(names.get(4), string()), field(names.get(5), string()),
                field(names.get(6), string()), field(names.get(7), string()),
                field(names.get(8), string()), field(names.get(9), string()),
                field(names.get(10), string()), field(names.get(11), string()),
                field(names.get(12), string()), field(names.get(13), string()),
                field(names.get(14), string()), field(names.get(15), string())
        ).strict((a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p) -> "ok");

        var complete = new java.util.LinkedHashMap<String, Object>();
        names.forEach(n -> complete.put(n, "v"));

        switch (dec.decode(Map.copyOf(complete))) {
            case Ok(_) -> { }
            case Err(var issues) -> fail("every field should be known, got: " + issues.asList());
        }

        complete.put("extra", "v");
        assertEquals(List.of("/extra"), unknownFieldPointers(dec.decode(Map.copyOf(complete))));
    }

    private static List<String> unknownFieldPointers(Result<?> result) {
        return switch (result) {
            case Ok(var v) -> throw new AssertionError("Expected Err, got Ok: " + v);
            case Err(var issues) -> issues.asList().stream()
                    .filter(i -> i.code().equals(ErrorCodes.UNKNOWN_FIELD))
                    .map(i -> i.path().toJsonPointer())
                    .toList();
        };
    }
}
