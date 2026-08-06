package net.unit8.raoh;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.stream.Stream;

/**
 * Verifies that every constant in {@link MessageKeys} has a template in both bundled
 * locales, and that each key is namespaced under the {@link ErrorCodes} constant it
 * refines.
 *
 * <p>The prefix check is what keeps {@code code} and {@code messageKey} from drifting
 * apart: a message key names a specific constraint, but the machine-readable code it
 * belongs to must stay recoverable from it.
 */
class MessageKeysBundleCoverageTest {

    /**
     * Returns all message key string values declared in {@link MessageKeys}.
     *
     * @return a stream of message key strings
     */
    static Stream<String> allMessageKeys() {
        return Arrays.stream(MessageKeys.class.getDeclaredFields())
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

    private static Stream<String> allErrorCodes() {
        return ErrorCodesDefaultCoverageTest.allErrorCodes();
    }

    /**
     * Asserts that a message key is the name of an error code followed by a qualifier,
     * so {@code messageKey} always identifies which {@code code} it refines.
     *
     * @param key the message key to test
     */
    @ParameterizedTest(name = "message key is namespaced under an error code: {0}")
    @MethodSource("allMessageKeys")
    void keyIsNamespacedUnderAnErrorCode(String key) {
        int dot = key.indexOf('.');
        Assertions.assertTrue(dot > 0, "message key must be '<code>.<qualifier>': " + key);
        String code = key.substring(0, dot);
        Assertions.assertTrue(
                allErrorCodes().anyMatch(code::equals),
                "message key '" + key + "' is not namespaced under any ErrorCodes constant"
        );
    }

    /**
     * Asserts that the English bundle contains a template for every message key.
     *
     * @param key the message key to test
     */
    @ParameterizedTest(name = "messages.properties covers message key: {0}")
    @MethodSource("allMessageKeys")
    void defaultBundleCoversKey(String key) {
        var noFallback = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
        var bundle = ResourceBundle.getBundle("net.unit8.raoh.messages", Locale.ENGLISH, noFallback);
        Assertions.assertTrue(
                bundle.containsKey(MessageResolver.KEY_PREFIX + key),
                "messages.properties missing key 'raoh." + key + "'"
        );
    }

    private static final Properties JA_PROPS = loadProperties("/net/unit8/raoh/messages_ja.properties");

    private static Properties loadProperties(String resource) {
        var props = new Properties();
        try (var is = MessageKeysBundleCoverageTest.class.getResourceAsStream(resource)) {
            if (is == null) throw new RuntimeException(resource + " not found on classpath");
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return props;
    }

    /**
     * Asserts that the Japanese bundle contains a template for every message key.
     *
     * <p>Loads the properties file directly rather than through {@link ResourceBundle}
     * so parent-bundle fallback cannot mask a missing Japanese translation.
     *
     * @param key the message key to test
     */
    @ParameterizedTest(name = "messages_ja.properties covers message key: {0}")
    @MethodSource("allMessageKeys")
    void japaneseBundleCoversKey(String key) {
        Assertions.assertTrue(
                JA_PROPS.containsKey(MessageResolver.KEY_PREFIX + key),
                "messages_ja.properties missing key 'raoh." + key + "'"
        );
    }
}
