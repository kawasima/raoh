package net.unit8.raoh.jooq;

import net.unit8.raoh.*;
import net.unit8.raoh.combinator.*;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Factory of decoders for {@link org.jooq.Record} input.
 *
 * <p>Usage: {@code import static net.unit8.raoh.jooq.JooqRecordDecoders.*;}
 *
 * <p>For primitive decoders ({@code string()}, {@code int_()}, etc.) that work on raw
 * {@code Object} values extracted from a jOOQ record, see {@link net.unit8.raoh.ObjectDecoders}.
 */
public final class JooqRecordDecoders {

    private JooqRecordDecoders() {}

    // --- field / optionalField / nullable ---

    /**
     * Extracts a named field from a {@link org.jooq.Record} and decodes it.
     *
     * @param <T>  the decoded value type
     * @param name the column name (case-insensitive per jOOQ convention)
     * @param dec  decoder for the raw value
     * @return a decoder for the named field
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
     * @param <T>  the decoded value type
     * @param name the column name
     * @param dec  decoder for the raw value
     * @return a decoder that produces {@code Optional<T>}
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
     * @param <T>  the decoded value type
     * @param name the column name
     * @param dec  decoder for the raw value when non-null
     * @return a decoder that produces {@link Presence Presence&lt;T&gt;}
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
     * @param <T> the decoded value type
     * @param dec the inner decoder
     * @return a decoder that passes through {@code null} without error
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
     * @param <T> the decoded value type
     * @param dec the decoder to apply to the same record
     * @return a decoder that delegates to {@code dec}
     */
    public static <T> JooqRecordDecoder<T> nested(JooqRecordDecoder<T> dec) {
        return dec::decode;
    }

    // --- combine delegates ---

    /**
     * Combines two field decoders, accumulating all errors.
     *
     * <p>Overloads for 2-16 decoders are provided. Call {@code .apply(MyRecord::new)} on the
     * returned combiner to produce the final {@code Decoder<Record, T>}.
     *
     * @param <A> the first decoded type
     * @param <B> the second decoded type
     * @param da  the first decoder
     * @param db  the second decoder
     * @return a combiner that can be finished with {@code apply}
     */
    public static <A, B> Combiner2<org.jooq.Record, A, B> combine(
            Decoder<org.jooq.Record, A> da, Decoder<org.jooq.Record, B> db) {
        return Decoders.combine(da, db);
    }

    /**
     * Combines 3 decoders for applicative-style validation.
     *
     * @param <A> the first decoded type
     * @param <B> the second decoded type
     * @param <C> the third decoded type
     * @param da  the first decoder
     * @param db  the second decoder
     * @param dc  the third decoder
     * @return a combiner that can be finished with {@code apply}
     * @see Decoders#combine(Decoder, Decoder, Decoder)
     */
    public static <A, B, C> Combiner3<org.jooq.Record, A, B, C> combine(
            Decoder<org.jooq.Record, A> da, Decoder<org.jooq.Record, B> db,
            Decoder<org.jooq.Record, C> dc) {
        return Decoders.combine(da, db, dc);
    }

