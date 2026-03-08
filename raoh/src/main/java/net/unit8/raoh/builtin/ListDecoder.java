package net.unit8.raoh.builtin;

import net.unit8.raoh.Decoder;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A decoder for list (array) values with a fluent API for size constraints.
 *
 * @param <I> the input type
 * @param <T> the element type
 */
public class ListDecoder<I, T> implements Decoder<I, List<T>> {

    private final Decoder<I, List<T>> inner;

    public ListDecoder(Decoder<I, List<T>> inner) {
        this.inner = inner;
    }

    @Override
    public Result<List<T>> decode(I in, Path path) {
        return inner.decode(in, path);
    }

    public ListDecoder<I, T> nonempty() {
        return chain((value, path) -> {
            if (value.isEmpty()) {
                return Result.fail(path, ErrorCodes.TOO_SMALL, "must not be empty",
                        Map.of("min", 1, "actual", 0));
            }
            return Result.ok(value);
        });
    }

    public ListDecoder<I, T> minSize(int n) {
        return chain((value, path) -> {
            if (value.size() < n) {
                return Result.fail(path, ErrorCodes.TOO_SMALL,
                        "must have at least %d elements".formatted(n),
                        Map.of("min", n, "actual", value.size()));
            }
            return Result.ok(value);
        });
    }

    public ListDecoder<I, T> maxSize(int n) {
        return chain((value, path) -> {
            if (value.size() > n) {
                return Result.fail(path, ErrorCodes.TOO_BIG,
                        "must have at most %d elements".formatted(n),
                        Map.of("max", n, "actual", value.size()));
            }
            return Result.ok(value);
        });
    }

    public ListDecoder<I, T> fixedSize(int n) {
        return chain((value, path) -> {
            if (value.size() != n) {
                return Result.fail(path, ErrorCodes.INVALID_SIZE,
                        "must have exactly %d elements".formatted(n),
                        Map.of("expected", n, "actual", value.size()));
            }
            return Result.ok(value);
        });
    }

    /**
     * Requires the list to contain the specified element.
     *
     * @param element the element that must be present
     * @return a new decoder that fails with {@link ErrorCodes#MISSING_ELEMENT} if the element is absent
     */
    public ListDecoder<I, T> contains(T element) {
        Objects.requireNonNull(element, "element must not be null");
        var message = "must contain %s".formatted(element);
        return chain((value, path) -> {
            if (!value.contains(element)) {
                var meta = Map.<String, Object>of("expected", element);
                return Result.fail(path, ErrorCodes.MISSING_ELEMENT, message, meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Requires the list to contain all specified elements.
     *
     * @param elements the elements that must all be present
     * @return a new decoder that fails with {@link ErrorCodes#MISSING_ELEMENTS} if any element is absent
     * @throws IllegalArgumentException if {@code elements} is empty
     */
    @SafeVarargs
    public final ListDecoder<I, T> containsAll(T... elements) {
        if (elements.length == 0) throw new IllegalArgumentException("elements must not be empty");
        var required = List.of(elements);
        var message = "must contain all of %s".formatted(required);
        return chain((value, path) -> {
            var valueSet = new HashSet<>(value);
            var missing = new ArrayList<T>();
            for (var e : required) {
                if (!valueSet.contains(e)) {
                    missing.add(e);
                }
            }
            if (!missing.isEmpty()) {
                var meta = Map.<String, Object>of("expected", required, "missing", List.copyOf(missing));
                return Result.fail(path, ErrorCodes.MISSING_ELEMENTS, message, meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Requires all elements in the list to be unique (no duplicates).
     *
     * @return a new decoder that fails with {@link ErrorCodes#DUPLICATE_ELEMENT} if duplicates are found
     */
    public ListDecoder<I, T> unique() {
        return chain((value, path) -> {
            var seen = new HashSet<T>();
            LinkedHashSet<T> duplicates = null;
            for (var e : value) {
                if (!seen.add(e)) {
                    if (duplicates == null) {
                        duplicates = new LinkedHashSet<>();
                    }
                    duplicates.add(e);
                }
            }
            if (duplicates != null) {
                var meta = Map.<String, Object>of("duplicates", List.copyOf(duplicates));
                return Result.fail(path, ErrorCodes.DUPLICATE_ELEMENT,
                        "must not contain duplicates", meta);
            }
            return Result.ok(value);
        });
    }

    public Decoder<I, Set<T>> toSet() {
        return (in, path) -> this.decode(in, path).map(list -> Set.copyOf(new LinkedHashSet<>(list)));
    }

    private ListDecoder<I, T> chain(Decoder<List<T>, List<T>> constraint) {
        return new ListDecoder<>((in, path) ->
                this.decode(in, path).flatMap(value -> constraint.decode(value, path))
        );
    }
}
