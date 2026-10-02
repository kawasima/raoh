package net.unit8.raoh.testing;

import net.unit8.raoh.jooq.JooqRecordDecoders;
import net.unit8.raoh.decode.Decoders;
import net.unit8.raoh.testing.PublicVarargsOverloads.Allowed;
import net.unit8.raoh.testing.PublicVarargsOverloads.Signature;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** No public method of raoh-jooq can have its varargs calls taken over by a method of its name. */
class RaohJooqApiTest {

    /** The classes Raoh's documentation imports on demand together that include one of this module's. */
    private static final List<List<Class<?>>> STATIC_IMPORT_GROUPS = DocumentedImports.ofModule(JooqRecordDecoders.class,
            DocumentedImports.groups(DocumentedImports.documents(Path.of(".."))));

    @Test
    void theDocumentedImportsCanBeCalledUnqualified() {
        DocumentedImports.assertCallable(STATIC_IMPORT_GROUPS);
    }

    @Test
    void noMethodCanTakeOverAVarargsCall() {
        PublicVarargsOverloads.assertNone(JooqRecordDecoders.class, STATIC_IMPORT_GROUPS,
                new Allowed(Signature.of(JooqRecordDecoders.class, "discriminate", String.class, Decoders.Variant[].class),
                        Signature.of(JooqRecordDecoders.class, "discriminate", String.class, Map.class),
                        "under the @NullMarked contract a Variant is never null, and as a record it is never a Map"));
    }
}
