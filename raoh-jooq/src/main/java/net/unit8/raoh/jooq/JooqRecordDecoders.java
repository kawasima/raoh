package net.unit8.raoh.jooq;

import net.unit8.raoh.*;
import net.unit8.raoh.builtin.*;
import net.unit8.raoh.combinator.*;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Factory of decoders for {@link org.jooq.Record} input.
 *
 * <p>Usage: {@code import static net.unit8.raoh.jooq.JooqRecordDecoders.*;}
 *
 * <p>Primitive decoders receive raw {@code Object} values extracted from
 * a jOOQ {@link org.jooq.Record} by {@link #field}. The raw value is
 * whatever jOOQ maps the SQL type to (e.g. {@link Integer}, {@link String}).
 */
public final class JooqRecordDecoders {

    private JooqRecordDecoders() {}

    // --- Primitive decoders ---

    /**
     * Decodes a field value as a {@link String}.
     */
    public static StringDecoder<Object> string() {
        Decoder<Object, String> base = stringBase();
        return new StringDecoder<>(base, base);
    }

    /**
     * Decodes a field value as a {@link String}, allowing blank values.
     */
    public static StringDecoder<Object> allowBlankString() {
        return new StringDecoder<>(stringBase());
    }

    private static Decoder<Object, String> stringBase() {
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
     * Decodes a field value as an {@code int}.
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
     * Decodes a field value as a {@code long}.
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
     * Decodes a field value as a {@code boolean}.
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
     * Decodes a field value as a {@link BigDecimal}.
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

    // --- field / optionalField / nullable ---

    /**
     * Extracts a named field from a {@link org.jooq.Record} and decodes it.
     *
     * @param name the column name (case-insensitive per jOOQ convention)
     * @param dec  decoder for the raw value
     */
    public static <T> JooqRecordDecoder<T> field(String name, Decoder<Object, T> dec) {
        return (in, path) -> {
            var fieldPath = path.append(name);
            if (in == null) {
                return Result.fail(fieldPath, ErrorCodes.REQUIRED, "is required");
            }
            if (in.field(name) == null) {
                return Result.fail(fieldPath, ErrorCodes.MISSING_FIELD, "field '" + name + "' not found in record");
            }
            return dec.decode(in.get(name), fieldPath);
        };
    }

    /**
     * Extracts a named field from a {@link org.jooq.Record} as an {@link Optional}.
     * Returns {@link Optional#empty()} when the field is absent from the record.
     *
     * @param name the column name
     * @param dec  decoder for the raw value
     */
    public static <T> JooqRecordDecoder<Optional<T>> optionalField(String name, Decoder<Object, T> dec) {
        return (in, path) -> {
            var fieldPath = path.append(name);
            if (in == null || in.field(name) == null) {
                return Result.ok(Optional.empty());
            }
            return dec.decode(in.get(name), fieldPath).map(Optional::of);
        };
    }

    /**
     * Extracts a named field from a {@link org.jooq.Record} as a {@link Presence}.
     * Distinguishes absent, present-null, and present-with-value.
     *
     * @param name the column name
     * @param dec  decoder for the raw value when non-null
     */
    public static <T> JooqRecordDecoder<Presence<T>> optionalNullableField(String name, Decoder<Object, T> dec) {
        return (in, path) -> {
            var fieldPath = path.append(name);
            if (in == null || in.field(name) == null) {
                return Result.ok(new Presence.Absent<>());
            }
            var value = in.get(name);
            if (value == null) {
                return Result.ok(new Presence.PresentNull<>());
            }
            return dec.decode(value, fieldPath)
                    .map(v -> (Presence<T>) new Presence.Present<>(v));
        };
    }

    /**
     * Makes a decoder accept {@code null} as a valid value, returning {@code null}.
     *
     * <p><strong>Note:</strong> The returned decoder produces {@code Ok(null)} when the column
     * value is SQL NULL. Callers must handle {@code null} explicitly. Prefer
     * {@link #optionalField} for optional semantics.
     *
     * @param dec the inner decoder
     */
    public static <T> Decoder<Object, @Nullable T> nullable(Decoder<Object, T> dec) {
        return (in, path) -> {
            if (in == null) {
                return Result.ok(null);
            }
            return dec.decode(in, path);
        };
    }

    /**
     * Applies another {@link JooqRecordDecoder} to the same {@link org.jooq.Record}.
     *
     * <p>This is the primary building block for mapping a flat JOIN result into a
     * nested domain type. Each sub-decoder reads its own subset of columns from
     * the same record, and {@link #combine} accumulates their errors together.
     *
     * <pre>{@code
     * // SELECT u.name, u.email, a.city, a.zip FROM users u JOIN addresses a ...
     * Decoder<Record, UserWithAddress> dec = combine(
     *     nested(userDecoder),
     *     nested(addressDecoder)
     * ).apply(UserWithAddress::new);
     * }</pre>
     *
     * @param dec the decoder to apply to the same record
     */
    public static <T> JooqRecordDecoder<T> nested(JooqRecordDecoder<T> dec) {
        return dec::decode;
    }

    // --- enumOf ---

    /**
     * Decodes a field value as an enum constant of the given class.
     *
     * @param cls the enum class
     */
    public static <E extends Enum<E>> Decoder<Object, E> enumOf(Class<E> cls) {
        return Decoders.enumOf(cls, allowBlankString());
    }

    // --- combine delegates ---

    /**
     * Combines two field decoders, accumulating all errors.
     */
    public static <A, B> Combiner2<org.jooq.Record, A, B> combine(
            Decoder<org.jooq.Record, A> da, Decoder<org.jooq.Record, B> db) {
        return Decoders.combine(da, db);
    }

    /**
     * Combines three field decoders, accumulating all errors.
     */
    public static <A, B, C> Combiner3<org.jooq.Record, A, B, C> combine(
            Decoder<org.jooq.Record, A> da, Decoder<org.jooq.Record, B> db,
            Decoder<org.jooq.Record, C> dc) {
        return Decoders.combine(da, db, dc);
    }

    /**
     * Combines four field decoders, accumulating all errors.
     */
    public static <A, B, C, D> Combiner4<org.jooq.Record, A, B, C, D> combine(
            Decoder<org.jooq.Record, A> da, Decoder<org.jooq.Record, B> db,
            Decoder<org.jooq.Record, C> dc, Decoder<org.jooq.Record, D> dd) {
        return Decoders.combine(da, db, dc, dd);
    }

    /**
     * Combines five field decoders, accumulating all errors.
     */
    public static <A, B, C, D, E> Combiner5<org.jooq.Record, A, B, C, D, E> combine(
            Decoder<org.jooq.Record, A> da, Decoder<org.jooq.Record, B> db,
            Decoder<org.jooq.Record, C> dc, Decoder<org.jooq.Record, D> dd,
            Decoder<org.jooq.Record, E> de) {
        return Decoders.combine(da, db, dc, dd, de);
    }

    /**
     * Combines six field decoders, accumulating all errors.
     */
    public static <A, B, C, D, E, F> Combiner6<org.jooq.Record, A, B, C, D, E, F> combine(
            Decoder<org.jooq.Record, A> da, Decoder<org.jooq.Record, B> db,
            Decoder<org.jooq.Record, C> dc, Decoder<org.jooq.Record, D> dd,
            Decoder<org.jooq.Record, E> de, Decoder<org.jooq.Record, F> df) {
        return Decoders.combine(da, db, dc, dd, de, df);
    }

    /**
     * Combines seven field decoders, accumulating all errors.
     */
    public static <A, B, C, D, E, F, G> Combiner7<org.jooq.Record, A, B, C, D, E, F, G> combine(
            Decoder<org.jooq.Record, A> da, Decoder<org.jooq.Record, B> db,
            Decoder<org.jooq.Record, C> dc, Decoder<org.jooq.Record, D> dd,
            Decoder<org.jooq.Record, E> de, Decoder<org.jooq.Record, F> df,
            Decoder<org.jooq.Record, G> dg) {
        return Decoders.combine(da, db, dc, dd, de, df, dg);
    }

    /**
     * Combines eight field decoders, accumulating all errors.
     */
    public static <A, B, C, D, E, F, G, H> Combiner8<org.jooq.Record, A, B, C, D, E, F, G, H> combine(
            Decoder<org.jooq.Record, A> da, Decoder<org.jooq.Record, B> db,
            Decoder<org.jooq.Record, C> dc, Decoder<org.jooq.Record, D> dd,
            Decoder<org.jooq.Record, E> de, Decoder<org.jooq.Record, F> df,
            Decoder<org.jooq.Record, G> dg, Decoder<org.jooq.Record, H> dh) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh);
    }
}
