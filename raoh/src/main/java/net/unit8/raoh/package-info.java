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
 * <p>Built-in decoders do not themselves read the JVM default locale or the JVM default time
 * zone. Such context enters only through a decoder's input or its explicit configuration. What a
 * caller-provided input does in its own methods, such as a {@code List} implementation's
 * {@code get}, is outside this guarantee. Locale enters Raoh's own message resolution through
 * {@link net.unit8.raoh.MessageResolver#resolve(net.unit8.raoh.Issue, java.util.Locale)}.
 */
@NullMarked
package net.unit8.raoh;

import org.jspecify.annotations.NullMarked;
