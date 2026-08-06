package net.unit8.raoh;

import net.unit8.raoh.decode.ObjectDecoders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static net.unit8.raoh.decode.ObjectDecoders.date;
import static net.unit8.raoh.decode.ObjectDecoders.decimal;
import static net.unit8.raoh.decode.ObjectDecoders.double_;
import static net.unit8.raoh.decode.ObjectDecoders.float_;
import static net.unit8.raoh.decode.ObjectDecoders.int_;
import static net.unit8.raoh.decode.ObjectDecoders.long_;
import static net.unit8.raoh.decode.ObjectDecoders.string;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * End-to-end checks that each built-in constraint resolves to a message describing
 * that constraint, in both bundled locales.
 *
 * <p>Covers the two failure classes behind #123 and #124: a template asking for a
 * placeholder the metadata never supplied, and two different constraints sharing one
 * template because {@code code} alone could not tell them apart.
 */
class ConstraintMessageResolutionTest {

    private static final ResourceBundleMessageResolver BUNDLE =
            new ResourceBundleMessageResolver("net.unit8.raoh.messages");

    private static Issue firstIssue(Result<?> result) {
        return ((Err<?>) result).issues().asList().get(0);
    }

    private static String en(Result<?> result) {
        return firstIssue(result).resolve(BUNDLE, Locale.ENGLISH).message();
    }

    private static String ja(Result<?> result) {
        return firstIssue(result).resolve(BUNDLE, Locale.JAPANESE).message();
    }

    // --- Every numeric bound shape, spelled out ---

    /** Each numeric bound shape resolves to a message naming only the bounds it has. */
    @Test
    void resolvesEveryNumericShapeInEnglish() {
        assertEquals("must be at least 0", en(int_().min(0).decode(-1, Path.ROOT)));
        assertEquals("must be at most 100", en(int_().max(100).decode(101, Path.ROOT)));
        assertEquals("must be between 0 and 100", en(int_().range(0, 100).decode(101, Path.ROOT)));
        assertEquals("must be positive", en(int_().positive().decode(0, Path.ROOT)));
        assertEquals("must be negative", en(int_().negative().decode(0, Path.ROOT)));
        assertEquals("must be non-negative", en(int_().nonNegative().decode(-1, Path.ROOT)));
        assertEquals("must be non-positive", en(int_().nonPositive().decode(1, Path.ROOT)));
    }

    /** The Japanese bundle describes the same shapes without inventing a missing bound. */
    @Test
    void resolvesEveryNumericShapeInJapanese() {
        assertEquals("0以上で入力してください", ja(int_().min(0).decode(-1, Path.ROOT)));
        assertEquals("100以下で入力してください", ja(int_().max(100).decode(101, Path.ROOT)));
        assertEquals("0以上100以下で入力してください", ja(int_().range(0, 100).decode(101, Path.ROOT)));
        assertEquals("正の値で入力してください", ja(int_().positive().decode(0, Path.ROOT)));
        assertEquals("負の値で入力してください", ja(int_().negative().decode(0, Path.ROOT)));
        assertEquals("0以上で入力してください", ja(int_().nonNegative().decode(-1, Path.ROOT)));
        assertEquals("0以下で入力してください", ja(int_().nonPositive().decode(1, Path.ROOT)));
    }

    // --- Temporal bounds ---

    /** Temporal bounds carry {@code before}/{@code after}/{@code from}/{@code to}, not min/max. */
    @Test
    void resolvesTemporalShapes() {
        var past = LocalDate.of(2020, 1, 1);
        var future = LocalDate.of(2030, 1, 1);

        assertEquals("must be before 2020-01-01", en(date().before(past).decode("2025-06-01", Path.ROOT)));
        assertEquals("must be after 2030-01-01", en(date().after(future).decode("2025-06-01", Path.ROOT)));
        assertEquals("must be between 2030-01-01 and 2030-01-01",
                en(date().between(future, future).decode("2025-06-01", Path.ROOT)));

        assertEquals("2020-01-01より前で入力してください", ja(date().before(past).decode("2025-06-01", Path.ROOT)));
        assertEquals("2030-01-01より後で入力してください", ja(date().after(future).decode("2025-06-01", Path.ROOT)));
        assertEquals("2030-01-01から2030-01-01の範囲で入力してください",
                ja(date().between(future, future).decode("2025-06-01", Path.ROOT)));
    }

