package net.unit8.raoh;

import net.unit8.raoh.builtin.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory of primitive decoders for raw {@code Object} input.
 *
 * <p>These decoders accept a raw {@code Object} value and perform a runtime type check,
 * returning the value as the expected type or a {@code type_mismatch} error.
 * They are used as building blocks for boundary-specific decoder factories such as
 * {@code MapDecoders} and {@code JooqRecordDecoders}, and can also be used directly
 * in custom decoder classes.
 *
 * <p>Usage: {@code import static net.unit8.raoh.ObjectDecoders.*;}
 */
public final class ObjectDecoders {

    private ObjectDecoders() {}

    // --- Primitive decoders ---

    /**
     * Creates a string decoder that trims whitespace by default.
     *
     * <p>Returns {@code required} if the value is {@code null}, and {@code type_mismatch}
     * if the value is not a {@link String}.
     *
     * @return a string decoder for {@code Object} input
     */
    public static StringDecoder<Object> string() {
        Decoder<Object, String> base = allowBlankBase();
        return new StringDecoder<>(base, base);
    }

    /**
     * Creates a string decoder that preserves blank values (no trim or nonBlank validation).
     *
     * @return a string decoder for {@code Object} input that allows blank strings
     */
    public static StringDecoder<Object> allowBlankString() {
        return new StringDecoder<>(allowBlankBase());
    }

