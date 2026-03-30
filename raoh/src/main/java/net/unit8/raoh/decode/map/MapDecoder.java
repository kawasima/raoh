package net.unit8.raoh.decode.map;

import net.unit8.raoh.*;
import net.unit8.raoh.decode.*;

import java.util.Map;

/**
 * A convenience interface for decoders that operate on {@code Map<String, Object>} input.
 *
 * @param <T> the decoded output type
 */
public interface MapDecoder<T> extends Decoder<Map<String, Object>, T> {
}
