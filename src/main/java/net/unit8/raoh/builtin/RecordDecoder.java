package net.unit8.raoh.builtin;

import net.unit8.raoh.Decoder;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import java.util.Map;

/**
 * A decoder for string-keyed map (record/object) values with a fluent API for size constraints.
 *
 * @param <I> the input type
 * @param <V> the value type
 */
public class RecordDecoder<I, V> implements Decoder<I, Map<String, V>> {

    private final Decoder<I, Map<String, V>> inner;

    public RecordDecoder(Decoder<I, Map<String, V>> inner) {
        this.inner = inner;
    }

    @Override
    public Result<Map<String, V>> decode(I in, Path path) {
        return inner.decode(in, path);
    }

    public RecordDecoder<I, V> nonempty() {
        return chain((value, path) -> {
            if (value.isEmpty()) {
                return Result.fail(path, "too_small", "must not be empty",
                        Map.of("min", 1, "actual", 0));
            }
            return Result.ok(value);
        });
    }

    public RecordDecoder<I, V> minSize(int n) {
        return chain((value, path) -> {
            if (value.size() < n) {
                return Result.fail(path, "too_small",
                        "must have at least %d entries".formatted(n),
                        Map.of("min", n, "actual", value.size()));
            }
            return Result.ok(value);
        });
    }

    public RecordDecoder<I, V> maxSize(int n) {
        return chain((value, path) -> {
            if (value.size() > n) {
                return Result.fail(path, "too_big",
                        "must have at most %d entries".formatted(n),
                        Map.of("max", n, "actual", value.size()));
            }
            return Result.ok(value);
        });
    }

    public RecordDecoder<I, V> fixedSize(int n) {
        return chain((value, path) -> {
            if (value.size() != n) {
                return Result.fail(path, "invalid_size",
                        "must have exactly %d entries".formatted(n),
                        Map.of("expected", n, "actual", value.size()));
            }
            return Result.ok(value);
        });
    }

    private RecordDecoder<I, V> chain(Decoder<Map<String, V>, Map<String, V>> constraint) {
        return new RecordDecoder<>((in, path) ->
                this.decode(in, path).flatMap(value -> constraint.decode(value, path))
        );
    }
}
