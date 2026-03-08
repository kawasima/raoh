package net.unit8.raoh;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Modifier;
import java.util.Arrays;
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
    @ParameterizedTest(name = "DEFAULT covers error code: {0}")
    @MethodSource("allErrorCodes")
    void defaultResolverCoversCode(String code) {
        String message = MessageResolver.DEFAULT.resolve(code, Map.of(
                "min", 1, "max", 10, "expected", "foo", "actual", "bar",
                "divisor", 2, "maxScale", 3, "length", 5, "size", 5
        ));
        assertFalse(
                message.startsWith("validation failed:"),
                "MessageResolver.DEFAULT fell through to default branch for code: " + code
        );
    }
}
