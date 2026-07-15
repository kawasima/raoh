package net.unit8.raoh.encode;

import org.junit.jupiter.api.Test;

import org.jspecify.annotations.Nullable;

import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.decode.Decoders;
import net.unit8.raoh.decode.ObjectDecoders;
import net.unit8.raoh.decode.map.MapDecoders;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static net.unit8.raoh.encode.MapEncoders.*;
import static net.unit8.raoh.encode.ObjectEncoders.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for nested and list encoding with {@link MapEncoders#nested} and
 * {@link MapEncoders#list}, including the recursive case built on {@link MapEncoders#lazy}.
 *
 * <p>Uses a Restaurant / Table domain to demonstrate a dependent relationship
 * where the parent (Restaurant) holds a collection of children (Table).
 * This mirrors the decoder-side pattern of {@code nested()} + {@code list()}.
 */
class NestedEncoderTest {

    // --- Domain model ---

    record TableId(long value) {}

    record Table(TableId id, int tableNumber, int capacity) {}

    record RestaurantId(long value) {}

    record Restaurant(RestaurantId id, String name, List<Table> tables) {}

    // --- Encoders ---

    static final Encoder<Table, Map<String, @Nullable Object>> TABLE_ENCODER = object(
            property("id",           Table::id,          long_().contramap(TableId::value)),
            property("table_number", Table::tableNumber, int_()),
            property("capacity",     Table::capacity,    int_())
    );

    static final Encoder<Restaurant, Map<String, @Nullable Object>> RESTAURANT_ENCODER = object(
            property("id",     Restaurant::id,     long_().contramap(RestaurantId::value)),
            property("name",   Restaurant::name,   string()),
            property("tables", Restaurant::tables, list(nested(TABLE_ENCODER)))
    );

    // --- Tests ---

    @Test
    void encodesNestedList() {
        var restaurant = new Restaurant(
                new RestaurantId(1L),
                "Sakura",
                List.of(
                        new Table(new TableId(10L), 1, 4),
                        new Table(new TableId(11L), 2, 2)
                )
        );

        var row = RESTAURANT_ENCODER.encode(restaurant);

        assertEquals(1L,      row.get("id"));
        assertEquals("Sakura", row.get("name"));

        @SuppressWarnings("unchecked")
        var tables = (List<Object>) row.get("tables");
        assertEquals(2, tables.size());

        @SuppressWarnings("unchecked")
        var table0 = (Map<String, Object>) tables.get(0);
        assertEquals(10L, table0.get("id"));
        assertEquals(1,   table0.get("table_number"));
        assertEquals(4,   table0.get("capacity"));
    }

    @Test
    void encodesEmptyList() {
        var restaurant = new Restaurant(new RestaurantId(2L), "Empty", List.of());
        var row = RESTAURANT_ENCODER.encode(restaurant);

        @SuppressWarnings("unchecked")
        var tables = (List<Object>) row.get("tables");
        assertTrue(tables.isEmpty());
    }

    @Test
    void tableEncoderCanBeUsedStandalone() {
        var table = new Table(new TableId(5L), 3, 6);
        var row = TABLE_ENCODER.encode(table);

        assertEquals(5L, row.get("id"));
        assertEquals(3,  row.get("table_number"));
        assertEquals(6,  row.get("capacity"));
    }

    // --- lazy: recursive encoders ---

    record Node(int value, List<Node> children) {}

    /**
     * A self-referential encoder — it only compiles because {@code lazy} defers the reference to
     * {@code NODE} until encode time, after this field's initializer has completed.
     */
    static final Encoder<Node, Map<String, @Nullable Object>> NODE = object(
            property("value",    Node::value,    int_()),
            property("children", Node::children, list(nested(lazy(() -> NestedEncoderTest.NODE)))));

    /** The decode dual of {@link #NODE}; a static method, so the recursion needs no forward reference. */
    private static Decoder<Map<String, Object>, Node> nodeDecoder() {
        return MapDecoders.combine(
                MapDecoders.field("value", ObjectDecoders.int_()),
                MapDecoders.field("children",
                        ObjectDecoders.list(MapDecoders.nested(Decoders.lazy(NestedEncoderTest::nodeDecoder))))
        ).map(Node::new);
    }

    private static Node sampleTree() {
        return new Node(1, List.of(
                new Node(2, List.of()),
                new Node(3, List.of(new Node(4, List.of())))));
    }

    @Test
    void lazyEncodesRecursiveStructure() {
        assertEquals(
                Map.of("value", 1, "children", List.of(
                        Map.of("value", 2, "children", List.of()),
                        Map.of("value", 3, "children", List.of(
                                Map.of("value", 4, "children", List.of()))))),
                NODE.encode(sampleTree()));
    }

    @Test
    void lazyRoundTripsARecursiveTreeThroughTheDecoder() {
        // The point of encode-side lazy: a recursive type that decodes via lazy encodes back the
        // same way. Encoding then decoding must return the original tree.
        var tree = sampleTree();
        @SuppressWarnings("unchecked")
        var asInput = (Map<String, Object>) (Map<String, ?>) NODE.encode(tree);
        assertEquals(tree, nodeDecoder().decode(asInput).getOrThrow());
    }

    @Test
    void lazyResolvesTheSupplierOnEachEncode() {
        // Spec: "supplies the encoder on each invocation" — mirroring Decoders.lazy. Construction
        // must not call the supplier; each encode call must.
        var calls = new AtomicInteger();
        Encoder<String, Object> enc = lazy(() -> {
            calls.incrementAndGet();
            return string();
        });
        assertEquals(0, calls.get(), "supplier must not run at construction time");
        enc.encode("a");
        enc.encode("b");
        assertEquals(2, calls.get(), "supplier runs once per encode invocation");
    }
}
