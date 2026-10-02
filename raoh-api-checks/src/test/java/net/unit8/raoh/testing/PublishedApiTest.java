package net.unit8.raoh.testing;

import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.decode.Decoders;
import net.unit8.raoh.decode.builtin.DoubleDecoder;
import net.unit8.raoh.decode.builtin.FloatDecoder;
import net.unit8.raoh.decode.builtin.IntDecoder;
import net.unit8.raoh.decode.builtin.LongDecoder;
import net.unit8.raoh.decode.builtin.StringDecoder;
import net.unit8.raoh.decode.map.MapDecoders;
import net.unit8.raoh.jooq.JooqRecordDecoders;
import net.unit8.raoh.json.JsonDecoders;
import net.unit8.raoh.testing.PublicVarargsOverloads.Allowed;
import net.unit8.raoh.testing.PublicVarargsOverloads.Signature;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The published API of raoh, raoh-json and raoh-jooq keeps every existing call's meaning when a
 * method is added, and the documentation's imports can be used as written.
 */
class PublishedApiTest {

    /** The modules whose API is checked, each by one of its classes. */
    private static final List<Class<?>> MODULES = List.of(Result.class, JsonDecoders.class, JooqRecordDecoders.class);

    /** The classes the documentation's examples import on demand together. */
    private static final List<List<Class<?>>> STATIC_IMPORT_GROUPS = DocumentedImports.load(
            DocumentedImports.groups(DocumentedImports.documents(Path.of(".."))), Result.class.getClassLoader());

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
        PublicVarargsOverloads.assertNone(MODULES, STATIC_IMPORT_GROUPS,
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
                new Allowed(Signature.of(JsonDecoders.class, "discriminate", String.class, Decoders.Variant[].class),
                        Signature.of(JsonDecoders.class, "discriminate", String.class, Map.class),
                        "under the @NullMarked contract a Variant is never null, and as a record it is never a Map"),
                new Allowed(Signature.of(JooqRecordDecoders.class, "discriminate", String.class, Decoders.Variant[].class),
                        Signature.of(JooqRecordDecoders.class, "discriminate", String.class, Map.class),
                        "under the @NullMarked contract a Variant is never null, and as a record it is never a Map"),
                // Across the classes the tutorial imports on demand together.
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
