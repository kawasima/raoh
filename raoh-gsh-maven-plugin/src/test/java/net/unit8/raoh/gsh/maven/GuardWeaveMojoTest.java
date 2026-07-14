package net.unit8.raoh.gsh.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link GuardWeaveMojo}, exercising the {@code weave} goal's behaviour directly by
 * invoking {@link GuardWeaveMojo#execute()} against temp class directories.
 *
 * <p>Rather than the maven-plugin-testing-harness (which is JUnit&nbsp;4 centric), the mojo is
 * driven as a plain object: its {@code @Parameter} fields are set by reflection and {@code execute()}
 * is called. That covers exactly what this issue asks — which directories get woven — without the
 * harness's plexus/test-pom machinery. The guard-injection semantics themselves are covered by
 * {@code GuardWeaverTest} in raoh-gsh-weaver.
 *
 * <p>"Woven" is detected by the {@code checkActive} marker: the weaver injects an
 * {@code invokestatic DomainConstructionScope.checkActive} into each guarded constructor, so a woven
 * class file contains the string {@code "checkActive"} in its constant pool; an unwoven one does not.
 */
class GuardWeaveMojoTest {

    private static final String FIXTURE_INTERNAL = "net/unit8/raoh/gsh/maven/fixture/SampleDomain";
    private static final String FIXTURE_FQCN = "net.unit8.raoh.gsh.maven.fixture.SampleDomain";
    private static final String FIXTURE_PACKAGE_GLOB = "net.unit8.raoh.gsh.maven.fixture.**";

    /** Reads the compiled fixture class bytes from the test classpath. */
    private static byte[] fixtureBytes() throws IOException {
        try (var in = GuardWeaveMojoTest.class.getResourceAsStream("/" + FIXTURE_INTERNAL + ".class")) {
            assertNotNull(in, "SampleDomain.class fixture must be on the test classpath");
            return in.readAllBytes();
        }
    }

    /** Writes the fixture class into {@code dir} at its package path and returns the class file. */
    private static Path writeFixture(Path dir) throws IOException {
        Path classFile = dir.resolve(FIXTURE_INTERNAL + ".class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, fixtureBytes());
        return classFile;
    }

    /** True if the class file contains the injected guard call. */
    private static boolean isWoven(Path classFile) throws IOException {
        var text = new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
        return text.contains("checkActive");
    }

    private static void set(GuardWeaveMojo mojo, String field, Object value) throws ReflectiveOperationException {
        var f = GuardWeaveMojo.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(mojo, value);
    }

    @Test
    void weavesTestClassesButNotMainByDefault(@TempDir Path tmp) throws Exception {
        Path mainDir = Files.createDirectories(tmp.resolve("classes"));
        Path testDir = Files.createDirectories(tmp.resolve("test-classes"));
        Path mainClass = writeFixture(mainDir);
        Path testClass = writeFixture(testDir);

        var mojo = new GuardWeaveMojo();
        set(mojo, "packages", FIXTURE_PACKAGE_GLOB);
        set(mojo, "target", mainDir.toString());
        set(mojo, "testTarget", testDir.toString());
        set(mojo, "weaveMain", false);
        mojo.execute();

        assertTrue(isWoven(testClass), "the guard must be woven into test classes");
        assertFalse(isWoven(mainClass), "main classes must NOT be woven when weaveMain=false");
    }

    @Test
    void weavesMainClassesWhenWeaveMainTrue(@TempDir Path tmp) throws Exception {
        Path mainDir = Files.createDirectories(tmp.resolve("classes"));
        Path testDir = Files.createDirectories(tmp.resolve("test-classes"));
        Path mainClass = writeFixture(mainDir);
        Path testClass = writeFixture(testDir);

        var mojo = new GuardWeaveMojo();
        set(mojo, "packages", FIXTURE_PACKAGE_GLOB);
        set(mojo, "target", mainDir.toString());
        set(mojo, "testTarget", testDir.toString());
        set(mojo, "weaveMain", true);
        mojo.execute();

        assertTrue(isWoven(mainClass), "main classes must be woven when weaveMain=true");
        assertTrue(isWoven(testClass), "test classes are always woven");
    }

    @Test
    void classesParameterSelectsByExactFqcn(@TempDir Path tmp) throws Exception {
        Path testDir = Files.createDirectories(tmp.resolve("test-classes"));
        Path testClass = writeFixture(testDir);

        var mojo = new GuardWeaveMojo();
        set(mojo, "classes", FIXTURE_FQCN);
        set(mojo, "target", tmp.resolve("classes").toString()); // non-existent → skipped
        set(mojo, "testTarget", testDir.toString());
        mojo.execute();

        assertTrue(isWoven(testClass), "the guard must be woven when the class is selected by exact name");
    }

    @Test
    void excludedClassIsNotWoven(@TempDir Path tmp) throws Exception {
        Path testDir = Files.createDirectories(tmp.resolve("test-classes"));
        Path testClass = writeFixture(testDir);

        var mojo = new GuardWeaveMojo();
        set(mojo, "packages", FIXTURE_PACKAGE_GLOB);
        set(mojo, "exclude", "net.unit8.raoh.gsh.maven.fixture.**"); // excludes what packages selects
        set(mojo, "target", tmp.resolve("classes").toString());
        set(mojo, "testTarget", testDir.toString());
        mojo.execute();

        assertFalse(isWoven(testClass), "an excluded class must not be woven");
    }

    @Test
    void nonMatchingConfigWeavesNothing(@TempDir Path tmp) throws Exception {
        Path testDir = Files.createDirectories(tmp.resolve("test-classes"));
        Path testClass = writeFixture(testDir);

        var mojo = new GuardWeaveMojo();
        set(mojo, "packages", "com.other.**"); // does not match the fixture
        set(mojo, "target", tmp.resolve("classes").toString());
        set(mojo, "testTarget", testDir.toString());
        mojo.execute();

        assertFalse(isWoven(testClass), "no class is woven when the config matches nothing");
    }

    @Test
    void missingDirectoriesAreSkippedWithoutError(@TempDir Path tmp) throws Exception {
        var mojo = new GuardWeaveMojo();
        set(mojo, "packages", FIXTURE_PACKAGE_GLOB);
        set(mojo, "target", tmp.resolve("nope-main").toString());
        set(mojo, "testTarget", tmp.resolve("nope-test").toString());
        assertDoesNotThrow(mojo::execute, "non-existent class directories must be skipped, not fail");
    }
}
