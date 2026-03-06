package net.unit8.raoh;

import java.util.Map;

/**
 * Resolves error codes into human-readable messages.
 *
 * <p>Implementations can provide localized or customized messages for each error code.
 * Use {@link Issues#resolve(MessageResolver)} to apply a resolver to all issues.
 */
@FunctionalInterface
public interface MessageResolver {
    /**
     * Resolves an error code into a human-readable message.
     *
     * @param code the error code
     * @param meta additional metadata associated with the issue
     * @return the resolved message
     */
    String resolve(String code, Map<String, Object> meta);

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
        case ErrorCodes.INVALID_FORMAT  -> "invalid format";
        case ErrorCodes.TYPE_MISMATCH   -> "expected %s".formatted(meta.get("expected"));
        case ErrorCodes.TOO_SMALL       -> "must have at least %s elements".formatted(meta.get("min"));
        case ErrorCodes.TOO_BIG         -> "must have at most %s elements".formatted(meta.get("max"));
        case ErrorCodes.NOT_MULTIPLE_OF -> "must be a multiple of %s".formatted(meta.get("divisor"));
        case ErrorCodes.ONE_OF_FAILED   -> "no variant matched";
        case ErrorCodes.UNKNOWN_FIELD   -> "unknown field";
        case ErrorCodes.INVALID_SCALE   -> "too many decimal places (max %s)".formatted(meta.get("maxScale"));
        default -> "validation failed: " + code;
    };
}
