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

    // --- Constraints that used to collide ---

    /** {@code positive()} and {@code nonNegative()} both bound below but differ on zero (#124). */
    @Test
    void positiveIsDistinctFromNonNegative() {
        var positive = int_().positive().decode(-1, Path.ROOT);
        var nonNegative = int_().nonNegative().decode(-1, Path.ROOT);

        assertNotEquals(en(positive), en(nonNegative));
        assertNotEquals(ja(positive), ja(nonNegative));
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
