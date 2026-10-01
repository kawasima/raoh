package net.unit8.raoh;

import java.util.AbstractList;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The list behind {@link Issues}: immutable, and cheap to extend at its end.
 *
 * <p>Decoders accumulate issues one failure at a time, so appending has to cost the issues
 * appended, not the issues already there; copying the whole list on every append made a list of
 * n failing elements take time in n². Lists made by appending share one array: each list is a
 * prefix of it, of its own {@link #size}. Appending to the list that ends where the array's used
 * part ends writes past that end and claims it, so a fold that appends again and again extends one
 * array, doubling it when full. Appending to any other list, such as one already appended to
 * before, copies into a new array; a list never sees what is written past its own end.
 *
 * <p>So appending costs time in the issues appended, amortized over a fold, and reading an element
 * costs constant time, at any point of the fold.
 *
 * <p>Claiming the end of the array is a compare-and-set, so two threads appending to the same list
 * each get a list of their own: one extends the array, the others copy. A list must show all its
 * elements to a thread it reaches without synchronization, as any immutable value does, yet the
 * elements before its end may have been written by other threads that appended earlier. So
 * appending in place also requires that every append so far has finished writing
 * ({@link Buffer#published}, read before the claim and written after the elements): the appending
 * thread then has all earlier writes happen before it makes the new list, whose array and size are
 * final fields, and the rules for final fields carry them to any thread that reads the list.
 */
final class IssueList extends AbstractList<Issue> implements RandomAccess {

    /**
     * The array lists share; its value is how much of the array some list has claimed. It is the
     * counter itself rather than holding one, which saves an object for every {@code Issues} made.
     */
    @SuppressWarnings("serial")
    private static final class Buffer extends AtomicInteger {
        final Issue[] slots;

        /** How much of the array has been written, by appends that have finished. */
        volatile int published;

        Buffer(Issue[] slots, int used) {
            super(used);
            this.slots = slots;
            this.published = used;
        }
    }

    /** The empty list. Its array has no room, so appending to it always starts a new array. */
    static final IssueList EMPTY = new IssueList(new Buffer(new Issue[0], 0), 0);

    private final Buffer buffer;
    private final int size;

    private IssueList(Buffer buffer, int size) {
        this.buffer = buffer;
        this.size = size;
    }

    /**
     * {@code list} as an {@code IssueList}: itself when it is one, a copy otherwise.
     *
     * @param list the issues
     * @return the issues as an {@code IssueList}
     * @throws NullPointerException if {@code list} or one of its issues is {@code null}
     */
    static IssueList copyOf(List<Issue> list) {
        if (list instanceof IssueList issues) {
            return issues;
        }
        int n = list.size();
        if (n == 0) {
            return EMPTY;
        }
        var slots = new Issue[n];
        int i = 0;
        for (Issue issue : list) {
            slots[i++] = Objects.requireNonNull(issue, "issue");
        }
        if (i != n) {
            throw new IllegalStateException("the list changed while it was being copied");
        }
        return new IssueList(new Buffer(slots, n), n);
    }

    /**
     * This list followed by {@code more}.
     *
     * @param more the issues to append
     * @return the longer list
     */
    IssueList append(IssueList more) {
        int k = more.size;
        if (k == 0) {
            return this;
        }
        int n = size;
        Issue[] slots = buffer.slots;
        if (n + k <= slots.length && buffer.published == n && buffer.compareAndSet(n, n + k)) {
            System.arraycopy(more.buffer.slots, 0, slots, n, k);
            buffer.published = n + k;
            return new IssueList(buffer, n + k);
        }
        var grown = new Issue[Math.max(n + k, 2 * n)];
        System.arraycopy(slots, 0, grown, 0, n);
        System.arraycopy(more.buffer.slots, 0, grown, n, k);
        return new IssueList(new Buffer(grown, n + k), n + k);
    }

    /**
     * This list followed by {@code issue}.
     *
     * @param issue the issue to append
     * @return the longer list
     */
    IssueList append(Issue issue) {
        Objects.requireNonNull(issue, "issue");
        int n = size;
        Issue[] slots = buffer.slots;
        if (n < slots.length && buffer.published == n && buffer.compareAndSet(n, n + 1)) {
            slots[n] = issue;
            buffer.published = n + 1;
            return new IssueList(buffer, n + 1);
        }
        var grown = new Issue[Math.max(n + 1, 2 * n)];
        System.arraycopy(slots, 0, grown, 0, n);
        grown[n] = issue;
        return new IssueList(new Buffer(grown, n + 1), n + 1);
    }

    /**
     * Whether this list and {@code other} are views of the same array, that is, whether one was
     * made by appending in place to the other or to a list they share. For tests of which path an
     * append took.
     *
     * @param other the other list
     * @return whether they share an array
     */
    boolean sharesArrayWith(IssueList other) {
        return buffer == other.buffer;
    }

    @Override
    public Issue get(int index) {
        Objects.checkIndex(index, size);
        return buffer.slots[index];
    }

    @Override
    public int size() {
        return size;
    }
}
