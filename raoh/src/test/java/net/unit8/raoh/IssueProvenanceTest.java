package net.unit8.raoh;

import net.unit8.raoh.internal.IssueProvenance;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Which issues an unknown-members check made is carried by the list of an {@link Issues}: the
 * steps that keep what an issue is keep it, and nothing observable about the issues shows it.
 */
class IssueProvenanceTest {

    private static final Issue UNKNOWN = Issue.of(Path.of("b"), ErrorCodes.UNKNOWN_FIELD, "unknown field",
            Map.of("field", "b"));
    private static final Issue OTHER = Issue.of(Path.of("a"), ErrorCodes.TYPE_MISMATCH, "wrong type");

    /** {@code [OTHER, UNKNOWN]}, with only {@code UNKNOWN} marked. */
    private static Issues mixed() {
        return new Issues(List.of(OTHER)).merge(new Issues(IssueProvenance.unknownMembers(List.of(UNKNOWN))));
    }

    /** The marks of the issues, in order. */
    private static List<Boolean> marks(Issues issues) {
        var provenance = assertInstanceOf(IssueProvenance.class, issues.asList());
        var marks = new ArrayList<Boolean>();
        for (int i = 0; i < issues.asList().size(); i++) {
            marks.add(provenance.fromUnknownMembers(i));
        }
        return marks;
    }

    @Test
    void mergingKeepsTheMarks() {
        assertEquals(List.of(false, true), marks(mixed()));
        assertEquals(List.of(true, false), marks(new Issues(IssueProvenance.unknownMembers(List.of(UNKNOWN)))
                .merge(new Issues(List.of(OTHER)))));
    }

    @Test
    void addingKeepsTheMarksAndAddsAnUnmarkedIssue() {
        assertEquals(List.of(false, true, false), marks(mixed().add(UNKNOWN)));
    }

    @Test
    void rebasingKeepsTheMarks() {
        var rebased = mixed().rebase(Path.of("o"));
        assertEquals("/o/b", rebased.asList().get(1).path().toJsonPointer());
        assertEquals(List.of(false, true), marks(rebased));
    }

    @Test
    void resolvingKeepsTheMarks() {
        assertEquals(List.of(false, true), marks(mixed().resolve(MessageResolver.DEFAULT)));
        assertEquals(List.of(false, true), marks(mixed().resolve(MessageResolver.DEFAULT, Locale.ROOT)));
    }

    @Test
    void resolvingAnIssueThatHoldsIssuesKeepsTheMarks() {
        Issue holding = assertInstanceOf(Err.class, net.unit8.raoh.decode.Decoders.<Object, Object>oneOf(
                (in, path) -> Result.fail(path, "x", "x")).decode(1, Path.ROOT)).issues().asList().getFirst();
        var issues = new Issues(List.of(holding)).merge(new Issues(IssueProvenance.unknownMembers(List.of(UNKNOWN))));

        assertEquals(List.of(false, true), marks(issues.resolve(MessageResolver.DEFAULT)));
    }

    @Test
    void aListBuiltAnewFromTheIssuesHasNoMarks() {
        assertEquals(List.of(false, false), marks(new Issues(new ArrayList<>(mixed().asList()))));
    }

    @Test
    void theMarksAreNotPartOfEquality() {
        var unmarked = new Issues(List.of(OTHER, UNKNOWN));
        assertEquals(unmarked, mixed());
        assertEquals(unmarked.hashCode(), mixed().hashCode());
        assertEquals(unmarked.toJsonList(), mixed().toJsonList());
    }
}