    static Decoder<Object, String> allowBlankBase() {
        return (in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (in instanceof String s) {
                return Result.ok(s);
            }
            return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected string",
                    Map.of("expected", "string", "actual", in.getClass().getSimpleName()));
        };
    }

    /**
     * Creates an integer decoder.
     *
     * <p>Accepts {@link Integer} values directly, and narrows other {@link Number} subtypes
     * via {@link Number#intValue()}. Returns {@code required} if the value is {@code null},
     * and {@code type_mismatch} if the value is not a number.
     *
     * @return an integer decoder for {@code Object} input
     */
    public static IntDecoder<Object> int_() {
        return new IntDecoder<>((in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (in instanceof Integer i) {
                return Result.ok(i);
            }
            if (in instanceof Number n) {
                return Result.ok(n.intValue());
            }
            return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected integer",
                    Map.of("expected", "integer", "actual", in.getClass().getSimpleName()));
        });
    }

    /**
     * Creates a long decoder.
     *
     * <p>Accepts {@link Long} values directly, and narrows other {@link Number} subtypes
     * via {@link Number#longValue()}. Returns {@code required} if the value is {@code null},
     * and {@code type_mismatch} if the value is not a number.
     *
     * @return a long decoder for {@code Object} input
     */
    public static LongDecoder<Object> long_() {
        return new LongDecoder<>((in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (in instanceof Long l) {
                return Result.ok(l);
            }
            if (in instanceof Number n) {
                return Result.ok(n.longValue());
            }
            return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected long",
                    Map.of("expected", "long", "actual", in.getClass().getSimpleName()));
        });
    }

    /**
     * Creates a boolean decoder.
     *
     * <p>Returns {@code required} if the value is {@code null},
     * and {@code type_mismatch} if the value is not a {@link Boolean}.
     *
     * @return a boolean decoder for {@code Object} input
     */
    public static BoolDecoder<Object> bool() {
        return new BoolDecoder<>((in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (in instanceof Boolean b) {
                return Result.ok(b);
            }
            return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected boolean",
                    Map.of("expected", "boolean", "actual", in.getClass().getSimpleName()));
        });
    }

    /**
     * Creates a {@link BigDecimal} decoder.
     *
     * <p>Accepts {@link BigDecimal} values directly, and converts other {@link Number}
     * subtypes via their string representation. Returns {@code required} if the value is
     * {@code null}, and {@code type_mismatch} if the value is not a number.
     *
     * @return a decimal decoder for {@code Object} input
     */
    public static DecimalDecoder<Object> decimal() {
        return new DecimalDecoder<>((in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (in instanceof BigDecimal bd) {
                return Result.ok(bd);
            }
            if (in instanceof Number n) {
                return Result.ok(new BigDecimal(n.toString()));
            }
            return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected number",
                    Map.of("expected", "number", "actual", in.getClass().getSimpleName()));
        });
    }

    // --- Temporal decoders ---

    /**
     * Creates a {@link LocalDate} decoder.
     *
     * <p>Returns {@code required} if the value is {@code null}, and {@code type_mismatch}
     * if the value is not a {@link LocalDate}.
     *
     * @return a temporal decoder for {@code Object} input producing {@link LocalDate}
     */
    public static TemporalDecoder<Object, LocalDate> date() {
        return temporalOf(LocalDate.class, "date");
    }

    /**
     * Creates a {@link LocalTime} decoder.
     *
     * <p>Returns {@code required} if the value is {@code null}, and {@code type_mismatch}
     * if the value is not a {@link LocalTime}.
     *
     * @return a temporal decoder for {@code Object} input producing {@link LocalTime}
     */
    public static TemporalDecoder<Object, LocalTime> time() {
        return temporalOf(LocalTime.class, "time");
    }

    /**
     * Creates a {@link LocalDateTime} decoder.
     *
     * <p>Returns {@code required} if the value is {@code null}, and {@code type_mismatch}
     * if the value is not a {@link LocalDateTime}.
     *
     * @return a temporal decoder for {@code Object} input producing {@link LocalDateTime}
     */
    public static TemporalDecoder<Object, LocalDateTime> dateTime() {
        return temporalOf(LocalDateTime.class, "date-time");
    }

    /**
     * Creates an {@link Instant} decoder.
     *
     * <p>Returns {@code required} if the value is {@code null}, and {@code type_mismatch}
     * if the value is not an {@link Instant}.
     *
     * @return a temporal decoder for {@code Object} input producing {@link Instant}
     */
    public static TemporalDecoder<Object, Instant> iso8601() {
        return temporalOf(Instant.class, "instant");
    }

    /**
     * Creates an {@link OffsetDateTime} decoder.
     *
     * <p>Returns {@code required} if the value is {@code null}, and {@code type_mismatch}
     * if the value is not an {@link OffsetDateTime}.
     *
     * @return a temporal decoder for {@code Object} input producing {@link OffsetDateTime}
     */
    public static TemporalDecoder<Object, OffsetDateTime> offsetDateTime() {
        return temporalOf(OffsetDateTime.class, "offset-date-time");
    }

    private static <T extends Comparable<? super T>> TemporalDecoder<Object, T> temporalOf(
            Class<T> type, String typeName) {
        return new TemporalDecoder<>((in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (type.isInstance(in)) {
                return Result.ok(type.cast(in));
            }
            return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected " + typeName,
                    Map.of("expected", typeName, "actual", in.getClass().getSimpleName()));
        });
    }

    // --- nullable / list / map ---

    /**
     * Wraps a decoder to accept {@code null} input, returning {@code null} as the decoded value
     * instead of a {@code required} error.
     *
     * @param <T> the decoded value type
     * @param dec the inner decoder to apply when the value is non-null
     * @return a decoder that passes through {@code null} without error
     */
    public static <T> Decoder<Object, T> nullable(Decoder<Object, T> dec) {
        return (in, path) -> {
            if (in == null) {
                return Result.ok(null);
            }
            return dec.decode(in, path);
        };
    }

    /**
     * Creates a list decoder that decodes each element of a {@link List} with the given decoder,
     * accumulating all errors rather than short-circuiting on the first failure.
     *
     * <p>Returns {@code required} if the value is {@code null},
     * and {@code type_mismatch} if the value is not a {@link List}.
     *
     * @param <T>        the decoded element type
     * @param elementDec the decoder for each list element
     * @return a list decoder for {@code Object} input
     */
    public static <T> ListDecoder<Object, T> list(Decoder<Object, T> elementDec) {
        return new ListDecoder<>((in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (!(in instanceof List<?> rawList)) {
                return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected array",
                        Map.of("expected", "array", "actual", in.getClass().getSimpleName()));
            }
            var issues = Issues.EMPTY;
            var results = new ArrayList<T>();
            for (int i = 0; i < rawList.size(); i++) {
                var elemPath = path.append(String.valueOf(i));
                var r = elementDec.decode(rawList.get(i), elemPath);
                switch (r) {
                    case Ok<T> ok -> results.add(ok.value());
                    case Err<T> err -> issues = issues.merge(err.issues());
                }
            }
            if (!issues.isEmpty()) {
                return Result.err(issues);
            }
            return Result.ok(List.copyOf(results));
        });
    }

    /**
     * Creates a map decoder that decodes each value of a {@code Map<String, ?>} with the given
     * decoder, accumulating all errors. Keys are preserved as-is.
     *
     * <p>Returns {@code required} if the value is {@code null},
     * and {@code type_mismatch} if the value is not a {@link Map}.
     *
     * @param <V>    the decoded value type
     * @param valDec the decoder for each map value
     * @return a record decoder for {@code Object} input
     */
    public static <V> RecordDecoder<Object, V> map(Decoder<Object, V> valDec) {
        return new RecordDecoder<>((in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (!(in instanceof Map<?, ?> rawMap)) {
                return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected object",
                        Map.of("expected", "object", "actual", in.getClass().getSimpleName()));
            }
            var issues = Issues.EMPTY;
            var results = new LinkedHashMap<String, V>();
            for (var entry : rawMap.entrySet()) {
                var keyPath = path.append(String.valueOf(entry.getKey()));
                var r = valDec.decode(entry.getValue(), keyPath);
                switch (r) {
                    case Ok<V> ok -> results.put(String.valueOf(entry.getKey()), ok.value());
                    case Err<V> err -> issues = issues.merge(err.issues());
                }
            }
            if (!issues.isEmpty()) {
                return Result.err(issues);
            }
            return Result.ok(Map.copyOf(results));
        });
    }

    // --- enumOf / literal ---

    /**
     * Creates an enum decoder that parses a string value into the given enum type.
     *
     * @param <E> the enum type
     * @param cls the enum class
     * @return a decoder that produces enum constants from string input
     */
    public static <E extends Enum<E>> Decoder<Object, E> enumOf(Class<E> cls) {
        return Decoders.enumOf(cls, allowBlankString());
    }

    /**
     * Creates a decoder that accepts only the given literal string value.
     *
     * @param expected the expected string value
     * @return a decoder that succeeds only when the input matches {@code expected}
     */
    public static Decoder<Object, String> literal(String expected) {
        return Decoders.literal(expected, allowBlankString());
    }
}
