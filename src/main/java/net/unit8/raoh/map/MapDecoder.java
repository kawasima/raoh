package net.unit8.raoh.map;

import net.unit8.raoh.*;
import net.unit8.raoh.combinator.*;
import net.unit8.raoh.builtin.*;

import java.util.Map;

/**
 * A convenience interface for decoders that operate on {@code Map<String, Object>} input.
 *
 * @param <T> the decoded output type
 */
public interface MapDecoder<T> extends Decoder<Map<String, Object>, T> {
}
