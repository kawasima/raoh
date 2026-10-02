package net.unit8.raoh.json;

import net.unit8.raoh.decode.Decoders;
import net.unit8.raoh.testing.PublicVarargsOverloads;
import net.unit8.raoh.testing.PublicVarargsOverloads.Allowed;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/** No public method of raoh-json can have its varargs calls taken over by a method of its name. */
class PublicVarargsOverloadCompatibilityTest {

    @Test
    void noFixedArityMethodCanTakeOverAVarargsCall() {
        PublicVarargsOverloads.assertNone(JsonDecoders.class,
                new Allowed(JsonDecoders.class, "discriminate",
                        List.of(String.class, Decoders.Variant[].class),
                        List.of(String.class, Map.class),
                        "a Variant is a record, so never a Map"));
    }
}
