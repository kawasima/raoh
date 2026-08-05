package net.unit8.raoh.decode;

import net.unit8.raoh.Err;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Issues;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import net.unit8.raoh.decode.builtin.BoolDecoder;
import net.unit8.raoh.decode.builtin.DecimalDecoder;
import net.unit8.raoh.decode.builtin.DoubleDecoder;
import net.unit8.raoh.decode.builtin.FloatDecoder;
import net.unit8.raoh.decode.builtin.IntDecoder;
import net.unit8.raoh.decode.builtin.ListDecoder;
import net.unit8.raoh.decode.builtin.LongDecoder;
import net.unit8.raoh.decode.builtin.RecordDecoder;
import net.unit8.raoh.decode.builtin.StringDecoder;
import net.unit8.raoh.decode.builtin.TemporalDecoder;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Factory of primitive decoders for raw {@code Object} input.
 *
 * <p>These decoders accept a raw {@code Object} value and perform a runtime type check,
 * returning the value as the expected type or a {@code type_mismatch} error. The temporal
 * decoders additionally accept the {@code java.sql} counterpart of their type and ISO-8601 text,
 * so they read both what a JDBC driver hands over and what {@code ObjectEncoders} writes.
 * They are used as building blocks for boundary-specific decoder factories such as
 * {@code MapDecoders} and {@code JooqRecordDecoders}, and can also be used directly
 * in custom decoder classes.
 *
 * <p>Usage: {@code import static net.unit8.raoh.decode.ObjectDecoders.*;}
 */
public final class ObjectDecoders {

    private ObjectDecoders() {}

    // --- Primitive decoders ---

    /**
     * Creates a string decoder.
     *
     * <p>Returns {@code required} if the value is {@code null},
     * and {@code type_mismatch} if the value is not a {@link String}.
     * Blank strings are accepted by default; chain {@link net.unit8.raoh.decode.builtin.StringDecoder#nonBlank() nonBlank()}
     * to reject them. Constraints are additive — there is no opt-out combinator to remove a
     * previously-applied constraint.
     *
     * @return a string decoder for {@code Object} input
     */
    // NullAway (JSpecify mode) cannot yet see that a concrete Decoder<@Nullable Object, String>
    // matches the constructor's Decoder<I, String> when I is instantiated to @Nullable Object;
    // the types are identical, so this is a checker generics limitation, not a nullness hole.
    @SuppressWarnings("NullAway")
    public static StringDecoder<@Nullable Object> string() {
        return new StringDecoder<@Nullable Object>(allowBlankBase());
    }

