package net.unit8.raoh;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a consumer receives is the jar, not {@code target/classes}: a jar-plugin exclude or any
 * later repackaging step could drop the docs while every class-path test still passed. This runs
 * after {@code package} and reads the packaged artifact itself, so the registry, the index and
 * every guide are asserted where they have to be.
 */
class ShippedDocsJarIT {

    private static final String SET = "META-INF/souther-docs/raoh/";

    @Test
    void thePackagedJarCarriesTheRegistryNamingTheRaohDocSet() throws IOException {
        try (JarFile jar = jar()) {
            assertEquals(List.of("raoh"), lines(jar, "META-INF/souther-docs/sets"));
        }
    }

    @Test
    void thePackagedJarCarriesEveryGuideTheIndexNames() throws IOException {
        try (JarFile jar = jar()) {
            List<String> indexed = lines(jar, SET + "index");

            assertTrue(indexed.contains("tutorial.md"), "the tutorial is among the topics: " + indexed);
            for (String topic : indexed) {
                assertNotNull(jar.getEntry(SET + topic),
                        "the index promises `" + topic + "` and the jar must carry it");
            }
        }
    }

    @Test
    void thePackagedJarCarriesNoGuideTheIndexOmits() throws IOException {
        try (JarFile jar = jar()) {
            List<String> indexed = lines(jar, SET + "index");
            List<String> packaged = jar.stream()
                    .map(JarEntry::getName)
                    .filter(name -> name.startsWith(SET) && name.endsWith(".md"))
                    .map(name -> name.substring(SET.length()))
                    .sorted()
                    .toList();

            assertTrue(packaged.contains("tutorial.md"), "the jar ships guides at all: " + packaged);
            for (String doc : packaged) {
                assertTrue(indexed.contains(doc),
                        "the jar ships `" + doc + "` and the index must list it, or `souther doc`"
                                + " never offers it; indexed: " + indexed);
            }
        }
    }

    private static JarFile jar() throws IOException {
        String path = System.getProperty("raoh.jar");
        assertNotNull(path, "failsafe passes the packaged jar as the `raoh.jar` system property");
        return new JarFile(path);
    }

    private static List<String> lines(JarFile jar, String name) throws IOException {
        JarEntry entry = jar.getJarEntry(name);
        assertNotNull(entry, "the jar carries " + name);
        try (InputStream in = jar.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty())
                    .toList();
        }
    }
}