    // --- Which key each constraint emits, on every numeric type ---

    /**
     * A constraint and the key naming it.
     *
     * @param label        what emitted the issue, for test names
     * @param result       the failing decode result
     * @param expectedKey  the key that constraint must report
     */
    record KeyCase(String label, Result<?> result, String expectedKey) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static KeyCase k(String label, Result<?> result, String expectedKey) {
        return new KeyCase(label, result, expectedKey);
    }

    /**
     * Every bounded constraint on every type, paired with the key it must emit.
     *
     * <p>Spelling the key out per type is what pins {@code positive()} apart from
     * {@code nonNegative()} on {@code BigDecimal}, {@code double} and {@code float}: those
     * three put the same bound in metadata for both, so the key is the only thing that
     * distinguishes them, and asserting on the rendered message alone would not notice the
     * two being swapped.
     *
     * @return one case per constraint and type
     */
    static List<KeyCase> everyConstraintKey() {
        var past = LocalDate.of(2020, 1, 1);
        var future = LocalDate.of(2030, 1, 1);
        return List.of(
                k("int.min", int_().min(0).decode(-1, Path.ROOT), MessageKeys.OUT_OF_RANGE_MINIMUM),
                k("int.max", int_().max(0).decode(1, Path.ROOT), MessageKeys.OUT_OF_RANGE_MAXIMUM),
                k("int.range", int_().range(0, 1).decode(2, Path.ROOT), MessageKeys.OUT_OF_RANGE_RANGE),
                k("int.positive", int_().positive().decode(0, Path.ROOT), MessageKeys.OUT_OF_RANGE_POSITIVE),
                k("int.negative", int_().negative().decode(0, Path.ROOT), MessageKeys.OUT_OF_RANGE_NEGATIVE),
                k("int.nonNegative", int_().nonNegative().decode(-1, Path.ROOT), MessageKeys.OUT_OF_RANGE_NON_NEGATIVE),
                k("int.nonPositive", int_().nonPositive().decode(1, Path.ROOT), MessageKeys.OUT_OF_RANGE_NON_POSITIVE),

                k("long.min", long_().min(0).decode(-1L, Path.ROOT), MessageKeys.OUT_OF_RANGE_MINIMUM),
                k("long.max", long_().max(0).decode(1L, Path.ROOT), MessageKeys.OUT_OF_RANGE_MAXIMUM),
                k("long.range", long_().range(0, 1).decode(2L, Path.ROOT), MessageKeys.OUT_OF_RANGE_RANGE),
                k("long.positive", long_().positive().decode(0L, Path.ROOT), MessageKeys.OUT_OF_RANGE_POSITIVE),
                k("long.negative", long_().negative().decode(0L, Path.ROOT), MessageKeys.OUT_OF_RANGE_NEGATIVE),
                k("long.nonNegative", long_().nonNegative().decode(-1L, Path.ROOT), MessageKeys.OUT_OF_RANGE_NON_NEGATIVE),
                k("long.nonPositive", long_().nonPositive().decode(1L, Path.ROOT), MessageKeys.OUT_OF_RANGE_NON_POSITIVE),

                k("double.min", double_().min(0).decode(-1.0, Path.ROOT), MessageKeys.OUT_OF_RANGE_MINIMUM),
                k("double.max", double_().max(0).decode(1.0, Path.ROOT), MessageKeys.OUT_OF_RANGE_MAXIMUM),
                k("double.range", double_().range(0, 1).decode(2.0, Path.ROOT), MessageKeys.OUT_OF_RANGE_RANGE),
                k("double.positive", double_().positive().decode(0.0, Path.ROOT), MessageKeys.OUT_OF_RANGE_POSITIVE),
                k("double.negative", double_().negative().decode(0.0, Path.ROOT), MessageKeys.OUT_OF_RANGE_NEGATIVE),
                k("double.nonNegative", double_().nonNegative().decode(-1.0, Path.ROOT), MessageKeys.OUT_OF_RANGE_NON_NEGATIVE),
                k("double.nonPositive", double_().nonPositive().decode(1.0, Path.ROOT), MessageKeys.OUT_OF_RANGE_NON_POSITIVE),

                k("float.min", float_().min(0).decode(-1.0f, Path.ROOT), MessageKeys.OUT_OF_RANGE_MINIMUM),
                k("float.max", float_().max(0).decode(1.0f, Path.ROOT), MessageKeys.OUT_OF_RANGE_MAXIMUM),
                k("float.range", float_().range(0, 1).decode(2.0f, Path.ROOT), MessageKeys.OUT_OF_RANGE_RANGE),
                k("float.positive", float_().positive().decode(0.0f, Path.ROOT), MessageKeys.OUT_OF_RANGE_POSITIVE),
                k("float.negative", float_().negative().decode(0.0f, Path.ROOT), MessageKeys.OUT_OF_RANGE_NEGATIVE),
                k("float.nonNegative", float_().nonNegative().decode(-1.0f, Path.ROOT), MessageKeys.OUT_OF_RANGE_NON_NEGATIVE),
                k("float.nonPositive", float_().nonPositive().decode(1.0f, Path.ROOT), MessageKeys.OUT_OF_RANGE_NON_POSITIVE),

                k("decimal.min", decimal().min(BigDecimal.ZERO).decode(BigDecimal.valueOf(-1), Path.ROOT), MessageKeys.OUT_OF_RANGE_MINIMUM),
                k("decimal.max", decimal().max(BigDecimal.ZERO).decode(BigDecimal.ONE, Path.ROOT), MessageKeys.OUT_OF_RANGE_MAXIMUM),
                k("decimal.range", decimal().range(BigDecimal.ZERO, BigDecimal.ONE).decode(BigDecimal.TEN, Path.ROOT), MessageKeys.OUT_OF_RANGE_RANGE),
                k("decimal.positive", decimal().positive().decode(BigDecimal.ZERO, Path.ROOT), MessageKeys.OUT_OF_RANGE_POSITIVE),
                k("decimal.negative", decimal().negative().decode(BigDecimal.ZERO, Path.ROOT), MessageKeys.OUT_OF_RANGE_NEGATIVE),
                k("decimal.nonNegative", decimal().nonNegative().decode(BigDecimal.valueOf(-1), Path.ROOT), MessageKeys.OUT_OF_RANGE_NON_NEGATIVE),
                k("decimal.nonPositive", decimal().nonPositive().decode(BigDecimal.ONE, Path.ROOT), MessageKeys.OUT_OF_RANGE_NON_POSITIVE),

                k("date.before", date().before(past).decode("2025-06-01", Path.ROOT), MessageKeys.OUT_OF_RANGE_BEFORE),
                k("date.after", date().after(future).decode("2025-06-01", Path.ROOT), MessageKeys.OUT_OF_RANGE_AFTER),
                k("date.between", date().between(future, future).decode("2025-06-01", Path.ROOT), MessageKeys.OUT_OF_RANGE_BETWEEN),

                k("list.nonempty", ObjectDecoders.list(string()).nonempty().decode(List.of(), Path.ROOT), MessageKeys.TOO_SMALL_NONEMPTY),
                k("map.nonempty", ObjectDecoders.map(string()).nonempty().decode(java.util.Map.of(), Path.ROOT), MessageKeys.TOO_SMALL_NONEMPTY)
        );
    }

