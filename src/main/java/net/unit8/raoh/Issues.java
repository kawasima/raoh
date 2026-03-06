package net.unit8.raoh;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record Issues(List<Issue> list) {

    public static final Issues EMPTY = new Issues(List.of());

    public Issues add(Issue i) {
        var copy = new ArrayList<>(list);
        copy.add(i);
        return new Issues(List.copyOf(copy));
    }

    public Issues merge(Issues other) {
        if (this.list.isEmpty()) return other;
        if (other.list.isEmpty()) return this;
        var merged = new ArrayList<>(list);
        merged.addAll(other.list);
        return new Issues(List.copyOf(merged));
    }

    public Issues rebase(Path prefix) {
        return new Issues(
                list.stream()
                        .map(i -> i.rebase(prefix))
                        .toList()
        );
    }

    public Issues resolve(MessageResolver resolver) {
        return new Issues(
                list.stream()
                        .map(i -> i.resolve(resolver))
                        .toList()
        );
    }

    public Map<String, List<String>> flatten() {
        return list.stream()
                .collect(Collectors.groupingBy(
                        i -> i.path().toJsonPointer(),
                        LinkedHashMap::new,
                        Collectors.mapping(Issue::message, Collectors.toList())
                ));
    }

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

    public Map<String, List<Issue>> groupByPath() {
        return list.stream()
                .collect(Collectors.groupingBy(
                        i -> i.path().toJsonPointer(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }
}
