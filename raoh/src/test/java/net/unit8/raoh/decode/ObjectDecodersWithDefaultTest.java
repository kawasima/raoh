package net.unit8.raoh.decode;

import net.unit8.raoh.Err;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Issue;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static net.unit8.raoh.decode.Decoders.combine;
import static net.unit8.raoh.decode.ObjectDecoders.int_;
import static net.unit8.raoh.decode.ObjectDecoders.nullable;
import static net.unit8.raoh.decode.ObjectDecoders.withDefault;
import static net.unit8.raoh.decode.map.MapDecoders.field;
import static net.unit8.raoh.decode.map.MapDecoders.nested;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link ObjectDecoders#withDefault} gives the default for a {@code null} value and otherwise
 * returns what the inner decoder gives, without looking at it (#164). The cases follow the Raoh
 * Specification's R000824–R000830 and R000836–R000839, with a {@code Map} as the object.
 */
class ObjectDecodersWithDefaultTest {

    private static final Decoder<Map<String, Object>, List<Integer>> ID_AND_PAGE = combine(
            field("id", withDefault(int_(), 0)),
            field("page", withDefault(int_(), 1))
    ).map((id, page) -> List.of(id, page));

    private static final Decoder<Map<String, Object>, List<Integer>> A_AND_B = combine(
            field("a", int_()),
            field("b", int_())
    ).map((a, b) -> List.of(a, b));

    private static Map<String, Object> mapOf(Object... keysAndValues) {
        var map = new HashMap<String, Object>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    // --- a default inside a field: an absent key and a null value both take it ---

    @Test
    void anAbsentKeyTakesTheDefault() {
        // R000824
        assertEquals(List.of(0, 1), ok(ID_AND_PAGE.decode(Map.of())));
    }

    @Test
    void aNullValueTakesTheDefault() {
        // R000825, R000828
        assertEquals(List.of(0, 1), ok(ID_AND_PAGE.decode(mapOf("page", null))));
        assertEquals(List.of(0, 1), ok(ID_AND_PAGE.decode(mapOf("id", null, "page", null))));
    }

    @Test
    void aPresentValueIsDecoded() {
        // R000829
        assertEquals(List.of(3, 4), ok(ID_AND_PAGE.decode(Map.of("id", 3, "page", 4))));
    }

    @Test
    void aPresentValueOfTheWrongTypeFails() {
        // R000826, R000830
        assertIssue(ID_AND_PAGE.decode(Map.of("page", "x")), "/page", ErrorCodes.TYPE_MISMATCH);
        assertIssue(ID_AND_PAGE.decode(Map.of("id", "x")), "/id", ErrorCodes.TYPE_MISMATCH);
    }

    // --- a default around an object: only a null object takes it ---

    @Test
    void anObjectMissingItsMembersKeepsTheirIssues() {
        // R000836, R000837: the object is there; its missing members are its own failure.
        var dec = withDefault(nested(A_AND_B), List.of(0, 0));
        var both = err(dec.decode(Map.of(), Path.ROOT));
        assertEquals(List.of("/a", "/b"), both.stream().map(i -> i.path().toString()).toList());
        assertEquals(List.of(ErrorCodes.REQUIRED, ErrorCodes.REQUIRED),
                both.stream().map(Issue::code).toList());
        assertIssue(dec.decode(Map.of("a", 1), Path.ROOT), "/b", ErrorCodes.REQUIRED);
    }

    @Test
    void aNullObjectTakesTheDefault() {
        // R000838
        var dec = withDefault(nested(A_AND_B), List.of(0, 0));
        assertEquals(List.of(0, 0), ok(dec.decode(null, Path.ROOT)));
    }

    @Test
    void theDefaultIsTakenBeforeAnInnerNullable() {
        // R000839: withDefault looks at the value before nullable can turn null into a value.
        assertEquals(42, ok(withDefault(nullable(int_()), 42).decode(null, Path.ROOT)));
    }

    // --- what the inner decoder gives is not looked at ---

    @Test
    void anInnerFailureIsReturnedAsItIs() {
        // An inner decoder that fails with required on a present value: the old withDefault took
        // that for absence and gave the default.
        Result<Integer> failure = Result.fail(Path.ROOT.append("x"), ErrorCodes.REQUIRED, "is required");
        Decoder<@Nullable Object, Integer> inner = (in, path) -> failure;
        assertSame(failure, withDefault(inner, 42).decode("present", Path.ROOT));
    }

    @Test
    void theInnerDecoderRunsOnlyForAPresentValueAndTheSupplierOnlyForNull() {
        var innerCalls = new AtomicInteger();
        var supplierCalls = new AtomicInteger();
        Decoder<@Nullable Object, String> inner = (in, path) -> {
            innerCalls.incrementAndGet();
            return "ok".equals(in) ? Result.ok("decoded") : Result.fail(path, ErrorCodes.TYPE_MISMATCH, "no");
        };
        Supplier<String> fallback = () -> {
            supplierCalls.incrementAndGet();
            return "fallback";
        };
        var dec = withDefault(inner, fallback);

        assertEquals("fallback", ok(dec.decode(null, Path.ROOT)));
        assertEquals(List.of(0, 1), List.of(innerCalls.get(), supplierCalls.get()));

        assertEquals("decoded", ok(dec.decode("ok", Path.ROOT)));
        assertEquals(List.of(1, 1), List.of(innerCalls.get(), supplierCalls.get()));

        err(dec.decode("bad", Path.ROOT));
        assertEquals(List.of(2, 1), List.of(innerCalls.get(), supplierCalls.get()));
    }

    // --- helpers ---

    private static <T> T ok(Result<T> result) {
        return switch (result) {
            case Ok<T>(var value) -> value;
            case Err<T>(var issues) -> fail("expected Ok, got Err: " + issues);
        };
    }

    private static List<Issue> err(Result<?> result) {
        return switch (result) {
            case Ok<?>(var value) -> fail("expected Err, got Ok: " + value);
            case Err<?>(var issues) -> issues.asList();
        };
    }

    private static void assertIssue(Result<?> result, String path, String code) {
        var issues = err(result);
        assertEquals(1, issues.size(), issues.toString());
        assertEquals(path, issues.getFirst().path().toString());
        assertEquals(code, issues.getFirst().code());
    }
}
