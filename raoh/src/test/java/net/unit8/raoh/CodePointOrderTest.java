package net.unit8.raoh;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodePointOrderTest {

    @Test
    void aCharacterAboveFfffComesAfterOneBelowIt() {
        // String.compareTo says the opposite: U+1F600 starts with the unit U+D83D < U+FF21.
        assertTrue("😀".compareTo("Ａ") < 0);
        assertTrue(CodePointOrder.compare("😀", "Ａ") > 0);
    }

    @Test
    void agreesWithCompareToOnTheBasicPlane() {
        var words = List.of("b", "a", "ab", "", "Z", "あ", "Ａ", "a\u0000");
        var byUnits = new ArrayList<>(words);
        byUnits.sort(String::compareTo);
        var byCodePoints = new ArrayList<>(words);
        byCodePoints.sort(CodePointOrder.COMPARATOR);
        assertEquals(byUnits, byCodePoints);
    }

    @Test
    void aPrefixComesFirstAndEqualStringsCompareEqual() {
        assertTrue(CodePointOrder.compare("😀", "😀a") < 0);
        assertTrue(CodePointOrder.compare("ab", "a") > 0);
        assertEquals(0, CodePointOrder.compare("😀x", "😀x"));
    }

    /**
     * An unpaired surrogate is no scalar value, so no text holds one; a Java string can, and it is
     * placed above every character of the basic plane, where the unit a pair starts with is.
     */
    @Test
    void anUnpairedSurrogateComesAfterEveryBasicPlaneCharacter() {
        assertTrue(CodePointOrder.compare("\uD800", "\uFFFF") > 0);
        assertTrue(CodePointOrder.compare("\uDC00", "\uFFFF") > 0);
        assertTrue(CodePointOrder.compare("\uFFFF", "\uD800") < 0);
        assertTrue(CodePointOrder.compare("a\uD800", "a\uE000") > 0);
        assertEquals(0, CodePointOrder.compare("\uD800", "\uD800"));
    }

    @Test
    void sortedListsInCodePointOrder() {
        assertEquals(List.of("a", "Ａ", "😀"), CodePointOrder.sorted(Set.of("😀", "Ａ", "a")));
    }
}
