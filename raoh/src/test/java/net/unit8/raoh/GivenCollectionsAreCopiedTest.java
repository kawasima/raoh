package net.unit8.raoh;

import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.decode.Decoders;
import net.unit8.raoh.decode.combinator.CombinePart;
import net.unit8.raoh.decode.map.MapDecoders;
import net.unit8.raoh.encode.EntryEncoder;
import net.unit8.raoh.encode.MapEncoders;
import net.unit8.raoh.encode.ObjectEncoders;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static net.unit8.raoh.decode.ObjectDecoders.int_;
import static net.unit8.raoh.decode.ObjectDecoders.string;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A value or decoder built from a collection or array keeps its own copy, so changing what was
 * passed in afterwards changes nothing, and a value read later reads what was there when it was
 * made (#167).
 */
class GivenCollectionsAreCopiedTest {

    private static Issue issue(String code) {
        return Issue.of(Path.ROOT, code, code);
    }

    @Test
    void issuesKeepTheirOwnList() {
        var backing = new ArrayList<>(List.of(issue("a")));
        var issues = new Issues(backing);
        backing.add(issue("b"));
        backing.set(0, issue("c"));
        assertEquals(List.of(issue("a")), issues.asList());
    }

    @Test
    void oneOfCandidatesReadWhatTheyFailedWith() {
        // A custom decoder that hands back Issues over a list it keeps changing.
        var backing = new ArrayList<>(List.of(issue("first")));
        Decoder<Object, Object> custom = (in, path) -> Result.err(new Issues(backing));
        Issue failed = assertInstanceOf(Err.class, Decoders.oneOf(custom, int_()).decode("x", Path.ROOT))
                .issues().asList().get(0);
        backing.set(0, issue("changed"));
        backing.add(issue("added"));
        @SuppressWarnings("unchecked")
        var candidates = (List<Map<String, Object>>) failed.meta().get("candidates");
        @SuppressWarnings("unchecked")
        var first = (List<Map<String, Object>>) candidates.get(0).get("issues");
        assertEquals(1, first.size());
        assertEquals("first", first.get(0).get("code"));
    }

    @Test
    void oneOfKeepsItsOwnCandidates() {
        @SuppressWarnings("unchecked")
        Decoder<Object, ? extends Object>[] candidates = new Decoder[]{int_(), string()};
        Decoder<Object, Object> dec = Decoders.oneOf(candidates);
        candidates[1] = int_();
        assertEquals("x", dec.decode("x", Path.ROOT).getOrThrow());
    }

    @Test
    void strictKeepsItsOwnKnownFields() {
        var known = new HashSet<>(List.of("a"));
        Decoder<Map<String, Object>, Map<String, Object>> dec = MapDecoders.strict((in, path) -> Result.ok(in), known);
        known.add("b");
        assertInstanceOf(Err.class, dec.decode(Map.of("a", 1, "b", 2), Path.ROOT));
        known.remove("a");
        assertEquals(Map.of("a", 1), dec.decode(Map.of("a", 1), Path.ROOT).getOrThrow());
    }

    @Test
    void discriminateKeepsItsOwnVariants() {
        var variants = new HashMap<String, Decoder<Map<String, Object>, ? extends Object>>();
        variants.put("n", MapDecoders.field("v", int_()).asDecoder());
        Decoder<Map<String, Object>, Object> dec = MapDecoders.discriminate("type", variants);
        variants.put("s", MapDecoders.field("v", string()).asDecoder());
        variants.remove("n");
        assertEquals(1, dec.decode(Map.of("type", "n", "v", 1), Path.ROOT).getOrThrow());
        assertInstanceOf(Err.class, dec.decode(Map.of("type", "s", "v", "x"), Path.ROOT));
    }

    @Test
    void combineKeepsItsOwnParts() {
        var parts = new ArrayList<CombinePart<Map<String, Object>, ?>>();
        parts.add(MapDecoders.field("a", int_()));
        var dec = Decoders.combine(parts).map(values -> List.of(values));
        parts.add(MapDecoders.field("b", int_()));
        assertEquals(1, dec.decode(Map.of("a", 1), Path.ROOT).getOrThrow().size());
    }

    record Point(int x, int y) {}

    @Test
    void objectEncoderKeepsItsOwnEntries() {
        @SuppressWarnings("unchecked")
        EntryEncoder<Point>[] entries = new EntryEncoder[]{
                MapEncoders.property("x", Point::x, ObjectEncoders.int_())};
        var encoder = MapEncoders.object(entries);
        entries[0] = MapEncoders.property("y", Point::y, ObjectEncoders.int_());
        assertEquals(Map.of("x", 1), encoder.encode(new Point(1, 2)));
    }
}