    /**
     * Asserts that each constraint reports the key that names it, and that its code is
     * unchanged.
     *
     * @param testCase the constraint and the key it must emit
     */
    @ParameterizedTest
    @MethodSource("everyConstraintKey")
    void constraintEmitsItsOwnMessageKey(KeyCase testCase) {
        var issue = firstIssue(testCase.result());
        assertEquals(testCase.expectedKey(), issue.messageKey());
        assertEquals(testCase.expectedKey().substring(0, testCase.expectedKey().indexOf('.')), issue.code());
    }

    // --- Constraints that used to collide ---

    /**
     * The four zero-relative bounds, on the three types whose metadata cannot tell them
     * apart. {@code BigDecimal}, {@code double} and {@code float} all record the bound as
     * zero for both {@code positive()} and {@code nonNegative()}, so before the message key
     * existed there was nothing left to distinguish them by (#124).
     *
     * @return one case per constraint and type, with the wording expected in each locale
     */
    static List<Object[]> zeroRelativeBounds() {
        return List.of(
                new Object[]{"decimal", decimal().positive().decode(BigDecimal.ZERO, Path.ROOT), "must be positive", "正の値で入力してください"},
                new Object[]{"decimal", decimal().negative().decode(BigDecimal.ZERO, Path.ROOT), "must be negative", "負の値で入力してください"},
                new Object[]{"decimal", decimal().nonNegative().decode(BigDecimal.valueOf(-1), Path.ROOT), "must be non-negative", "0以上で入力してください"},
                new Object[]{"decimal", decimal().nonPositive().decode(BigDecimal.ONE, Path.ROOT), "must be non-positive", "0以下で入力してください"},
                new Object[]{"double", double_().positive().decode(0.0, Path.ROOT), "must be positive", "正の値で入力してください"},
                new Object[]{"double", double_().negative().decode(0.0, Path.ROOT), "must be negative", "負の値で入力してください"},
                new Object[]{"double", double_().nonNegative().decode(-1.0, Path.ROOT), "must be non-negative", "0以上で入力してください"},
                new Object[]{"double", double_().nonPositive().decode(1.0, Path.ROOT), "must be non-positive", "0以下で入力してください"},
                new Object[]{"float", float_().positive().decode(0.0f, Path.ROOT), "must be positive", "正の値で入力してください"},
                new Object[]{"float", float_().negative().decode(0.0f, Path.ROOT), "must be negative", "負の値で入力してください"},
                new Object[]{"float", float_().nonNegative().decode(-1.0f, Path.ROOT), "must be non-negative", "0以上で入力してください"},
                new Object[]{"float", float_().nonPositive().decode(1.0f, Path.ROOT), "must be non-positive", "0以下で入力してください"});
    }

