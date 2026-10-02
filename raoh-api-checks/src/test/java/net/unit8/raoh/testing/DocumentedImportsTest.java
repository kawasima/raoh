package net.unit8.raoh.testing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentedImportsTest {

    @TempDir
    Path dir;

    private static final String OBJECT = "net.unit8.raoh.decode.ObjectDecoders";
    private static final String MAP = "net.unit8.raoh.decode.map.MapDecoders";
    private static final String JSON = "net.unit8.raoh.json.JsonDecoders";

    private List<List<String>> groupsOf(String name, String content) throws IOException {
        return DocumentedImports.groups(List.of(Files.writeString(dir.resolve(name), content)));
    }

    /** What stands between two imports of one example does not part them, as it does not in Java. */
    @Test
    void anExampleIsOneGroupWhateverStandsBetweenItsImports() throws IOException {
        assertEquals(List.of(List.of(OBJECT, MAP)), groupsOf("a.md", """
                ```java
                import static net.unit8.raoh.decode.ObjectDecoders.*;
                import java.util.List;
                // structure decoders

                import static net.unit8.raoh.decode.map.MapDecoders.*;

                var d = string();
                ```
                """));
        assertEquals(List.of(List.of(OBJECT, MAP)), groupsOf("A.java", """
                /**
                 * <pre>{@code
                 * import static net.unit8.raoh.decode.ObjectDecoders.*;
                 *
                 * import java.util.Map;
                 * // structure decoders
                 * import static net.unit8.raoh.decode.map.MapDecoders.*;
                 * }</pre>
                 */
                class A {}
                """));
    }

    @Test
    void twoImportsOnOneLineAreTwoImports() throws IOException {
        assertEquals(List.of(List.of(OBJECT, MAP)), groupsOf("b.md", """
                ```java
                import static net.unit8.raoh.decode.ObjectDecoders.*; import static net.unit8.raoh.decode.map.MapDecoders.*;
                ```
                """));
    }

    @Test
    void anImportMayLeaveOutItsSemicolonAsInJshell() throws IOException {
        assertEquals(List.of(List.of(OBJECT, MAP)), groupsOf("j.md", """
                ```
                jshell> import static net.unit8.raoh.decode.ObjectDecoders.*
                jshell> import static net.unit8.raoh.decode.map.MapDecoders.*
                ```
                """));
    }

    @Test
    void separateExamplesAreSeparateGroups() throws IOException {
        assertEquals(List.of(), groupsOf("c.md", """
                ```java
                import static net.unit8.raoh.decode.ObjectDecoders.*;
                ```

                ~~~java
                import static net.unit8.raoh.json.JsonDecoders.*;
                ~~~
                """));
        assertEquals(List.of(), groupsOf("C.java", """
                /**
                 * <pre>{@code
                 * import static net.unit8.raoh.decode.ObjectDecoders.*;
                 * }</pre>
                 * <pre>{@code
                 * import static net.unit8.raoh.json.JsonDecoders.*;
                 * }</pre>
                 */
                class C {}
                """));
    }

    @Test
    void tildeFencesAndIndentedBlocksAreExamples() throws IOException {
        assertEquals(List.of(List.of(OBJECT, MAP)), groupsOf("d.md", """
                ~~~java
                import static net.unit8.raoh.decode.ObjectDecoders.*;
                ```
                import static net.unit8.raoh.decode.map.MapDecoders.*;
                ~~~
                """));
        assertEquals(List.of(List.of(OBJECT, JSON)), groupsOf("e.md", """
                Some prose.

                    import static net.unit8.raoh.decode.ObjectDecoders.*;

                    import static net.unit8.raoh.json.JsonDecoders.*;

                More prose.
                """));
    }

    @Test
    void importsInAJavadocCommentOutsidePreAreOneExample() throws IOException {
        assertEquals(List.of(List.of(OBJECT, MAP)), groupsOf("F.java", """
                /**
                 * Usage: {@code import static net.unit8.raoh.decode.ObjectDecoders.*;} and
                 * {@code import static net.unit8.raoh.decode.map.MapDecoders.*;}
                 */
                class F {}
                """));
    }

    @Test
    void theSourcesOwnImportsAreNotDocumentation() throws IOException {
        assertEquals(List.of(), groupsOf("G.java", """
                import static net.unit8.raoh.decode.ObjectDecoders.*;
                import static net.unit8.raoh.decode.map.MapDecoders.*;

                /** A class. */
                class G {}
                """));
    }

    @Test
    void examplesAreLeftOut() throws IOException {
        assertEquals(List.of(), groupsOf("h.md", """
                ```java
                import static net.unit8.raoh.decode.ObjectDecoders.*;
                import static net.unit8.raoh.examples.Example.*;
                ```
                """));
    }

    @Test
    void theDocumentationIsTheReadmeDocsAndThePublishedJavadoc() throws IOException {
        String pkg = "net/unit8/raoh/testing/";
        for (String file : List.of("README.md", "CHANGELOG.md", "CLAUDE.md", "docs/guide.md",
                "raoh/src/main/java/" + pkg + "PublishedFixture.java",
                "raoh/src/main/java/" + pkg + "HiddenFixture.java",
                "raoh/src/main/java/" + pkg + "package-info.java",
                "raoh-json/src/main/java/net/unit8/raoh/internal/Internal.java",
                "raoh-jooq/src/main/java/module-info.java",
                "raoh-gsh/src/main/java/" + pkg + "PublishedFixture.java")) {
            Files.createDirectories(dir.resolve(file).getParent());
            Files.writeString(dir.resolve(file), "");
        }

        var documents = DocumentedImports.documents(dir, getClass().getClassLoader()).stream()
                .map(p -> dir.relativize(p).toString().replace(dir.getFileSystem().getSeparator(), "/")).toList();

        // The CHANGELOG records earlier versions, CLAUDE.md is for whoever works on Raoh, and the
        // Javadoc of a type that is not public or of the internal package is not published.
        assertEquals(List.of("README.md", "raoh/src/main/java/" + pkg + "PublishedFixture.java",
                "raoh/src/main/java/" + pkg + "package-info.java", "docs/guide.md"), documents);
    }

    @Test
    void aSourceWhoseTypeDoesNotLoadFails() {
        assertThrows(AssertionError.class, () -> DocumentedImports.published(
                Path.of("net/unit8/raoh/testing/NoSuchType.java"), getClass().getClassLoader()));
    }

    /** A nested class, imported by its canonical name. */
    public static class Outer {
        public static class Inner {
        }
    }

    @Test
    void aNestedClassIsLoadedByItsCanonicalName() {
        var loaded = DocumentedImports.load(List.of(List.of(Outer.Inner.class.getCanonicalName())),
                getClass().getClassLoader());

        assertEquals(List.of(List.of(Outer.Inner.class)), loaded);
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

    /** Two generic methods of one shape, whose type variables only are named apart. */
    public static class GenericA {
        public static <T> T first(List<T> values) {
            return null;
        }
    }

    public static class GenericB {
        public static <U> U first(List<U> values) {
            return null;
        }
    }

    @Test
    void genericMethodsOfOneShapeClash() {
        assertEquals(1, DocumentedImports.clashes(List.of(List.of(GenericA.class, GenericB.class))).size());
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
    void aMoreSpecificGenericMethodIsNoClash() {
        assertEquals(List.of(), DocumentedImports.clashes(List.of(List.of(AnyInput.class, MapInput.class))));
    }
}
