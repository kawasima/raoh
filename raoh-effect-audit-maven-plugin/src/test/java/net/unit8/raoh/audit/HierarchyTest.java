package net.unit8.raoh.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HierarchyTest {

    @TempDir
    Path dir;

    @Test
    void resolvesABridgeAndTheRealMethodSeparately() throws IOException {
        // C overrides get() covariantly and is final, so its String get() is not overridable; the
        // bridge Object get() it also declares is a different member and is resolved as such.
        var compiled = Fixtures.compile(dir, Map.of(
                "B", "public class B { public Object get() { return 1; } }",
                "C", "public final class C extends B { @Override public String get() { return \"c\"; } }"));
        var hierarchy = compiled.hierarchy();
        assertEquals("fixture.C", hierarchy.declaringClass(Member.parse("fixture.C#get():java.lang.String")).orElseThrow());
        assertEquals("fixture.C", hierarchy.declaringClass(Member.parse("fixture.C#get():java.lang.Object")).orElseThrow());
        assertEquals("fixture.B", hierarchy.declaringClass(Member.parse("fixture.B#get():java.lang.Object")).orElseThrow());
        assertTrue(hierarchy.declaringClass(Member.parse("fixture.B#get():java.lang.String")).isEmpty());
        assertTrue(hierarchy.isOverridable(Member.parse("fixture.B#get():java.lang.Object")));
        assertFalse(hierarchy.isOverridable(Member.parse("fixture.C#get():java.lang.String")));
    }

    @Test
    void anOverridableMethodIsFoundThroughAnInterface() throws IOException {
        try (var hierarchy = Hierarchy.open(List.of(), Fixtures.RELEASE)) {
            assertTrue(hierarchy.isOverridable(Member.parse("java.util.List#get(int):java.lang.Object")));
            assertTrue(hierarchy.isOverridable(Member.parse("java.util.List#toString():java.lang.String")));
            assertFalse(hierarchy.isOverridable(Member.parse("java.lang.String#trim():java.lang.String")));
        }
    }

    @Test
    void readsTheJdkApiOfTheRequestedReleaseFromEitherSource() throws IOException {
        // The running JDK's own release comes from its image, an earlier one from ct.sym; both
        // must answer. Java 21 added List#getFirst(), which Java 17's API does not have.
        int running = Runtime.version().feature();
        var getFirst = Member.parse("java.util.List#getFirst():java.lang.Object");
        try (var current = Hierarchy.open(List.of(), running);
             var older = Hierarchy.open(List.of(), 17)) {
            assertEquals("java.util.List", current.declaringClass(getFirst).orElseThrow());
            assertTrue(older.declaringClass(getFirst).isEmpty());
            assertTrue(older.isSubtype("java.util.ArrayList", "java.util.List"));
            assertFalse(older.isOverridable(Member.parse("java.lang.String#trim():java.lang.String")));
        }
    }
}
