package net.unit8.raoh.testing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentedImportsTest {

    @TempDir
    Path dir;

    @Test
    void aGroupIsARunOfImportsInJavadocOrMarkdown() throws IOException {
        var javadoc = Files.writeString(dir.resolve("A.java"), """
                /**
                 * <pre>{@code
                 * import static net.unit8.raoh.decode.map.MapDecoders.*;
                 *
                 * import static net.unit8.raoh.decode.ObjectDecoders.*;
                 * import static net.unit8.raoh.examples.Example.*;
                 *
                 * var d = string();
                 * import static net.unit8.raoh.json.JsonDecoders.*;
                 * }</pre>
                 */
                class A {}
                """);
        var markdown = Files.writeString(dir.resolve("b.md"), """
                ```java
                import static net.unit8.raoh.encode.MapEncoders.*;
                import static net.unit8.raoh.encode.ObjectEncoders.*;
                ```
                """);

        assertEquals(List.of(
                        List.of("net.unit8.raoh.decode.ObjectDecoders", "net.unit8.raoh.decode.map.MapDecoders"),
                        List.of("net.unit8.raoh.encode.MapEncoders", "net.unit8.raoh.encode.ObjectEncoders")),
                DocumentedImports.groups(List.of(javadoc, markdown)));
    }

    /** Two classes with the same static method, which neither can call once both are imported. */
    public static class Decoding {
        public static Object int_() {
            return null;
        }
    }

    public static class Encoding {
        public static Object int_() {
            return null;
        }

        public static Object int_(String name) {
            return null;
        }
    }

    @Test
    void theSameStaticMethodInTwoClassesOfAGroupIsAClash() {
        var clashes = DocumentedImports.clashes(List.of(List.of(Decoding.class, Encoding.class)));

        assertEquals(1, clashes.size(), clashes.toString());
    }

    /** As Decoders.combine and MapDecoders.combine: the second is more specific, so a call goes to it. */
    public static class AnyInput {
        public static <I> Object combine(List<I> parts) {
            return null;
        }
    }

    public static class MapInput {
        public static Object combine(List<String> parts) {
            return null;
        }
    }

    @Test
    void genericMethodsAreNotCompared() {
        assertEquals(List.of(), DocumentedImports.clashes(List.of(List.of(AnyInput.class, MapInput.class))));
    }
}
