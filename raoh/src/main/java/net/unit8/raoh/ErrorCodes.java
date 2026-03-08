package net.unit8.raoh;

/**
 * String constants for all built-in error codes produced by Raoh decoders.
 *
 * <p>Use these constants instead of bare string literals when writing custom
 * {@link MessageResolver} implementations or inspecting {@link Issue#code()}:
 *
 * <pre>{@code
 * MessageResolver resolver = (code, meta) -> switch (code) {
 *     case ErrorCodes.REQUIRED      -> "このフィールドは必須です";
 *     case ErrorCodes.TOO_SHORT     -> "%s文字以上入力してください".formatted(meta.get("min"));
 *     default                       -> MessageResolver.DEFAULT.resolve(code, meta);
 * };
 * }</pre>
 */
public final class ErrorCodes {

    private ErrorCodes() {}

    // --- Presence ---

    /** Value is absent or null when a non-null value was expected. */
    public static final String REQUIRED = "required";

    // --- String length ---

    /** String is shorter than the minimum allowed length. */
    public static final String TOO_SHORT = "too_short";

    /** String is longer than the maximum allowed length. */
    public static final String TOO_LONG = "too_long";

    /** String length does not match the required exact length. */
    public static final String INVALID_LENGTH = "invalid_length";

    // --- Numeric range ---

    /** Numeric value is outside the allowed range. */
    public static final String OUT_OF_RANGE = "out_of_range";

    /** Numeric value is not a multiple of the required divisor. */
    public static final String NOT_MULTIPLE_OF = "not_multiple_of";

    /** Decimal value has more fractional digits than allowed. */
    public static final String INVALID_SCALE = "invalid_scale";

    // --- Collection size ---

    /** Collection has fewer elements than the minimum allowed count. */
    public static final String TOO_SMALL = "too_small";

    /** Collection has more elements than the maximum allowed count. */
    public static final String TOO_BIG = "too_big";

    /** Collection size does not match the required exact size. */
    public static final String INVALID_SIZE = "invalid_size";

    // --- Value ---

    /** Value does not satisfy a required value constraint (e.g., must be {@code true} or {@code false}). */
    public static final String INVALID_VALUE = "invalid_value";

    // --- Format ---

    /** Value does not match the required format (e.g., email, URL, UUID). */
    public static final String INVALID_FORMAT = "invalid_format";

    // --- Type ---

    /** Value is of the wrong type. */
    public static final String TYPE_MISMATCH = "type_mismatch";

    // --- Object shape ---

    /** Input contains a field that is not declared in the known-fields list. */
    public static final String UNKNOWN_FIELD = "unknown_field";

    // --- Union ---

    /** None of the candidates in a {@code oneOf} decoder matched the input. */
    public static final String ONE_OF_FAILED = "one_of_failed";

    // --- jOOQ ---

    /** A required field was not found in the jOOQ Record. */
    public static final String MISSING_FIELD = "missing_field";
}
