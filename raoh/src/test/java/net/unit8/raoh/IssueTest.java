package net.unit8.raoh;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that {@link Issue#meta()} is a copy ordered by key (#142), so that the per-JVM iteration
 * order of a {@code Map.of} built by a constraint never reaches what an issue exposes.
 */
class IssueTest {

    @Test
    void metaKeysIterateInNaturalOrderWhateverOrderTheyCameIn() {
        var forward = new LinkedHashMap<String, Object>();
        forward.put("actual", 3);
        forward.put("max", 10);
        forward.put("min", 5);
        var backward = new LinkedHashMap<String, Object>();
        backward.put("min", 5);
        backward.put("max", 10);
        backward.put("actual", 3);

        var expected = List.of("actual", "max", "min");
        assertEquals(expected, keys(Issue.of(Path.ROOT, ErrorCodes.OUT_OF_RANGE, "m", forward)));
        assertEquals(expected, keys(Issue.of(Path.ROOT, ErrorCodes.OUT_OF_RANGE, "m", backward)));
        assertEquals(expected, keys(Issue.of(Path.ROOT, ErrorCodes.OUT_OF_RANGE, "m",
                Map.of("min", 5, "max", 10, "actual", 3))));
    }

    @Test
    void orderSurvivesTheCopiesAnIssueMakesOfItself() {
        var issue = Issue.of(Path.ROOT, ErrorCodes.OUT_OF_RANGE, "m", Map.of("min", 5, "actual", 3));

        assertEquals(List.of("actual", "min"), keys(issue.rebase(Path.ROOT.append("age"))));
        assertEquals(List.of("actual", "min"), keys(issue.withCustomMessage("custom")));
    }

    @Test
    void laterChangesToThePassedMapDoNotReachTheIssue() {
        var meta = new HashMap<String, Object>();
        meta.put("min", 5);
        var issue = Issue.of(Path.ROOT, ErrorCodes.OUT_OF_RANGE, "m", meta);

        meta.put("actual", 3);
        meta.remove("min");

        assertEquals(Map.of("min", 5), issue.meta());
    }

    @Test
    void nullValuesAreKept() {
        var meta = new HashMap<String, Object>();
        meta.put("actual", null);
        var issue = Issue.of(Path.ROOT, ErrorCodes.INVALID_VALUE, "m", meta);

        assertTrue(issue.meta().containsKey("actual"));
        assertNull(issue.meta().get("actual"));
    }

    @Test
    void metaCannotBeModified() {
        var issue = Issue.of(Path.ROOT, ErrorCodes.OUT_OF_RANGE, "m", Map.of("min", 5));

        assertThrows(UnsupportedOperationException.class, () -> issue.meta().put("max", 10));
    }

    private static List<String> keys(Issue issue) {
        return new ArrayList<>(issue.meta().keySet());
    }
}
