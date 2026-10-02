package net.unit8.raoh.internal;

import net.unit8.raoh.Issue;

import java.util.AbstractList;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

/**
 * Which issues of a list an unknown-members check made, the check behind {@code strict}.
 *
 * <p>An outer {@code strict} leaves out a member an inner one already reported, and only an
 * {@code unknown_field} that an unknown-members check made counts: the same-looking issue a
 * user's decoder returned does not. {@link net.unit8.raoh.Issue} does not say what made it, and
 * should not, since it is not part of what the issue means; the list of an
 * {@link net.unit8.raoh.Issues} carries it instead. Equality, serialization and every public view of
 * the issues ignore it. {@code add}, {@code merge}, {@code rebase} and {@code resolve} keep it; a list
 * built anew from the issues, as a user's decoder would, does not.
 *
 * <p>Only Raoh's own lists are believed: {@link net.unit8.raoh.Issues} takes the marks of the list
 * {@link #unknownMembers(List)} returns and keeps those of its own, and ignores what any other
 * implementation of this interface claims.
 *
 * <p>Not part of Raoh's API. It is public only so that Raoh's other packages can use it, and it
 * may change or go in any release.
 */
public interface IssueProvenance {

    /**
     * Whether an unknown-members check made the issue at {@code index}.
     *
     * @param index the index of the issue in the list
     * @return whether an unknown-members check made it
     * @throws IndexOutOfBoundsException if {@code index} is not an index of the list
     */
    boolean fromUnknownMembers(int index);

    /**
     * The issues, each marked as made by an unknown-members check. Pass the result to
     * {@link net.unit8.raoh.Issues#Issues(List)} to keep the mark.
     *
     * @param issues the issues an unknown-members check made
     * @return the issues, marked
     * @throws NullPointerException if {@code issues} or one of its issues is {@code null}
     */
    static List<Issue> unknownMembers(List<Issue> issues) {
        return new UnknownMembers(List.copyOf(issues));
    }

    /** The list {@link #unknownMembers(List)} returns: every issue in it is marked. */
    final class UnknownMembers extends AbstractList<Issue> implements IssueProvenance, RandomAccess {
        private final List<Issue> issues;

        private UnknownMembers(List<Issue> issues) {
            this.issues = issues;
        }

        @Override
        public boolean fromUnknownMembers(int index) {
            Objects.checkIndex(index, issues.size());
            return true;
        }

        @Override
        public Issue get(int index) {
            return issues.get(index);
        }

        @Override
        public int size() {
            return issues.size();
        }
    }
}
