package net.unit8.raoh.json;

import net.unit8.raoh.decode.Decoder;

import org.jspecify.annotations.Nullable;

import tools.jackson.databind.JsonNode;

/**
 * A convenience interface for decoders that operate on Jackson {@link JsonNode} input.
 *
 * @param <T> the decoded output type
 */
public interface JsonDecoder<T extends @Nullable Object> extends Decoder<JsonNode, T> {
}
