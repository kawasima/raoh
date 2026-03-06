package net.unit8.raoh.json;

import net.unit8.raoh.*;
import net.unit8.raoh.combinator.*;
import net.unit8.raoh.builtin.*;

import com.fasterxml.jackson.databind.JsonNode;

public interface JsonDecoder<T> extends Decoder<JsonNode, T> {
}
