package net.unit8.raoh.testing;

import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.decode.Decoders;
import net.unit8.raoh.decode.ObjectDecoders;
import net.unit8.raoh.decode.builtin.DoubleDecoder;
import net.unit8.raoh.decode.builtin.FloatDecoder;
import net.unit8.raoh.decode.builtin.IntDecoder;
import net.unit8.raoh.decode.builtin.LongDecoder;
import net.unit8.raoh.decode.builtin.StringDecoder;
import net.unit8.raoh.decode.map.MapDecoders;
import net.unit8.raoh.encode.MapEncoders;
import net.unit8.raoh.encode.ObjectEncoders;
import net.unit8.raoh.testing.PublicVarargsOverloads.Allowed;
import net.unit8.raoh.testing.PublicVarargsOverloads.Signature;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** No public method of raoh can have its varargs calls taken over by a method of its name. */
class RaohApiTest {

    /** The classes Raoh's documentation imports on demand together that include one of this module's. */
    private static final List<List<Class<?>>> STATIC_IMPORT_GROUPS = DocumentedImports.ofModule(Result.class,
            DocumentedImports.groups(DocumentedImports.documents(Path.of(".."))));

    /**
     * {@code oneOf}'s values are of a final class that is not a {@code Collection}, so no call of
     * the varargs form that passes non-null values, as the {@code @NullMarked} contract requires,
     * fits the {@code Collection} one, whatever conversion applies.
     *
     * @param owner the decoder class
     * @param value the class of its values
     * @return the allowed pair
     */
    private static Allowed oneOf(Class<?> owner, Class<?> value) {
        return new Allowed(Signature.of(owner, "oneOf", value.arrayType()),
                Signature.of(owner, "oneOf", Collection.class, String.class),
                "under the @NullMarked contract a " + value.getSimpleName()
                        + " is never null, and as a final class that is not a Collection it is never one");
    }

    @Test
    void theDocumentedImportsCanBeCalledUnqualified() {
        DocumentedImports.assertCallable(STATIC_IMPORT_GROUPS);
    }

    @Test
    void noMethodCanTakeOverAVarargsCall() {
        PublicVarargsOverloads.assertNone(Result.class, STATIC_IMPORT_GROUPS,
                oneOf(StringDecoder.class, String.class),
                oneOf(IntDecoder.class, Integer.class),
                oneOf(LongDecoder.class, Long.class),
                oneOf(FloatDecoder.class, Float.class),
                oneOf(DoubleDecoder.class, Double.class),
                new Allowed(Signature.of(Decoders.class, "discriminate", String.class, Decoder.class, Decoders.Variant[].class),
                        Signature.of(Decoders.class, "discriminate", String.class, Decoder.class, Map.class),
                        "under the @NullMarked contract a Variant is never null, and as a record it is never a Map"),
                new Allowed(Signature.of(MapDecoders.class, "discriminate", String.class, Decoders.Variant[].class),
                        Signature.of(MapDecoders.class, "discriminate", String.class, Map.class),
                        "under the @NullMarked contract a Variant is never null, and as a record it is never a Map"),
                // Across the classes imported on demand together.
                new Allowed(Signature.of(Decoders.class, "discriminate", String.class, Decoder.class, Decoders.Variant[].class),
                        Signature.of(MapDecoders.class, "discriminate", String.class, Decoders.Variant[].class),
                        "under the @NullMarked contract no Variant is null, and a Variant is a record that is not a Decoder, so no call fits both"),
                new Allowed(Signature.of(Decoders.class, "discriminate", String.class, Decoder.class, Map.class),
                        Signature.of(MapDecoders.class, "discriminate", String.class, Decoders.Variant[].class),
                        "under the @NullMarked contract no Variant is null, and a Variant is a record that is not a Decoder, so no call fits both"),
                new Allowed(Signature.of(Decoders.class, "discriminate", String.class, Decoder.class, Decoders.Variant[].class),
                        Signature.of(MapDecoders.class, "discriminate", String.class, Map.class),
                        "only an argument that is both a Decoder and a Map fits both, and the fixed-arity Map form, there before the varargs form was added beside it in 0.6.0, has taken such a call before and since"));
    }
}
