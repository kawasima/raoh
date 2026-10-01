package net.unit8.raoh;

import net.unit8.raoh.internal.IssueBearingMeta;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * A single validation issue describing what went wrong and where.
 *
 * <p>{@code code} classifies the failure for a program to branch on; {@code messageKey}
 * identifies the wording that describes it. They differ whenever one code covers several
 * constraints — {@code positive()} and {@code min(0)} both report
 * {@link ErrorCodes#OUT_OF_RANGE} but need different sentences and carry different
 * metadata. See {@link MessageKeys}.
 *
 * <p>{@code meta} is copied when the issue is created and exposed as an unmodifiable map whose
 * keys iterate in their natural {@code String} order, whatever map was passed in. Built-in
 * constraints build their metadata with {@code Map.of}, whose iteration order can differ between
 * JVM runs; the copy keeps that order out of {@link #meta()} and out of anything serialized from
 * it, such as {@link Issues#toJsonList()}. Keys must not be {@code null}; values may be. Only the
 * top-level map is copied and ordered: a value that is itself a collection or map, such as one a
 * {@code refine} metadata function returns, is kept as given.
 *
 * <p>An issue can hold other issues in its metadata: a {@code one_of_failed} issue's
 * {@code candidates} lists the issues each candidate failed with. They read as maps, the form
 * {@link Issues#toJsonList()} writes, but keep everything an issue has, so {@link #resolve} and
 * {@link #rebase} reach them too. Equality compares them as they read, so two issues whose nested
 * issues differ only in their message key or in whether their message is custom are equal.
 *
 * @param path          the location in the input structure where the issue occurred
 * @param code          a machine-readable error code (e.g., {@code "required"}, {@code "out_of_range"})
 * @param messageKey    the key identifying which message describes this issue; defaults to {@code code}
 * @param message       a human-readable error message
 * @param meta          additional metadata about the issue (e.g., min/max values), ordered by key
 * @param customMessage whether the message was explicitly set and should not be overridden by a {@link MessageResolver}
 */
public record Issue(Path path, String code, String messageKey, String message,
                    Map<String, Object> meta, boolean customMessage) {

    /**
     * Creates an issue, copying {@code meta} into an unmodifiable map ordered by key.
     *
     * @param path          the location in the input structure where the issue occurred
     * @param code          a machine-readable error code
     * @param messageKey    the key identifying which message describes this issue
     * @param message       a human-readable error message
     * @param meta          additional metadata about the issue; its keys must not be {@code null}
     * @param customMessage whether the message was explicitly set and should not be overridden by a {@link MessageResolver}
     * @throws NullPointerException if {@code meta} or one of its keys is {@code null}
     */
    public Issue {
        meta = Collections.unmodifiableMap(new TreeMap<>(meta));
    }

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
     * <p>The issues this issue's metadata holds, such as each candidate's issues under a
     * {@code one_of_failed} issue's {@code candidates}, are resolved first, the same way and
     * whether or not this issue's own message is custom; the resolver then sees this issue with
     * them already resolved.
     *
     * @param resolver the message resolver
     * @return a new issue with the resolved message, or this issue if there is nothing to resolve
     */
    public Issue resolve(MessageResolver resolver) {
        return IssueTree.holdsIssues(this) ? IssueTree.map(this, i -> i.resolvedAlone(resolver))
                : resolvedAlone(resolver);
    }

    /**
     * Resolves this issue's message using the given resolver and locale,
     * unless it has a custom message.
     *
     * <p>The issues this issue's metadata holds are resolved first, as
     * {@link #resolve(MessageResolver)} says.
     *
     * @param resolver the message resolver
     * @param locale   the target locale for the message
     * @return a new issue with the resolved message, or this issue if there is nothing to resolve
     */
    public Issue resolve(MessageResolver resolver, Locale locale) {
        return IssueTree.holdsIssues(this) ? IssueTree.map(this, i -> i.resolvedAlone(resolver, locale))
                : resolvedAlone(resolver, locale);
    }

    /**
     * Returns a copy of this issue with its path prepended by the given prefix.
     *
     * <p>The issues this issue's metadata holds, such as each candidate's issues under a
     * {@code one_of_failed} issue's {@code candidates}, are at paths of the same input, so their
     * paths are prepended too.
     *
     * @param prefix the path prefix
     * @return a new issue with the rebased path
     */
    public Issue rebase(Path prefix) {
        if (!IssueTree.holdsIssues(this)) {
            return rebasedAlone(prefix);
        }
        // The issues below record the prefix and apply it when read: rebasing runs once per level
        // a failure is passed up, and rebuilding the whole tree each time would be quadratic.
        var rebasedMeta = new java.util.LinkedHashMap<String, Object>();
        meta.forEach((key, value) -> rebasedMeta.put(key,
                value instanceof IssueBearingMeta nested ? nested.rebased(prefix) : value));
        return new Issue(prefix.append(path), code, messageKey, message, rebasedMeta, customMessage);
    }

    // The steps below change this issue alone and leave the issues its metadata holds as they
    // are; IssueTree applies them to every issue of the tree, the ones below first.

    Issue resolvedAlone(MessageResolver resolver) {
        return customMessage ? this : new Issue(path, code, messageKey, resolver.resolve(this), meta, true);
    }

    Issue resolvedAlone(MessageResolver resolver, Locale locale) {
        return customMessage ? this
                : new Issue(path, code, messageKey, resolver.resolve(this, locale), meta, true);
    }

    Issue rebasedAlone(Path prefix) {
        return new Issue(prefix.append(path), code, messageKey, message, meta, customMessage);
    }
}
