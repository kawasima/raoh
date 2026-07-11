package net.unit8.raoh.encode;

import org.junit.jupiter.api.Test;

import org.jspecify.annotations.Nullable;

import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.decode.ObjectDecoders;
import net.unit8.raoh.decode.map.MapDecoders;

import java.math.BigDecimal;
import java.util.List;
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

    // --- discriminate ---

    sealed interface Shape permits Circle, Rect {}
    record Circle(double radius) implements Shape {}
    record Rect(double width, double height) implements Shape {}

    static final Encoder<Circle, Map<String, @Nullable Object>> CIRCLE_ENCODER =
            object(property("radius", Circle::radius, double_()));
    static final Encoder<Rect, Map<String, @Nullable Object>> RECT_ENCODER = object(
            property("width",  Rect::width,  double_()),
            property("height", Rect::height, double_()));

    static final Encoder<Shape, Map<String, @Nullable Object>> SHAPE_ENCODER = discriminate("type",
            variant(Circle.class, "circle", CIRCLE_ENCODER),
            variant(Rect.class,   "rect",   RECT_ENCODER));

    @Test
    void discriminateDispatchesAndInjectsTag() {
        var circle = SHAPE_ENCODER.encode(new Circle(2.0));
        assertEquals("circle", circle.get("type"));
        assertEquals(2.0, circle.get("radius"));

        var rect = SHAPE_ENCODER.encode(new Rect(3.0, 4.0));
        assertEquals("rect", rect.get("type"));
        assertEquals(3.0, rect.get("width"));
        assertEquals(4.0, rect.get("height"));
    }

    @Test
    void discriminatePlacesTagFirst() {
        var keys = SHAPE_ENCODER.encode(new Circle(1.0)).keySet().stream().toList();
        assertEquals("type", keys.getFirst());
    }

    @Test
    void discriminateRoundTripsWithDecoder() {
        Decoder<Map<String, Object>, Shape> dec = MapDecoders.discriminate("type", Map.of(
                "circle", MapDecoders.field("radius", ObjectDecoders.decimal())
                        .map(r -> new Circle(r.doubleValue())),
                "rect", MapDecoders.combine(
                                MapDecoders.field("width",  ObjectDecoders.decimal()),
                                MapDecoders.field("height", ObjectDecoders.decimal()))
                        .map((w, h) -> new Rect(w.doubleValue(), h.doubleValue()))));

        for (Shape original : List.of(new Circle(2.0), new Rect(3.0, 4.0))) {
            var encoded = SHAPE_ENCODER.encode(original);
            // The encoder's output (Map<String, @Nullable Object>) feeds straight back into the decoder.
            @SuppressWarnings("unchecked")
            var asInput = (Map<String, Object>) (Map<String, ?>) encoded;
            assertEquals(original, dec.decode(asInput).getOrThrow());
        }
    }

    @Test
    void discriminateThrowsForUnregisteredType() {
        Encoder<Shape, Map<String, @Nullable Object>> enc =
                discriminate("type", variant(Circle.class, "circle", CIRCLE_ENCODER));
        assertThrows(IllegalArgumentException.class, () -> enc.encode(new Rect(1.0, 2.0)));
    }

    @Test
    void discriminateThrowsForDuplicateVariant() {
        assertThrows(IllegalArgumentException.class, () -> discriminate("type",
                variant(Circle.class, "circle", CIRCLE_ENCODER),
                variant(Circle.class, "disc",   CIRCLE_ENCODER)));
    }

    @Test
    void discriminateThrowsForDuplicateTag() {
        // Two distinct classes sharing one tag would emit a non-unique discriminator
        // that the tag-keyed decoder side cannot round-trip.
        assertThrows(IllegalArgumentException.class, () -> discriminate("type",
                variant(Circle.class, "shape", CIRCLE_ENCODER),
                variant(Rect.class,   "shape", RECT_ENCODER)));
    }

    @Test
    void discriminateTagIsAuthoritativeOverVariantOutput() {
        // A variant encoder that (incorrectly) also emits the discriminator key.
        Encoder<Circle, Map<String, @Nullable Object>> rogue = object(
                property("type",   c -> "WRONG",   string()),
                property("radius", Circle::radius, double_()));
        Encoder<Shape, Map<String, @Nullable Object>> enc =
                discriminate("type", variant(Circle.class, "circle", rogue));

        var out = enc.encode(new Circle(1.0));
        assertEquals("circle", out.get("type"));
        assertEquals(1.0, out.get("radius"));
    }
}
