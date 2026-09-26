package net.unit8.raoh.json;

import net.unit8.raoh.Err;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static net.unit8.raoh.json.JsonDecoders.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JSON type-mismatch issues carry the node type as {@code actual} metadata; it must not follow
 * the JVM default locale (#136). In {@code tr-TR}, a default-locale lower-casing turns
 * {@code STRING} into {@code "strıng"}.
 */
@ResourceLock(Resources.LOCALE)
class DefaultLocaleIndependenceTest {

    static final List<Locale> LOCALES = List.of(
            Locale.forLanguageTag("tr-TR"),
            Locale.forLanguageTag("th-TH-u-nu-thai"),
            Locale.forLanguageTag("ar-EG"));

    static final JsonNode STRING_NODE = JsonNodeFactory.instance.stringNode("x");

    static Map<String, Supplier<Result<?>>> cases() {
        return Map.of(
                "int_ on a string node", () -> int_().decode(STRING_NODE, Path.ROOT),
                "bool on a string node", () -> bool().decode(STRING_NODE, Path.ROOT),
                "int_ min", () -> int_().min(1000).decode(JsonNodeFactory.instance.numberNode(5), Path.ROOT));
    }

    @Test
    void decoderResultsDoNotDependOnTheDefaultLocale() {
        // assertAll reports every case that differs, not only the first.
        assertAll(cases().entrySet().stream().flatMap(c -> {
            var baseline = withDefaultLocale(Locale.ROOT, c.getValue());
            return LOCALES.stream().map(locale -> () -> assertEquals(baseline,
                    withDefaultLocale(locale, c.getValue()), c.getKey() + " under " + locale.toLanguageTag()));
        }));
    }

    @Test
    void typeMismatchReportsTheRootLowerCasedNodeType() {
        var result = withDefaultLocale(Locale.ROOT, () -> int_().decode(STRING_NODE, Path.ROOT));
        var err = assertInstanceOf(Err.class, result);
        assertEquals("string", err.issues().asList().getFirst().meta().get("actual"));
    }

    static <T> T withDefaultLocale(Locale locale, Supplier<T> body) {
        var saved = Locale.getDefault();
        var savedFormat = Locale.getDefault(Locale.Category.FORMAT);
        var savedDisplay = Locale.getDefault(Locale.Category.DISPLAY);
        Locale.setDefault(locale);
        try {
            return body.get();
        } finally {
            Locale.setDefault(saved);
            Locale.setDefault(Locale.Category.FORMAT, savedFormat);
            Locale.setDefault(Locale.Category.DISPLAY, savedDisplay);
        }
    }
}
