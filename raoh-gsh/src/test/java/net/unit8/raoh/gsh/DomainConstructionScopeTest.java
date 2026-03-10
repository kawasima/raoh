package net.unit8.raoh.gsh;

import net.unit8.raoh.gsh.DomainConstructionScope.DecoderMethodSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DomainConstructionScopeTest {

    /** Custom spec that matches the test helper's decode method in this class. */
    private static final List<DecoderMethodSpec> TEST_SPECS = List.of(
            new DecoderMethodSpec("decode", "DomainConstructionScopeTest"));

    @Test
    void checkActiveDoesNothingOutsideScope() {
        // Outside a scope, guard is inactive — no exception
        assertDoesNotThrow(() -> DomainConstructionScope.checkActive("com.example.Email"));
    }

    @Test
    void checkActiveThrowsInsideScopeWithoutDecode() {
        // Inside a scope, direct construction (no "decode" on stack) throws
        DomainConstructionScope.run(() ->
                assertThrows(DomainConstructionGuardException.class,
                        () -> DomainConstructionScope.checkActive("com.example.Email")));
    }

    @Test
    void checkActivePassesInsideScopeWithDecode() {
        // Simulate Decoder invocation by calling from a method named "decode"
        DomainConstructionScope.run(TEST_SPECS, () -> decode("com.example.Email"));
    }

    @Test
    void callThrowsInsideScopeWithoutDecode() throws Exception {
        var ex = assertThrows(DomainConstructionGuardException.class, () ->
                DomainConstructionScope.call(() -> {
                    DomainConstructionScope.checkActive("com.example.Email");
                    return "unreachable";
                }));
        assertTrue(ex.getMessage().contains("com.example.Email"));
    }

    @Test
    void callPassesInsideScopeWithDecode() throws Exception {
        String result = DomainConstructionScope.call(TEST_SPECS, () -> {
            decode("com.example.Email");
            return "ok";
        });
        assertEquals("ok", result);
    }

    @Test
    void defaultSpecRejectsUnrelatedDecodeMethod() {
        // Default spec requires class name to contain "Decoder"
        // Our test helper class doesn't match, so it should throw even
        // when called from a method named "decode"
        DomainConstructionScope.run(() ->
                assertThrows(DomainConstructionGuardException.class,
                        () -> decode("com.example.Email", true)));
    }

    @Test
    void isActiveReturnsFalseOutsideScope() {
        assertFalse(DomainConstructionScope.isActive());
    }

    @Test
    void isActiveReturnsTrueInsideScope() {
        DomainConstructionScope.run(() -> assertTrue(DomainConstructionScope.isActive()));
    }

    @Test
    void nestedScopesWork() {
        DomainConstructionScope.run(() -> {
            assertTrue(DomainConstructionScope.isActive());
            DomainConstructionScope.run(() -> {
                assertTrue(DomainConstructionScope.isActive());
                // Still throws without decode on stack
                assertThrows(DomainConstructionGuardException.class,
                        () -> DomainConstructionScope.checkActive("Nested"));
            });
            assertTrue(DomainConstructionScope.isActive());
        });
    }

    @Test
    void scopeIsInactiveAfterRunCompletes() {
        DomainConstructionScope.run(() -> {});
        assertFalse(DomainConstructionScope.isActive());
    }

    @Test
    void exceptionMessageContainsClassName() {
        DomainConstructionScope.run(() -> {
            var ex = assertThrows(DomainConstructionGuardException.class,
                    () -> DomainConstructionScope.checkActive("com.example.Email"));
            assertTrue(ex.getMessage().contains("com.example.Email"));
            assertEquals("com.example.Email", ex.getClassName());
        });
    }

    /**
     * Simulates a Decoder's decode method. The method name "decode" is what
     * DomainConstructionScope looks for on the call stack.
     */
    private static void decode(String className) {
        assertDoesNotThrow(() -> DomainConstructionScope.checkActive(className));
    }

    /**
     * Calls checkActive from a method named "decode" without assertion wrapping,
     * allowing the caller to catch the thrown exception with assertThrows.
     */
    @SuppressWarnings("overloads")
    private static void decode(String className, boolean propagateException) {
        DomainConstructionScope.checkActive(className);
    }
}
