package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Issue;
import net.unit8.raoh.MessageKeys;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.decode.Decoders;
import net.unit8.raoh.decode.ObjectDecoders;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.unit8.raoh.decode.ObjectDecoders.double_;
import static net.unit8.raoh.decode.ObjectDecoders.float_;
import static net.unit8.raoh.decode.ObjectDecoders.int_;
import static net.unit8.raoh.decode.ObjectDecoders.long_;
import static net.unit8.raoh.decode.ObjectDecoders.string;
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeErr;
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeOk;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every operation that gives an issue takes a message, which replaces the message of that
 * operation's own issues only (Raoh Specification 0.9.0, {@code <form>.message}).
 */
class GivenMessageTest {

    private enum Color { RED, GREEN }

    /** Asserts the issue has the code and key, and the given message as a custom one. */
    private static void assertGiven(Issue issue, String code, String messageKey, String message) {
        assertEquals(code, issue.code());
        assertEquals(messageKey, issue.messageKey());
        assertEquals(message, issue.message());
        assertTrue(issue.customMessage(), "the given message is custom: " + issue);
    }

    /** Asserts the issue kept its own, derived message. */
    private static void assertDerived(Issue issue, String code) {
        assertEquals(code, issue.code());
        assertFalse(issue.customMessage(), "the message is derived: " + issue);
    }

    @Test
    void floatSignConstraintsTakeAMessage() {
        // R000927–R000929, R000931
        assertGiven(decodeErr(float_().negative("m"), 0.5f), ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_NEGATIVE, "m");
        assertGiven(decodeErr(float_().nonNegative("m"), -0.0f), ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_NON_NEGATIVE, "m");
        assertGiven(decodeErr(float_().nonPositive("m"), 1.0f), ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_NON_POSITIVE, "m");
        assertGiven(decodeErr(float_().positive("m"), 0.0f), ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_POSITIVE, "m");
        assertEquals(Map.of("min", 0.0f, "actual", 0.0f), decodeErr(float_().positive("m"), 0.0f).meta());
        assertEquals("must be positive", decodeErr(float_().positive(), 0.0f).message());
    }

    @Test
    void doubleSignConstraintsTakeAMessage() {
        // R000935–R000937, R000939
        assertGiven(decodeErr(double_().negative("m"), 0.5), ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_NEGATIVE, "m");
        assertGiven(decodeErr(double_().nonNegative("m"), -0.0), ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_NON_NEGATIVE, "m");
        assertGiven(decodeErr(double_().nonPositive("m"), 1.0), ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_NON_POSITIVE, "m");
        assertGiven(decodeErr(double_().positive("m"), 0.0), ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_POSITIVE, "m");
        assertEquals(Map.of("max", 0.0, "actual", 0.5), decodeErr(double_().negative("m"), 0.5).meta());
        assertEquals("must be negative", decodeErr(double_().negative(), 0.5).message());
    }

    @Test
    void oneOfTakesACollectionAndAMessage() {
        // R000990, R000950, R000959, R000930, R000938
        var s = decodeErr(string().oneOf(List.of("b", "a"), "m"), "c");
        assertGiven(s, ErrorCodes.NOT_ALLOWED, ErrorCodes.NOT_ALLOWED, "m");
        assertEquals(List.of("a", "b"), s.meta().get("allowed"));

        var i = decodeErr(int_().oneOf(List.of(3, 1), "m"), 2);
        assertGiven(i, ErrorCodes.NOT_ALLOWED, ErrorCodes.NOT_ALLOWED, "m");
        assertEquals(List.of(1, 3), i.meta().get("allowed"));

        assertGiven(decodeErr(long_().oneOf(List.of(3L, 1L), "m"), 2L), ErrorCodes.NOT_ALLOWED, ErrorCodes.NOT_ALLOWED, "m");
        assertGiven(decodeErr(float_().oneOf(List.of(2.0f, 1.0f), "m"), 3.0f), ErrorCodes.NOT_ALLOWED, ErrorCodes.NOT_ALLOWED, "m");
        assertGiven(decodeErr(double_().oneOf(List.of(2.0, 1.0), "m"), 3.0), ErrorCodes.NOT_ALLOWED, ErrorCodes.NOT_ALLOWED, "m");

        assertEquals("must be one of [1, 3]", decodeErr(int_().oneOf(List.of(3, 1), null), 2).message());
        assertEquals("must be one of [1, 3]", decodeErr(int_().oneOf(3, 1), 2).message());
    }

    @Test
    void oneOfCopiesTheCollection() {
        var allowed = new ArrayList<>(List.of("a"));
        var dec = string().oneOf(allowed, null);
        allowed.add("b");

        decodeErr(dec, "b");
        assertEquals("a", decodeOk(dec, "a"));
    }

    @Test
    void oneOfRefusesAValueGivenTwice() {
        assertThrows(IllegalArgumentException.class, () -> string().oneOf("a", "a"));
        assertThrows(IllegalArgumentException.class, () -> string().oneOf(List.of("a", "a"), "m"));
        assertThrows(IllegalArgumentException.class, () -> int_().oneOf(1, 1));
        // -0.0 and 0.0 are different values, so they are not given twice.
        assertEquals(-0.0, decodeOk(double_().oneOf(-0.0, 0.0), -0.0));
    }

    @Test
    void nonBlankTakesAMessageForItsOwnIssueOnly() {
        assertGiven(decodeErr(string().nonBlank("m"), "  "), ErrorCodes.BLANK, ErrorCodes.BLANK, "m");
        // R000988: a missing value is the string decoder's required, which keeps its message.
        assertDerived(decodeErr(string().nonBlank("m"), null), ErrorCodes.REQUIRED);
    }

    @Test
    void enumOfTakesAMessageForItsOwnIssueOnly() {
        // R000907
        assertGiven(decodeErr(ObjectDecoders.enumOf(Color.class, "m"), "blue"),
                ErrorCodes.INVALID_FORMAT, MessageKeys.INVALID_FORMAT_ENUM, "m");
        // R001007
        assertDerived(decodeErr(Decoders.enumOf(Color.class, string(), "m"), 1), ErrorCodes.TYPE_MISMATCH);
        assertEquals(Color.GREEN, decodeOk(ObjectDecoders.enumOf(Color.class, "m"), "green"));
    }

    @Test
    void literalTakesAMessageForItsOwnIssueOnly() {
        // R000908
        assertGiven(decodeErr(ObjectDecoders.literal("v1", "m"), "v2"),
                ErrorCodes.INVALID_FORMAT, MessageKeys.INVALID_FORMAT_LITERAL, "m");
        // R001006
        Decoder<@Nullable Object, String> literal = Decoders.literal("v1", string(), "m");
        assertDerived(decodeErr(literal, 1), ErrorCodes.TYPE_MISMATCH);
        assertEquals("v1", decodeOk(literal, "v1"));
    }
}
