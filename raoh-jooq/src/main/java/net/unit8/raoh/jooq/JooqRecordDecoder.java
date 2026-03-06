package net.unit8.raoh.jooq;

import net.unit8.raoh.Decoder;

/**
 * A convenience interface for decoders that operate on {@link org.jooq.Record} input.
 *
 * @param <T> the decoded output type
 */
public interface JooqRecordDecoder<T> extends Decoder<org.jooq.Record, T> {
}
