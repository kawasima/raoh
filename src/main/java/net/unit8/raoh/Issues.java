package net.unit8.raoh;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * An immutable collection of {@link Issue}s accumulated during decoding.
 *
 * @param list the list of issues
 */
public record Issues(List<Issue> list) {

    /** An empty issues instance. */
    public static final Issues EMPTY = new Issues(List.of());

    /**
     * Returns a new {@code Issues} with the given issue appended.
     *
     * @param i the issue to add
     * @return a new issues instance
     */
    public Issues add(Issue i) {
        var copy = new ArrayList<>(list);
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
        if (this.list.isEmpty()) return other;
        if (other.list.isEmpty()) return this;
        var merged = new ArrayList<>(list);
        merged.addAll(other.list);
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
                list.stream()
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
                list.stream()
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
        return list.stream()
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
     * @return a nested map representation of the issues
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> format() {
        Map<String, Object> root = new LinkedHashMap<>();
        for (var issue : list) {
            var segments = issue.path().segments();
            Map<String, Object> current = root;
            for (int i = 0; i < segments.size(); i++) {
                var seg = segments.get(i);
                current = (Map<String, Object>) current.computeIfAbsent(seg, k -> new LinkedHashMap<>());
            }
            var errors = (List<String>) current.computeIfAbsent("_errors", k -> new ArrayList<>());
            errors.add(issue.message());
        }
        return root;
    }

    /**
     * Converts issues to a list of maps suitable for JSON serialization.
     * Each map contains {@code path}, {@code code}, {@code message}, and {@code meta}.
     *
     * @return a list of issue maps
     */
    public List<Map<String, Object>> toJsonList() {
        return list.stream()
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
        return list.stream()
                .collect(Collectors.groupingBy(
                        i -> i.path().toJsonPointer(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }
}
