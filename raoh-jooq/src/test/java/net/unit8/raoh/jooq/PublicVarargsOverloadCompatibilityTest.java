package net.unit8.raoh.jooq;

import net.unit8.raoh.decode.Decoders;
import net.unit8.raoh.testing.PublicVarargsOverloads;
import net.unit8.raoh.testing.PublicVarargsOverloads.Allowed;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/** No public method of raoh-jooq can have its varargs calls taken over by a method of its name. */
class PublicVarargsOverloadCompatibilityTest {

    @Test
    void noFixedArityMethodCanTakeOverAVarargsCall() {
        PublicVarargsOverloads.assertNone(JooqRecordDecoders.class,
                new Allowed(JooqRecordDecoders.class, "discriminate",
                        List.of(String.class, Decoders.Variant[].class),
                        List.of(String.class, Map.class),
                        "a Variant is a record, so never a Map"));
    }
}
