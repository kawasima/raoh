package net.unit8.raoh.builtin;

import net.unit8.raoh.Decoder;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                return Result.fail(path, "too_small", "must not be empty",
                        Map.of("min", 1, "actual", 0));
            }
            return Result.ok(value);
        });
    }

    public ListDecoder<I, T> minSize(int n) {
        return chain((value, path) -> {
            if (value.size() < n) {
                return Result.fail(path, "too_small",
                        "must have at least %d elements".formatted(n),
                        Map.of("min", n, "actual", value.size()));
            }
            return Result.ok(value);
        });
    }

    public ListDecoder<I, T> maxSize(int n) {
        return chain((value, path) -> {
            if (value.size() > n) {
                return Result.fail(path, "too_big",
                        "must have at most %d elements".formatted(n),
                        Map.of("max", n, "actual", value.size()));
            }
            return Result.ok(value);
        });
    }

    public ListDecoder<I, T> fixedSize(int n) {
        return chain((value, path) -> {
            if (value.size() != n) {
                return Result.fail(path, "invalid_size",
                        "must have exactly %d elements".formatted(n),
                        Map.of("expected", n, "actual", value.size()));
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
