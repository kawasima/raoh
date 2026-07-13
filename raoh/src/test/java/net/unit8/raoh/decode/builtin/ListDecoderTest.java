package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.ErrorCodes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static net.unit8.raoh.decode.ObjectDecoders.int_;
import static net.unit8.raoh.decode.ObjectDecoders.list;
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeErr;
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeOk;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Direct unit tests for {@link ListDecoder}, covering the size, content, and uniqueness
 * constraints plus the {@code toSet()} conversion. The element decoder is a plain
 * {@code int_()} so the tests focus on list-level behaviour.
 */
class ListDecoderTest {

    // --- size constraints ---

    @Test
    void nonemptyAcceptsNonEmptyRejectsEmpty() {
        assertEquals(List.of(1), decodeOk(list(int_()).nonempty(), List.of(1)));
        var issue = decodeErr(list(int_()).nonempty(), List.of());
        assertEquals(ErrorCodes.TOO_SMALL, issue.code());
        assertEquals("must not be empty", issue.message());
    }

    @Test
    void minSizeAcceptsBoundaryRejectsBelow() {
        assertEquals(List.of(1, 2), decodeOk(list(int_()).minSize(2), List.of(1, 2)));
        var issue = decodeErr(list(int_()).minSize(2), List.of(1));
        assertEquals(ErrorCodes.TOO_SMALL, issue.code());
        assertEquals("must have at least 2 elements", issue.message());
        assertEquals(2, issue.meta().get("min"));
        assertEquals(1, issue.meta().get("actual"));
    }

    @Test
    void maxSizeAcceptsBoundaryRejectsAbove() {
        assertEquals(List.of(1, 2), decodeOk(list(int_()).maxSize(2), List.of(1, 2)));
        var issue = decodeErr(list(int_()).maxSize(2), List.of(1, 2, 3));
        assertEquals(ErrorCodes.TOO_BIG, issue.code());
        assertEquals("must have at most 2 elements", issue.message());
    }

    @Test
    void fixedSizeAcceptsExactRejectsDifferent() {
        assertEquals(List.of(1, 2), decodeOk(list(int_()).fixedSize(2), List.of(1, 2)));
        var issue = decodeErr(list(int_()).fixedSize(2), List.of(1, 2, 3));
        assertEquals(ErrorCodes.INVALID_SIZE, issue.code());
        assertEquals("must have exactly 2 elements", issue.message());
    }

    // --- content constraints ---

    @Test
    void containsAcceptsPresentRejectsAbsent() {
        assertEquals(List.of(1, 2, 3), decodeOk(list(int_()).contains(2), List.of(1, 2, 3)));
        var issue = decodeErr(list(int_()).contains(2), List.of(1, 3));
        assertEquals(ErrorCodes.MISSING_ELEMENT, issue.code());
        assertEquals("must contain 2", issue.message());
        assertEquals(2, issue.meta().get("expected"));
    }

    @Test
    void containsRejectsNullElementAtConstruction() {
        assertThrows(NullPointerException.class, () -> list(int_()).contains(null));
    }

    @Test
    void containsAllAcceptsAllPresentRejectsMissing() {
        assertEquals(List.of(1, 2, 3), decodeOk(list(int_()).containsAll(2, 3), List.of(1, 2, 3)));
        var issue = decodeErr(list(int_()).containsAll(2, 3), List.of(1, 2));
        assertEquals(ErrorCodes.MISSING_ELEMENTS, issue.code());
        assertEquals("must contain all of [2, 3] (missing: [3])", issue.message());
        assertEquals(List.of(2, 3), issue.meta().get("expected"));
        assertEquals(List.of(3), issue.meta().get("missing"));
    }

    @Test
    void containsAllRejectsEmptyArgsAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> list(int_()).containsAll());
    }

    @Test
    void uniqueAcceptsDistinctRejectsDuplicates() {
        assertEquals(List.of(1, 2, 3), decodeOk(list(int_()).unique(), List.of(1, 2, 3)));
        var issue = decodeErr(list(int_()).unique(), List.of(1, 1, 2));
        assertEquals(ErrorCodes.DUPLICATE_ELEMENT, issue.code());
        assertEquals("must not contain duplicates: [1]", issue.message());
        assertEquals(List.of(1), issue.meta().get("duplicates"));
    }

    // --- conversion ---

    @Test
    void toSetDeduplicatesElementsPreservingInsertionOrder() {
        var set = decodeOk(list(int_()).toSet(), List.of(3, 1, 3, 2));
        // Deduplication: [3, 1, 3, 2] -> {3, 1, 2}, with first-seen (insertion) order preserved.
        assertEquals(Set.of(1, 2, 3), set);
        assertEquals(List.of(3, 1, 2), new ArrayList<>(set));
    }

    // --- base decoder behaviour ---

    @Test
    void rejectsNullAsRequired() {
        assertEquals(ErrorCodes.REQUIRED, decodeErr(list(int_()), null).code());
    }

    @Test
    void rejectsNonListAsTypeMismatch() {
        assertEquals(ErrorCodes.TYPE_MISMATCH, decodeErr(list(int_()), "not a list").code());
    }
}
