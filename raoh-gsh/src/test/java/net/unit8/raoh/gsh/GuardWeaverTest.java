package net.unit8.raoh.gsh;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

class GuardWeaverTest {

    @Test
    void wovenRecordDoesNothingOutsideScope() throws Exception {
        // Outside a scope, guard is inactive — no exception even for direct new
        Class<?> wovenClass = loadWovenClass("net.unit8.raoh.gsh.testdomain.TestEmail");
        Constructor<?> ctor = wovenClass.getDeclaredConstructor(String.class);

        Object email = ctor.newInstance("test@example.com");
        assertNotNull(email);
    }

    @Test
    void wovenRecordThrowsInsideScopeWithoutDecode() throws Exception {
        // Inside a scope, direct new (no "decode" on the stack) throws
        Class<?> wovenClass = loadWovenClass("net.unit8.raoh.gsh.testdomain.TestEmail");
        Constructor<?> ctor = wovenClass.getDeclaredConstructor(String.class);

        DomainConstructionScope.run(() -> {
            var ex = assertThrows(InvocationTargetException.class,
                    () -> ctor.newInstance("test@example.com"));
            assertInstanceOf(DomainConstructionGuardException.class, ex.getCause());
            assertTrue(ex.getCause().getMessage().contains("TestEmail"));
        });
    }

    @Test
    void wovenRecordSucceedsInsideScopeViaDecode() throws Exception {
        // Inside a scope, construction through a method named "decode" is allowed
        Class<?> wovenClass = loadWovenClass("net.unit8.raoh.gsh.testdomain.TestEmail");
        Constructor<?> ctor = wovenClass.getDeclaredConstructor(String.class);

        DomainConstructionScope.run(() -> decode(ctor, "test@example.com"));
    }

    @Test
    void wovenClassThrowsInsideScopeWithoutDecode() throws Exception {
        Class<?> wovenClass = loadWovenClass("net.unit8.raoh.gsh.testdomain.TestUser");

        // Primary constructor
        Constructor<?> primaryCtor = wovenClass.getDeclaredConstructor(String.class, int.class);
        DomainConstructionScope.run(() -> {
            var ex1 = assertThrows(InvocationTargetException.class,
                    () -> primaryCtor.newInstance("Alice", 30));
            assertInstanceOf(DomainConstructionGuardException.class, ex1.getCause());
        });

        // Delegating constructor (delegates to primary which has the guard)
        Constructor<?> delegatingCtor = wovenClass.getDeclaredConstructor(String.class);
        DomainConstructionScope.run(() -> {
            var ex2 = assertThrows(InvocationTargetException.class,
                    () -> delegatingCtor.newInstance("Bob"));
            assertInstanceOf(DomainConstructionGuardException.class, ex2.getCause());
        });
    }

    @Test
    void wovenClassSucceedsInsideScopeViaDecode() throws Exception {
        Class<?> wovenClass = loadWovenClass("net.unit8.raoh.gsh.testdomain.TestUser");
        Constructor<?> primaryCtor = wovenClass.getDeclaredConstructor(String.class, int.class);
        Constructor<?> delegatingCtor = wovenClass.getDeclaredConstructor(String.class);

        DomainConstructionScope.run(() -> {
            decode(primaryCtor, "Alice", 30);
            decode(delegatingCtor, "Bob");
        });
    }

    /**
     * Simulates a Decoder's decode method. The method name "decode" is what
     * DomainConstructionScope looks for on the call stack.
     */
    private static void decode(Constructor<?> ctor, Object... args) {
        try {
            Object obj = ctor.newInstance(args);
            assertNotNull(obj);
        } catch (Exception e) {
            fail("Should not throw when called through decode", e);
        }
    }

    /**
     * Loads the original class bytes, weaves them, and loads the result
     * via an isolated class loader so the woven version is used.
     */
    private Class<?> loadWovenClass(String className) throws IOException, ClassNotFoundException {
        String resourcePath = className.replace('.', '/') + ".class";
        byte[] originalBytes;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new ClassNotFoundException("Cannot find class resource: " + resourcePath);
            }
            originalBytes = is.readAllBytes();
        }

        byte[] wovenBytes = GuardWeaver.weave(originalBytes);

        ClassLoader wovenLoader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(className)) {
                    Class<?> c = defineClass(name, wovenBytes, 0, wovenBytes.length);
                    if (resolve) resolveClass(c);
                    return c;
                }
                return super.loadClass(name, resolve);
            }
        };

        return wovenLoader.loadClass(className);
    }
}
