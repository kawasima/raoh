package net.unit8.raoh.json;

import net.unit8.raoh.Err;
import net.unit8.raoh.Issue;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static net.unit8.raoh.json.JsonDecoders.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link JsonDecoders#readTree(String)} and its overloads: numbers as written, negative zero,
 * duplicate member names, documents and streams, and read constraints (#170).
 */
class ReadTreeTest {

    private static final JsonFactory FACTORY = JsonFactory.builder().build();

    private static <T> T ok(Decoder<JsonNode, T> decoder, String json) {
        Result<T> result = decoder.decode(readTree(json));
        if (result instanceof Ok<T>(T value)) {
            return value;
        }
        return fail("expected Ok for " + json + " but got " + result);
    }

    private static Issue only(Decoder<JsonNode, ?> decoder, String json) {
        Result<?> result = decoder.decode(readTree(json));
        if (result instanceof Err<?>(var issues)) {
            assertEquals(1, issues.asList().size(), issues.toString());
            return issues.asList().getFirst();
        }
        return fail("expected Err for " + json + " but got " + result);
    }

    private static boolean isNegativeZero(double d) {
        return Double.doubleToRawLongBits(d) == Double.doubleToRawLongBits(-0.0d);
    }

    private static boolean isNegativeZero(float f) {
        return Float.floatToRawIntBits(f) == Float.floatToRawIntBits(-0.0f);
    }

    // --- Numbers as written (spec cases) ---

    @Test
    void decimalKeepsTheScaleAsWritten() {
        // R000010
        assertEquals("0.0001", ok(decimal(), "0.0001").toPlainString());
        assertEquals(new BigDecimal("1.50"), ok(decimal(), "1.50"));
        assertEquals(new BigDecimal("1E+3"), ok(decimal(), "1e3"));
        assertEquals(new BigDecimal("0.12345678901234567890123"), ok(decimal(), "0.12345678901234567890123"));
    }

    @Test
    void decimalMinComparesTheNumberAsWritten() {
        // R000011
        Issue issue = only(decimal().min(new BigDecimal("0.0005")), "0.0001");
        assertEquals("out_of_range", issue.code());
        assertEquals("out_of_range.minimum", issue.messageKey());
        assertEquals(new BigDecimal("0.0001"), issue.meta().get("actual"));
    }

    @Test
    void floatRoundsOnceFromTheDecimal() {
        // Each is just above the midpoint 1 + 2^-24 between 1.0f and the next float, and so rounds
        // up; through a double first it lands on the midpoint and then rounds to even, 1.0f.
        // R000845
        assertEquals(1.0000001f, ok(float_(), "1.0000000596046448"));
        assertEquals(1.0000001f, ok(float_(), "1.000000059604644775390625000000001"));
        assertEquals(1.0000001f, ok(float_(), "1.00000005960464477539062501"));
        // The midpoint itself rounds to even.
        assertEquals(1.0f, ok(float_(), "1.000000059604644775390625"));
    }

    // --- Negative zero (spec #2) ---

    @ParameterizedTest
    @ValueSource(strings = {"-0", "-0.0", "-0e0", "-0e10", "-0.000e10", "-0E-5", "-1e-400"})
    void doubleReadsEveryNegativeZeroAsNegativeZero(String json) {
        // R000851, R000853, R000855, R000066
        assertTrue(isNegativeZero(ok(double_(), json)), json);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0", "-0.0", "-0e0", "-0.000e10", "-1e-50"})
    void floatReadsEveryNegativeZeroAsNegativeZero(String json) {
        // R000850, R000852, R000854, R000112
        assertTrue(isNegativeZero(ok(float_(), json)), json);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.0", "0e10", "1e-400"})
    void aZeroWithoutAMinusSignIsPositive(String json) {
        double d = ok(double_(), json);
        assertEquals(0L, Double.doubleToRawLongBits(d), json);
    }

    @Test
    void constraintsSeeTheNegativeZero() {
        // R000072, R000074
        assertTrue(isNegativeZero(ok(double_().negative(), "-0.0")));
        assertTrue(isNegativeZero(ok(double_().nonPositive(), "-0.0")));
        // R000073
        Issue nonNegative = only(double_().nonNegative(), "-0.0");
        assertEquals("out_of_range.non_negative", nonNegative.messageKey());
        assertEquals(-0.0d, nonNegative.meta().get("actual"));
        // R000075
        Issue notAllowed = only(double_().oneOf(0.0), "-0.0");
        assertEquals("not_allowed", notAllowed.code());
        assertEquals(-0.0d, notAllowed.meta().get("actual"));
    }

    @Test
    void integerNegativeZeroIsStillTheIntegerZero() {
        // R000165, R000201
        assertEquals(0, ok(int_(), "-0"));
        assertEquals(0L, ok(long_(), "-0"));
        assertEquals(BigDecimal.ZERO, ok(decimal(), "-0"));
    }

    @Test
    void fractionalNegativeZeroIsNotAnInteger() {
        // R000202
        assertEquals("type_mismatch", only(long_(), "-0.0").code());
        assertEquals("type_mismatch", only(int_(), "-0.0").code());
    }

    @Test
    void decimalReadsANegativeZeroWithItsScale() {
        BigDecimal zero = ok(decimal(), "-0.000e10");
        assertEquals(0, zero.signum());
        assertEquals(-7, zero.scale());
    }

    // --- Range boundaries ---

    @Test
    void doubleRangeBoundaries() {
        assertEquals(Double.MAX_VALUE, ok(double_(), "1.7976931348623157e308"));
        assertEquals("type_mismatch", only(double_(), "1e400").code());
        assertEquals("type_mismatch", only(double_(), "-1e400").code());
        assertEquals(Double.MIN_VALUE, ok(double_(), "4.9e-324"));
    }

    @Test
    void floatRangeBoundaries() {
        assertEquals(Float.MAX_VALUE, ok(float_(), "3.4028235677973366e38"));
        assertEquals("type_mismatch", only(float_(), "3.4028236e38").code());
        assertEquals("type_mismatch", only(float_(), "1e40").code());
        assertEquals(Float.MIN_VALUE, ok(float_(), "1.4e-45"));
        assertTrue(isNegativeZero(ok(float_(), "-1e-46")));
    }

    @Test
    void integersBecomeTheNodeOfTheSizeTheyNeed() {
        assertTrue(readTree("2147483647").isInt());
        assertTrue(readTree("2147483648").isLong());
        assertTrue(readTree("-9223372036854775808").isLong());
        assertTrue(readTree("9223372036854775808").isBigInteger());
        assertEquals(new BigInteger("123456789012345678901234567890"),
                readTree("123456789012345678901234567890").bigIntegerValue());
        assertEquals(Long.MAX_VALUE, ok(long_(), "9223372036854775807"));
        assertEquals("type_mismatch", only(long_(), "9223372036854775808").code());
    }

    @Test
    void anExponentBeyondWhatABigDecimalHoldsIsRefused() {
        var e = assertThrows(StreamReadException.class, () -> readTree("1e3000000000"));
        assertTrue(e.getMessage().contains("1e3000000000"), e.getMessage());
    }

    // --- A parser the caller owns ---

    @Test
    void numbersComeFromTheTextWhateverTheParserWasAskedBefore() {
        try (JsonParser parser = FACTORY.createParser(ObjectReadContext.empty(), "0.12345678901234567890")) {
            assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
            parser.getDoubleValue();
            assertEquals(new BigDecimal("0.12345678901234567890"), decimal().decode(readTree(parser)).getOrThrow());
        }
        try (JsonParser parser = FACTORY.createParser(ObjectReadContext.empty(), "-0")) {
            parser.nextToken();
            parser.getIntValue();
            assertTrue(isNegativeZero(double_().decode(readTree(parser)).getOrThrow()));
        }
    }

    @Test
    void readsOneValueAtATimeAndLeavesTheParserOnItsLastToken() {
        List<JsonNode> values = new ArrayList<>();
        try (JsonParser parser = FACTORY.createParser(ObjectReadContext.empty(), "{\"a\":[1,2]} 3 [] \"x\"")) {
            while (parser.nextToken() != null) {
                values.add(readTree(parser));
            }
        }
        assertEquals(List.of(readTree("{\"a\":[1,2]}"), readTree("3"), readTree("[]"), readTree("\"x\"")), values);
    }

    @Test
    void readsFromTheNextTokenWhenTheParserHasNone() {
        try (JsonParser parser = FACTORY.createParser(ObjectReadContext.empty(), "[true]")) {
            assertEquals(readTree("[true]"), readTree(parser));
            assertEquals(JsonToken.END_ARRAY, parser.currentToken());
        }
    }

    @Test
    void aParserNotAtTheStartOfAValueIsRefused() {
        try (JsonParser parser = FACTORY.createParser(ObjectReadContext.empty(), "{\"a\":1}")) {
            parser.nextToken();
            parser.nextToken();
            assertEquals(JsonToken.PROPERTY_NAME, parser.currentToken());
            assertThrows(IllegalArgumentException.class, () -> readTree(parser));
        }
    }

    @Test
    void theCallersParserIsNotClosed() {
        JsonParser parser = FACTORY.createParser(ObjectReadContext.empty(), "1");
        readTree(parser);
        assertFalse(parser.isClosed());
        parser.close();
    }

    // --- Duplicate member names ---

    private static final String DUPLICATE = "{\"a\":{\"x\":1,\"y\":[1,{\"x\":2,\"x\":3}]}}";

    @Test
    void aDuplicateMemberNameIsRefusedByEveryOverload() {
        List<Function<String, JsonNode>> overloads = List.of(
                JsonDecoders::readTree,
                json -> readTree(new StringReader(json)),
                json -> readTree(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))),
                json -> {
                    // The caller's parser leaves duplicate detection off, as Jackson does by default.
                    try (JsonParser parser = FACTORY.createParser(ObjectReadContext.empty(), json)) {
                        return readTree(parser);
                    }
                });
        for (Function<String, JsonNode> read : overloads) {
            var e = assertThrows(StreamReadException.class, () -> read.apply(DUPLICATE));
            assertTrue(e.getMessage().startsWith("Duplicate member name \"x\""), e.getMessage());
        }
    }

    @Test
    void theSameNameInDifferentObjectsIsNotADuplicate() {
        JsonNode node = readTree("{\"x\":{\"x\":1},\"y\":[{\"x\":2},{\"x\":3}]}");
        assertEquals(1, node.get("x").get("x").intValue());
        assertEquals(3, node.get("y").get(1).get("x").intValue());
    }

    // --- Documents ---

    @ParameterizedTest
    @ValueSource(strings = {"1 2", "{} []", "[1]]", "\"a\" x"})
    void aDocumentHoldsExactlyOneValue(String json) {
        assertThrows(JacksonException.class, () -> readTree(json));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "[1,", "{\"a\":"})
    void aDocumentEndingBeforeItsValueIsRefused(String json) {
        assertThrows(StreamReadException.class, () -> readTree(json));
    }

    @Test
    void theOverloadsReadTheSameTree() {
        String json = "{\"n\":-0.000e10,\"m\":[1,2.50,\"s\",null,true,false],\"o\":{}}";
        JsonNode expected = readTree(json);
        assertEquals(expected, readTree(new StringReader(json)));
        assertEquals(expected, readTree(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
        assertEquals(expected, readTree(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_16))));
    }

    @Test
    void theCallersReaderAndStreamAreLeftOpen() {
        var reader = new StringReader("1") {
            boolean closed;

            @Override
            public void close() {
                closed = true;
                super.close();
            }
        };
        readTree(reader);
        assertFalse(reader.closed);
        var in = new ByteArrayInputStream("1".getBytes(StandardCharsets.UTF_8)) {
            boolean closed;

            @Override
            public void close() {
                closed = true;
            }
        };
        readTree(in);
        assertFalse(in.closed);
    }

    // --- Read constraints and depth ---

    @Test
    void theOwnedOverloadsKeepJacksonsNumberLengthAndTheCallersParserCanLiftIt() {
        String digits = "1." + "1".repeat(StreamReadConstraints.DEFAULT_MAX_NUM_LEN + 1);
        assertThrows(JacksonException.class, () -> readTree(digits));
        JsonFactory lenient = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNumberLength(1_000_000).build())
                .build();
        try (JsonParser parser = lenient.createParser(ObjectReadContext.empty(), digits)) {
            assertEquals(new BigDecimal(digits), decimal().decode(readTree(parser)).getOrThrow());
        }
    }

    @Test
    void depthDoesNotReachTheCallStack() {
        int depth = 200_000;
        String json = "[".repeat(depth) + "]".repeat(depth);
        JsonFactory deep = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(depth).build())
                .build();
        JsonNode node;
        try (JsonParser parser = deep.createParser(ObjectReadContext.empty(), json)) {
            node = readTree(parser);
        }
        int seen = 1;
        for (JsonNode at = node; at.size() > 0; at = at.get(0)) {
            seen++;
        }
        assertEquals(depth, seen);
    }

    // --- A mapper's tree, for contrast ---

    @Test
    void aMappersTreeHasAlreadyLostWhatReadTreeKeeps() {
        ObjectMapper mapper = new ObjectMapper();
        assertEquals(0L, Double.doubleToRawLongBits(double_().decode(mapper.readTree("-0")).getOrThrow()));
        assertEquals(1.0f, float_().decode(mapper.readTree("1.000000059604644775390625000000001")).getOrThrow());
        assertTrue(isNegativeZero(double_().decode(readTree("-0")).getOrThrow()));
        assertEquals(1.0000001f, float_().decode(readTree("1.000000059604644775390625000000001")).getOrThrow());
    }
}
