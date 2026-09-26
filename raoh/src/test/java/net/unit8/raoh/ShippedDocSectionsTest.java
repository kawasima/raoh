package net.unit8.raoh;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ShippedDocsTest} passes only if {@link ShippedDocSections} would have failed on a guide
 * Souther refuses, so the refusals are checked here against small documents written to trip them.
 */
class ShippedDocSectionsTest {

    @Test
    void aDeclarationNamesTheHeadingOnTheNextLine() {
        List<ShippedDocSections.Heading> headings = ShippedDocSections.headings("t", """
                # Title
                <!-- souther-section: flat -->
                ## 6. Structuring flat data — flat
                ## Unnamed
                """);

        assertEquals(List.of(
                new ShippedDocSections.Heading(null, 1, "Title", 1),
                new ShippedDocSections.Heading("flat", 2, "6. Structuring flat data — flat", 3),
                new ShippedDocSections.Heading(null, 2, "Unnamed", 4)), headings);
    }

    @Test
    void aDeclarationAboveSomethingOtherThanAHeadingIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ShippedDocSections.headings("t", """
                <!-- souther-section: flat -->

                ## Flat
                """));
    }

    @Test
    void aDeclarationOnTheLastLineIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> ShippedDocSections.headings("t", "<!-- souther-section: flat -->"));
    }

    @Test
    void aDeclarationInsideAFenceIsOnlySampleText() {
        // Souther reads fenced lines as they stand, so a guide may show the notation itself.
        assertEquals(List.of(new ShippedDocSections.Heading(null, 2, "After", 6)),
                ShippedDocSections.headings("t", """
                        ```markdown
                        <!-- souther-section: Not_Raoh_Style -->
                        ## Example
                        <!-- souther-section: dangling -->
                        ```
                        ## After
                        """));
    }

    @Test
    void aDeclarationAboveAFenceIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ShippedDocSections.headings("t", """
                <!-- souther-section: flat -->
                ```
                ## Flat
                ```
                """));
    }

    @Test
    void anAffordanceIsRefusedEvenInsideAFence() {
        for (String text : List.of("See {{stdlib-api}}.\n", "```\n{{stdlib-source:list}}\n```\n")) {
            assertThrows(IllegalArgumentException.class,
                    () -> ShippedDocSections.headings("t", text), text);
        }
    }

    @Test
    void aDoubleBraceInitializerIsNotAnAffordance() {
        assertDoesNotThrow(() -> ShippedDocSections.headings("t", """
                ```java
                new HashMap<>() {{ put("a", 1); }};
                ```
                """));
    }

    @Test
    void aHeadingInsideAFenceIsNotAHeading() {
        assertEquals(List.of(), ShippedDocSections.headings("t", """
                ~~~~
                ## Example input
                ```
                ~~~~
                """));
    }

    @Test
    void aNameOutsideRaohsConventionIsRefused() {
        for (String line : List.of(
                "<!-- souther-section: Flat -->",
                "<!-- souther-section: flat_map -->",
                "<!-- souther-section: -flat -->",
                "<!--souther-section: flat-->",
                " <!-- souther-section: flat -->")) {
            assertThrows(IllegalArgumentException.class,
                    () -> ShippedDocSections.headings("t", line + "\n## Flat\n"), line);
        }
    }

    @Test
    void namesThatDifferOnlyInCaseCollide() {
        assertThrows(IllegalArgumentException.class, () -> ShippedDocSections.requireDistinct(
                List.of("raoh/tutorial/flat", "raoh/Tutorial/flat")));
    }

    @Test
    void namesThatAreTheSameWordsCollide() {
        // A section `ja` in the tutorial reads as the Japanese tutorial.
        assertThrows(IllegalArgumentException.class, () -> ShippedDocSections.requireDistinct(
                List.of("raoh/tutorial.ja", "raoh/tutorial/ja")));
    }

    @Test
    void theSameSectionInTwoTopicsIsTwoNames() {
        assertDoesNotThrow(() -> ShippedDocSections.requireDistinct(
                List.of("raoh/tutorial", "raoh/tutorial.ja", "raoh/tutorial/flat",
                        "raoh/tutorial.ja/flat")));
    }
}
