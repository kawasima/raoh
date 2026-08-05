package net.unit8.raoh.decode.combinator;

import net.unit8.raoh.Err;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.decode.Decoders;
import net.unit8.raoh.decode.InputFields;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.unit8.raoh.decode.ObjectDecoders.int_;
import static net.unit8.raoh.decode.ObjectDecoders.string;
import static net.unit8.raoh.decode.map.MapDecoders.combine;
import static net.unit8.raoh.decode.map.MapDecoders.field;
import static net.unit8.raoh.decode.map.MapDecoders.flat;
import static net.unit8.raoh.decode.map.MapDecoders.nullableField;
import static net.unit8.raoh.decode.map.MapDecoders.optionalField;
import static net.unit8.raoh.decode.map.MapDecoders.optionalNullableField;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A combine component is not a {@link Decoder}, which is what stops a schema from losing the field
 * it declares. {@code strict()} then needs two independent facts — what the schema declares, and
 * how the input's own fields can be enumerated — and reports each absence separately.
 */
class CombinePartTest {

    private static final Map<String, Object> INPUT = Map.of("name", "Taro", "age", 20);

    private static void assertAccepted(Result<?> result) {
        switch (result) {
            case Ok(_) -> { }
            case Err(var issues) -> fail("Expected Ok, got: " + issues.asList());
        }
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

    // --- the guarantee ---

    /**
     * The reason for the whole design. Wrapping a decoder used to erase the field it declared, and
     * {@code strict()} would then reject a field the schema plainly contains. A wrapper cannot take
     * a component at all now, so this test is written the way the code has to be written instead:
     * wrapping happens inside the component, and the declaration survives.
     */
    @Test
    void wrappingHappensInsideAComponentSoTheDeclarationSurvives() {
        var seen = new java.util.ArrayList<String>();

        // The wrapper takes the value decoder, not the component — which is the only thing that
        // typechecks. `traced(field("age", int_()))` does not compile.
        Decoder<Object, Integer> traced = (in, path) -> {
            seen.add(path.toJsonPointer());
            return int_().decode(in, path);
        };

        var dec = combine(field("name", string()), field("age", traced))
                .strict((name, age) -> name + age);

        assertAccepted(dec.decode(INPUT));
        assertEquals(List.of("/age"), seen);
        assertEquals(List.of("/extra"),
                unknownFieldPointers(dec.decode(Map.of("name", "Taro", "age", 20, "extra", true))));
    }

    // --- path contract ---

    @Test
    void aNamedComponentAppendsItsOwnName() {
        var part = field("age", int_());

        assertEquals(20, part.decode(INPUT).getOrThrow());
        switch (part.decode(Map.of("age", "not-a-number"))) {
            case Ok(var v) -> fail("Expected Err, got Ok: " + v);
            case Err(var issues) -> assertEquals("/age", issues.asList().getFirst().path().toJsonPointer());
        }
    }

    @Test
    void standaloneAndCombinedUseShareOnePathContract() {
        var part = field("age", int_());
        var combined = combine(field("name", string()), part).map((name, age) -> name + age);

        var standalone = switch (part.decode(Map.of("age", "nope"))) {
            case Ok(var v) -> throw new AssertionError(v);
            case Err(var issues) -> issues.asList().getFirst().path().toJsonPointer();
        };
        var viaCombiner = switch (combined.decode(Map.of("name", "Taro", "age", "nope"))) {
            case Ok(var v) -> throw new AssertionError(v);
            case Err(var issues) -> issues.asList().getFirst().path().toJsonPointer();
        };

        assertEquals(standalone, viaCombiner);
        assertEquals("/age", standalone);
    }

    @Test
    void asDecoderGivesUpTheDeclarationDeliberately() {
        Decoder<Map<String, Object>, Integer> dec = field("age", int_()).asDecoder();
        assertEquals(20, dec.decode(INPUT, Path.ROOT).getOrThrow());
    }

    // --- strict: declared fields ---

    @Test
    void everyFieldShapeIsDeclared() {
        assertAccepted(combine(field("name", string()), field("age", int_()))
                .strict((name, age) -> name + age).decode(INPUT));
        assertAccepted(combine(field("name", string()), optionalField("age", int_()))
                .strict((name, age) -> name + age.orElse(0)).decode(INPUT));
        assertAccepted(combine(field("name", string()), optionalNullableField("age", int_()))
                .strict((name, age) -> name + age).decode(INPUT));
        assertAccepted(combine(field("name", string()), nullableField("age", int_()))
                .strict((name, age) -> name + age).decode(INPUT));
    }

    @Test
    void anUnknownFieldIsStillRejected() {
        var dec = combine(field("name", string()), field("age", int_()))
                .strict((name, age) -> name + age);
        assertEquals(List.of("/extra"),
                unknownFieldPointers(dec.decode(Map.of("name", "Taro", "age", 20, "extra", true))));
    }

    /**
     * A flat component reads the whole input opaquely, so there is no way to say which fields the
     * schema covers. Refusing when the strict decoder is assembled is the only honest answer.
     */
    @Test
    void strictRefusesACombinerContainingAFlatComponent() {
        var ageOnly = combine(field("age", int_()), field("name", string()))
                .map((age, name) -> age);
        var withFlat = combine(field("name", string()), flat(ageOnly));

        var thrown = assertThrows(IllegalStateException.class,
                () -> withFlat.strict((name, age) -> name + age));
        assertTrue(thrown.getMessage().contains("do not declare the fields they consume"),
                thrown.getMessage());
    }

    @Test
    void aFlatComponentStillDecodes() {
        var dec = combine(
                field("name", string()),
                flat(combine(field("age", int_()), field("name", string())).map((age, name) -> age))
        ).map((name, age) -> name + age);

        assertAccepted(dec.decode(INPUT));
    }

    // --- strict: input-field enumeration ---

    /**
     * Declaring the field names is not enough: something has to say what the input actually
     * contains. jOOQ is the real case — its fields are named but a {@code Record} has no scanner —
     * and it is reported separately from the flat-component refusal.
     */
    @Test
    void strictRefusesWhenNothingCanEnumerateTheInput() {
        var noScanner = Decoders.combine(
                CombinePart.named("name", (Map<String, Object> in, Path path) -> Result.ok("Taro")),
                CombinePart.named("age", (Map<String, Object> in, Path path) -> Result.ok(20)));

        var thrown = assertThrows(IllegalStateException.class,
                () -> noScanner.strict((name, age) -> name + age));
        assertTrue(thrown.getMessage().contains("input-field enumeration"), thrown.getMessage());
    }

    @Test
    void oneComponentWithAScannerIsEnough() {
        var mixed = Decoders.combine(
                field("name", string()),
                CombinePart.named("age", (Map<String, Object> in, Path path) -> Result.ok(20)));

        var dec = mixed.strict((name, age) -> name + age);
        assertAccepted(dec.decode(INPUT));
        assertEquals(List.of("/extra"),
                unknownFieldPointers(dec.decode(Map.of("name", "Taro", "age", 20, "extra", true))));
    }

    /**
     * Two scanners over the same input type are two contradictory claims about what it contains.
     * Taking either would make strictness depend on the order the fields were written in.
     */
    @Test
    void twoDifferentScannersAreRefusedInBothOrders() {
        InputFields<Map<String, Object>> nameOnly =
                in -> in.containsKey("name") ? List.of("name") : List.of();
        var withOwnScanner = CombinePart.named("name",
                (Map<String, Object> in, Path path) -> Result.ok((String) in.get("name")), nameOnly);
        var fromFactory = field("age", int_());

        var forward = assertThrows(IllegalStateException.class,
                () -> Decoders.combine(withOwnScanner, fromFactory).strict((name, age) -> name + age));
        var reversed = assertThrows(IllegalStateException.class,
                () -> Decoders.combine(fromFactory, withOwnScanner).strict((age, name) -> age + name));

        assertTrue(forward.getMessage().contains("two different InputFields"), forward.getMessage());
        assertEquals(forward.getMessage(), reversed.getMessage(), "both orders must fail the same way");
    }

    @Test
    void componentsSharingOneScannerAreOrderIndependent() {
        var input = Map.<String, Object>of("name", "Taro", "age", 20, "extra", true);

        var forward = combine(field("name", string()), field("age", int_()))
                .strict((name, age) -> name + age);
        var reversed = combine(field("age", int_()), field("name", string()))
                .strict((age, name) -> age + name);

        assertEquals(unknownFieldPointers(forward.decode(input)), unknownFieldPointers(reversed.decode(input)));
        assertEquals(List.of("/extra"), unknownFieldPointers(forward.decode(input)));
    }

    // --- custom boundary ---

    /** A representation the library knows nothing about, policed through the public factories. */
    record Row(Map<String, Object> cells) {}

    private static final InputFields<Row> ROW_FIELDS =
            row -> row == null ? List.of() : List.copyOf(row.cells().keySet());

    private static CombinePart<Row, Object> rowField(String name) {
        return CombinePart.named(name,
                (Row in, Path fieldPath) -> in.cells().containsKey(name)
                        ? Result.ok(in.cells().get(name))
                        : Result.fail(fieldPath, ErrorCodes.REQUIRED, "is required"),
                ROW_FIELDS);
    }

    @Test
    void strictWorksOnACustomBoundary() {
        var dec = Decoders.combine(rowField("name"), rowField("age"))
                .strict((name, age) -> name + ":" + age);

        assertAccepted(dec.decode(new Row(Map.of("name", "Taro", "age", 20))));
        assertEquals(List.of("/extra"),
                unknownFieldPointers(dec.decode(new Row(Map.of("name", "Taro", "age", 20, "extra", true)))));
    }

    // --- arity ---

    /**
     * The sixteen combiner records were rewired mechanically; a component left out of the strict
     * call would show up as a field going unrecognised. Sixteen is the widest, so it is the one
     * worth exercising.
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
        assertAccepted(dec.decode(Map.copyOf(complete)));

        complete.put("extra", "v");
        assertEquals(List.of("/extra"), unknownFieldPointers(dec.decode(Map.copyOf(complete))));
    }

    // --- boundary scanners ---

    @Test
    void mapScannerEnumeratesTheKeysAndToleratesNull() {
        assertEquals(java.util.Set.of("name", "age"),
                java.util.Set.copyOf(net.unit8.raoh.decode.map.MapDecoders.MAP_FIELDS.fieldNames(INPUT)));
        assertEquals(List.of(), net.unit8.raoh.decode.map.MapDecoders.MAP_FIELDS.fieldNames(null));
    }
}
