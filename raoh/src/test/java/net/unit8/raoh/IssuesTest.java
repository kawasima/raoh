package net.unit8.raoh;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link Issues} accumulation and its serialization views (merge / rebase / add /
 * flatten / groupByPath / toJsonList / format), which were previously covered only indirectly.
 *
 * <p>Expected values are derived from the documented contract — JSON Pointer paths from
 * {@link Path#toJsonPointer()} (root is {@code ""}), and the nested {@code _errors} shape from the
 * {@link Issues#format()} spec — not read back from the implementation.
 */
class IssuesTest {

    private static final Issue EMAIL_REQUIRED =
            Issue.of(Path.ROOT.append("email"), ErrorCodes.REQUIRED, "is required");
    private static final Issue EMAIL_FORMAT =
            Issue.of(Path.ROOT.append("email"), ErrorCodes.INVALID_FORMAT, "not a valid email");
    private static final Issue CITY_REQUIRED =
            Issue.of(Path.ROOT.append("address").append("city"), ErrorCodes.REQUIRED, "is required");
    private static final Issue ROOT_ERROR =
            Issue.of(Path.ROOT, ErrorCodes.INVALID_VALUE, "bad");

    // --- add / merge / rebase ---

    @Test
    void addIsImmutableAndAppends() {
        var base = Issues.EMPTY.add(EMAIL_REQUIRED);
        var extended = base.add(CITY_REQUIRED);
        assertEquals(1, base.asList().size(), "the original Issues must be unchanged");
        assertEquals(List.of(EMAIL_REQUIRED, CITY_REQUIRED), extended.asList());
    }

    @Test
    void mergeConcatenatesInOrder() {
        var a = Issues.EMPTY.add(EMAIL_REQUIRED);
        var b = Issues.EMPTY.add(CITY_REQUIRED);
        assertEquals(List.of(EMAIL_REQUIRED, CITY_REQUIRED), a.merge(b).asList());
    }

    @Test
    void mergeWithEmptyKeepsTheOtherSide() {
        var a = Issues.EMPTY.add(EMAIL_REQUIRED);
        assertEquals(a.asList(), Issues.EMPTY.merge(a).asList());
        assertEquals(a.asList(), a.merge(Issues.EMPTY).asList());
    }

    @Test
    void rebasePrependsPrefixToEveryPath() {
        var issues = Issues.EMPTY.add(EMAIL_REQUIRED).add(CITY_REQUIRED);
        var rebased = issues.rebase(Path.ROOT.append("user"));
        assertEquals("/user/email", rebased.asList().get(0).path().toJsonPointer());
        assertEquals("/user/address/city", rebased.asList().get(1).path().toJsonPointer());
    }

    // --- flatten / groupByPath ---

    @Test
    void flattenGroupsMessagesByPointerPreservingInsertionOrder() {
        var issues = Issues.EMPTY.add(EMAIL_REQUIRED).add(EMAIL_FORMAT).add(CITY_REQUIRED);
        var flat = issues.flatten();
        assertEquals(List.of("is required", "not a valid email"), flat.get("/email"));
        assertEquals(List.of("is required"), flat.get("/address/city"));
        // flatten() documents accumulation order: paths appear in first-seen order.
        assertEquals(List.of("/email", "/address/city"), List.copyOf(flat.keySet()));
    }

    @Test
    void groupByPathReturnsIssuesPerPointer() {
        var issues = Issues.EMPTY.add(EMAIL_REQUIRED).add(EMAIL_FORMAT);
        var grouped = issues.groupByPath();
        assertEquals(List.of(EMAIL_REQUIRED, EMAIL_FORMAT), grouped.get("/email"));
    }

    // --- toJsonList ---

    @Test
    void toJsonListEmitsPathCodeMessageAndMeta() {
        var meta = Map.<String, Object>of("expected", "string");
        var issue = Issue.of(Path.ROOT.append("name"), ErrorCodes.TYPE_MISMATCH, "wrong type", meta);
        var json = Issues.EMPTY.add(issue).toJsonList();
        var entry = json.getFirst();
        assertEquals("/name", entry.get("path"));
        assertEquals(ErrorCodes.TYPE_MISMATCH, entry.get("code"));
        assertEquals("wrong type", entry.get("message"));
        assertEquals(meta, entry.get("meta"));
    }

    // --- format (nested _errors) ---

    @Test
    void formatNestsErrorsUnderTheStructurePath() {
        // /address/city  ->  {address: {city: {_errors: ["is required"]}}}
        var formatted = Issues.EMPTY.add(CITY_REQUIRED).format();
        var address = asMap(formatted.get("address"));
        var city = asMap(address.get("city"));
        assertEquals(List.of("is required"), city.get("_errors"));
    }

    @Test
    void formatPlacesRootErrorsAtTopLevel() {
        // A ROOT-path issue has no segments, so its message lands in the top-level _errors.
        var formatted = Issues.EMPTY.add(ROOT_ERROR).format();
        assertEquals(List.of("bad"), formatted.get("_errors"));
    }

    @Test
    void formatAccumulatesMultipleMessagesAtTheSamePath() {
        var formatted = Issues.EMPTY.add(EMAIL_REQUIRED).add(EMAIL_FORMAT).format();
        var email = asMap(formatted.get("email"));
        assertEquals(List.of("is required", "not a valid email"), email.get("_errors"));
    }

    @Test
    void emptyIssuesReportEmpty() {
        assertTrue(Issues.EMPTY.isEmpty());
        assertTrue(Issues.EMPTY.flatten().isEmpty());
        assertTrue(Issues.EMPTY.toJsonList().isEmpty());
    }

    private static Map<?, ?> asMap(Object o) {
        assertTrue(o instanceof Map, "expected a nested map, got: " + o);
        return (Map<?, ?>) o;
    }
}
