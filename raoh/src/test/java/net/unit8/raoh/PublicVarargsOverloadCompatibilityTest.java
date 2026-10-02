package net.unit8.raoh;

import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.decode.Decoders;
import net.unit8.raoh.decode.builtin.DoubleDecoder;
import net.unit8.raoh.decode.builtin.FloatDecoder;
import net.unit8.raoh.decode.builtin.IntDecoder;
import net.unit8.raoh.decode.builtin.LongDecoder;
import net.unit8.raoh.decode.builtin.StringDecoder;
import net.unit8.raoh.decode.map.MapDecoders;
import net.unit8.raoh.testing.PublicVarargsOverloads;
import net.unit8.raoh.testing.PublicVarargsOverloads.Allowed;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** No public method of raoh can have its varargs calls taken over by a method of its name. */
class PublicVarargsOverloadCompatibilityTest {

    /**
     * {@code oneOf}'s values are of a final class that is not a {@code Collection}, so no call of
     * the varargs form fits the {@code Collection} one, whatever conversion applies.
     *
     * @param owner the decoder class
     * @param value the class of its values
     * @return the allowed pair
     */
    private static Allowed oneOf(Class<?> owner, Class<?> value) {
        return new Allowed(owner, "oneOf", List.of(value.arrayType()), List.of(Collection.class, String.class),
                "a " + value.getSimpleName() + " is never a Collection");
    }

    @Test
    void noFixedArityMethodCanTakeOverAVarargsCall() {
        PublicVarargsOverloads.assertNone(Result.class,
                oneOf(StringDecoder.class, String.class),
                oneOf(IntDecoder.class, Integer.class),
                oneOf(LongDecoder.class, Long.class),
                oneOf(FloatDecoder.class, Float.class),
                oneOf(DoubleDecoder.class, Double.class),
                new Allowed(Decoders.class, "discriminate",
                        List.of(String.class, Decoder.class, Decoders.Variant[].class),
                        List.of(String.class, Decoder.class, Map.class),
                        "a Variant is a record, so never a Map"),
                new Allowed(MapDecoders.class, "discriminate",
                        List.of(String.class, Decoders.Variant[].class),
                        List.of(String.class, Map.class),
                        "a Variant is a record, so never a Map"));
    }
}
