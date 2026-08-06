package net.unit8.raoh;

import java.util.Locale;
import java.util.Map;

/**
 * A single validation issue describing what went wrong and where.
 *
 * <p>{@code code} classifies the failure for a program to branch on; {@code messageKey}
 * identifies the wording that describes it. They differ whenever one code covers several
 * constraints — {@code positive()} and {@code min(0)} both report
 * {@link ErrorCodes#OUT_OF_RANGE} but need different sentences and carry different
 * metadata. See {@link MessageKeys}.
 *
 * @param path          the location in the input structure where the issue occurred
 * @param code          a machine-readable error code (e.g., {@code "required"}, {@code "out_of_range"})
 * @param messageKey    the key identifying which message describes this issue; defaults to {@code code}
 * @param message       a human-readable error message
 * @param meta          additional metadata about the issue (e.g., min/max values)
 * @param customMessage whether the message was explicitly set and should not be overridden by a {@link MessageResolver}
 */
public record Issue(Path path, String code, String messageKey, String message,
                    Map<String, Object> meta, boolean customMessage) {

    /**
     * Creates an issue whose message key is its error code.
     *
     * @param path          the path where the issue occurred
     * @param code          the error code
     * @param message       the error message
     * @param meta          additional metadata
     * @param customMessage whether the message should be left alone by a {@link MessageResolver}
     */
    public Issue(Path path, String code, String message, Map<String, Object> meta, boolean customMessage) {
        this(path, code, code, message, meta, customMessage);
    }

    /**
     * Creates an issue with a default (non-custom) message.
     *
     * @param path    the path where the issue occurred
     * @param code    the error code
     * @param message the error message
     * @param meta    additional metadata
     * @return a new issue
     */
    public static Issue of(Path path, String code, String message, Map<String, Object> meta) {
        return new Issue(path, code, code, message, meta, false);
    }

    /**
     * Creates an issue with a default (non-custom) message and an explicit message key.
     *
     * @param path       the path where the issue occurred
     * @param code       the error code
     * @param messageKey the key identifying which message describes this issue
     * @param message    the error message
     * @param meta       additional metadata
     * @return a new issue
     */
    public static Issue of(Path path, String code, String messageKey, String message, Map<String, Object> meta) {
        return new Issue(path, code, messageKey, message, meta, false);
    }

    /**
     * Creates an issue with no metadata.
     *
     * @param path    the path where the issue occurred
     * @param code    the error code
     * @param message the error message
     * @return a new issue
     */
    public static Issue of(Path path, String code, String message) {
        return new Issue(path, code, code, message, Map.of(), false);
    }

    /**
     * Returns a copy of this issue with the given custom message.
     *
     * @param message the custom message
     * @return a new issue with {@code customMessage} set to {@code true}
     */
    public Issue withCustomMessage(String message) {
        return new Issue(path, code, messageKey, message, meta, true);
    }

    /**
     * Resolves this issue's message using the given resolver, unless it has a custom message.
     *
     * <p>A resolver that cannot describe this issue returns {@link #message()} unchanged,
     * so the message stored at decode time is never replaced by a worse one.
     *
     * @param resolver the message resolver
     * @return a new issue with the resolved message, or this issue if already custom
     */
    public Issue resolve(MessageResolver resolver) {
        return customMessage ? this
                : new Issue(path, code, messageKey, resolver.resolve(this), meta, true);
    }

    /**
     * Resolves this issue's message using the given resolver and locale,
     * unless it has a custom message.
     *
     * @param resolver the message resolver
     * @param locale   the target locale for the message
     * @return a new issue with the resolved message, or this issue if already custom
     */
    public Issue resolve(MessageResolver resolver, Locale locale) {
        return customMessage ? this
                : new Issue(path, code, messageKey, resolver.resolve(this, locale), meta, true);
    }

    /**
     * Returns a copy of this issue with its path prepended by the given prefix.
     *
     * @param prefix the path prefix
     * @return a new issue with the rebased path
     */
    public Issue rebase(Path prefix) {
        return new Issue(prefix.append(path), code, messageKey, message, meta, customMessage);
    }
}
