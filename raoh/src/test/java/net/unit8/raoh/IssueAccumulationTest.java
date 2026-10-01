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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static net.unit8.raoh.decode.ObjectDecoders.int_;
import static net.unit8.raoh.decode.ObjectDecoders.list;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /** Three issues: an array of four, so one more append fits in place. */
    private static Issues baseWithRoom() {
        Issues base = Issues.EMPTY.add(issue(-3)).add(issue(-2)).add(issue(-1));
        assertEquals(3, base.asList().size());
        return base;
    }

    private static boolean shareArray(Issues a, Issues b) {
        return ((IssueList) a.asList()).sharesArrayWith((IssueList) b.asList());
    }

    @Test
    void appendingTwiceToTheSameIssuesExtendsInPlaceOnceAndCopiesOnce() {
        Issues base = baseWithRoom();
        Issues left = base.add(issue(0));
        Issues right = base.add(issue(1));

        // The first append claims the free slot; the second finds it claimed and copies.
        assertTrue(shareArray(base, left), "the first append extends the array in place");
        assertFalse(shareArray(base, right), "the second append copies");
        assertEquals(List.of(issue(-3), issue(-2), issue(-1)), base.asList());
        assertEquals(List.of(issue(-3), issue(-2), issue(-1), issue(0)), left.asList());
        assertEquals(List.of(issue(-3), issue(-2), issue(-1), issue(1)), right.asList());

        // Each goes on independently: the copy has room of its own, the original array is full.
        Issues rightLonger = right.add(issue(2));
        Issues leftLonger = left.add(issue(3));
        assertTrue(shareArray(right, rightLonger));
        assertFalse(shareArray(left, leftLonger));
        assertEquals(List.of(issue(-3), issue(-2), issue(-1), issue(1), issue(2)), rightLonger.asList());
        assertEquals(List.of(issue(-3), issue(-2), issue(-1), issue(0), issue(3)), leftLonger.asList());
        assertEquals(List.of(issue(-3), issue(-2), issue(-1), issue(0)), left.asList());
    }

    @Test
    void mergingTwiceIntoTheSameIssuesExtendsInPlaceOnceAndCopiesOnce() {
        // Five issues: an array of eight, room for three.
        Issues base = Issues.EMPTY.add(issue(-4)).add(issue(-3)).add(issue(-2)).add(issue(-1)).add(issue(0));
        Issues first = base.merge(new Issues(List.of(issue(1), issue(2))));
        Issues second = base.merge(new Issues(List.of(issue(3))));
        assertTrue(shareArray(base, first));
        assertFalse(shareArray(base, second));
        assertEquals(List.of(issue(-4), issue(-3), issue(-2), issue(-1), issue(0), issue(1), issue(2)), first.asList());
        assertEquals(List.of(issue(-4), issue(-3), issue(-2), issue(-1), issue(0), issue(3)), second.asList());
        assertEquals(5, base.asList().size());
    }

    @Test
    void threadsAppendingToTheSameIssuesRaceForTheFreeSlotAndEachGetTheirOwn() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int round = 0; round < 200; round++) {
                Issues base = baseWithRoom();
                var start = new CyclicBarrier(threads);
                var futures = new ArrayList<Future<Issues>>();
                for (int t = 0; t < threads; t++) {
                    int own = t;
                    futures.add(pool.submit(() -> {
                        start.await();
                        return base.add(issue(own));
                    }));
                }
                int inPlace = 0;
                for (int t = 0; t < threads; t++) {
                    Issues got = futures.get(t).get();
                    assertEquals(List.of(issue(-3), issue(-2), issue(-1), issue(t)), got.asList());
                    if (shareArray(base, got)) {
                        inPlace++;
                    }
                }
                assertEquals(1, inPlace, "exactly one thread claims the free slot; the others copy");
                assertEquals(List.of(issue(-3), issue(-2), issue(-1)), base.asList());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void threadsKeepAppendingFromTheSameIssuesIndependently() throws Exception {
        Issues base = baseWithRoom();
        int threads = 8;
        int each = 10_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            var start = new CyclicBarrier(threads);
            var futures = new ArrayList<Future<Issues>>();
            for (int t = 0; t < threads; t++) {
                int offset = t * each;
                futures.add(pool.submit(() -> {
                    start.await();
                    Issues current = base;
                    for (int i = 0; i < each; i++) {
                        current = current.add(issue(offset + i));
                    }
                    return current;
                }));
            }
            for (int t = 0; t < threads; t++) {
                List<Issue> got = futures.get(t).get().asList();
                assertEquals(each + 3, got.size());
                assertEquals(List.of(issue(-3), issue(-2), issue(-1)), got.subList(0, 3));
                for (int i = 0; i < each; i++) {
                    assertEquals(issue(t * each + i), got.get(i + 3));
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(List.of(issue(-3), issue(-2), issue(-1)), base.asList());
    }

    @Test
    void aFoldExtendsOneArrayUntilItIsFull() {
        // Amortized constant time, checked by structure rather than by the clock: a fold of n adds
        // starts a new array only when the last one is full, doubling it, so about log2(n) times.
        Issues current = Issues.EMPTY;
        int arrays = 0;
        for (int i = 0; i < MANY; i++) {
            Issues next = current.add(issue(i));
            if (!shareArray(current, next)) {
                arrays++;
            }
            current = next;
        }
        assertTrue(arrays <= 1 + 32 - Integer.numberOfLeadingZeros(MANY), "arrays started: " + arrays);
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
