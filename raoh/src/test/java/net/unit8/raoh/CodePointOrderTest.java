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

    @Test
    void sortedListsInCodePointOrder() {
        assertEquals(List.of("a", "Ａ", "😀"), CodePointOrder.sorted(Set.of("😀", "Ａ", "a")));
    }
}
