package net.unit8.raoh;

/**
 * String constants for the message keys produced by built-in Raoh decoders.
 *
 * <p>An {@link ErrorCodes} constant classifies a failure for a program to branch on.
 * A message key identifies which wording describes it. The two are separate because
 * several distinct constraints share one code: {@code positive()}, {@code min()} and
 * {@code before()} all report {@link ErrorCodes#OUT_OF_RANGE}, but no single sentence
 * describes all three, and their metadata does not carry the same keys.
 *
 * <p>Every key is the error code it refines, a dot, and a qualifier. A resolver that
 * knows nothing about a given key can fall back to the plain code:
 *
 * <pre>{@code
 * raoh.out_of_range.positive=must be positive
 * raoh.out_of_range.minimum=must be at least {min}
 * raoh.out_of_range=must be between {min} and {max}
 * }</pre>
 *
 * <p>Issues built without an explicit key use the code as their key, so
 * {@link Issue#messageKey()} is always populated.
 */
public final class MessageKeys {

    private MessageKeys() {}

    // --- Numeric and temporal bounds (ErrorCodes.OUT_OF_RANGE) ---

    /** Lower bound from {@code min(n)}. Supplies {@code min}. */
    public static final String OUT_OF_RANGE_MINIMUM = "out_of_range.minimum";

    /** Upper bound from {@code max(n)}. Supplies {@code max}. */
    public static final String OUT_OF_RANGE_MAXIMUM = "out_of_range.maximum";

    /** Both bounds from {@code range(min, max)}. Supplies {@code min} and {@code max}. */
    public static final String OUT_OF_RANGE_RANGE = "out_of_range.range";

    /** Strictly greater than zero, from {@code positive()}. */
    public static final String OUT_OF_RANGE_POSITIVE = "out_of_range.positive";

    /** Strictly less than zero, from {@code negative()}. */
    public static final String OUT_OF_RANGE_NEGATIVE = "out_of_range.negative";

    /** Zero or greater, from {@code nonNegative()}. */
    public static final String OUT_OF_RANGE_NON_NEGATIVE = "out_of_range.non_negative";

    /** Zero or less, from {@code nonPositive()}. */
    public static final String OUT_OF_RANGE_NON_POSITIVE = "out_of_range.non_positive";

    /** Exclusive upper bound from {@code before(bound)}. Supplies {@code before}. */
    public static final String OUT_OF_RANGE_BEFORE = "out_of_range.before";

    /** Exclusive lower bound from {@code after(bound)}. Supplies {@code after}. */
    public static final String OUT_OF_RANGE_AFTER = "out_of_range.after";

    /** Inclusive bounds from {@code between(from, to)}. Supplies {@code from} and {@code to}. */
    public static final String OUT_OF_RANGE_BETWEEN = "out_of_range.between";

    // --- Collection size (ErrorCodes.TOO_SMALL) ---

    /**
     * Emptiness rejected by {@code nonempty()}, as distinct from the minimum-size bound
     * {@code minSize(1)} reports with the same code and the same metadata.
     */
    public static final String TOO_SMALL_NONEMPTY = "too_small.nonempty";
}
