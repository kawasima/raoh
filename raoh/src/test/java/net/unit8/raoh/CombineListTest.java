package net.unit8.raoh;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.unit8.raoh.map.MapDecoders.*;
import static net.unit8.raoh.ObjectDecoders.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Decoders#combine(List)} — the fallback combiner for 17 or more fields.
 */
class CombineListTest {

    /**
     * A 17-field record used to verify high-arity combining.
     * The {@code Object[]} constructor enables {@code MyRow::new} as a method reference
     * with {@link Decoders#combine(List)}.
     */
    record Row17(
            String f1, String f2, String f3, String f4, String f5,
            String f6, String f7, String f8, String f9, String f10,
            String f11, String f12, String f13, String f14, String f15,
            String f16, String f17) {
        Row17(Object[] args) {
            this(
                (String) args[0],  (String) args[1],  (String) args[2],
                (String) args[3],  (String) args[4],  (String) args[5],
                (String) args[6],  (String) args[7],  (String) args[8],
                (String) args[9],  (String) args[10], (String) args[11],
                (String) args[12], (String) args[13], (String) args[14],
                (String) args[15], (String) args[16]
            );
        }
    }

    private static final List<Decoder<Map<String, Object>, ?>> DECODERS_17 = List.of(
            field("f1",  string()), field("f2",  string()), field("f3",  string()),
            field("f4",  string()), field("f5",  string()), field("f6",  string()),
            field("f7",  string()), field("f8",  string()), field("f9",  string()),
            field("f10", string()), field("f11", string()), field("f12", string()),
            field("f13", string()), field("f14", string()), field("f15", string()),
            field("f16", string()), field("f17", string())
    );

    private static Map<String, Object> allValid() {
        return Map.ofEntries(
            Map.entry("f1",  "a"), Map.entry("f2",  "b"), Map.entry("f3",  "c"),
            Map.entry("f4",  "d"), Map.entry("f5",  "e"), Map.entry("f6",  "f"),
            Map.entry("f7",  "g"), Map.entry("f8",  "h"), Map.entry("f9",  "i"),
            Map.entry("f10", "j"), Map.entry("f11", "k"), Map.entry("f12", "l"),
            Map.entry("f13", "m"), Map.entry("f14", "n"), Map.entry("f15", "o"),
            Map.entry("f16", "p"), Map.entry("f17", "q")
        );
    }

    @Test
    void allFieldsSucceed() {
        var decoder = Decoders.combine(DECODERS_17).apply(Row17::new);
        var result  = decoder.decode(allValid());
        assertInstanceOf(Ok.class, result);
        var row = ((Ok<Row17>) result).value();
        assertEquals("a", row.f1());
        assertEquals("q", row.f17());
    }

    @Test
    void singleFieldFails() {
        var input = new java.util.HashMap<>(allValid());
        input.remove("f5");  // missing → required error

        var decoder = Decoders.combine(DECODERS_17).apply(Row17::new);
        var result  = decoder.decode(input);
        assertInstanceOf(Err.class, result);
        var issues  = ((Err<Row17>) result).issues().asList();
        assertEquals(1, issues.size());
        assertEquals(ErrorCodes.REQUIRED, issues.getFirst().code());
    }

    @Test
    void multipleFieldsFailAccumulated() {
        var input = new java.util.HashMap<>(allValid());
        input.remove("f1");
        input.remove("f17");

        var decoder = Decoders.combine(DECODERS_17).apply(Row17::new);
        var result  = decoder.decode(input);
        assertInstanceOf(Err.class, result);
        var issues  = ((Err<Row17>) result).issues().asList();
        assertEquals(2, issues.size());
    }

    @Test
    void constructorReferenceViaObjectArrayCtor() {
        // Verify that MyRow::new works as a method reference when
        // an Object[]-accepting constructor is defined on the record.
        var decoder = Decoders.combine(DECODERS_17).apply(Row17::new);
        assertNotNull(decoder);
        assertInstanceOf(Ok.class, decoder.decode(allValid()));
    }
}
