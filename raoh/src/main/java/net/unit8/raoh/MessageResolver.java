package net.unit8.raoh;

import org.jspecify.annotations.Nullable;

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
     * Resolves an issue into a human-readable message.
     *
     * <p>This is the entry point {@link Issue#resolve(MessageResolver)} calls. It sees
     * more than {@link #resolve(String, Map)} does: {@link Issue#messageKey()} says which
     * constraint failed, and {@link Issue#message()} is the message stored at decode time.
     * An implementation that cannot describe the issue should return {@link Issue#message()}
     * rather than a message that fits some other constraint.
     *
     * <p>The default implementation delegates to {@link #resolve(String, Map)}, so a
     * resolver written as a two-argument lambda keeps working unchanged.
     *
     * @param issue the issue to describe
     * @return the resolved message
     */
    default String resolve(Issue issue) {
        return resolve(issue.code(), issue.meta());
    }

    /**
     * Resolves an issue into a locale-aware human-readable message.
     *
     * @param issue  the issue to describe
     * @param locale the target locale for the message
     * @return the resolved message
     */
    default String resolve(Issue issue, Locale locale) {
        return resolve(issue.code(), issue.meta(), locale);
    }

    /**
     * Replaces named placeholders in a message template with values from the meta map.
     *
     * <p>Each {@code {key}} occurrence in {@code template} is replaced by the string
     * representation of the corresponding value in {@code meta}.
     * For example, a template {@code "must be at least {min} characters"} with
     * {@code meta = {"min": 3}} produces {@code "must be at least 3 characters"}.
     *
     * <p>The template is scanned once, so a value that itself contains braces is
     * inserted verbatim and never treated as a placeholder. A placeholder with no
     * matching key in {@code meta} is left as written.
     *
     * <p>A placeholder name is a letter or underscore followed by letters, digits,
     * underscores, dots or hyphens. Braces around anything else are literal text, so a
     * template may contain prose such as {@code an object like {"id": 1}}.
     *
     * @param template the message template containing {@code {key}} placeholders
     * @param meta     the metadata map supplying placeholder values
     * @return the template with its placeholders replaced
     */
    static String interpolate(String template, Map<String, Object> meta) {
        return Placeholders.fill(template, meta);
    }

    /**
     * Fills a template only if {@code meta} supplies every placeholder it asks for,
     * and returns {@code null} otherwise.
     *
     * <p>Use this instead of {@link #interpolate(String, Map)} when a resolver has
     * something better to fall back to, which inside {@link #resolve(Issue)} is
     * {@link Issue#message()}:
     *
     * <pre>{@code
     * var filled = MessageResolver.interpolateFully(template, issue.meta());
     * return filled != null ? filled : issue.message();
     * }</pre>
     *
     * <p>A half-filled template is worse than no template at all: {@code out_of_range}
     * covers both one-sided and two-sided bounds, so applying the two-sided wording to a
     * {@code min()} constraint would show a reader an upper bound that does not exist.
     * The check has to happen before substitution, because afterwards a brace that came
     * from a metadata value cannot be told apart from one the template wrote.
     *
     * @param template the message template containing {@code {key}} placeholders
     * @param meta     the metadata map supplying placeholder values
     * @return the filled template, or {@code null} if any placeholder has no value
     */
    static @Nullable String interpolateFully(String template, Map<String, Object> meta) {
        if (!meta.keySet().containsAll(Placeholders.namesIn(template))) {
            return null;
        }
        return Placeholders.fill(template, meta);
    }

    /**
     * A default resolver that provides English messages for all built-in error codes.
     *
     * <p>Resolving an {@link Issue} uses its {@link Issue#messageKey()} where the code
     * alone is ambiguous, so a temporal bound or a {@code positive()} constraint is
     * described as itself rather than as whichever bound its metadata happens to carry.
     */
    MessageResolver DEFAULT = new MessageResolver() {

        @Override
        public String resolve(Issue issue) {
            var meta = issue.meta();
            return switch (issue.messageKey()) {
                case MessageKeys.OUT_OF_RANGE_POSITIVE     -> "must be positive";
                case MessageKeys.OUT_OF_RANGE_NEGATIVE     -> "must be negative";
                case MessageKeys.OUT_OF_RANGE_NON_NEGATIVE -> "must be non-negative";
                case MessageKeys.OUT_OF_RANGE_NON_POSITIVE -> "must be non-positive";
                case MessageKeys.OUT_OF_RANGE_BEFORE  -> "must be before %s".formatted(meta.get("before"));
                case MessageKeys.OUT_OF_RANGE_AFTER   -> "must be after %s".formatted(meta.get("after"));
                case MessageKeys.OUT_OF_RANGE_BETWEEN ->
                        "must be between %s and %s".formatted(meta.get("from"), meta.get("to"));
                case MessageKeys.TOO_SMALL_NONEMPTY   -> "must not be empty";
                default -> resolve(issue.code(), meta);
            };
        }

        @Override
        public String resolve(Issue issue, Locale locale) {
            return resolve(issue);
        }

        @Override
        public String resolve(String code, Map<String, Object> meta) {
            return byCode(code, meta);
        }
    };

    /**
     * The English wording {@link #DEFAULT} uses when only an error code is available.
     *
     * @param code the error code
     * @param meta additional metadata associated with the issue
     * @return the resolved message
     */
    private static String byCode(String code, Map<String, Object> meta) {
        return switch (code) {
            case ErrorCodes.REQUIRED        -> "is required";
            case ErrorCodes.BLANK           -> "must not be blank";
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
            case ErrorCodes.MISSING_ELEMENTS -> "must contain all of %s (missing: %s)".formatted(meta.get("expected"), meta.get("missing"));
            case ErrorCodes.DUPLICATE_ELEMENT -> "must not contain duplicates: %s".formatted(meta.get("duplicates"));
            case ErrorCodes.NOT_ALLOWED     -> "must be one of %s".formatted(meta.get("allowed"));
            case ErrorCodes.ONE_OF_FAILED   -> "no variant matched";
            case ErrorCodes.UNKNOWN_FIELD   -> "unknown field";
            case ErrorCodes.INVALID_SCALE   -> "too many decimal places (max %s)".formatted(meta.get("maxScale"));
            case ErrorCodes.MISSING_FIELD   -> "field is missing";
            default -> "validation failed: " + code;
        };
    }
}
