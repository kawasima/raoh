package net.unit8.raoh;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * An immutable collection of {@link Issue}s accumulated during decoding.
 *
 * @param asList the list of issues
 */
public record Issues(List<Issue> asList) {

    /** An empty issues instance. */
    public static final Issues EMPTY = new Issues(List.of());

    /**
     * Returns {@code true} if this collection contains no issues.
     *
     * @return {@code true} if empty
     */
    public boolean isEmpty() {
        return asList.isEmpty();
    }

    /**
     * Returns a new {@code Issues} with the given issue appended.
     *
     * @param i the issue to add
     * @return a new issues instance
     */
    public Issues add(Issue i) {
        var copy = new ArrayList<Issue>(asList.size() + 1);
        copy.addAll(asList);
        copy.add(i);
        return new Issues(List.copyOf(copy));
    }

    /**
     * Merges this issues with another, combining all issues.
     *
     * @param other the other issues to merge
     * @return a new merged issues instance
     */
    public Issues merge(Issues other) {
        if (this.asList.isEmpty()) return other;
        if (other.asList.isEmpty()) return this;
        var merged = new ArrayList<Issue>(asList.size() + other.asList.size());
        merged.addAll(asList);
        merged.addAll(other.asList);
        return new Issues(List.copyOf(merged));
    }

    /**
     * Rebases all issue paths by prepending the given prefix.
     *
     * @param prefix the path prefix
     * @return a new issues instance with rebased paths
     */
    public Issues rebase(Path prefix) {
        return new Issues(
                asList.stream()
                        .map(i -> i.rebase(prefix))
                        .toList()
        );
    }

    /**
     * Resolves all issue messages using the given resolver.
     *
     * @param resolver the message resolver
     * @return a new issues instance with resolved messages
     */
    public Issues resolve(MessageResolver resolver) {
        return new Issues(
                asList.stream()
                        .map(i -> i.resolve(resolver))
                        .toList()
        );
    }

    /**
     * Flattens issues into a map of JSON Pointer paths to lists of error messages.
     *
     * @return a map from path to error messages
     */
    public Map<String, List<String>> flatten() {
        return asList.stream()
                .collect(Collectors.groupingBy(
                        i -> i.path().toJsonPointer(),
                        LinkedHashMap::new,
                        Collectors.mapping(Issue::message, Collectors.toList())
                ));
    }

    /**
     * Formats issues as a nested map structure mirroring the input structure,
     * with {@code _errors} keys holding the error messages at each level.
     *
     * <p><strong>Note:</strong> {@code "_errors"} is a reserved key in the output map. If any
     * field in the input schema is literally named {@code "_errors"}, the behaviour of this
     * method is undefined. Use {@link #flatten()} or {@link #toJsonList()} as alternatives.
     *
     * @return a nested map representation of the issues
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> format() {
        Map<String, Object> root = new LinkedHashMap<>();
        for (var issue : asList) {
            List<String> segments = issue.path().segments();
            Map<String, Object> current = root;
            for (String seg : segments) {
                var existing = current.get(seg);
                if (existing instanceof Map<?, ?>) {
                    current = (Map<String, Object>) existing;
                } else {
                    // If existing value is a List (i.e. "_errors" was used as a field name),
                    // create a sibling map to avoid ClassCastException.
                    var next = new LinkedHashMap<String, Object>();
                    current.put(seg, next);
                    current = next;
                }
            }
            var errorsObj = current.get("_errors");
            List<String> errors;
            if (errorsObj instanceof List<?>) {
                errors = (List<String>) errorsObj;
            } else {
                errors = new ArrayList<>();
                current.put("_errors", errors);
            }
            errors.add(issue.message());
        }
        return root;
    }

    /**
     * Converts issues to a list of maps suitable for JSON serialization.
     * Each map contains {@code path}, {@code code}, {@code message}, and {@code meta}.
     *
     * <p><strong>Security note:</strong> The {@code meta} map may contain raw user-supplied
     * values (e.g., {@code "actual"} from type-mismatch or literal errors). Filter or omit
     * {@code meta} before returning this list in an HTTP response to avoid unintentional
     * information disclosure.
     *
     * @return a list of issue maps
     */
    public List<Map<String, Object>> toJsonList() {
        return asList.stream()
                .map(i -> {
                    var m = new LinkedHashMap<String, Object>();
                    m.put("path", i.path().toJsonPointer());
                    m.put("code", i.code());
                    m.put("message", i.message());
                    m.put("meta", i.meta());
                    return (Map<String, Object>) m;
                })
                .toList();
    }

    /**
     * Groups issues by their JSON Pointer path.
     *
     * @return a map from path to list of issues at that path
     */
    public Map<String, List<Issue>> groupByPath() {
        return asList.stream()
                .collect(Collectors.groupingBy(
                        i -> i.path().toJsonPointer(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }
}
