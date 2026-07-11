package net.unit8.raoh.jooq;

import net.unit8.raoh.decode.Decoder;

import org.jspecify.annotations.Nullable;

/**
 * A convenience interface for decoders that operate on {@link org.jooq.Record} input.
 *
 * @param <T> the decoded output type
 */
public interface JooqRecordDecoder<T extends @Nullable Object> extends Decoder<org.jooq.Record, T> {
}
