package net.unit8.raoh;

import java.util.stream.Collectors;

/**
 * A failed decoding result containing validation issues.
 *
 * @param issues the accumulated validation issues
 * @param <T>    the expected value type (not present due to failure)
 */
public record Err<T>(Issues issues) implements Result<T> {
    /**
     * Coerces this error to a different value type. Safe because errors carry no value.
     *
     * @param <U> the target type
     * @return this error with the new type parameter
     */
    @SuppressWarnings("unchecked")
    public <U> Err<U> coerce() {
        return (Err<U>) this;
    }

    /**
     * Returns a concise string representation of this failed result.
     *
     * <p>Each issue is formatted as {@code path: message}, where the root path
     * is rendered as {@code /}. Multiple issues are comma-separated.
     * Example: {@code Err[/: is required, /name: must not be blank]}
     *
     * @return a string of the form {@code Err[path: message, ...]}
     */
    @Override
    public String toString() {
        return issues.asList().stream()
            .map(issue -> {
                String path = issue.path().toString();
                return (path.isEmpty() ? "/" : path) + ": " + issue.message();
            })
            .collect(Collectors.joining(", ", "Err[", "]"));
    }
}
