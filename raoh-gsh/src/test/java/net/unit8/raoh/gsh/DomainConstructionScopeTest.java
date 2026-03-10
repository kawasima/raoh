package net.unit8.raoh.gsh;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DomainConstructionScopeTest {

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
        DomainConstructionScope.run(() -> decode("com.example.Email"));
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
        String result = DomainConstructionScope.call(() -> {
            decode("com.example.Email");
            return "ok";
        });
        assertEquals("ok", result);
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
}
