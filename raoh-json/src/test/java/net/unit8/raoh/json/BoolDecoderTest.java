package net.unit8.raoh.json;

import net.unit8.raoh.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static net.unit8.raoh.json.JsonDecoders.*;

class BoolDecoderTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    // --- isTrue ---

    @Test
    void isTruePassesOnTrue() {
        var dec = field("accepted", bool().isTrue());
        assertEquals(true, assertOk(dec.decode(parse("{\"accepted\":true}"))));
    }

    @Test
    void isTrueFailsOnFalse() {
        var dec = field("accepted", bool().isTrue());
        var result = dec.decode(parse("{\"accepted\":false}"));
        assertErr(result, ErrorCodes.INVALID_VALUE);
    }

    @Test
    void isTrueCustomMessage() {
        var dec = field("accepted", bool().isTrue("you must accept the terms"));
        var result = dec.decode(parse("{\"accepted\":false}"));
        switch (result) {
            case Ok(_) -> fail("Expected Err");
            case Err(var issues) -> assertEquals("you must accept the terms", issues.asList().getFirst().message());
        }
    }

    // --- isFalse ---

    @Test
    void isFalsePassesOnFalse() {
        var dec = field("disabled", bool().isFalse());
        assertEquals(false, assertOk(dec.decode(parse("{\"disabled\":false}"))));
    }

    @Test
    void isFalseFailsOnTrue() {
        var dec = field("disabled", bool().isFalse());
        var result = dec.decode(parse("{\"disabled\":true}"));
        assertErr(result, ErrorCodes.INVALID_VALUE);
    }

    @Test
    void isFalseCustomMessage() {
        var dec = field("disabled", bool().isFalse("must not be disabled"));
        var result = dec.decode(parse("{\"disabled\":true}"));
        switch (result) {
            case Ok(_) -> fail("Expected Err");
            case Err(var issues) -> assertEquals("must not be disabled", issues.asList().getFirst().message());
        }
    }

    // --- meta verification ---

    @Test
    void isTrueMetaContainsExpectedAndActual() {
        var dec = field("accepted", bool().isTrue());
        var result = dec.decode(parse("{\"accepted\":false}"));
        switch (result) {
            case Ok(_) -> fail("Expected Err");
            case Err(var issues) -> {
                var issue = issues.asList().getFirst();
                assertEquals(true, issue.meta().get("expected"));
                assertEquals(false, issue.meta().get("actual"));
            }
        }
    }

    @Test
    void isFalseMetaContainsExpectedAndActual() {
        var dec = field("disabled", bool().isFalse());
        var result = dec.decode(parse("{\"disabled\":true}"));
        switch (result) {
            case Ok(_) -> fail("Expected Err");
            case Err(var issues) -> {
                var issue = issues.asList().getFirst();
                assertEquals(false, issue.meta().get("expected"));
                assertEquals(true, issue.meta().get("actual"));
            }
        }
    }

    // --- type mismatch still works ---

    @Test
    void nonBooleanInputFails() {
        var dec = field("accepted", bool().isTrue());
        assertErr(dec.decode(parse("{\"accepted\":\"yes\"}")), ErrorCodes.TYPE_MISMATCH);
    }

    // --- Helpers ---

    static <T> T assertOk(Result<T> result) {
        return switch (result) {
            case Ok(var value) -> value;
            case Err(var issues) -> { fail("Expected Ok, got: " + issues); yield null; }
        };
    }

    static void assertErr(Result<?> result, String expectedCode) {
        switch (result) {
            case Ok(_) -> fail("Expected Err, got Ok");
            case Err(var issues) -> assertEquals(expectedCode, issues.asList().getFirst().code());
        }
    }

    private static JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
