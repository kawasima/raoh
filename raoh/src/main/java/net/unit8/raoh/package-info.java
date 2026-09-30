/**
 * Raoh — a composable decoder library for Java.
 *
 * <p>The core types are:
 * <ul>
 *   <li>{@link net.unit8.raoh.decode.Decoder} — transforms and validates input data</li>
 *   <li>{@link net.unit8.raoh.Result} — the outcome of decoding ({@link net.unit8.raoh.Ok} or {@link net.unit8.raoh.Err})</li>
 *   <li>{@link net.unit8.raoh.Issue} / {@link net.unit8.raoh.Issues} — accumulated validation errors</li>
 *   <li>{@link net.unit8.raoh.decode.Decoders} — core combinators (combine, oneOf, withDefault, etc.)</li>
 * </ul>
 *
 * <p>For input-specific factories, see:
 * <ul>
 *   <li>{@link net.unit8.raoh.decode.ObjectDecoders} — for raw {@code Object} values (field values from maps, jOOQ records, etc.)</li>
 *   <li>{@code net.unit8.raoh.json.JsonDecoders} — for Jackson {@code JsonNode}</li>
 *   <li>{@link net.unit8.raoh.decode.map.MapDecoders} — for {@code Map<String, Object>} structure (field extraction, combine)</li>
 * </ul>
 *
 * <p>Built-in decoders do not acquire ambient capabilities on their own: the JVM default locale
 * or time zone, the clock, randomness, the host's network or filesystem, and the like. State that
 * affects decoding must be reachable from the decoder input or supplied explicitly as
 * configuration. What caller-provided inputs and callbacks do in their own methods, such as a
 * {@code List} implementation's {@code get}, is outside this guarantee. Locale enters Raoh's own
 * message resolution through
 * {@link net.unit8.raoh.MessageResolver#resolve(net.unit8.raoh.Issue, java.util.Locale)}.
 *
 * <p>When built-in decoding creates an observable collection, it either keeps the order the input
 * gives, as the map decoders keep key order, or uses a deterministic canonical order, as
 * {@link net.unit8.raoh.Issue#meta()} orders its keys. Collection values supplied by callers, such
 * as the values a {@code refine} metadata function returns, are kept as given.
 *
 * <p>What a text means does not depend on the running Java platform version either. Case
 * conversion, normalization and whitespace follow Unicode 18.0.0, and which texts are a date, a
 * time or an instant and which strings a pattern accepts are defined by 199x-notation, which Raoh
 * shares with Souther, rather than by the JDK's temporal parsers or the dialect of
 * {@code java.util.regex}.
 */
@NullMarked
package net.unit8.raoh;

import org.jspecify.annotations.NullMarked;
