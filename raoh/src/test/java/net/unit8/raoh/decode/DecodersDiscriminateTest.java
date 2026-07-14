package net.unit8.raoh.decode;

import net.unit8.raoh.Err;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Issue;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;
import org.junit.jupiter.api.Test;

import static net.unit8.raoh.decode.Decoders.discriminate;
import static net.unit8.raoh.decode.Decoders.variant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Direct unit tests for the typed {@code Decoders.discriminate(String, Decoder, Variant...)} and
 * {@code Decoders.variant(...)}. Uses a computed tag decoder (the part before {@code ':'}) to
 * exercise the core capability that facades and the computed-tag proposal build on.
 */
class DecodersDiscriminateTest {

    sealed interface Animal permits Dog, Cat {}
    record Dog(String name) implements Animal {}
    record Cat(String name) implements Animal {}

    // A computed tag: everything before the first ':'. Not a plain field lookup.
    static final Decoder<String, String> TAG = (in, path) -> Result.ok(in.split(":", 2)[0]);
    static final Decoder<String, Dog> DOG = (in, path) -> Result.ok(new Dog(in.split(":", 2)[1]));
    static final Decoder<String, Cat> CAT = (in, path) -> Result.ok(new Cat(in.split(":", 2)[1]));

    @Test
    void typedDiscriminateDispatchesCastFree() {
        // The Dog/Cat variants need no up-cast; T is pinned to Animal by the target type.
        Decoder<String, Animal> dec = discriminate("type", TAG,
                variant("dog", DOG),
                variant("cat", CAT));
        assertEquals(new Dog("rex"), assertOk(dec.decode("dog:rex")));
        assertEquals(new Cat("mia"), assertOk(dec.decode("cat:mia")));
    }

    @Test
    void unknownTagIsNotAllowed() {
        Decoder<String, Animal> dec = discriminate("type", TAG, variant("dog", DOG));
        var issue = assertErr(dec.decode("fish:nemo"));
        assertEquals(ErrorCodes.NOT_ALLOWED, issue.code());
        assertEquals("/type", issue.path().toJsonPointer());
    }

    @Test
    void zeroVariantsFailsEveryTag() {
        // Explicit decision: zero variants is allowed and always fails NOT_ALLOWED, matching
        // discriminate(fieldName, tagDec, Map.of()).
        Decoder<String, Animal> dec = discriminate("type", TAG);
        assertEquals(ErrorCodes.NOT_ALLOWED, assertErr(dec.decode("dog:rex")).code());
    }

    @Test
    void duplicateTagThrowsAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> discriminate("type", TAG, variant("dog", DOG), variant("dog", CAT)));
    }

    @Test
    void variantRejectsNullTagAndDecoder() {
        assertThrows(NullPointerException.class, () -> variant(null, DOG));
        assertThrows(NullPointerException.class, () -> variant("dog", null));
    }

    // --- helpers ---

    private static <T> T assertOk(Result<T> r) {
        return switch (r) {
            case Ok<T> ok -> ok.value();
            case Err<T> err -> {
                fail("expected Ok, got " + err);
                yield null;
            }
        };
    }

    private static Issue assertErr(Result<?> r) {
        return switch (r) {
            case Ok<?> ok -> {
                fail("expected Err, got " + ok);
                yield null;
            }
            case Err<?> err -> err.issues().asList().getFirst();
        };
    }
}
