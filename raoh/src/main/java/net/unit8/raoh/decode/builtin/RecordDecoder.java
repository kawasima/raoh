package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.MessageKeys;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A decoder for string-keyed map (record/object) values with a fluent API for size constraints.
 *
 * @param <I> the input type
 * @param <V> the value type
 */
public final class RecordDecoder<I extends @Nullable Object, V> implements Decoder<I, Map<String, V>> {

    private final Decoder<I, Map<String, V>> inner;

    /**
     * Creates a new {@link RecordDecoder} wrapping the given decoder.
     *
     * @param inner the underlying decoder that produces a map value
     */
    public RecordDecoder(Decoder<I, Map<String, V>> inner) {
        this.inner = inner;
    }

    @Override
    public Result<Map<String, V>> decode(I in, Path path) {
        return inner.decode(in, path);
    }

    /**
     * Requires the record to have at least one entry.
     *
     * @return a new decoder that fails with {@link ErrorCodes#TOO_SMALL} if the map is empty
     */
    public RecordDecoder<I, V> nonempty() {
        return nonempty(null);
    }

    /**
     * Requires the record to have at least one entry.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#TOO_SMALL} if the map is empty
     */
    public RecordDecoder<I, V> nonempty(@Nullable String message) {
        return chain((value, path) -> {
            if (value.isEmpty()) {
                return Result.failWith(path, ErrorCodes.TOO_SMALL, MessageKeys.TOO_SMALL_NONEMPTY, message, "must not be empty",
                        Map.of("min", 1, "actual", 0));
            }
            return Result.ok(value);
        });
    }

    /**
     * Requires the record to have at least {@code n} entries.
     *
     * @param n the minimum number of entries
     * @return a new decoder that fails with {@link ErrorCodes#TOO_SMALL} if the map has fewer entries
     */
    public RecordDecoder<I, V> minSize(int n) {
        return minSize(n, null);
    }

    /**
     * Requires the record to have at least {@code n} entries.
     *
     * @param n       the minimum number of entries
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#TOO_SMALL} if the map has fewer entries
     */
    public RecordDecoder<I, V> minSize(int n, @Nullable String message) {
        return chain((value, path) -> {
            if (value.size() < n) {
                return Result.failWith(path, ErrorCodes.TOO_SMALL, message,
                        "must have at least %d entries".formatted(n),
                        Map.of("min", n, "actual", value.size()));
            }
            return Result.ok(value);
        });
    }

    /**
     * Requires the record to have at most {@code n} entries.
     *
     * @param n the maximum number of entries
     * @return a new decoder that fails with {@link ErrorCodes#TOO_BIG} if the map has more entries
     */
    public RecordDecoder<I, V> maxSize(int n) {
        return maxSize(n, null);
    }

    /**
     * Requires the record to have at most {@code n} entries.
     *
     * @param n       the maximum number of entries
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#TOO_BIG} if the map has more entries
     */
    public RecordDecoder<I, V> maxSize(int n, @Nullable String message) {
        return chain((value, path) -> {
            if (value.size() > n) {
                return Result.failWith(path, ErrorCodes.TOO_BIG, message,
                        "must have at most %d entries".formatted(n),
                        Map.of("max", n, "actual", value.size()));
            }
            return Result.ok(value);
        });
    }

    /**
     * Requires the record to have exactly {@code n} entries.
     *
     * @param n the required number of entries
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_SIZE} if the map size differs
     */
    public RecordDecoder<I, V> fixedSize(int n) {
        return fixedSize(n, null);
    }

    /**
     * Requires the record to have exactly {@code n} entries.
     *
     * @param n       the required number of entries
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_SIZE} if the map size differs
     */
    public RecordDecoder<I, V> fixedSize(int n, @Nullable String message) {
        return chain((value, path) -> {
            if (value.size() != n) {
                return Result.failWith(path, ErrorCodes.INVALID_SIZE, message,
                        "must have exactly %d entries".formatted(n),
                        Map.of("expected", n, "actual", value.size()));
            }
            return Result.ok(value);
        });
    }


    /**
     * {@inheritDoc}
     *
     * <p>The return type is narrowed to {@link RecordDecoder} so that record constraints can still be chained
     * after the refinement.
     */
    @Override
    public RecordDecoder<I, V> refine(Predicate<? super Map<String, V>> ok, String code, String message) {
        return refine(ok, code, message, v -> Map.of());
    }

    /**
     * {@inheritDoc}
     *
     * <p>The return type is narrowed to {@link RecordDecoder} so that record constraints can still be chained
     * after the refinement.
     */
    @Override
    public RecordDecoder<I, V> refine(Predicate<? super Map<String, V>> ok, String code, String message,
                                      Function<? super Map<String, V>, ? extends Map<String, Object>> metaFn) {
        return refine(ok, (v, path) -> Result.failCustom(path, code, message, metaFn.apply(v)));
    }

    /**
     * {@inheritDoc}
     *
     * <p>The return type is narrowed to {@link RecordDecoder} so that record constraints can still be chained
     * after the refinement.
     */
    @Override
    public RecordDecoder<I, V> refine(Predicate<? super Map<String, V>> ok,
                                      BiFunction<? super Map<String, V>, ? super Path, ? extends Result<Map<String, V>>> onFail) {
        return chain((value, path) -> ok.test(value) ? Result.ok(value) : onFail.apply(value, path));
    }

    private RecordDecoder<I, V> chain(Decoder<Map<String, V>, Map<String, V>> constraint) {
        return new RecordDecoder<>((in, path) ->
                this.decode(in, path).flatMap(value -> constraint.decode(value, path))
        );
    }
}
