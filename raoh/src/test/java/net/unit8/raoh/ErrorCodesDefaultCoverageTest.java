package net.unit8.raoh;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies that {@link MessageResolver#DEFAULT} covers every constant in
 * {@link ErrorCodes} and does not fall through to the {@code default} branch.
 *
 * <p>This test enumerates all {@code public static final String} fields of
 * {@link ErrorCodes} via reflection, so adding a new constant to {@link ErrorCodes}
 * automatically surfaces a test failure here if {@link MessageResolver#DEFAULT}
 * is not updated at the same time.
 */
class ErrorCodesDefaultCoverageTest {

    /**
     * Returns all error code string values declared in {@link ErrorCodes}.
     *
     * @return a stream of error code strings
     */
    static Stream<String> allErrorCodes() {
        return Arrays.stream(ErrorCodes.class.getDeclaredFields())
                .filter(f -> Modifier.isPublic(f.getModifiers())
                        && Modifier.isStatic(f.getModifiers())
                        && Modifier.isFinal(f.getModifiers())
                        && f.getType() == String.class)
                .map(f -> {
                    try {
                        return (String) f.get(null);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * Asserts that {@link MessageResolver#DEFAULT} does not fall through to its
     * {@code default} branch (which returns {@code "validation failed: <code>"})
     * for any built-in error code.
     *
     * @param code the error code to test
     */
    /** Shared meta map with enough keys to satisfy any built-in placeholder. */
    private static final Map<String, Object> FULL_META = Map.of(
            "min", 1, "max", 10, "expected", "foo", "actual", "bar",
            "divisor", 2, "maxScale", 3, "length", 5, "size", 5
    );

    /**
     * Asserts that {@link MessageResolver#DEFAULT} does not fall through to its
     * {@code default} branch (which returns {@code "validation failed: <code>"})
     * for any built-in error code.
     *
     * @param code the error code to test
     */
    @ParameterizedTest(name = "DEFAULT covers error code: {0}")
    @MethodSource("allErrorCodes")
    void defaultResolverCoversCode(String code) {
        String message = MessageResolver.DEFAULT.resolve(code, FULL_META);
        assertFalse(
                message.startsWith("validation failed:"),
                "MessageResolver.DEFAULT fell through to default branch for code: " + code
        );
    }

    /**
     * Asserts that the built-in {@code net.unit8.raoh.messages} bundle contains
     * a key for every constant in {@link ErrorCodes}.
     *
     * <p>Uses {@link ResourceBundleMessageResolver} with a sentinel meta map and
     * checks that the resolved message does not match the {@link MessageResolver#DEFAULT}
     * fallback result — which would indicate the bundle key is absent and resolution
     * silently fell back to the default English message instead of the bundle entry.
     *
     * <p>This test fails when a new {@link ErrorCodes} constant is added without
     * a corresponding entry in {@code messages.properties}.
     *
     * @param code the error code to test
     */
    @ParameterizedTest(name = "messages.properties covers error code: {0}")
    @MethodSource("allErrorCodes")
    void defaultBundleCoversCode(String code) {
        var resolver = new ResourceBundleMessageResolver("net.unit8.raoh.messages");
        String fromBundle = resolver.resolve(code, FULL_META, Locale.ENGLISH);
        assertFalse(
                fromBundle.startsWith("validation failed:"),
                "messages.properties missing key for error code: " + code
        );
        // Load the bundle directly to confirm the key is actually present,
        // not just falling back to MessageResolver.DEFAULT.
        var bundle = java.util.ResourceBundle.getBundle("net.unit8.raoh.messages", Locale.ENGLISH);
        org.junit.jupiter.api.Assertions.assertTrue(
                bundle.containsKey(MessageResolver.KEY_PREFIX + code),
                "messages.properties missing key 'raoh." + code + "'"
        );
    }
}
