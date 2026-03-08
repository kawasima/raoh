package net.unit8.raoh;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies that {@link MessageResolver#DEFAULT} and the built-in
 * {@code net.unit8.raoh.messages} bundle cover every constant in {@link ErrorCodes}.
 *
 * <p>Both tests enumerate all {@code public static final String} fields of
 * {@link ErrorCodes} via reflection, so adding a new constant to {@link ErrorCodes}
 * automatically surfaces a test failure if {@link MessageResolver#DEFAULT} or
 * {@code messages.properties} is not updated at the same time.
 */
class ErrorCodesDefaultCoverageTest {

    /** Shared meta map with enough keys to satisfy any built-in placeholder. */
    private static final Map<String, Object> FULL_META = Map.ofEntries(
            Map.entry("min", 1), Map.entry("max", 10),
            Map.entry("expected", "foo"), Map.entry("actual", "bar"),
            Map.entry("divisor", 2), Map.entry("maxScale", 3),
            Map.entry("length", 5), Map.entry("size", 5),
            Map.entry("allowed", List.of("a", "b")),
            Map.entry("missing", List.of("x")),
            Map.entry("duplicates", List.of("a"))
    );

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
     * <p>The bundle is loaded with the same no-fallback {@link ResourceBundle.Control}
     * used by {@link ResourceBundleMessageResolver} so the test behaves identically
     * on JVMs whose default locale differs from {@link Locale#ENGLISH}.
     *
     * <p>This test fails when a new {@link ErrorCodes} constant is added without
     * a corresponding entry in {@code messages.properties}.
     *
     * @param code the error code to test
     */
    @ParameterizedTest(name = "messages.properties covers error code: {0}")
    @MethodSource("allErrorCodes")
    void defaultBundleCoversCode(String code) {
        var noFallback = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
        var bundle = ResourceBundle.getBundle("net.unit8.raoh.messages", Locale.ENGLISH, noFallback);
        Assertions.assertTrue(
                bundle.containsKey(MessageResolver.KEY_PREFIX + code),
                "messages.properties missing key 'raoh." + code + "'"
        );
    }
}
