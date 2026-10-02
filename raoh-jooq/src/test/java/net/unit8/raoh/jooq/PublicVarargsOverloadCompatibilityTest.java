package net.unit8.raoh.jooq;

import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.decode.Decoders;
import net.unit8.raoh.testing.PublicVarargsOverloads;
import net.unit8.raoh.testing.PublicVarargsOverloads.Allowed;
import net.unit8.raoh.testing.PublicVarargsOverloads.Signature;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/** No public method of raoh-jooq can have its varargs calls taken over by a method of its name. */
class PublicVarargsOverloadCompatibilityTest {

    /** The classes a user imports on demand together with JooqRecordDecoders. */
    private static final List<List<Class<?>>> STATIC_IMPORT_GROUPS = List.of(List.of(JooqRecordDecoders.class, Decoders.class));

    @Test
    void noMethodCanTakeOverAVarargsCall() {
        PublicVarargsOverloads.assertNone(JooqRecordDecoders.class, STATIC_IMPORT_GROUPS,
                new Allowed(Signature.of(JooqRecordDecoders.class, "discriminate", String.class, Decoders.Variant[].class),
                        Signature.of(JooqRecordDecoders.class, "discriminate", String.class, Map.class),
                        "under the @NullMarked contract a Variant is never null, and as a record it is never a Map"),
                // Across the classes imported on demand together.
                new Allowed(Signature.of(Decoders.class, "discriminate", String.class, Decoder.class, Decoders.Variant[].class),
                        Signature.of(JooqRecordDecoders.class, "discriminate", String.class, Decoders.Variant[].class),
                        "under the @NullMarked contract no Variant is null, and a Variant is a record that is not a Decoder, so no call fits both"),
                new Allowed(Signature.of(Decoders.class, "discriminate", String.class, Decoder.class, Map.class),
                        Signature.of(JooqRecordDecoders.class, "discriminate", String.class, Decoders.Variant[].class),
                        "under the @NullMarked contract no Variant is null, and a Variant is a record that is not a Decoder, so no call fits both"),
                new Allowed(Signature.of(Decoders.class, "discriminate", String.class, Decoder.class, Decoders.Variant[].class),
                        Signature.of(JooqRecordDecoders.class, "discriminate", String.class, Map.class),
                        "only an argument that is both a Decoder and a Map fits both, and the fixed-arity Map form, there before the varargs form was added beside it in 0.6.0, has taken such a call before and since"));
    }
}
