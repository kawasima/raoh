package net.unit8.raoh;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks what a resolver does when it cannot produce a better message than the one
 * already stored on the {@link Issue}, and that the pre-{@code messageKey} API keeps
 * working unchanged.
 */
class MessageResolverFallbackTest {

    private static final ResourceBundleMessageResolver BUNDLE =
            new ResourceBundleMessageResolver("net.unit8.raoh.messages");

    // --- Declining to resolve ---

    /**
     * A template needing a placeholder the metadata lacks is not applied at all;
     * the message stored at decode time survives.
     */
    @Test
    void keepsStoredMessageWhenAPlaceholderWouldBeUnfilled() {
        var issue = new Issue(Path.ROOT, ErrorCodes.OUT_OF_RANGE, ErrorCodes.OUT_OF_RANGE,
                "must be at least 0", Map.of("min", 0, "actual", -1), false);

        assertEquals("must be at least 0", issue.resolve(BUNDLE, Locale.ENGLISH).message());
        assertEquals("must be at least 0", issue.resolve(BUNDLE, Locale.JAPANESE).message());
    }

    /** An unknown key leaves the stored message in place rather than inventing one. */
    @Test
    void keepsStoredMessageWhenKeyIsMissing() {
        var issue = Issue.of(Path.ROOT, "no_such_code", "something specific went wrong");

        assertEquals("something specific went wrong", issue.resolve(BUNDLE, Locale.ENGLISH).message());
    }

    /** A message key with no template falls back to the plain code's template. */
    @Test
    void fallsBackFromMessageKeyToCode() {
        var issue = new Issue(Path.ROOT, ErrorCodes.OUT_OF_RANGE, "out_of_range.not_in_any_bundle",
                "stored", Map.of("min", 0, "max", 10), false);

        assertEquals("must be between 0 and 10", issue.resolve(BUNDLE, Locale.ENGLISH).message());
    }

    /** A caller-supplied message is never replaced by a resolver. */
    @Test
    void customMessageIsNotResolved() {
        var issue = new Issue(Path.ROOT, ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_MINIMUM,
                "年齢を確認してください", Map.of("min", 0), true);

        assertEquals("年齢を確認してください", issue.resolve(BUNDLE, Locale.ENGLISH).message());
    }

    // --- Interpolation is single-pass ---

    /**
     * A brace sequence arriving inside a metadata value is left alone. Substituting
     * repeatedly over the whole string would treat it as a placeholder of its own.
     */
    @Test
    void metaValuesContainingBracesAreNotSubstitutedAgain() {
        var result = MessageResolver.interpolate(
                "must be between {min} and {max}",
                Map.of("min", "{max}", "max", 5));

        assertEquals("must be between {max} and 5", result);
    }

    /** A placeholder with no matching metadata key is left as written. */
    @Test
    void unknownPlaceholderIsLeftAlone() {
        assertEquals("must be at least {min}",
                MessageResolver.interpolate("must be at least {min}", Map.of("actual", 3)));
    }

    // --- Compatibility with the pre-messageKey API ---

    /** A two-argument lambda resolver still resolves through {@link Issue#resolve}. */
    @Test
    void twoArgumentLambdaResolverStillWorks() {
        MessageResolver legacy = (code, meta) -> "resolved:" + code;
        var issue = Issue.of(Path.ROOT, ErrorCodes.REQUIRED, "is required");

        assertEquals("resolved:required", issue.resolve(legacy).message());
        assertEquals("resolved:required", issue.resolve(legacy, Locale.JAPANESE).message());
    }

    /** The five-argument constructor defaults {@code messageKey} to {@code code}. */
    @Test
    void legacyConstructorDefaultsMessageKeyToCode() {
        var issue = new Issue(Path.ROOT, ErrorCodes.REQUIRED, "is required", Map.of(), false);

        assertEquals(ErrorCodes.REQUIRED, issue.messageKey());
        assertEquals(ErrorCodes.REQUIRED, Issue.of(Path.ROOT, ErrorCodes.REQUIRED, "x").messageKey());
    }

    /**
     * The code-and-meta resolver entry point has no stored message to fall back to,
     * so it keeps falling back to {@link MessageResolver#DEFAULT}.
     */
    @Test
    void codeOnlyEntryPointStillFallsBackToDefault() {
        assertEquals("must be at least 0",
                BUNDLE.resolve(ErrorCodes.OUT_OF_RANGE, Map.of("min", 0), Locale.ENGLISH));
        assertEquals("unknown field",
                BUNDLE.resolve(ErrorCodes.UNKNOWN_FIELD, Map.of(), Locale.ENGLISH));
    }

    /** {@link MessageResolver#DEFAULT} describes temporal bounds instead of giving up. */
    @Test
    void defaultResolverDescribesTemporalBounds() {
        var before = new Issue(Path.ROOT, ErrorCodes.OUT_OF_RANGE, MessageKeys.OUT_OF_RANGE_BEFORE,
                "must be before 2020-01-01", Map.of("before", "2020-01-01"), false);

        assertEquals("must be before 2020-01-01", before.resolve(MessageResolver.DEFAULT).message());
    }
}