    private static Decoder<@Nullable Object, String> allowBlankBase() {
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
    public static IntDecoder<@Nullable Object> int_() {
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
    public static LongDecoder<@Nullable Object> long_() {
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
     * Creates a double decoder.
     *
     * <p>Accepts any {@link Number} via {@link Number#doubleValue()}. Returns {@code required} if
     * the value is {@code null}, and {@code type_mismatch} if the value is not a number or its
     * magnitude is beyond the {@code double} range (i.e. narrows to an infinity).
     *
     * @return a double decoder for {@code Object} input
     */
    public static DoubleDecoder<@Nullable Object> double_() {
        return new DoubleDecoder<>((in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (in instanceof Number n) {
                double d = n.doubleValue();
                // A magnitude beyond the double range (e.g. a huge BigInteger, or Double.INFINITY)
                // is a representation failure, not a valid value — reject it rather than silently
                // yielding Infinity. NaN is left to flow through so range constraints can reject it
                // as out_of_range (see DoubleDecoderTest#rangeRejectsNaN).
                if (Double.isInfinite(d)) {
                    return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected double",
                            Map.of("expected", "double"));
                }
                return Result.ok(d);
            }
            return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected double",
                    Map.of("expected", "double", "actual", in.getClass().getSimpleName()));
        });
    }

    /**
     * Creates a float decoder.
     *
     * <p>Accepts any {@link Number} via {@link Number#floatValue()}. Returns {@code required} if
     * the value is {@code null}, and {@code type_mismatch} if the value is not a number or its
     * magnitude is beyond the {@code float} range (i.e. narrows to an infinity).
     *
     * @return a float decoder for {@code Object} input
     */
    public static FloatDecoder<@Nullable Object> float_() {
        return new FloatDecoder<>((in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (in instanceof Number n) {
                float f = n.floatValue();
                // See double_(): a value beyond the float range narrows to Infinity — reject it as a
                // representation failure. NaN flows through for range constraints to catch.
                if (Float.isInfinite(f)) {
                    return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected float",
                            Map.of("expected", "float"));
                }
                return Result.ok(f);
            }
            return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected float",
                    Map.of("expected", "float", "actual", in.getClass().getSimpleName()));
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
    public static BoolDecoder<@Nullable Object> bool() {
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
    public static DecimalDecoder<@Nullable Object> decimal() {
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
     * <p>Accepts {@link LocalDate} values directly, converts {@link java.sql.Date}
     * via {@link java.sql.Date#toLocalDate()}, and parses a {@link String} as ISO-8601 text —
     * the representation {@code ObjectEncoders.date()} writes. Returns {@code required} if the
     * value is {@code null}, {@code invalid_format} if the text does not parse, and
     * {@code type_mismatch} for any other type.
     *
     * @return a temporal decoder for {@code Object} input producing {@link LocalDate}
     */
    public static TemporalDecoder<@Nullable Object, LocalDate> date() {
        return new TemporalDecoder<>((in, path) -> switch (in) {
            case null -> Result.fail(path, ErrorCodes.REQUIRED, "is required");
            case LocalDate d -> Result.ok(d);
            case java.sql.Date sd -> Result.ok(sd.toLocalDate());
            case String s -> parseText(path, s, LocalDate::parse, "not a valid date (yyyy-MM-dd)");
            default -> typeMismatch(path, "date", in);
        });
    }

    /**
     * Creates a {@link LocalTime} decoder.
     *
     * <p>Accepts {@link LocalTime} values directly, converts {@link java.sql.Time}
     * via {@link java.sql.Time#toLocalTime()}, and parses a {@link String} as ISO-8601 text —
     * the representation {@code ObjectEncoders.time()} writes. Returns {@code required} if the
     * value is {@code null}, {@code invalid_format} if the text does not parse, and
     * {@code type_mismatch} for any other type.
     *
     * @return a temporal decoder for {@code Object} input producing {@link LocalTime}
     */
    public static TemporalDecoder<@Nullable Object, LocalTime> time() {
        return new TemporalDecoder<>((in, path) -> switch (in) {
            case null -> Result.fail(path, ErrorCodes.REQUIRED, "is required");
            case LocalTime t -> Result.ok(t);
            case java.sql.Time st -> Result.ok(st.toLocalTime());
            case String s -> parseText(path, s, LocalTime::parse, "not a valid time (HH:mm:ss)");
            default -> typeMismatch(path, "time", in);
        });
    }

    /**
     * Creates a {@link LocalDateTime} decoder.
     *
     * <p>Accepts {@link LocalDateTime} values directly, converts {@link java.sql.Timestamp}
     * via {@link java.sql.Timestamp#toLocalDateTime()}, and parses a {@link String} as ISO-8601
     * text — the representation {@code ObjectEncoders.dateTime()} writes. Returns
     * {@code required} if the value is {@code null}, {@code invalid_format} if the text does not
     * parse, and {@code type_mismatch} for any other type.
     *
     * @return a temporal decoder for {@code Object} input producing {@link LocalDateTime}
     */
    public static TemporalDecoder<@Nullable Object, LocalDateTime> dateTime() {
        return new TemporalDecoder<>((in, path) -> switch (in) {
            case null -> Result.fail(path, ErrorCodes.REQUIRED, "is required");
            case LocalDateTime dt -> Result.ok(dt);
            case java.sql.Timestamp ts -> Result.ok(ts.toLocalDateTime());
            case String s -> parseText(path, s, LocalDateTime::parse,
                    "not a valid ISO-8601 local date-time (e.g., 2024-01-15T10:30 or 2024-01-15T10:30:45)");
            default -> typeMismatch(path, "date-time", in);
        });
    }

    /**
     * Creates an {@link Instant} decoder.
     *
     * <p>Accepts {@link Instant} values directly, converts {@link java.sql.Timestamp}
     * via {@link java.sql.Timestamp#toInstant()}, and parses a {@link String} as ISO-8601 text —
     * the representation {@code ObjectEncoders.iso8601()} writes. Returns {@code required} if the
     * value is {@code null}, {@code invalid_format} if the text does not parse, and
     * {@code type_mismatch} for any other type.
     *
     * @return a temporal decoder for {@code Object} input producing {@link Instant}
     */
    public static TemporalDecoder<@Nullable Object, Instant> iso8601() {
        return new TemporalDecoder<>((in, path) -> switch (in) {
            case null -> Result.fail(path, ErrorCodes.REQUIRED, "is required");
            case Instant i -> Result.ok(i);
            case java.sql.Timestamp ts -> Result.ok(ts.toInstant());
            case String s -> parseText(path, s, Instant::parse, "not a valid ISO 8601 instant");
            default -> typeMismatch(path, "instant", in);
        });
    }

    /**
     * Creates an {@link OffsetDateTime} decoder.
     *
     * <p>Accepts {@link OffsetDateTime} values directly and parses a {@link String} as ISO-8601
     * text — the representation {@code ObjectEncoders.offsetDateTime()} writes. Returns
     * {@code required} if the value is {@code null}, {@code invalid_format} if the text does not
     * parse, and {@code type_mismatch} for any other type.
     *
     * <p>Unlike the other temporal decoders this one has no {@code java.sql} conversion.
     * {@link java.sql.Timestamp} carries no offset, so converting one would mean picking a zone
     * on the caller's behalf; JDBC 4.2 reads {@code TIMESTAMP WITH TIME ZONE} as an
     * {@link OffsetDateTime} directly.
     *
     * @return a temporal decoder for {@code Object} input producing {@link OffsetDateTime}
     */
    public static TemporalDecoder<@Nullable Object, OffsetDateTime> offsetDateTime() {
        return new TemporalDecoder<>((in, path) -> switch (in) {
            case null -> Result.fail(path, ErrorCodes.REQUIRED, "is required");
            case OffsetDateTime odt -> Result.ok(odt);
            case String s -> parseText(path, s, OffsetDateTime::parse,
                    "not a valid ISO-8601 offset date-time (e.g., 2024-01-15T10:30:00+09:00)");
            default -> typeMismatch(path, "offset-date-time", in);
        });
    }

    private static <T> Result<T> typeMismatch(Path path, String expected, Object actual) {
        return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected " + expected,
                Map.of("expected", expected, "actual", actual.getClass().getSimpleName()));
    }

    /**
     * Parses ISO-8601 text with {@code parse}, reporting {@code invalid_format} on failure with
     * the same message {@code StringDecoder} uses, so the two routes to a temporal value are
     * indistinguishable from the caller's side.
     */
    private static <T> Result<T> parseText(Path path, String text, Function<String, T> parse, String message) {
        try {
            return Result.ok(parse.apply(text));
        } catch (DateTimeParseException e) {
            return Result.fail(path, ErrorCodes.INVALID_FORMAT, message);
        }
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
    // The null branch deliberately produces a Result whose value is null (Decoder<..., @Nullable T>).
    // NullAway cannot verify the type-parameter variance of the lambda's Result<@Nullable T> return,
    // so this single honest nullness-widening site is suppressed.
    @SuppressWarnings("NullAway")
    public static <T> Decoder<@Nullable Object, @Nullable T> nullable(Decoder<@Nullable Object, T> dec) {
        return (in, path) -> {
            if (in == null) {
                return Result.<@Nullable T>ok(null);
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
    public static <T> ListDecoder<@Nullable Object, T> list(Decoder<@Nullable Object, T> elementDec) {
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
     * decoder, accumulating all errors. Non-{@link String} keys are converted via
     * {@link String#valueOf(Object)}; {@link String} keys are used directly.
     *
     * <p>Returns {@code required} if the value is {@code null},
     * and {@code type_mismatch} if the value is not a {@link Map}.
     *
     * @param <V>    the decoded value type
     * @param valDec the decoder for each map value
     * @return a record decoder for {@code Object} input
     */
    public static <V> RecordDecoder<@Nullable Object, V> map(Decoder<@Nullable Object, V> valDec) {
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

    // --- bytes ---

    /**
     * Creates a {@code byte[]} decoder.
     *
     * <p>Accepts {@code byte[]} values directly. Returns {@code required} if the value is
     * {@code null}, and {@code type_mismatch} if the value is not a {@code byte[]}.
     *
     * <p>Suitable for JDBC binary columns such as PostgreSQL {@code BYTEA}
     * or SQL standard {@code VARBINARY}.
     *
     * @return a byte array decoder for {@code Object} input
     */
    public static Decoder<@Nullable Object, byte[]> bytes() {
        return (in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (in instanceof byte[] ba) {
                return Result.ok(ba);
            }
            return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected byte[]",
                    Map.of("expected", "byte[]", "actual", in.getClass().getSimpleName()));
        };
    }

    // --- enumOf / literal ---

    /**
     * Creates an enum decoder that parses a string value into the given enum type.
     *
     * @param <E> the enum type
     * @param cls the enum class
     * @return a decoder that produces enum constants from string input
     */
    public static <E extends Enum<E>> Decoder<@Nullable Object, E> enumOf(Class<E> cls) {
        return Decoders.<@Nullable Object, E>enumOf(cls, string());
    }

    /**
     * Creates a decoder that accepts only the given literal string value.
     *
     * @param expected the expected string value
     * @return a decoder that succeeds only when the input matches {@code expected}
     */
    public static Decoder<@Nullable Object, String> literal(String expected) {
        return Decoders.<@Nullable Object>literal(expected, string());
    }
}
