package net.unit8.raoh;

import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.decode.Decoders;
import net.unit8.raoh.decode.map.MapDecoders;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static net.unit8.raoh.decode.ObjectDecoders.int_;
import static net.unit8.raoh.decode.ObjectDecoders.list;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Accumulating issues takes time in the number of issues, and what is accumulated stays an
 * immutable list (#179).
 */
class IssueAccumulationTest {

    /** Far above what linear accumulation takes (tens of ms), far below quadratic (seconds per run). */
    private static final Duration LIMIT = Duration.ofSeconds(5);

    private static final int MANY = 100_000;

    private static Issue issue(int i) {
        return Issue.of(Path.of(String.valueOf(i)), "c" + i, "m" + i);
    }

    // --- Linear time, where decoders accumulate ---

    @Test
    void aListOfFailingElementsIsDecodedInLinearTime() {
        var input = Collections.<Object>nCopies(MANY, "x");
        Result<?> result = assertTimeoutPreemptively(LIMIT, () -> list(int_()).decode(input, Path.ROOT));
        assertEquals(MANY, assertInstanceOf(Err.class, result).issues().asList().size());
    }

    @Test
    void unknownFieldsAreReportedInLinearTime() {
        var input = new HashMap<String, Object>();
        for (int i = 0; i < MANY; i++) {
            input.put("f" + i, i);
        }
        Decoder<Map<String, Object>, Map<String, Object>> dec = MapDecoders.strict((in, path) -> Result.ok(in), java.util.Set.of());
        Result<?> result = assertTimeoutPreemptively(LIMIT, () -> dec.decode(input, Path.ROOT));
        assertEquals(MANY, assertInstanceOf(Err.class, result).issues().asList().size());
    }

    @Test
    void readingAfterEachAppendStaysLinear() {
        // A fold that looks at what it has so far after every step.
        Issues all = assertTimeoutPreemptively(LIMIT, () -> {
            Issues current = Issues.EMPTY;
            for (int i = 0; i < MANY; i++) {
                current = current.add(issue(i));
                assertEquals(issue(0), current.asList().get(0));
                assertEquals(issue(i), current.asList().get(current.asList().size() - 1));
            }
            return current;
        });
        assertEquals(MANY, all.asList().size());
    }

    @Test
    void mergingInALoopStaysLinear() {
        Issues all = assertTimeoutPreemptively(LIMIT, () -> {
            Issues current = Issues.EMPTY;
            for (int i = 0; i < MANY; i++) {
                current = current.merge(new Issues(List.of(issue(i))));
            }
            return current;
        });
        assertEquals(MANY, all.asList().size());
        assertEquals(issue(MANY - 1), all.asList().get(MANY - 1));
    }

    // --- Immutable, whoever appends ---

    @Test
    void appendingTwiceToTheSameIssuesGivesTwoListsThatDoNotSeeEachOther() {
        Issues base = Issues.EMPTY.add(issue(0)).add(issue(1));
        Issues left = base.add(issue(2));
        Issues right = base.add(issue(3));
        Issues longer = left.merge(new Issues(List.of(issue(4))));

        assertEquals(List.of(issue(0), issue(1)), base.asList());
        assertEquals(List.of(issue(0), issue(1), issue(2)), left.asList());
        assertEquals(List.of(issue(0), issue(1), issue(3)), right.asList());
        assertEquals(List.of(issue(0), issue(1), issue(2), issue(4)), longer.asList());
    }

    @Test
    void threadsAppendingToTheSameIssuesEachGetTheirOwn() throws Exception {
        Issues base = Issues.EMPTY.add(issue(-1));
        int threads = 8;
        int each = 10_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            var futures = new ArrayList<Future<Issues>>();
            for (int t = 0; t < threads; t++) {
                int offset = t * each;
                futures.add(pool.submit(() -> {
                    Issues current = base;
                    for (int i = 0; i < each; i++) {
                        current = current.add(issue(offset + i));
                    }
                    return current;
                }));
            }
            for (int t = 0; t < threads; t++) {
                List<Issue> got = futures.get(t).get().asList();
                assertEquals(each + 1, got.size());
                assertEquals(issue(-1), got.get(0));
                for (int i = 0; i < each; i++) {
                    assertEquals(issue(t * each + i), got.get(i + 1));
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(List.of(issue(-1)), base.asList());
    }

    @Test
    void theListCannotBeChanged() {
        List<Issue> issues = Issues.EMPTY.add(issue(0)).add(issue(1)).asList();
        assertThrows(UnsupportedOperationException.class, () -> issues.add(issue(2)));
        assertThrows(UnsupportedOperationException.class, () -> issues.set(0, issue(2)));
        assertThrows(UnsupportedOperationException.class, () -> issues.remove(0));
        assertThrows(UnsupportedOperationException.class, issues::clear);
    }

    @Test
    void nullIssuesAreRefused() {
        assertThrows(NullPointerException.class, () -> Issues.EMPTY.add(null));
        var withNull = new ArrayList<Issue>();
        withNull.add(null);
        assertThrows(NullPointerException.class, () -> new Issues(withNull));
    }

    // --- A list like any other ---

    @Test
    void equalityIsThatOfTheList() {
        Issues appended = Issues.EMPTY.add(issue(0)).merge(new Issues(List.of(issue(1), issue(2))));
        Issues copied = new Issues(new ArrayList<>(List.of(issue(0), issue(1), issue(2))));
        assertEquals(copied, appended);
        assertEquals(copied.hashCode(), appended.hashCode());
        assertEquals(List.of(issue(0), issue(1), issue(2)), appended.asList());
        assertEquals(appended.asList(), List.of(issue(0), issue(1), issue(2)));
        assertNotEquals(appended, Issues.EMPTY.add(issue(0)));
        assertEquals(List.of(issue(1), issue(2)), appended.asList().subList(1, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> appended.asList().get(3));
    }

    @Test
    void oneOfAndCombinersStillAccumulate() {
        Decoder<Object, Object> dec = Decoders.oneOf(int_(), list(int_()));
        assertInstanceOf(Err.class, dec.decode("x", Path.ROOT));
    }
}
