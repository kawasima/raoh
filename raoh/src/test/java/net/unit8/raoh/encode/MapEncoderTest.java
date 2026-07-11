package net.unit8.raoh.encode;

import org.junit.jupiter.api.Test;

import org.jspecify.annotations.Nullable;

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

    static final Encoder<Item, Map<String, @Nullable Object>> ITEM_ENCODER = object(
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
    void nullablePropertyWritesNullWhenGetterReturnsNull() {
        record Row(@Nullable String note) {}
        var enc = object(nullableProperty("note", Row::note, string()));
        assertNull(enc.encode(new Row(null)).get("note"));
    }

    @Test
    void nullablePropertyEncodesValueWhenPresent() {
        record Row(@Nullable String note) {}
        var enc = object(nullableProperty("note", Row::note, string()));
        assertEquals("hello", enc.encode(new Row("hello")).get("note"));
    }

    @Test
    void doubleEncoderPassesThrough() {
        assertEquals(3.14, double_().encode(3.14));
    }

    @Test
    void floatEncoderPassesThrough() {
        assertEquals(2.5f, float_().encode(2.5f));
    }

    @Test
    void propertyWithDefaultEncodesDefaultWhenNull() {
        record Row(@Nullable String name) {}
        var enc = object(propertyWithDefault("name", Row::name, string(), "N/A"));
        assertEquals("N/A", enc.encode(new Row(null)).get("name"));
    }

    @Test
    void propertyWithDefaultEncodesValueWhenNonNull() {
        record Row(@Nullable String name) {}
        var enc = object(propertyWithDefault("name", Row::name, string(), "N/A"));
        assertEquals("hello", enc.encode(new Row("hello")).get("name"));
    }

    @Test
    void propertyWithDefaultSupplierEncodesDefaultWhenNull() {
        record Row(@Nullable String name) {}
        java.util.function.Supplier<String> supplier = () -> "generated";
        var enc = object(propertyWithDefault("name", Row::name, string(), supplier));
        assertEquals("generated", enc.encode(new Row(null)).get("name"));
    }

    @Test
    void propertyWithDefaultSupplierEncodesValueWhenNonNull() {
        record Row(@Nullable String name) {}
        java.util.function.Supplier<String> supplier = () -> "generated";
        var enc = object(propertyWithDefault("name", Row::name, string(), supplier));
        assertEquals("world", enc.encode(new Row("world")).get("name"));
    }
}
