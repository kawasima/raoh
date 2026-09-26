package net.unit8.raoh;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static net.unit8.raoh.decode.ObjectDecoders.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A decoder's result is a function of its input and configuration, never of the JVM default
 * locale (#136). Each case is run under {@link Locale#ROOT} to get a baseline, then under
 * locales whose case mapping or digits differ from it; the whole {@link Result} — value, or
 * every issue's path, code, message key, message, meta and custom flag — must be identical.
 *
 * <p>This checks observable results, not call sites. The build's forbidden-API check is what
 * keeps a new locale-sensitive call from being added to a decoder that no case here covers.
 */
@ResourceLock(Resources.LOCALE)
class DefaultLocaleIndependenceTest {

    /** Turkish dotless i: "I" lower-cases to "ı" and "i" upper-cases to "İ". */
    static final Locale TURKISH = Locale.forLanguageTag("tr-TR");
    /** Thai digits: {@code %d} of 1000 formats as "๑๐๐๐". */
    static final Locale THAI_DIGITS = Locale.forLanguageTag("th-TH-u-nu-thai");
    /** Arabic-Indic digits: {@code %d} of 1000 formats as "١٠٠٠". */
    static final Locale ARABIC_EGYPT = Locale.forLanguageTag("ar-EG");

    static final List<Locale> LOCALES = List.of(TURKISH, THAI_DIGITS, ARABIC_EGYPT);

    enum Honorific { TITLE, MISTER }

    static Map<String, Supplier<Object>> cases() {
        var cases = new LinkedHashMap<String, Supplier<Object>>();
        cases.put("enumOf matches a lower-case name", () -> enumOf(Honorific.class).decode("title", Path.ROOT));
        cases.put("enumOf lists allowed names on a miss", () -> enumOf(Honorific.class).decode("bogus", Path.ROOT));
        cases.put("toLowerCase", () -> string().toLowerCase().decode("TITLE", Path.ROOT));
        cases.put("toUpperCase", () -> string().toUpperCase().decode("title", Path.ROOT));
        cases.put("int min", () -> int_().min(1000).decode(5, Path.ROOT));
        cases.put("int max", () -> int_().max(1000).decode(5000, Path.ROOT));
        cases.put("long min", () -> long_().min(1000L).decode(5L, Path.ROOT));
        cases.put("decimal min", () -> decimal().min(new BigDecimal("1000")).decode(new BigDecimal("5"), Path.ROOT));
        cases.put("string minLength", () -> string().minLength(1000).decode("short", Path.ROOT));
        cases.put("string maxLength", () -> string().maxLength(2).decode("long", Path.ROOT));
        cases.put("list minSize", () -> list(int_()).minSize(1000).decode(List.of(1), Path.ROOT));
        cases.put("MessageResolver.DEFAULT", () -> MessageResolver.DEFAULT.resolve(
                ErrorCodes.TOO_SHORT, Map.of("min", 1000)));
        return cases;
    }

    @Test
    void theseLocalesReallyDifferFromRoot() {
        // Without this, a JDK lacking the locale data would make every comparison below pass
        // for the wrong reason.
        var baseline = withDefaultLocale(Locale.ROOT, DefaultLocaleIndependenceTest::localeProbe);
        for (var locale : LOCALES) {
            assertNotEquals(baseline, withDefaultLocale(locale, DefaultLocaleIndependenceTest::localeProbe),
                    locale.toLanguageTag());
        }
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
    void baselineIsTheExpectedRootResult() {
        // Pins the ROOT results themselves, so that the comparison above cannot pass by every
        // locale producing the same wrong answer.
        var baseline = withDefaultLocale(Locale.ROOT, () -> List.of(
                enumOf(Honorific.class).decode("title", Path.ROOT),
                string().toUpperCase().decode("title", Path.ROOT),
                int_().min(1000).decode(5, Path.ROOT)));
        assertEquals(Result.ok(Honorific.TITLE), baseline.get(0));
        assertEquals(Result.ok("TITLE"), baseline.get(1));
        var err = assertInstanceOf(Err.class, baseline.get(2));
        assertEquals("must be at least 1000", err.issues().asList().getFirst().message());
    }

    private static List<String> localeProbe() {
        return List.of("TITLE".toLowerCase(), "title".toUpperCase(), "%d".formatted(1000));
    }

    /**
     * Runs {@code body} with the JVM default locale set to {@code locale} for every category,
     * restoring all of them afterwards.
     */
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
