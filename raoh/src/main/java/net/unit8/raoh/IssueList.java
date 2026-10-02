package net.unit8.raoh;

import net.unit8.raoh.internal.IssueProvenance;

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
 *
 * <p>The list also records which of its issues an unknown-members check made
 * ({@link IssueProvenance}). A slot holds such an issue inside a {@link FromUnknownMembers}, so the
 * other issues cost nothing for it, and appending copies the mark along with the slot.
 */
final class IssueList extends AbstractList<Issue> implements IssueProvenance, RandomAccess {

    /** A slot's issue that an unknown-members check made. */
    private record FromUnknownMembers(Issue issue) {
    }

    /**
     * The array lists share; its value is how much of the array some list has claimed. It is the
     * counter itself rather than holding one, which saves an object for every {@code Issues} made.
     */
    @SuppressWarnings("serial")
    private static final class Buffer extends AtomicInteger {
        /** Each an {@link Issue} or a {@link FromUnknownMembers}. */
        final Object[] slots;

        /** How much of the array has been written, by appends that have finished. */
        volatile int published;

        Buffer(Object[] slots, int used) {
            super(used);
            this.slots = slots;
            this.published = used;
        }
    }

    /** The empty list. Its array has no room, so appending to it always starts a new array. */
    static final IssueList EMPTY = new IssueList(new Buffer(new Object[0], 0), 0);

    private final Buffer buffer;
    private final int size;

    private IssueList(Buffer buffer, int size) {
        this.buffer = buffer;
        this.size = size;
    }

    /**
     * {@code list} as an {@code IssueList}: itself when it is one, a copy otherwise. A copy keeps
     * the marks of a list {@link IssueProvenance#unknownMembers(List)} made, and of no other: a list
     * of the caller's own could claim any mark, and only Raoh says what a {@code strict} made.
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
        var slots = new Object[n];
        var provenance = list instanceof IssueProvenance.UnknownMembers p ? p : null;
        int i = 0;
        for (Issue issue : list) {
            Objects.requireNonNull(issue, "issue");
            slots[i] = provenance != null && provenance.fromUnknownMembers(i) ? new FromUnknownMembers(issue) : issue;
            i++;
        }
        if (i != n) {
            throw new IllegalStateException("the list changed while it was being copied");
        }
        return new IssueList(new Buffer(slots, n), n);
    }

    /**
     * {@code replacements} in place of this list's issues, each marked as the issue it replaces.
     * For the steps that change each issue and keep what it is, such as rebasing and resolving.
     *
     * @param replacements the issues, one for each of this list's, in the same order
     * @return the replacements as an {@code IssueList}
     * @throws IllegalArgumentException if there are not as many replacements as issues
     * @throws NullPointerException     if one of the replacements is {@code null}
     */
    IssueList replacedBy(List<Issue> replacements) {
        int n = size;
        if (replacements.size() != n) {
            throw new IllegalArgumentException("expected " + n + " issues, got " + replacements.size());
        }
        if (n == 0) {
            return EMPTY;
        }
        var slots = new Object[n];
        for (int i = 0; i < n; i++) {
            Issue issue = Objects.requireNonNull(replacements.get(i), "issue");
            slots[i] = fromUnknownMembers(i) ? new FromUnknownMembers(issue) : issue;
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
        Object[] slots = buffer.slots;
        if (n + k <= slots.length && buffer.published == n && buffer.compareAndSet(n, n + k)) {
            System.arraycopy(more.buffer.slots, 0, slots, n, k);
            buffer.published = n + k;
            return new IssueList(buffer, n + k);
        }
        var grown = new Object[Math.max(n + k, 2 * n)];
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
        Object[] slots = buffer.slots;
        if (n < slots.length && buffer.published == n && buffer.compareAndSet(n, n + 1)) {
            slots[n] = issue;
            buffer.published = n + 1;
            return new IssueList(buffer, n + 1);
        }
        var grown = new Object[Math.max(n + 1, 2 * n)];
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
        Object slot = buffer.slots[index];
        return slot instanceof FromUnknownMembers marked ? marked.issue() : (Issue) slot;
    }

    @Override
    public boolean fromUnknownMembers(int index) {
        Objects.checkIndex(index, size);
        return buffer.slots[index] instanceof FromUnknownMembers;
    }

    @Override
    public int size() {
        return size;
    }
}
