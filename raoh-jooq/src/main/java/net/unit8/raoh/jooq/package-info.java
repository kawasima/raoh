/**
 * Decoders for {@link org.jooq.Record} input.
 *
 * <p>{@link net.unit8.raoh.jooq.JooqRecordDecoders} builds decoders that turn a single
 * {@link org.jooq.Record} (one row) into a domain object. To decode the result of a query, apply
 * such a decoder to the rows the caller has already fetched — raoh does not run the query itself.
 *
 * <p><strong>Single row.</strong> Apply the decoder directly to a fetched record:
 *
 * <pre>{@code
 * Result<Problem> one = PROBLEM_DECODER.decode(
 *         ctx.selectFrom(PROBLEMS).where(ID.eq(id)).fetchOne());
 *
 * // Zero-or-one row: keep the "no row" case as Optional, the decode outcome as Result
 * Optional<Result<Problem>> maybe = ctx.selectFrom(PROBLEMS).where(ID.eq(id))
 *         .fetchOptional().map(PROBLEM_DECODER::decode);
 * }</pre>
 *
 * <p><strong>Many rows.</strong> {@code ResultQuery.fetch()} returns an
 * {@link org.jooq.Result} (which is a {@link java.util.List} of records), so
 * {@link net.unit8.raoh.Result#traverse(java.util.List, java.util.function.BiFunction)} decodes
 * every row, accumulating each row's errors under an indexed path ({@code /0/col}, {@code /1/col}):
 *
 * <pre>{@code
 * Result<List<Problem>> problems =
 *         Result.traverse(ctx.selectFrom(PROBLEMS).fetch(), PROBLEM_DECODER::decode);
 * }</pre>
 *
 * <p>{@code traverse} infers its element type from the arguments, so a
 * {@code Result<SomeGeneratedRecord>} (a list of a subtype of {@code Record}) decodes cleanly with a
 * {@code Decoder<Record, T>} — no copy or cast is needed. Choose how to surface a failure on the
 * returned {@link net.unit8.raoh.Result}: {@link net.unit8.raoh.Result#getOrThrow()} to throw, or
 * {@link net.unit8.raoh.Result#orElseThrow(java.util.function.Function)} to map the issues to a
 * domain exception.
 *
 * @see net.unit8.raoh.jooq.JooqRecordDecoders
 * @see net.unit8.raoh.Result#traverse(java.util.List, java.util.function.BiFunction)
 */
@NullMarked
package net.unit8.raoh.jooq;

import org.jspecify.annotations.NullMarked;