    /**
     * Asserts the exact wording of each zero-relative bound in both locales.
     *
     * @param type       the numeric type, for the test name
     * @param result     the failing decode result
     * @param expectedEn the English wording
     * @param expectedJa the Japanese wording
     */
    @ParameterizedTest(name = "{0}: {2}")
    @MethodSource("zeroRelativeBounds")
    void zeroRelativeBoundsAreDescribedApart(String type, Result<?> result, String expectedEn, String expectedJa) {
        assertEquals(expectedEn, en(result));
        assertEquals(expectedJa, ja(result));
    }

    /** {@code positive()} and {@code nonNegative()} both bound below but differ on zero (#124). */
    @Test
    void positiveIsDistinctFromNonNegative() {
        for (var pair : List.of(
                List.of(int_().positive().decode(-1, Path.ROOT), int_().nonNegative().decode(-1, Path.ROOT)),
                List.of(decimal().positive().decode(BigDecimal.valueOf(-1), Path.ROOT),
                        decimal().nonNegative().decode(BigDecimal.valueOf(-1), Path.ROOT)),
                List.of(double_().positive().decode(-1.0, Path.ROOT), double_().nonNegative().decode(-1.0, Path.ROOT)),
                List.of(float_().positive().decode(-1.0f, Path.ROOT), float_().nonNegative().decode(-1.0f, Path.ROOT)))) {
            assertNotEquals(en(pair.get(0)), en(pair.get(1)));
            assertNotEquals(ja(pair.get(0)), ja(pair.get(1)));
        }
    }

    /** {@code nonempty()} and {@code minSize(1)} share code and metadata but not meaning. */
    @Test
    void nonemptyIsDistinctFromMinSizeOne() {
        var nonempty = ObjectDecoders.list(string()).nonempty().decode(List.of(), Path.ROOT);
        var minSize = ObjectDecoders.list(string()).minSize(1).decode(List.of(), Path.ROOT);

        assertEquals("must not be empty", en(nonempty));
        assertEquals("must have at least 1 elements", en(minSize));
        assertEquals("空にはできません", ja(nonempty));
        assertNotEquals(ja(nonempty), ja(minSize));
    }

