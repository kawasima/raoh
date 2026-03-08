package net.unit8.raoh;

import java.util.Locale;
import java.util.Map;

/**
 * Resolves error codes into human-readable messages.
 *
 * <p>Implementations can provide localized or customized messages for each error code.
 * Use {@link Issues#resolve(MessageResolver)} to apply a resolver to all issues.
 *
 * <p>For locale-aware resolution, override
 * {@link #resolve(String, Map, Locale)} and call
 * {@link Issues#resolve(MessageResolver, Locale)} at resolution time.
 */
@FunctionalInterface
public interface MessageResolver {

    /**
     * The prefix prepended to error codes when building resource bundle / message-source keys.
     * Both {@link ResourceBundleMessageResolver} and any custom resolver should use this constant
     * so the key format is defined in one place.
     */
    String KEY_PREFIX = "raoh.";

    /**
     * Resolves an error code into a human-readable message.
     *
     * @param code the error code
     * @param meta additional metadata associated with the issue
     * @return the resolved message
     */
    String resolve(String code, Map<String, Object> meta);

    /**
     * Resolves an error code into a locale-aware human-readable message.
     *
     * <p>The default implementation ignores the locale and delegates to
     * {@link #resolve(String, Map)}, so existing implementations remain
     * fully backward-compatible.
     *
     * @param code   the error code
     * @param meta   additional metadata associated with the issue
     * @param locale the target locale for the message
     * @return the resolved message
     */
    default String resolve(String code, Map<String, Object> meta, Locale locale) {
        return resolve(code, meta);
    }

    /**
     * Replaces named placeholders in a message template with values from the meta map.
     *
     * <p>Each {@code {key}} occurrence in {@code template} is replaced by the string
     * representation of the corresponding value in {@code meta}.
     * For example, a template {@code "must be at least {min} characters"} with
     * {@code meta = {"min": 3}} produces {@code "must be at least 3 characters"}.
     *
     * @param template the message template containing {@code {key}} placeholders
     * @param meta     the metadata map supplying placeholder values
     * @return the template with all placeholders replaced
     */
    static String interpolate(String template, Map<String, Object> meta) {
        for (var entry : meta.entrySet()) {
            template = template.replace(
                    "{" + entry.getKey() + "}",
                    String.valueOf(entry.getValue()));
        }
        return template;
    }

    /** A default resolver that provides English messages for all built-in error codes. */
    MessageResolver DEFAULT = (code, meta) -> switch (code) {
        case ErrorCodes.REQUIRED        -> "is required";
        case ErrorCodes.TOO_SHORT       -> "must be at least %s characters".formatted(meta.get("min"));
        case ErrorCodes.TOO_LONG        -> "must be at most %s characters".formatted(meta.get("max"));
        case ErrorCodes.OUT_OF_RANGE    -> {
            var min = meta.get("min");
            var max = meta.get("max");
            if (min != null && max != null) yield "must be between %s and %s".formatted(min, max);
            if (min != null) yield "must be at least %s".formatted(min);
            if (max != null) yield "must be at most %s".formatted(max);
            yield "out of range";
        }
        case ErrorCodes.INVALID_LENGTH  -> "must be exactly %s characters".formatted(meta.get("expected"));
        case ErrorCodes.INVALID_FORMAT  -> "invalid format";
        case ErrorCodes.TYPE_MISMATCH   -> "expected %s".formatted(meta.get("expected"));
        case ErrorCodes.INVALID_VALUE   -> "must be %s".formatted(meta.get("expected"));
        case ErrorCodes.TOO_SMALL       -> "must have at least %s elements".formatted(meta.get("min"));
        case ErrorCodes.TOO_BIG         -> "must have at most %s elements".formatted(meta.get("max"));
        case ErrorCodes.INVALID_SIZE    -> "must have exactly %s elements".formatted(meta.get("expected"));
        case ErrorCodes.NOT_MULTIPLE_OF -> "must be a multiple of %s".formatted(meta.get("divisor"));
        case ErrorCodes.MISSING_ELEMENT  -> "must contain %s".formatted(meta.get("expected"));
        case ErrorCodes.DUPLICATE_ELEMENT -> "must not contain duplicates";
        case ErrorCodes.NOT_ALLOWED     -> "must be one of %s".formatted(meta.get("allowed"));
        case ErrorCodes.ONE_OF_FAILED   -> "no variant matched";
        case ErrorCodes.UNKNOWN_FIELD   -> "unknown field";
        case ErrorCodes.INVALID_SCALE   -> "too many decimal places (max %s)".formatted(meta.get("maxScale"));
        case ErrorCodes.MISSING_FIELD   -> "field is missing";
        default -> "validation failed: " + code;
    };
}
