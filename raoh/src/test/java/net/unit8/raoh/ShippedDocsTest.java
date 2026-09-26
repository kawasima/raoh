package net.unit8.raoh;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The build copies the repo-root docs under {@code META-INF/souther-docs/raoh/}, so a consumer
 * that bundles raoh — the Souther CLI's {@code souther doc} above all — can serve these docs at
 * the exact version it depends on, without keeping a copy of its own. This holds the index and
 * the copied guides to each other; {@link ShippedDocsJarIT} asserts the same of the packaged jar.
 *
 * <p>It also holds the section names the guides declare. {@code raoh/tutorial/flat} is an
 * identifier a client writes down, so renaming or removing one breaks that client the way renaming
 * a public method does.
 */
class ShippedDocsTest {

    /**
     * The section names the tutorial publishes, in both languages. This is the compatibility
     * baseline: the markers in the markdown are what ships, and this list is what raoh has promised.
     * A new section is added here in the same change that adds its marker. A name that has shipped
     * in a release is never renamed or removed; reword the heading instead.
     *
     * <p>This does not compare against the last release: a change that edits both a marker and this
     * list passes. What it does is keep a rename from happening as a side effect of editing prose,
     * and put every change to the published names in this file, where a reviewer sees it.
     */
    private static final List<String> TUTORIAL_SECTIONS = List.of(
            "why-decoders",
            "setup",
            "primitive-values",
            "constraints",
            "string-constraints",
            "normalization",
            "uri-url",
            "numeric-constraints",
            "temporal-constraints",
            "coerce",
            "domain-primitives",
            "objects",
            "nested-objects",
            "flat",
            "one-to-many-join",
            "lists",
            "maps",
            "json-in-string",
            "optional-nullable",
            "enums",
            "one-of",
            "discriminate",
            "cross-field-validation",
            "conditional-decoding",
            "result-map2",
            "result-traverse",
            "defaults-and-recovery",
            "with-default",
            "recover",
            "strict-mode",
            "lazy",
            "error-handling",
            "flatten",
            "to-json-list",
            "locale-aware-messages",
            "user-registration",
            "list-to-map",
            "remaining-fields",
            "password-confirmation",
            "pagination",
            "amount-currency",
            "csv-import",
            "configuration-files",
            "tuples",
            "encoding",
            "encoding-nullable",
            "null-analysis",
            "encoding-nested",
            "encoding-discriminate");

    /** Section names per topic; a topic missing here publishes none. */
    private static final Map<String, List<String>> PUBLISHED_SECTIONS = Map.of(
            "tutorial", TUTORIAL_SECTIONS,
            "tutorial.ja", TUTORIAL_SECTIONS);

    @Test
    void theSetsRegistryNamesTheRaohDocSet() throws IOException {
        // Line-oriented, and a consumer reads it line by line: `not-raoh` would satisfy a
        // substring check while naming a set that does not exist here.
        assertEquals(List.of("raoh"), resource("META-INF/souther-docs/sets").lines()
                .map(String::strip)
                .filter(l -> !l.isEmpty())
                .toList());
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

    @Test
    void theSetPublishesNoTwoNamesSoutherWouldReadAsOne() throws IOException {
        // Souther registers topics and sections in one namespace and refuses the whole doc
        // command when two of them fold together, so topics are checked alongside sections.
        List<String> published = new ArrayList<>();
        for (String file : topics()) {
            String topic = file.replaceFirst("\\.md$", "");
            published.add("raoh/" + topic);
            for (String section : ShippedDocSections.names(topic, doc(file))) {
                published.add("raoh/" + topic + "/" + section);
            }
        }
        ShippedDocSections.requireDistinct(published);
    }

    @Test
    void theSectionsEachTopicDeclaresAreTheOnesRaohHasPublished() throws IOException {
        for (String file : topics()) {
            String topic = file.replaceFirst("\\.md$", "");
            List<String> declared = ShippedDocSections.names(topic, doc(file));
            List<String> promised = PUBLISHED_SECTIONS.getOrDefault(topic, List.of());
            // Order is free: moving a section breaks no one. Duplicates are refused by
            // theSetPublishesNoTwoNamesSoutherWouldReadAsOne.
            if (!Set.copyOf(declared).equals(Set.copyOf(promised))) {
                List<String> missing = new ArrayList<>(promised);
                missing.removeAll(declared);
                List<String> unlisted = new ArrayList<>(declared);
                unlisted.removeAll(promised);
                fail("`raoh/" + topic + "` declares sections that differ from the published"
                        + " baseline in ShippedDocsTest. A published name must not be renamed or"
                        + " removed; a new one is added to the baseline. Missing: " + missing
                        + ", not in the baseline: " + unlisted);
            }
        }
    }

    @Test
    void theTutorialNamesEveryHeadingBelowItsTitle() throws IOException {
        for (String file : List.of("tutorial.md", "tutorial.ja.md")) {
            for (ShippedDocSections.Heading heading
                    : ShippedDocSections.headings(file, doc(file))) {
                if (heading.level() > 1 && heading.name() == null) {
                    fail(file + ":" + heading.line() + ": `" + heading.heading()
                            + "` has no `<!-- souther-section: ... -->` above it");
                }
            }
        }
    }

    @Test
    void bothTutorialsNameTheSamePartsInTheSameOrder() throws IOException {
        // raoh/tutorial/flat and raoh/tutorial.ja/flat are one part in two languages, so the
        // name has to sit above the corresponding heading, at the same level, in each file.
        assertEquals(outline("tutorial.md"), outline("tutorial.ja.md"),
                "tutorial.ja.md names its headings as tutorial.md does");
    }

    private static List<String> outline(String file) throws IOException {
        return ShippedDocSections.headings(file, doc(file)).stream()
                .map(h -> "#".repeat(h.level()) + " " + h.name())
                .toList();
    }

    private static String doc(String file) throws IOException {
        return resource("META-INF/souther-docs/raoh/" + file);
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