    /** A map decoder's {@code nonempty()} is described the same way a list's is. */
    @Test
    void mapNonemptyResolvesToNotEmpty() {
        var empty = ObjectDecoders.map(string()).nonempty().decode(java.util.Map.of(), Path.ROOT);
        assertEquals("must not be empty", en(empty));
        assertEquals("空にはできません", ja(empty));
    }

    // --- No placeholder reaches a reader, for any built-in constraint ---

    /**
     * Every bound on every numeric type, so the sweep below covers all five decoders
     * rather than only the {@code int} ones spelled out above.
     *
     * @return failing results, one per constraint and numeric type
     */
    static List<Result<?>> everyBoundedConstraint() {
        var past = LocalDate.of(2020, 1, 1);
        var future = LocalDate.of(2030, 1, 1);
        return List.of(
                int_().min(0).decode(-1, Path.ROOT),
                int_().max(0).decode(1, Path.ROOT),
                int_().range(0, 1).decode(2, Path.ROOT),
                int_().positive().decode(0, Path.ROOT),
                int_().negative().decode(0, Path.ROOT),
                int_().nonNegative().decode(-1, Path.ROOT),
                int_().nonPositive().decode(1, Path.ROOT),

                long_().min(0).decode(-1L, Path.ROOT),
                long_().max(0).decode(1L, Path.ROOT),
                long_().range(0, 1).decode(2L, Path.ROOT),
                long_().positive().decode(0L, Path.ROOT),
                long_().negative().decode(0L, Path.ROOT),
                long_().nonNegative().decode(-1L, Path.ROOT),
                long_().nonPositive().decode(1L, Path.ROOT),

                double_().min(0).decode(-1.0, Path.ROOT),
                double_().max(0).decode(1.0, Path.ROOT),
                double_().range(0, 1).decode(2.0, Path.ROOT),
                double_().positive().decode(0.0, Path.ROOT),
                double_().negative().decode(0.0, Path.ROOT),
                double_().nonNegative().decode(-1.0, Path.ROOT),
                double_().nonPositive().decode(1.0, Path.ROOT),

                float_().min(0).decode(-1.0f, Path.ROOT),
                float_().max(0).decode(1.0f, Path.ROOT),
                float_().range(0, 1).decode(2.0f, Path.ROOT),
                float_().positive().decode(0.0f, Path.ROOT),
                float_().negative().decode(0.0f, Path.ROOT),
                float_().nonNegative().decode(-1.0f, Path.ROOT),
                float_().nonPositive().decode(1.0f, Path.ROOT),

                decimal().min(BigDecimal.ZERO).decode(BigDecimal.valueOf(-1), Path.ROOT),
                decimal().max(BigDecimal.ZERO).decode(BigDecimal.ONE, Path.ROOT),
                decimal().range(BigDecimal.ZERO, BigDecimal.ONE).decode(BigDecimal.TEN, Path.ROOT),
                decimal().positive().decode(BigDecimal.ZERO, Path.ROOT),
                decimal().negative().decode(BigDecimal.ZERO, Path.ROOT),
                decimal().nonNegative().decode(BigDecimal.valueOf(-1), Path.ROOT),
                decimal().nonPositive().decode(BigDecimal.ONE, Path.ROOT),

                date().before(past).decode("2025-06-01", Path.ROOT),
                date().after(future).decode("2025-06-01", Path.ROOT),
                date().between(future, future).decode("2025-06-01", Path.ROOT),

                ObjectDecoders.list(string()).nonempty().decode(List.of(), Path.ROOT),
                ObjectDecoders.map(string()).nonempty().decode(java.util.Map.of(), Path.ROOT)
        );
    }

    /**
     * Asserts that no resolved message leaves an unfilled {@code {placeholder}} behind,
     * in either bundled locale.
     *
     * @param result a failing decode result carrying exactly one issue
     */
    @ParameterizedTest
    @MethodSource("everyBoundedConstraint")
    void noPlaceholderSurvivesResolution(Result<?> result) {
        for (var locale : List.of(Locale.ENGLISH, Locale.JAPANESE)) {
            var message = firstIssue(result).resolve(BUNDLE, locale).message();
            assertFalse(message.contains("{"),
                    "unresolved placeholder in " + locale + ": " + message);
        }
    }
}
