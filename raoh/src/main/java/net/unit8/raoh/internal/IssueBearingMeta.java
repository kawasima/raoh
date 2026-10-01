package net.unit8.raoh.internal;

import net.unit8.raoh.Issues;

import java.util.List;
import java.util.Map;

/**
 * A metadata value that holds issues of its own, such as the failures of each candidate of a
 * {@code one_of_failed} issue.
 *
 * <p>An {@link net.unit8.raoh.Issue} that has such a value is the root of a tree of issues.
 * Resolving, rebasing and serializing an issue walk that tree, so the issues below keep their
 * message key and whether their message is custom, instead of being written out as JSON-like maps
 * when the issue is made. The walk is done by Raoh, with a stack of its own: none of these
 * operations calls back into the walk, so the depth of the tree never reaches the call stack.
 *
 * <p>Not part of Raoh's API. It is public only so that Raoh's other packages can use it, and it
 * may change or go in any release.
 */
public interface IssueBearingMeta {

    /**
     * The issues this value holds, in order.
     *
     * @return the issues
     */
    List<Issues> children();

    /**
     * This value with its issues replaced, each by the one at the same position.
     *
     * @param children the new issues, as many as {@link #children()} has
     * @return the new value
     */
    IssueBearingMeta withChildren(List<Issues> children);

    /**
     * The form this value takes in serialized output, built from its issues already serialized.
     *
     * <p>Must not serialize the issues itself; the walk has done it.
     *
     * @param children each of {@link #children()} as {@link Issues#toJsonList()} writes it
     * @return plain lists, maps and scalars, holding no Raoh type
     */
    Object project(List<List<Map<String, Object>>> children);
}
