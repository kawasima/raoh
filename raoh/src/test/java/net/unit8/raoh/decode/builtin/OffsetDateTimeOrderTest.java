package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.Err;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.MessageKeys;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.ObjectDecoders;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;

import static net.unit8.raoh.decode.ObjectDecoders.string;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Offset date-times are compared by instant alone, and remain different values: {@code 09:00Z} and
 * {@code 10:00+01:00} are the same instant, so neither is before the other (Raoh Specification
 * R000867, R000868).
 */
class OffsetDateTimeOrderTest {

    private static final OffsetDateTime TEN_PLUS_ONE = OffsetDateTime.parse("2024-01-01T10:00+01:00");
    private static final OffsetDateTime NINE_Z = OffsetDateTime.parse("2024-01-01T09:00Z");

    private static void assertOutOfRange(Result<?> result, String messageKey) {
        var issue = assertInstanceOf(Err.class, result).issues().asList().getFirst();
        assertEquals(ErrorCodes.OUT_OF_RANGE, issue.code());
        assertEquals(messageKey, issue.messageKey());
    }

    @Test
    void beforeRejectsTheSameInstantAtAnotherOffset() {
        // R000867
        var result = string().offsetDateTime().before(TEN_PLUS_ONE).decode("2024-01-01T09:00Z", Path.ROOT);

        assertOutOfRange(result, MessageKeys.OUT_OF_RANGE_BEFORE);
        assertEquals(Map.of("actual", NINE_Z, "before", TEN_PLUS_ONE),
                assertInstanceOf(Err.class, result).issues().asList().getFirst().meta());
    }

    @Test
    void afterRejectsTheSameInstantAtAnotherOffset() {
        assertOutOfRange(string().offsetDateTime().after(TEN_PLUS_ONE).decode("2024-01-01T09:00Z", Path.ROOT),
                MessageKeys.OUT_OF_RANGE_AFTER);
    }

    @Test
    void aOnePointBetweenAcceptsTheSameInstantAndKeepsItsOffset() {
        // R000868
        var result = string().offsetDateTime().between(TEN_PLUS_ONE, TEN_PLUS_ONE).decode("2024-01-01T09:00Z", Path.ROOT);

        var value = (OffsetDateTime) assertInstanceOf(Ok.class, result).value();
        assertEquals(NINE_Z, value);
    }

    @Test
    void betweensBoundsAreOrderedByInstant() {
        // compareTo orders 10:00+01:00 after 09:00Z, by local date-time; as instants they are equal.
        string().offsetDateTime().between(TEN_PLUS_ONE, NINE_Z);
        string().offsetDateTime().between(NINE_Z, TEN_PLUS_ONE);
        assertThrows(IllegalArgumentException.class,
                () -> string().offsetDateTime().between(TEN_PLUS_ONE, OffsetDateTime.parse("2024-01-01T08:59Z")));
    }

    @Test
    void theOrderIsKeptThroughRefine() {
        var dec = string().offsetDateTime().refine(v -> true, "never", "never").before(TEN_PLUS_ONE);

        assertOutOfRange(dec.decode("2024-01-01T09:00Z", Path.ROOT), MessageKeys.OUT_OF_RANGE_BEFORE);
    }

    @Test
    void theObjectDecoderComparesByInstantToo() {
        assertOutOfRange(ObjectDecoders.offsetDateTime().before(TEN_PLUS_ONE).decode(NINE_Z, Path.ROOT),
                MessageKeys.OUT_OF_RANGE_BEFORE);
        assertOutOfRange(ObjectDecoders.offsetDateTime().before(TEN_PLUS_ONE).decode("2024-01-01T09:00Z", Path.ROOT),
                MessageKeys.OUT_OF_RANGE_BEFORE);
    }

    @Test
    void anOrderMustBeGiven() {
        assertThrows(NullPointerException.class,
                () -> new TemporalDecoder<Object, OffsetDateTime>((in, path) -> Result.ok(NINE_Z), null));
    }
}
