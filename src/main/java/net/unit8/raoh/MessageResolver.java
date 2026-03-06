package net.unit8.raoh;

import java.util.Map;

@FunctionalInterface
public interface MessageResolver {
    String resolve(String code, Map<String, Object> meta);

    MessageResolver DEFAULT = (code, meta) -> switch (code) {
        case "required" -> "is required";
        case "too_short" -> "must be at least %s characters".formatted(meta.get("min"));
        case "too_long" -> "must be at most %s characters".formatted(meta.get("max"));
        case "out_of_range" -> "must be between %s and %s".formatted(meta.get("min"), meta.get("max"));
        case "invalid_format" -> "invalid format";
        case "type_mismatch" -> "expected %s".formatted(meta.get("expected"));
        case "too_small" -> "must have at least %s elements".formatted(meta.get("min"));
        case "too_big" -> "must have at most %s elements".formatted(meta.get("max"));
        case "not_multiple_of" -> "must be a multiple of %s".formatted(meta.get("divisor"));
        case "one_of_failed" -> "no variant matched";
        case "unknown_field" -> "unknown field";
        case "invalid_scale" -> "too many decimal places (max %s)".formatted(meta.get("maxScale"));
        default -> "validation failed: " + code;
    };
}
