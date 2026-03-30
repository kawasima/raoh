package net.unit8.raoh.encoder;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.unit8.raoh.encoder.MapEncoders.*;
import static net.unit8.raoh.encoder.ObjectEncoders.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for nested and list encoding with {@link MapEncoders#nested} and
 * {@link MapEncoders#list}.
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

    static final Encoder<Table, Map<String, Object>> TABLE_ENCODER = object(
            property("id",           Table::id,          long_().contramap(TableId::value)),
            property("table_number", Table::tableNumber, int_()),
            property("capacity",     Table::capacity,    int_())
    );

    static final Encoder<Restaurant, Map<String, Object>> RESTAURANT_ENCODER = object(
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
}
