package net.unit8.raoh.json;

import net.unit8.raoh.internal.DecimalConversion;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.BigIntegerNode;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.ContainerNode;
import tools.jackson.databind.node.DecimalNode;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.LongNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;

/**
 * Builds the {@link JsonNode} tree behind {@link JsonDecoders#readTree(String)} and its overloads.
 *
 * <p>A number is read from the text the parser holds for it ({@link JsonParser#getString()}), never
 * from a value the parser has converted, so the tree does not depend on which of the parser's
 * accessors anyone called before. An integer becomes Jackson's integer node of the size it needs,
 * the integer {@code -0} a {@link NegativeZeroIntNode}. A number with a fraction or an exponent
 * becomes a {@link DecimalNode} holding the exact {@link BigDecimal} it writes, scale included, and
 * a zero among them written with a minus sign a {@link NegativeZeroDecimalNode}.
 *
 * <p>What a {@link JsonNode} cannot hold is refused rather than dropped: a member name an object
 * already has, and a number whose exponent puts it outside what a {@link BigDecimal} holds. The
 * containers are kept on a stack of their own, so the depth of the input does not reach the call
 * stack.
 */
final class JsonTreeReader {

    /**
     * The factory for the parsers Raoh opens. Its read constraints are Jackson's built-in defaults,
     * set here so that {@link StreamReadConstraints#overrideDefaultStreamReadConstraints} cannot
     * change them; closing a parser leaves the caller's {@link Reader} or {@link InputStream} open.
     */
    private static final JsonFactory FACTORY = JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().build())
            .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
            .build();

    /** Integers of at most this many digits fit a {@code long}. */
    private static final int LONG_DIGITS = 18;

    /** How much of a refused number or name an exception message quotes. */
    private static final int QUOTED = 40;

    private JsonTreeReader() {
    }

    static JsonNode read(String content) {
        try (JsonParser parser = FACTORY.createParser(ObjectReadContext.empty(), content)) {
            return readDocument(parser);
        }
    }

    static JsonNode read(Reader reader) {
        try (JsonParser parser = FACTORY.createParser(ObjectReadContext.empty(), reader)) {
            return readDocument(parser);
        }
    }

    static JsonNode read(InputStream in) {
        try (JsonParser parser = FACTORY.createParser(ObjectReadContext.empty(), in)) {
            return readDocument(parser);
        }
    }

    /**
     * Reads one JSON document: a single value and nothing after it.
     *
     * @param parser a parser Raoh opened, before its first token
     * @return the value
     */
    private static JsonNode readDocument(JsonParser parser) {
        JsonNode value = readValue(parser);
        JsonToken after = parser.nextToken();
        if (after != null) {
            throw new StreamReadException(parser, "Unexpected " + after.name() + " after the JSON value");
        }
        return value;
    }

    /**
     * Reads the value at the parser's current token, or at its next token when it has none yet,
     * and leaves the parser on the last token of that value.
     *
     * @param parser the parser
     * @return the value
     */
    static JsonNode readValue(JsonParser parser) {
        JsonToken token = parser.currentToken();
        if (token == null) {
            token = parser.nextToken();
            if (token == null) {
                throw new StreamReadException(parser, "No JSON value: the input is empty");
            }
        }
        if (token != JsonToken.START_OBJECT && token != JsonToken.START_ARRAY && !token.isScalarValue()) {
            throw new IllegalArgumentException(
                    "The parser is not at the start of a JSON value but at " + token.name());
        }
        ArrayDeque<Open> open = new ArrayDeque<>();
        while (true) {
            JsonNode value;
            switch (token) {
                case START_OBJECT -> {
                    open.push(new Open(new ObjectNode(JsonNodeFactory.instance)));
                    token = next(parser);
                    continue;
                }
                case START_ARRAY -> {
                    open.push(new Open(new ArrayNode(JsonNodeFactory.instance)));
                    token = next(parser);
                    continue;
                }
                case PROPERTY_NAME -> {
                    Open object = open.element();
                    String name = parser.currentName();
                    if (((ObjectNode) object.node).has(name)) {
                        throw new StreamReadException(parser, "Duplicate member name \"" + quoted(name) + "\"");
                    }
                    object.name = name;
                    token = next(parser);
                    continue;
                }
                case END_OBJECT, END_ARRAY -> value = open.pop().node;
                case VALUE_STRING -> value = StringNode.valueOf(parser.getString());
                case VALUE_NUMBER_INT -> value = integer(parser);
                case VALUE_NUMBER_FLOAT -> value = decimal(parser);
                case VALUE_TRUE -> value = BooleanNode.TRUE;
                case VALUE_FALSE -> value = BooleanNode.FALSE;
                case VALUE_NULL -> value = NullNode.getInstance();
                default -> throw new StreamReadException(parser, "Unexpected " + token.name() + " in a JSON value");
            }
            Open parent = open.peek();
            if (parent == null) {
                return value;
            }
            if (parent.node instanceof ObjectNode object) {
                object.set(parent.name(), value);
            } else {
                ((ArrayNode) parent.node).add(value);
            }
            token = next(parser);
        }
    }

    private static JsonToken next(JsonParser parser) {
        JsonToken token = parser.nextToken();
        if (token == null) {
            throw new StreamReadException(parser, "The input ends inside a JSON value");
        }
        return token;
    }

    private static JsonNode integer(JsonParser parser) {
        // Most integers are short: read them from the parser's own buffer, building no String.
        int length = parser.getStringLength();
        if (length <= LONG_DIGITS + 1) {
            char[] chars = parser.getStringCharacters();
            int at = parser.getStringOffset();
            int end = at + length;
            boolean negative = at < end && chars[at] == '-';
            if (at < end && (negative || chars[at] == '+')) {
                at++;
            }
            if (at < end && end - at <= LONG_DIGITS) {
                long value = 0;
                for (; at < end; at++) {
                    char c = chars[at];
                    if (c < '0' || c > '9') {
                        throw notADecimal(parser, parser.getString());
                    }
                    value = value * 10 + (c - '0');
                }
                if (value == 0 && negative) {
                    return NegativeZeroIntNode.INSTANCE;
                }
                long signed = negative ? -value : value;
                return (int) signed == signed ? IntNode.valueOf((int) signed) : LongNode.valueOf(signed);
            }
        }
        // Longer integers go through the decimal conversion, which does not take time quadratic in
        // the digits as new BigInteger(String) does (#161).
        String text = parser.getString();
        boolean negative = text.startsWith("-");
        BigDecimal written = DecimalConversion.toBigDecimal(text);
        if (written == null || written.scale() != 0) {
            throw notADecimal(parser, text);
        }
        BigInteger value = written.unscaledValue();
        if (value.signum() == 0 && negative) {
            return NegativeZeroIntNode.INSTANCE;
        }
        int bits = value.bitLength();
        if (bits < Integer.SIZE) {
            return IntNode.valueOf(value.intValue());
        }
        if (bits < Long.SIZE) {
            return LongNode.valueOf(value.longValue());
        }
        return BigIntegerNode.valueOf(value);
    }

    private static JsonNode decimal(JsonParser parser) {
        String text = parser.getString();
        BigDecimal value = DecimalConversion.toBigDecimal(text);
        if (value == null) {
            throw notADecimal(parser, text);
        }
        if (value.signum() == 0 && text.startsWith("-")) {
            return new NegativeZeroDecimalNode(value);
        }
        return new DecimalNode(value);
    }

    private static StreamReadException notADecimal(JsonParser parser, String text) {
        return new StreamReadException(parser, "The number " + quoted(text)
                + " is not decimal text, or its exponent is beyond what a BigDecimal holds");
    }

    private static String quoted(String text) {
        return text.length() <= QUOTED ? text : text.substring(0, QUOTED) + "...";
    }

    /** A container being filled, and the name of the member whose value comes next. */
    private static final class Open {
        final ContainerNode<?> node;
        @Nullable String name;

        Open(ContainerNode<?> node) {
            this.node = node;
        }

        String name() {
            String n = name;
            if (n == null) {
                throw new IllegalStateException("A member value without a name");
            }
            return n;
        }
    }
}
