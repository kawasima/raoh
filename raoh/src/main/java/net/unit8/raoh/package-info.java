/**
 * Raoh — a composable decoder library for Java.
 *
 * <p>The core types are:
 * <ul>
 *   <li>{@link net.unit8.raoh.Decoder} — transforms and validates input data</li>
 *   <li>{@link net.unit8.raoh.Result} — the outcome of decoding ({@link net.unit8.raoh.Ok} or {@link net.unit8.raoh.Err})</li>
 *   <li>{@link net.unit8.raoh.Issue} / {@link net.unit8.raoh.Issues} — accumulated validation errors</li>
 *   <li>{@link net.unit8.raoh.Decoders} — core combinators (combine, oneOf, withDefault, etc.)</li>
 * </ul>
 *
 * <p>For input-specific factories, see:
 * <ul>
 *   <li>{@code net.unit8.raoh.json.JsonDecoders} — for Jackson {@code JsonNode}</li>
 *   <li>{@link net.unit8.raoh.map.MapDecoders} — for {@code Map<String, Object>}</li>
 * </ul>
 */
package net.unit8.raoh;
