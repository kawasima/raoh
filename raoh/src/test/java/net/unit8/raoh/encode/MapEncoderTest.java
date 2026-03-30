package net.unit8.raoh.encode;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static net.unit8.raoh.encode.MapEncoders.*;
import static net.unit8.raoh.encode.ObjectEncoders.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for flat (non-nested) object encoding with {@link MapEncoders}.
 */
class MapEncoderTest {

    // --- Domain model ---

    record ItemId(long value) {}

    record Item(ItemId id, String name, BigDecimal price) {}

    // --- Encoders ---

    static final Encoder<Item, Map<String, Object>> ITEM_ENCODER = object(
            property("id",    Item::id,    long_().contramap(ItemId::value)),
            property("name",  Item::name,  string()),
            property("price", Item::price, decimal())
    );

    // --- Tests ---

    @Test
    void encodesAllFields() {
        var item = new Item(new ItemId(42L), "Widget", new BigDecimal("9.99"));
        var row = ITEM_ENCODER.encode(item);

        assertEquals(42L,                    row.get("id"));
        assertEquals("Widget",               row.get("name"));
        assertEquals(new BigDecimal("9.99"), row.get("price"));
    }

    @Test
    void preservesInsertionOrder() {
        var item = new Item(new ItemId(1L), "A", BigDecimal.ONE);
        var keys = ITEM_ENCODER.encode(item).keySet().stream().toList();
        assertEquals(java.util.List.of("id", "name", "price"), keys);
    }

    @Test
    void contramapUnwrapsValueObject() {
        var enc = long_().contramap(ItemId::value);
        assertEquals(7L, enc.encode(new ItemId(7L)));
    }

    @Test
    void andThenTransformsOutput() {
        var enc = long_().andThen(o -> "id=" + o);
        assertEquals("id=42", enc.encode(42L));
    }

    @Test
    void enumOfEncodesName() {
        enum Color { RED, GREEN, BLUE }
        Encoder<Color, Object> enc = enumOf();
        assertEquals("GREEN", enc.encode(Color.GREEN));
    }

    @Test
    void nullablePassesThroughNull() {
        var enc = nullable(string());
        assertNull(enc.encode(null));
    }

    @Test
    void nullableDelegatesToInnerEncoderWhenNonNull() {
        var enc = nullable(string());
        assertEquals("hello", enc.encode("hello"));
    }

    @Test
    void doubleEncoderPassesThrough() {
        assertEquals(3.14, double_().encode(3.14));
    }

    @Test
    void floatEncoderPassesThrough() {
        assertEquals(2.5f, float_().encode(2.5f));
    }
}
