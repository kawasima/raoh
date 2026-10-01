package net.unit8.raoh;

import net.unit8.raoh.internal.IssueBearingMeta;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The walk over issues and the issues their metadata holds ({@link IssueBearingMeta}), used by
 * every operation that has to reach them all: resolving, rebasing and serializing.
 *
 * <p>The walk is a left-to-right post-order: the issues of a list in order, and before an issue
 * itself the issues its metadata holds, value by value in key order and each value's issues in
 * order. So each issue is handed over with everything below it already done. The pending work is
 * kept on a stack of its own, so the depth of the tree never reaches the call stack.
 */
final class IssueTree {

    private IssueTree() {
    }

    /** What one issue becomes, given the issue with everything below it already done. */
    interface Step {
        /**
         * Changes one issue, leaving the issues its metadata holds as they are.
         *
         * @param issue the issue
         * @return the changed issue
         */
        Issue apply(Issue issue);
    }

    /**
     * What to make of each issue and each list of issues, given what was made of everything below.
     *
     * @param <A> what an issue becomes
     * @param <B> what a list of issues becomes
     */
    private interface Fold<A, B> {
        A issue(Issue issue, Map<String, List<B>> below);

        B issues(Issues issues, List<A> each);
    }

    /**
     * Replaces each issue, below ones first: the issue handed to {@code local} already holds the
     * replaced issues in its metadata.
     *
     * @param issues the issues
     * @param local  what to make of one issue
     * @return the replaced issues
     */
    static Issues map(Issues issues, Step local) {
        return fold(issues, new Fold<Issue, Issues>() {
            @Override
            public Issue issue(Issue issue, Map<String, List<Issues>> below) {
                return local.apply(below.isEmpty() ? issue : withBelow(issue, below));
            }

            @Override
            public Issues issues(Issues list, List<Issue> each) {
                return new Issues(List.copyOf(each));
            }
        });
    }

    /**
     * {@link #map(Issues, Step)} for one issue.
     *
     * @param issue the issue
     * @param local what to make of one issue
     * @return the replaced issue
     */
    static Issue map(Issue issue, Step local) {
        return map(new Issues(List.of(issue)), local).asList().get(0);
    }

    /**
     * The issues as {@link Issues#toJsonList()} writes them: plain lists, maps and scalars, with
     * each value that holds issues replaced by its serialized form.
     *
     * @param issues the issues
     * @return the serialized issues
     */
    static List<Map<String, Object>> project(Issues issues) {
        return fold(issues, new Fold<Map<String, Object>, List<Map<String, Object>>>() {
            @Override
            public Map<String, Object> issue(Issue issue, Map<String, List<List<Map<String, Object>>>> below) {
                Map<String, Object> meta = issue.meta();
                if (!below.isEmpty()) {
                    var plain = new LinkedHashMap<String, Object>();
                    issue.meta().forEach((key, value) -> plain.put(key,
                            value instanceof IssueBearingMeta nested ? nested.project(below.get(key)) : value));
                    meta = Collections.unmodifiableMap(plain);
                }
                var m = new LinkedHashMap<String, Object>();
                m.put("path", issue.path().toJsonPointer());
                m.put("code", issue.code());
                m.put("message", issue.message());
                m.put("meta", meta);
                return m;
            }

            @Override
            public List<Map<String, Object>> issues(Issues list, List<Map<String, Object>> each) {
                return Collections.unmodifiableList(each);
            }
        });
    }

    private static Issue withBelow(Issue issue, Map<String, List<Issues>> below) {
        var meta = new LinkedHashMap<String, Object>(issue.meta());
        below.forEach((key, children) -> meta.put(key, ((IssueBearingMeta) meta.get(key)).withChildren(children)));
        return new Issue(issue.path(), issue.code(), issue.messageKey(), issue.message(), meta, issue.customMessage());
    }

    /** A list of issues, and what was made of those of them already done. */
    private static final class ListFrame<A> {
        final Issues issues;
        final List<A> done;

        ListFrame(Issues issues) {
            this.issues = issues;
            this.done = new ArrayList<>(issues.asList().size());
        }
    }

    /** An issue, and what was made of the lists of issues its metadata holds, so far. */
    private static final class IssueFrame<B> {
        final Issue issue;
        /** The keys whose values hold issues, in key order. */
        final List<String> keys = new ArrayList<>();
        /** For each key done or under way, what was made of each of its lists. */
        final Map<String, List<B>> below = new LinkedHashMap<>();
        int key;

        IssueFrame(Issue issue) {
            this.issue = issue;
            issue.meta().forEach((k, v) -> {
                if (v instanceof IssueBearingMeta) {
                    keys.add(k);
                }
            });
        }

        /** The next list of issues to walk, or {@code null} when every list below is done. */
        @Nullable Issues next() {
            while (key < keys.size()) {
                String k = keys.get(key);
                List<Issues> children = ((IssueBearingMeta) issue.meta().get(k)).children();
                List<B> made = below.get(k);
                if (made == null) {
                    made = new ArrayList<>(children.size());
                    below.put(k, made);
                }
                if (made.size() < children.size()) {
                    return children.get(made.size());
                }
                key++;
            }
            return null;
        }

        void add(B made) {
            below.get(keys.get(key)).add(made);
        }
    }

    /**
     * Whether any of the issues holds issues in its metadata. When none does, a caller can change
     * them one by one instead of walking: the usual case, kept as fast as a plain loop.
     *
     * @param issues the issues
     * @return whether a walk is needed
     */
    static boolean anyHoldsIssues(List<Issue> issues) {
        for (Issue issue : issues) {
            if (holdsIssues(issue)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the issue holds issues in its metadata.
     *
     * @param issue the issue
     * @return whether a walk is needed
     */
    static boolean holdsIssues(Issue issue) {
        for (Object value : issue.meta().values()) {
            if (value instanceof IssueBearingMeta) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static <A, B> B fold(Issues root, Fold<A, B> fold) {
        // Frames alternate: a ListFrame's parent is an IssueFrame (or nothing), and an IssueFrame's
        // parent is a ListFrame.
        var stack = new ArrayDeque<Object>();
        stack.push(new ListFrame<A>(root));
        while (true) {
            Object top = stack.element();
            if (top instanceof ListFrame<?> raw) {
                var frame = (ListFrame<A>) raw;
                List<Issue> issues = frame.issues.asList();
                if (frame.done.size() < issues.size()) {
                    Issue issue = issues.get(frame.done.size());
                    if (holdsIssues(issue)) {
                        stack.push(new IssueFrame<B>(issue));
                    } else {
                        // Most issues hold none: no frame for them.
                        frame.done.add(fold.issue(issue, Map.of()));
                    }
                    continue;
                }
                stack.pop();
                B made = fold.issues(frame.issues, frame.done);
                Object parent = stack.peek();
                if (parent == null) {
                    return made;
                }
                ((IssueFrame<B>) parent).add(made);
            } else {
                var frame = (IssueFrame<B>) top;
                Issues next = frame.next();
                if (next != null) {
                    stack.push(new ListFrame<A>(next));
                    continue;
                }
                stack.pop();
                A made = fold.issue(frame.issue, frame.below);
                ((ListFrame<A>) stack.element()).done.add(made);
            }
        }
    }
}
