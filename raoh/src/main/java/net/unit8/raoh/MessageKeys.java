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

    // --- String formats and parsed values (ErrorCodes.INVALID_FORMAT) ---
    //
    // pattern() keeps the plain ErrorCodes.INVALID_FORMAT key: its message is already the
    // generic "invalid format", and the pattern it carries is not something a reader can act on.

    /** Email address from {@code email()}. */
    public static final String INVALID_FORMAT_EMAIL = "invalid_format.email";

    /** Absolute http or https URL from {@code url()}. */
    public static final String INVALID_FORMAT_URL = "invalid_format.url";

    /** URI of any scheme from {@code uri()}; the accepted text is described on {@code StringDecoder#uri(String)}. */
    public static final String INVALID_FORMAT_URI = "invalid_format.uri";

    /** UUID from {@code uuid()}. */
    public static final String INVALID_FORMAT_UUID = "invalid_format.uuid";

    /** IPv4 or IPv6 address from {@code ip()}. */
    public static final String INVALID_FORMAT_IP = "invalid_format.ip";

    /** IPv4 address from {@code ipv4()}. */
    public static final String INVALID_FORMAT_IPV4 = "invalid_format.ipv4";

    /** IPv6 address from {@code ipv6()}. */
    public static final String INVALID_FORMAT_IPV6 = "invalid_format.ipv6";

    /** ULID from {@code ulid()}. */
    public static final String INVALID_FORMAT_ULID = "invalid_format.ulid";

    /** CUID from {@code cuid()}. */
    public static final String INVALID_FORMAT_CUID = "invalid_format.cuid";

    /** Required prefix from {@code startsWith(prefix)}. Supplies {@code prefix}. */
    public static final String INVALID_FORMAT_STARTS_WITH = "invalid_format.starts_with";

    /** Required suffix from {@code endsWith(suffix)}. Supplies {@code suffix}. */
    public static final String INVALID_FORMAT_ENDS_WITH = "invalid_format.ends_with";

    /** Required substring from {@code includes(substring)}. Supplies {@code substring}. */
    public static final String INVALID_FORMAT_INCLUDES = "invalid_format.includes";

    /** Enum constant name from {@code enumOf(cls)}. Supplies {@code allowed}. */
    public static final String INVALID_FORMAT_ENUM = "invalid_format.enum";

    /** Exact string from {@code literal(expected)}. Supplies {@code expected}. */
    public static final String INVALID_FORMAT_LITERAL = "invalid_format.literal";

    /** ISO-8601 instant from {@code iso8601()}. */
    public static final String INVALID_FORMAT_INSTANT = "invalid_format.instant";

    /** ISO-8601 local date from {@code date()}. */
    public static final String INVALID_FORMAT_DATE = "invalid_format.date";

    /** ISO-8601 local time from {@code time()}. */
    public static final String INVALID_FORMAT_TIME = "invalid_format.time";

    /** ISO-8601 local date-time from {@code dateTime()}. */
    public static final String INVALID_FORMAT_DATE_TIME = "invalid_format.date_time";

    /** ISO-8601 offset date-time from {@code offsetDateTime()}. */
    public static final String INVALID_FORMAT_OFFSET_DATE_TIME = "invalid_format.offset_date_time";

    // --- Input shape (ErrorCodes.TYPE_MISMATCH) ---

    /**
     * A map with a key that is not a non-null {@code String}, from {@code ObjectDecoders.map()} and
     * {@code MapDecoders.nested()}. Supplies {@code expected} ({@code "object with string keys"}),
     * so the plain {@code type_mismatch} template still names the requirement, and {@code actual},
     * the offending key's type ({@code "Integer"}, {@code "null"}).
     */
    public static final String TYPE_MISMATCH_STRING_KEYS = "type_mismatch.string_keys";

    /**
     * A number the numeric decoders accept by type but whose value the target type cannot hold,
     * such as {@code 5000000000L} for {@code int_()} or {@code 1e40} for {@code float_()}.
     * Supplies {@code expected}, the target ({@code "integer"}, {@code "long"}, {@code "double"},
     * {@code "float"}).
     */
    public static final String TYPE_MISMATCH_NUMERIC_RANGE = "type_mismatch.numeric_range";
}
