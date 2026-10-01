package net.unit8.raoh.decode;

import net.unit8.raoh.Err;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Issues;
import net.unit8.raoh.MessageKeys;
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
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Factory of primitive decoders for raw {@code Object} input.
 *
 * <p>These decoders accept a raw {@code Object} value and perform a runtime type check,
 * returning the value as the expected type or a {@code type_mismatch} error. The temporal
 * decoders additionally accept ISO-8601 text, so they read what {@code ObjectEncoders} writes.
 * They do not accept {@code java.sql.Date}, {@code java.sql.Time} or {@code java.sql.Timestamp}:
 * converting those reads the JVM default time zone, which would make the decoder itself read
 * ambient state. Convert JDBC values to {@code java.time} types where they are read, for example with
 * {@code ResultSet.getObject(column, LocalDate.class)}.
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
            return typeMismatch(path, "string", in);
        };
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
            return typeMismatch(path, "boolean", in);
        });
    }

    // --- Numeric decoders ---
    // Raoh decides how each accepted source type converts to each target. The decoders accept a
    // closed set of JDK representations and never call a conversion method on an arbitrary
    // Number (intValue(), toString(), ...), whose result depends on how the input implements it.
    // BigInteger and BigDecimal are not final, so they are matched by exact class: a subclass
    // could override the very methods used below. JsonDecoders hands the number nodes it admits to
    // these decoders, so the JSON route converts by the same rules; which JSON numbers it admits
    // is decided in JsonDecoders.

    /**
     * Creates an integer decoder.
     *
     * <p>Accepts {@link Integer}, {@link Short}, {@link Byte}, {@link Long}, {@link BigInteger}
     * and {@link BigDecimal} when the value is an integer that fits {@code int}; a
     * {@code BigDecimal} such as {@code 5.00} is accepted as {@code 5}. Returns {@code required}
     * if the value is {@code null}, and {@code type_mismatch} for an integer outside the
     * {@code int} range, a {@code BigDecimal} with a fractional part, a {@link Double} or
     * {@link Float} (even when it holds an integral value), and any other type, other
     * {@link Number} subtypes included. A binary floating-point value is rejected even when
     * integral because it may already be the rounded form of a different number
     * ({@code 1.0000000000000001} parses to the {@code double} {@code 1.0}); a {@code BigDecimal}
     * holds the decimal it was given.
     *
     * @return an integer decoder for {@code Object} input
     */
    public static IntDecoder<@Nullable Object> int_() {
        return new IntDecoder<>((in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            return switch (in) {
                case Integer i -> Result.ok(i);
                case Short s -> Result.ok((int) s);
                case Byte b -> Result.ok((int) b);
                case Long l when l == (int) (long) l -> Result.ok((int) (long) l);
                case Long l -> outsideRange(path, "integer");
                case BigInteger bi when bi.getClass() == BigInteger.class -> toInt(bi, path);
                case BigDecimal bd when bd.getClass() == BigDecimal.class && isIntegral(bd) -> {
                    var bi = toBoundedBigInteger(bd);
                    yield bi == null ? outsideRange(path, "integer") : toInt(bi, path);
                }
                default -> typeMismatch(path, "integer", in);
            };
        });
    }

    /**
     * Creates a long decoder.
     *
     * <p>Accepts {@link Long}, {@link Integer}, {@link Short}, {@link Byte}, {@link BigInteger}
     * and {@link BigDecimal} when the value is an integer that fits {@code long}; a
     * {@code BigDecimal} such as {@code 5.00} is accepted as {@code 5}. Returns {@code required}
     * if the value is {@code null}, and {@code type_mismatch} for an integer outside the
     * {@code long} range, a {@code BigDecimal} with a fractional part, a {@link Double} or
     * {@link Float} (even when it holds an integral value), and any other type, other
     * {@link Number} subtypes included. A binary floating-point value is rejected even when
     * integral because it may already be the rounded form of a different number
     * ({@code 1.0000000000000001} parses to the {@code double} {@code 1.0}); a {@code BigDecimal}
     * holds the decimal it was given.
     *
     * @return a long decoder for {@code Object} input
     */
    public static LongDecoder<@Nullable Object> long_() {
        return new LongDecoder<>((in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            return switch (in) {
                case Long l -> Result.ok(l);
                case Integer i -> Result.ok((long) i);
                case Short s -> Result.ok((long) s);
                case Byte b -> Result.ok((long) b);
                case BigInteger bi when bi.getClass() == BigInteger.class -> toLong(bi, path);
                case BigDecimal bd when bd.getClass() == BigDecimal.class && isIntegral(bd) -> {
                    var bi = toBoundedBigInteger(bd);
                    yield bi == null ? outsideRange(path, "long") : toLong(bi, path);
                }
                default -> typeMismatch(path, "long", in);
            };
        });
    }

    /**
     * Creates a double decoder.
     *
     * <p>Accepts {@link Double}, {@link Float}, {@link Long}, {@link Integer}, {@link Short},
     * {@link Byte}, {@link BigInteger} and {@link BigDecimal}. A value {@code double} cannot hold
     * exactly is rounded to the nearest {@code double} (IEEE 754 round-to-nearest), so
     * {@code 9007199254740993L} decodes to {@code 9007199254740992.0} and
     * {@code new BigDecimal("0.1")} to {@code 0.1}. Returns {@code required} if the value is
     * {@code null}, and {@code type_mismatch} for an infinity, a value whose magnitude is beyond
     * the {@code double} range, and any other type, other {@link Number} subtypes included.
     * {@code NaN} is accepted, so that range constraints can reject it.
     *
     * @return a double decoder for {@code Object} input
     */
    public static DoubleDecoder<@Nullable Object> double_() {
        return new DoubleDecoder<>((in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            Double d = switch (in) {
                case Double v -> v;
                case Float f -> (double) f;
                case Long l -> (double) l;
                case Integer i -> (double) i;
                case Short s -> (double) s;
                case Byte b -> (double) b;
                case BigInteger bi when bi.getClass() == BigInteger.class -> bi.doubleValue();
                case BigDecimal bd when bd.getClass() == BigDecimal.class -> bd.doubleValue();
                default -> null;
            };
            if (d == null) {
                return typeMismatch(path, "double", in);
            }
            // NaN is left to flow through so range constraints can reject it as out_of_range
            // (see DoubleDecoderTest#rangeRejectsNaN).
            if (d.isInfinite()) {
                return outsideRange(path, "double");
            }
            return Result.ok(d);
        });
    }

    /**
     * Creates a float decoder.
     *
     * <p>Accepts the same types as {@link #double_()} and rounds to the nearest {@code float}
     * (IEEE 754 round-to-nearest), so {@code 16777217} decodes to {@code 16777216.0f}. Returns
     * {@code required} if the value is {@code null}, and {@code type_mismatch} for an infinity, a
     * value whose magnitude is beyond the {@code float} range, and any other type, other
     * {@link Number} subtypes included. {@code NaN} is accepted, so that range constraints can
     * reject it.
     *
     * @return a float decoder for {@code Object} input
     */
    public static FloatDecoder<@Nullable Object> float_() {
        return new FloatDecoder<>((in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            Float f = switch (in) {
                case Float v -> v;
                case Double d -> (float) (double) d;
                case Long l -> (float) l;
                case Integer i -> (float) i;
                case Short s -> (float) s;
                case Byte b -> (float) b;
                case BigInteger bi when bi.getClass() == BigInteger.class -> bi.floatValue();
                case BigDecimal bd when bd.getClass() == BigDecimal.class -> bd.floatValue();
                default -> null;
            };
            if (f == null) {
                return typeMismatch(path, "float", in);
            }
            // See double_(): NaN flows through for range constraints to catch.
            if (f.isInfinite()) {
                return outsideRange(path, "float");
            }
            return Result.ok(f);
        });
    }

    /**
     * Creates a {@link BigDecimal} decoder.
     *
     * <p>Accepts {@link BigDecimal} values directly, and converts {@link BigInteger},
     * {@link Long}, {@link Integer}, {@link Short} and {@link Byte} exactly with scale 0.
     * A finite {@link Double} or {@link Float} is converted to the decimal its
     * {@code toString()} prints, the shortest decimal that reads back as the same value, so
     * {@code 0.1d} decodes to {@code 0.1} rather than to the binary value's full expansion.
     * Returns {@code required} if the value is {@code null}, and {@code type_mismatch} for
     * {@code NaN}, an infinity, and any other type, other {@link Number} subtypes included.
     *
     * @return a decimal decoder for {@code Object} input
     */
    public static DecimalDecoder<@Nullable Object> decimal() {
        return new DecimalDecoder<>((in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            BigDecimal bd = switch (in) {
                case BigDecimal v when v.getClass() == BigDecimal.class -> v;
                case BigInteger bi when bi.getClass() == BigInteger.class -> new BigDecimal(bi);
                case Long l -> BigDecimal.valueOf(l);
                case Integer i -> BigDecimal.valueOf(i);
                case Short s -> BigDecimal.valueOf(s);
                case Byte b -> BigDecimal.valueOf(b);
                // BigDecimal.valueOf(double) goes through Double.toString(double).
                case Double d when Double.isFinite(d) -> BigDecimal.valueOf(d);
                case Float f when Float.isFinite(f) -> new BigDecimal(Float.toString(f));
                default -> null;
            };
            if (bd == null) {
                return typeMismatch(path, "number", in);
            }
            return Result.ok(bd);
        });
    }

    private static Result<Integer> toInt(BigInteger bi, Path path) {
        return bi.bitLength() < Integer.SIZE ? Result.ok(bi.intValue()) : outsideRange(path, "integer");
    }

    private static Result<Long> toLong(BigInteger bi, Path path) {
        return bi.bitLength() < Long.SIZE ? Result.ok(bi.longValue()) : outsideRange(path, "long");
    }

    /**
     * Whether {@code bd} has no fractional part, so that {@code 5.00} counts as an integer.
     * A non-positive scale short-circuits, so a value such as {@code 1E+999999999} is never
     * stripped.
     *
     * @param bd the value to check
     * @return {@code true} if {@code bd} is an integer
     */
    private static boolean isIntegral(BigDecimal bd) {
        return bd.scale() <= 0 || bd.stripTrailingZeros().scale() <= 0;
    }

    /**
     * The value of an integral {@code bd} as a {@link BigInteger}, or {@code null} when it has
     * more integer digits than any {@code long} (19). Counting digits first keeps
     * {@code toBigInteger()} from expanding a value such as {@code 1E+999999999} to a billion
     * digits.
     *
     * @param bd an integral value
     * @return the same value as a {@code BigInteger}, or {@code null} if it cannot fit a {@code long}
     */
    private static @Nullable BigInteger toBoundedBigInteger(BigDecimal bd) {
        if (bd.signum() == 0) {
            // precision - scale counts 0E+30 as 31 digits, but it is zero.
            return BigInteger.ZERO;
        }
        // long arithmetic: precision - scale overflows int for a scale near Integer.MIN_VALUE.
        return (long) bd.precision() - bd.scale() <= 19 ? bd.toBigInteger() : null;
    }

    private static <T> Result<T> outsideRange(Path path, String expected) {
        return Result.fail(path, ErrorCodes.TYPE_MISMATCH, MessageKeys.TYPE_MISMATCH_NUMERIC_RANGE,
                "value is outside the " + expected + " range", Map.of("expected", expected));
    }

    // --- Temporal decoders ---
    // A String input is handed to the matching StringDecoder conversion, so the Object route and
    // the String route read text by one set of rules and fail with the same Issue.

    /**
     * Creates a {@link LocalDate} decoder.
     *
     * <p>Accepts {@link LocalDate} values directly and parses a {@link String} as ISO-8601
     * text — the representation {@code ObjectEncoders.date()} writes. Returns {@code required}
     * if the value is {@code null}, {@code invalid_format} if the text does not parse, and
     * {@code type_mismatch} for any other type, {@link java.sql.Date} included.
     *
     * @return a temporal decoder for {@code Object} input producing {@link LocalDate}
     */
    public static TemporalDecoder<@Nullable Object, LocalDate> date() {
        var text = string().date();
        return new TemporalDecoder<>((in, path) -> switch (in) {
            case null -> Result.fail(path, ErrorCodes.REQUIRED, "is required");
            case LocalDate d -> Result.ok(d);
            case String s -> text.decode(s, path);
            default -> typeMismatch(path, "date", in);
        });
    }

    /**
     * Creates a {@link LocalTime} decoder.
     *
     * <p>Accepts {@link LocalTime} values directly and parses a {@link String} as ISO-8601
     * text — the representation {@code ObjectEncoders.time()} writes. Returns {@code required}
     * if the value is {@code null}, {@code invalid_format} if the text does not parse, and
     * {@code type_mismatch} for any other type, {@link java.sql.Time} included.
     *
     * @return a temporal decoder for {@code Object} input producing {@link LocalTime}
     */
    public static TemporalDecoder<@Nullable Object, LocalTime> time() {
        var text = string().time();
        return new TemporalDecoder<>((in, path) -> switch (in) {
            case null -> Result.fail(path, ErrorCodes.REQUIRED, "is required");
            case LocalTime t -> Result.ok(t);
            case String s -> text.decode(s, path);
            default -> typeMismatch(path, "time", in);
        });
    }

    /**
     * Creates a {@link LocalDateTime} decoder.
     *
     * <p>Accepts {@link LocalDateTime} values directly and parses a {@link String} as ISO-8601
     * text — the representation {@code ObjectEncoders.dateTime()} writes. Returns
     * {@code required} if the value is {@code null}, {@code invalid_format} if the text does not
     * parse, and {@code type_mismatch} for any other type, {@link java.sql.Timestamp} included.
     *
     * @return a temporal decoder for {@code Object} input producing {@link LocalDateTime}
     */
    public static TemporalDecoder<@Nullable Object, LocalDateTime> dateTime() {
        var text = string().dateTime();
        return new TemporalDecoder<>((in, path) -> switch (in) {
            case null -> Result.fail(path, ErrorCodes.REQUIRED, "is required");
            case LocalDateTime dt -> Result.ok(dt);
            case String s -> text.decode(s, path);
            default -> typeMismatch(path, "date-time", in);
        });
    }

    /**
     * Creates an {@link Instant} decoder.
     *
     * <p>Accepts {@link Instant} values directly and parses a {@link String} as ISO-8601 text —
     * the representation {@code ObjectEncoders.iso8601()} writes. Returns {@code required} if the
     * value is {@code null}, {@code invalid_format} if the text does not parse (see
     * {@link StringDecoder#iso8601(String)} for the accepted text), and {@code type_mismatch} for
     * any other type, {@link java.sql.Timestamp} included.
     *
     * @return a temporal decoder for {@code Object} input producing {@link Instant}
     */
    public static TemporalDecoder<@Nullable Object, Instant> iso8601() {
        var text = string().iso8601();
        return new TemporalDecoder<>((in, path) -> switch (in) {
            case null -> Result.fail(path, ErrorCodes.REQUIRED, "is required");
            case Instant i -> Result.ok(i);
            case String s -> text.decode(s, path);
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
     * @return a temporal decoder for {@code Object} input producing {@link OffsetDateTime}
     */
    public static TemporalDecoder<@Nullable Object, OffsetDateTime> offsetDateTime() {
        var text = string().offsetDateTime();
        return new TemporalDecoder<>((in, path) -> switch (in) {
            case null -> Result.fail(path, ErrorCodes.REQUIRED, "is required");
            case OffsetDateTime odt -> Result.ok(odt);
            case String s -> text.decode(s, path);
            default -> typeMismatch(path, "offset-date-time", in);
        });
    }

    private static <T> Result<T> typeMismatch(Path path, String expected, Object actual) {
        return Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected " + expected,
                Map.of("expected", expected, "actual", actual.getClass().getSimpleName()));
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
     * Gives {@code fallback} when the value is {@code null}, and decodes any other value with
     * {@code dec}, returning its result unchanged.
     *
     * <p>The value is looked at before {@code dec} runs, and {@code dec}'s result is never looked
     * at: a failure of {@code dec} is returned as it is, whatever its issues are. Use
     * {@link Decoders#recover(Decoder, Object) recover} to give a value instead of a failure.
     * Because the value is looked at first, {@code withDefault(nullable(dec), x)} gives {@code x}
     * for {@code null}.
     *
     * <p>A {@code Map} field passes {@code null} both for a key that is absent and for a key mapped
     * to {@code null}, so {@code field("role", withDefault(enumOf(Role.class), Role.MEMBER))} gives
     * the default in both cases. A jOOQ column that holds SQL {@code NULL} likewise gives the
     * default. A column missing from the record is a different thing: it is the record's structure,
     * not the column's value, and {@code JooqRecordDecoders.field} refuses it with
     * {@code missing_field} before the value decoder runs. To default both a missing column and
     * SQL {@code NULL}, write
     * {@code optionalField("currency", withDefault(string(), "JPY")).map(c -> c.orElse("JPY"))}:
     * {@code optionalField} gives the default for the missing column, and {@code withDefault} for
     * {@code NULL}, which {@code optionalField} alone passes to {@code string()} as {@code null}.
     *
     * @param <T>      the decoded value type
     * @param dec      the decoder for a value that is not {@code null}
     * @param fallback the value to give for {@code null}
     * @return a decoder that gives {@code fallback} for {@code null}
     */
    public static <T> Decoder<@Nullable Object, T> withDefault(Decoder<@Nullable Object, T> dec, T fallback) {
        return (in, path) -> in == null ? Result.ok(fallback) : dec.decode(in, path);
    }

    /**
     * Like {@link #withDefault(Decoder, Object)}, but the default is computed by {@code fallback},
     * which is called once for each {@code null} value and never otherwise.
     *
     * @param <T>      the decoded value type
     * @param dec      the decoder for a value that is not {@code null}
     * @param fallback supplies the value to give for {@code null}
     * @return a decoder that gives the supplied value for {@code null}
     */
    public static <T> Decoder<@Nullable Object, T> withDefault(Decoder<@Nullable Object, T> dec,
                                                               Supplier<? extends T> fallback) {
        return (in, path) -> in == null ? Result.ok(fallback.get()) : dec.decode(in, path);
    }

    /**
     * Creates a list decoder that decodes each element of a {@link List} with the given decoder,
     * accumulating all errors rather than short-circuiting on the first failure.
     *
     * <p>Returns {@code required} if the value is {@code null},
     * and {@code type_mismatch} if the value is not a {@link List}.
     *
     * <p>The decoded list is unmodifiable and holds every element in input order, including
     * {@code null} when {@code elementDec} decodes an element to {@code null}.
     *
     * @param <T>        the decoded element type
     * @param elementDec the decoder for each list element
     * @return a list decoder for {@code Object} input
     */
    public static <T extends @Nullable Object> ListDecoder<@Nullable Object, T> list(Decoder<@Nullable Object, T> elementDec) {
        return new ListDecoder<>((in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (!(in instanceof List<?> rawList)) {
                return typeMismatch(path, "array", in);
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
            return Result.ok(Collections.unmodifiableList(results));
        });
    }

    /**
     * Creates a map decoder that decodes each value of a {@code Map<String, ?>} with the given
     * decoder, accumulating all errors.
     *
     * <p>Returns {@code required} if the value is {@code null}, and {@code type_mismatch} if the
     * value is not a {@link Map} or any of its keys is not a non-null {@link String}. Keys are
     * checked before any value is decoded, and a bad key is reported at the map's own path: it is
     * never converted to a string, since {@code 1} and {@code "1"} would then be the same key.
     *
     * <p>The decoded map is unmodifiable, iterates in the input's key order, and keeps a
     * {@code null} value when {@code valDec} decodes a value to {@code null}.
     *
     * @param <V>    the decoded value type
     * @param valDec the decoder for each map value
     * @return a record decoder for {@code Object} input
     */
    public static <V extends @Nullable Object> RecordDecoder<@Nullable Object, V> map(Decoder<@Nullable Object, V> valDec) {
        return new RecordDecoder<>((in, path) -> {
            if (in == null) {
                return Result.fail(path, ErrorCodes.REQUIRED, "is required");
            }
            if (!(in instanceof Map<?, ?> rawMap)) {
                return typeMismatch(path, "object", in);
            }
            var keyType = nonStringKeyType(rawMap);
            if (keyType != null) {
                return nonStringKeyFailure(path, keyType);
            }
            var issues = Issues.EMPTY;
            var results = new LinkedHashMap<String, V>();
            for (var entry : rawMap.entrySet()) {
                var key = (String) entry.getKey();
                var r = valDec.decode(entry.getValue(), path.append(key));
                switch (r) {
                    case Ok<V> ok -> results.put(key, ok.value());
                    case Err<V> err -> issues = issues.merge(err.issues());
                }
            }
            if (!issues.isEmpty()) {
                return Result.err(issues);
            }
            return Result.ok(Collections.unmodifiableMap(results));
        });
    }

    /**
     * Names the type of the first key of {@code map} that is not a {@link String}, or returns
     * {@code null} if every key is one. Every key is checked: a map is a {@code Map<String, ?>} only if all of
     * them are, and the first key says nothing about the rest.
     *
     * <p>{@code MapDecoders.nested()} applies the same check at the same boundary.
     */
    private static @Nullable String nonStringKeyType(Map<?, ?> map) {
        for (var key : map.keySet()) {
            if (!(key instanceof String)) {
                return key == null ? "null" : key.getClass().getSimpleName();
            }
        }
        return null;
    }

    private static <T> Result<T> nonStringKeyFailure(Path path, String keyType) {
        return Result.fail(path, ErrorCodes.TYPE_MISMATCH, MessageKeys.TYPE_MISMATCH_STRING_KEYS,
                "expected object with string keys, found " + keyType + " key",
                Map.of("expected", "object with string keys", "actual", keyType));
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
            return typeMismatch(path, "byte[]", in);
        };
    }

    // --- enumOf / literal ---

    /**
     * Creates an enum decoder that parses a string value into the given enum type, matching the
     * constant name ASCII case-insensitively.
     *
     * <p>{@code A}-{@code Z} are equivalent to {@code a}-{@code z}; every other character must
     * match exactly. See {@link Decoders#enumOf} for the full contract.
     *
     * @param <E> the enum type
     * @param cls the enum class
     * @return a decoder that produces enum constants from string input
     * @throws IllegalArgumentException if two enum constant names are equal under ASCII
     *                                  case-insensitive matching
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