    /**
     * Combines 4 decoders for applicative-style validation.
     *
     * @param <A> the first decoded type
     * @param <B> the second decoded type
     * @param <C> the third decoded type
     * @param <D> the fourth decoded type
     * @param da  the first decoder
     * @param db  the second decoder
     * @param dc  the third decoder
     * @param dd  the fourth decoder
     * @return a combiner that can be finished with {@code apply}
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D> Combiner4<org.jooq.Record, A, B, C, D> combine(
            Decoder<org.jooq.Record, A> da, Decoder<org.jooq.Record, B> db,
            Decoder<org.jooq.Record, C> dc, Decoder<org.jooq.Record, D> dd) {
        return Decoders.combine(da, db, dc, dd);
    }

    /**
     * Combines 5 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E> Combiner5<org.jooq.Record, A, B, C, D, E> combine(
            Decoder<org.jooq.Record, A> da, Decoder<org.jooq.Record, B> db,
            Decoder<org.jooq.Record, C> dc, Decoder<org.jooq.Record, D> dd,
            Decoder<org.jooq.Record, E> de) {
        return Decoders.combine(da, db, dc, dd, de);
    }

    /**
     * Combines 6 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E, F> Combiner6<org.jooq.Record, A, B, C, D, E, F> combine(
            Decoder<org.jooq.Record, A> da, Decoder<org.jooq.Record, B> db,
            Decoder<org.jooq.Record, C> dc, Decoder<org.jooq.Record, D> dd,
            Decoder<org.jooq.Record, E> de, Decoder<org.jooq.Record, F> df) {
        return Decoders.combine(da, db, dc, dd, de, df);
    }

    /**
     * Combines 7 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E, F, G> Combiner7<org.jooq.Record, A, B, C, D, E, F, G> combine(
            Decoder<org.jooq.Record, A> da, Decoder<org.jooq.Record, B> db,
            Decoder<org.jooq.Record, C> dc, Decoder<org.jooq.Record, D> dd,
            Decoder<org.jooq.Record, E> de, Decoder<org.jooq.Record, F> df,
            Decoder<org.jooq.Record, G> dg) {
        return Decoders.combine(da, db, dc, dd, de, df, dg);
    }

    /**
     * Combines 8 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E, F, G, H> Combiner8<org.jooq.Record, A, B, C, D, E, F, G, H> combine(
            Decoder<org.jooq.Record, A> da, Decoder<org.jooq.Record, B> db,
            Decoder<org.jooq.Record, C> dc, Decoder<org.jooq.Record, D> dd,
            Decoder<org.jooq.Record, E> de, Decoder<org.jooq.Record, F> df,
            Decoder<org.jooq.Record, G> dg, Decoder<org.jooq.Record, H> dh) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh);
    }

    /**
     * Combines 9 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E, F, G, H, J> Combiner9<org.jooq.Record, A, B, C, D, E, F, G, H, J> combine(
            Decoder<org.jooq.Record, A> da, Decoder<org.jooq.Record, B> db,
            Decoder<org.jooq.Record, C> dc, Decoder<org.jooq.Record, D> dd,
            Decoder<org.jooq.Record, E> de, Decoder<org.jooq.Record, F> df,
            Decoder<org.jooq.Record, G> dg, Decoder<org.jooq.Record, H> dh,
            Decoder<org.jooq.Record, J> dj) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj);
    }

    /**
     * Combines 10 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E, F, G, H, J, K> Combiner10<org.jooq.Record, A, B, C, D, E, F, G, H, J, K> combine(
            Decoder<org.jooq.Record, A> da, Decoder<org.jooq.Record, B> db,
            Decoder<org.jooq.Record, C> dc, Decoder<org.jooq.Record, D> dd,
            Decoder<org.jooq.Record, E> de, Decoder<org.jooq.Record, F> df,
            Decoder<org.jooq.Record, G> dg, Decoder<org.jooq.Record, H> dh,
            Decoder<org.jooq.Record, J> dj, Decoder<org.jooq.Record, K> dk) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk);
    }

    /**
     * Combines 11 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E, F, G, H, J, K, L> Combiner11<org.jooq.Record, A, B, C, D, E, F, G, H, J, K, L> combine(
            Decoder<org.jooq.Record, A> da, Decoder<org.jooq.Record, B> db,
            Decoder<org.jooq.Record, C> dc, Decoder<org.jooq.Record, D> dd,
            Decoder<org.jooq.Record, E> de, Decoder<org.jooq.Record, F> df,
            Decoder<org.jooq.Record, G> dg, Decoder<org.jooq.Record, H> dh,
            Decoder<org.jooq.Record, J> dj, Decoder<org.jooq.Record, K> dk,
            Decoder<org.jooq.Record, L> dl) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl);
    }

    /**
     * Combines 12 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E, F, G, H, J, K, L, M> Combiner12<org.jooq.Record, A, B, C, D, E, F, G, H, J, K, L, M> combine(
            Decoder<org.jooq.Record, A> da, Decoder<org.jooq.Record, B> db,
            Decoder<org.jooq.Record, C> dc, Decoder<org.jooq.Record, D> dd,
            Decoder<org.jooq.Record, E> de, Decoder<org.jooq.Record, F> df,
            Decoder<org.jooq.Record, G> dg, Decoder<org.jooq.Record, H> dh,
            Decoder<org.jooq.Record, J> dj, Decoder<org.jooq.Record, K> dk,
            Decoder<org.jooq.Record, L> dl, Decoder<org.jooq.Record, M> dm) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm);
    }

    /**
     * Combines 13 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E, F, G, H, J, K, L, M, N> Combiner13<org.jooq.Record, A, B, C, D, E, F, G, H, J, K, L, M, N> combine(
            Decoder<org.jooq.Record, A> da, Decoder<org.jooq.Record, B> db,
            Decoder<org.jooq.Record, C> dc, Decoder<org.jooq.Record, D> dd,
            Decoder<org.jooq.Record, E> de, Decoder<org.jooq.Record, F> df,
            Decoder<org.jooq.Record, G> dg, Decoder<org.jooq.Record, H> dh,
            Decoder<org.jooq.Record, J> dj, Decoder<org.jooq.Record, K> dk,
            Decoder<org.jooq.Record, L> dl, Decoder<org.jooq.Record, M> dm,
            Decoder<org.jooq.Record, N> dn) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn);
    }

    /**
     * Combines 14 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E, F, G, H, J, K, L, M, N, O> Combiner14<org.jooq.Record, A, B, C, D, E, F, G, H, J, K, L, M, N, O> combine(
            Decoder<org.jooq.Record, A> da, Decoder<org.jooq.Record, B> db,
            Decoder<org.jooq.Record, C> dc, Decoder<org.jooq.Record, D> dd,
            Decoder<org.jooq.Record, E> de, Decoder<org.jooq.Record, F> df,
            Decoder<org.jooq.Record, G> dg, Decoder<org.jooq.Record, H> dh,
            Decoder<org.jooq.Record, J> dj, Decoder<org.jooq.Record, K> dk,
            Decoder<org.jooq.Record, L> dl, Decoder<org.jooq.Record, M> dm,
            Decoder<org.jooq.Record, N> dn, Decoder<org.jooq.Record, O> do_) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn, do_);
    }

    /**
     * Combines 15 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E, F, G, H, J, K, L, M, N, O, P> Combiner15<org.jooq.Record, A, B, C, D, E, F, G, H, J, K, L, M, N, O, P> combine(
            Decoder<org.jooq.Record, A> da, Decoder<org.jooq.Record, B> db,
            Decoder<org.jooq.Record, C> dc, Decoder<org.jooq.Record, D> dd,
            Decoder<org.jooq.Record, E> de, Decoder<org.jooq.Record, F> df,
            Decoder<org.jooq.Record, G> dg, Decoder<org.jooq.Record, H> dh,
            Decoder<org.jooq.Record, J> dj, Decoder<org.jooq.Record, K> dk,
            Decoder<org.jooq.Record, L> dl, Decoder<org.jooq.Record, M> dm,
            Decoder<org.jooq.Record, N> dn, Decoder<org.jooq.Record, O> do_,
            Decoder<org.jooq.Record, P> dp) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn, do_, dp);
    }

    /**
     * Combines 16 decoders for applicative-style validation.
     *
     * @see Decoders#combine(Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder, Decoder)
     */
    public static <A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, Q> Combiner16<org.jooq.Record, A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, Q> combine(
            Decoder<org.jooq.Record, A> da, Decoder<org.jooq.Record, B> db,
            Decoder<org.jooq.Record, C> dc, Decoder<org.jooq.Record, D> dd,
            Decoder<org.jooq.Record, E> de, Decoder<org.jooq.Record, F> df,
            Decoder<org.jooq.Record, G> dg, Decoder<org.jooq.Record, H> dh,
            Decoder<org.jooq.Record, J> dj, Decoder<org.jooq.Record, K> dk,
            Decoder<org.jooq.Record, L> dl, Decoder<org.jooq.Record, M> dm,
            Decoder<org.jooq.Record, N> dn, Decoder<org.jooq.Record, O> do_,
            Decoder<org.jooq.Record, P> dp, Decoder<org.jooq.Record, Q> dq) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn, do_, dp, dq);
    }

    /**
     * Returns a {@link CombinerList} for combining more than 16 decoders.
     *
     * @param <T>      the output type
     * @param decoders the decoders to combine
     * @return a combiner on which {@code .apply(f)} can be called
     * @see Decoders#combine(List)
     */
    public static <T> CombinerList<org.jooq.Record> combine(List<Decoder<org.jooq.Record, ?>> decoders) {
        return Decoders.combine(decoders);
    }
}
