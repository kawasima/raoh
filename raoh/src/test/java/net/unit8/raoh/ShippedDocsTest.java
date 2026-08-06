package net.unit8.raoh;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The jar ships the documentation under {@code META-INF/souther-docs/raoh/}, so a consumer that
 * bundles raoh — the Souther CLI's {@code souther doc} above all — can serve these docs at the
 * exact version it depends on, without keeping a copy of its own.
 */
class ShippedDocsTest {

    @Test
    void theSetsRegistryNamesTheRaohDocSet() throws IOException {
        assertTrue(resource("META-INF/souther-docs/sets").contains("raoh"));
    }

    @Test
    void everyTopicTheIndexListsIsShippedBesideIt() throws IOException {
        List<String> topics = topics();

        assertTrue(topics.contains("tutorial.md"), "the tutorial is among the topics: " + topics);
        for (String topic : topics) {
            assertNotNull(ShippedDocsTest.class.getClassLoader()
                            .getResource("META-INF/souther-docs/raoh/" + topic),
                    "the index promises `" + topic + "` and the jar carries it");
        }
    }

    @Test
    void everyDocTheBuildShipsIsListedInTheIndex() throws IOException, URISyntaxException {
        List<String> indexed = topics();
        URL shipped = ShippedDocsTest.class.getClassLoader().getResource("META-INF/souther-docs/raoh");
        assertNotNull(shipped, "the shipped doc directory is on the test class path");

        try (Stream<Path> files = Files.list(Path.of(shipped.toURI()))) {
            List<String> onDisk = files.map(f -> f.getFileName().toString())
                    .filter(f -> f.endsWith(".md"))
                    .sorted()
                    .toList();
            for (String doc : onDisk) {
                assertTrue(indexed.contains(doc),
                        "the build ships `" + doc + "` and the index must list it, or `souther doc`"
                                + " never offers it; indexed: " + indexed);
            }
        }
    }

    @Test
    void aShippedTopicCarriesItsMarkdownTitleForListings() throws IOException {
        assertTrue(resource("META-INF/souther-docs/raoh/tutorial.md").lines()
                        .anyMatch(l -> l.startsWith("# ")),
                "a first-level heading titles the topic in `souther doc` listings");
    }

    private static List<String> topics() throws IOException {
        return resource("META-INF/souther-docs/raoh/index").lines()
                .map(String::strip)
                .filter(l -> !l.isEmpty())
                .toList();
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = ShippedDocsTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, "the jar carries " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
