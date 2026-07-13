package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.ErrorCodes;
import org.junit.jupiter.api.Test;

import static net.unit8.raoh.decode.ObjectDecoders.bool;
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeErr;
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeOk;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct unit tests for {@link BoolDecoder}, covering the {@code isTrue()} and
 * {@code isFalse()} value constraints and their {@link ErrorCodes#INVALID_VALUE} failures.
 */
class BoolDecoderTest {

    // --- isTrue ---

    @Test
    void isTrueAcceptsTrue() {
        assertTrue(decodeOk(bool().isTrue(), true));
    }

    @Test
    void isTrueRejectsFalse() {
        var issue = decodeErr(bool().isTrue(), false);
        assertEquals(ErrorCodes.INVALID_VALUE, issue.code());
        assertEquals("must be true", issue.message());
        assertEquals(true, issue.meta().get("expected"));
        assertEquals(false, issue.meta().get("actual"));
    }

    @Test
    void isTrueUsesCustomMessageWhenProvided() {
        var issue = decodeErr(bool().isTrue("you must accept the terms"), false);
        assertEquals(ErrorCodes.INVALID_VALUE, issue.code());
        assertEquals("you must accept the terms", issue.message());
        assertTrue(issue.customMessage());
    }

    // --- isFalse ---

    @Test
    void isFalseAcceptsFalse() {
        assertFalse(decodeOk(bool().isFalse(), false));
    }

    @Test
    void isFalseRejectsTrue() {
        var issue = decodeErr(bool().isFalse(), true);
        assertEquals(ErrorCodes.INVALID_VALUE, issue.code());
        assertEquals("must be false", issue.message());
        assertEquals(false, issue.meta().get("expected"));
        assertEquals(true, issue.meta().get("actual"));
    }

    // --- base decoder behaviour ---

    @Test
    void rejectsNullAsRequired() {
        assertEquals(ErrorCodes.REQUIRED, decodeErr(bool(), null).code());
    }

    @Test
    void rejectsNonBooleanAsTypeMismatch() {
        assertEquals(ErrorCodes.TYPE_MISMATCH, decodeErr(bool(), "true").code());
    }
}
