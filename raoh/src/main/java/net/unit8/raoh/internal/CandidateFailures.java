package net.unit8.raoh.internal;

import net.unit8.raoh.Issues;
import org.jspecify.annotations.Nullable;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;

/**
 * The {@code candidates} metadata of a {@code one_of_failed} issue: the issues each candidate
 * failed with, in candidate order.
 *
 * <p>Read as metadata it is the list it has always been, one map per candidate with
 * {@code candidate} (its index) and {@code issues} (its issues as {@link Issues#toJsonList()}
 * writes them). Underneath it keeps the {@link Issues} themselves, so resolving or rebasing the
 * {@code one_of_failed} issue reaches the candidates' issues too.
 *
 * <p>Equality is the list's: two values are equal when they read as equal lists, and a value
 * equals a plain list with the same maps. What the maps do not show, such as a nested issue's
 * message key or whether its message is custom, takes no part in it.
 *
 * <p>Not part of Raoh's API. It is public only so that Raoh's other packages can use it, and it
 * may change or go in any release.
 */
public final class CandidateFailures extends AbstractList<Map<String, Object>>
        implements IssueBearingMeta, RandomAccess {

    private final List<Issues> failures;

    /** The list as read, built the first time it is read. */
    private @Nullable List<Map<String, Object>> projected;

    /**
     * Creates the metadata.
     *
     * @param failures the issues of each candidate, in candidate order
     */
    public CandidateFailures(List<Issues> failures) {
        this.failures = List.copyOf(failures);
    }

    @Override
    public List<Issues> children() {
        return failures;
    }

    @Override
    public CandidateFailures withChildren(List<Issues> children) {
        if (children.size() != failures.size()) {
            throw new IllegalArgumentException(
                    "expected " + failures.size() + " candidates, got " + children.size());
        }
        return new CandidateFailures(children);
    }

    @Override
    public List<Map<String, Object>> project(List<List<Map<String, Object>>> children) {
        var out = new ArrayList<Map<String, Object>>(children.size());
        for (int i = 0; i < children.size(); i++) {
            out.add(Map.of("candidate", i, "issues", children.get(i)));
        }
        return List.copyOf(out);
    }

    @Override
    public Map<String, Object> get(int index) {
        return read().get(index);
    }

    @Override
    public int size() {
        return failures.size();
    }

    private List<Map<String, Object>> read() {
        List<Map<String, Object>> read = projected;
        if (read == null) {
            var children = new ArrayList<List<Map<String, Object>>>(failures.size());
            for (Issues failure : failures) {
                children.add(failure.toJsonList());
            }
            read = project(children);
            projected = read;
        }
        return read;
    }
}
