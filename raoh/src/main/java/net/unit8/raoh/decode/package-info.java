/**
 * Core decoder abstractions and combinators.
 *
 * <p>This package contains the central types for decoding boundary input
 * into typed domain values:
 *
 * <ul>
 *   <li>{@link net.unit8.raoh.decode.Decoder} — the core decoding interface</li>
 *   <li>{@link net.unit8.raoh.decode.Decoders} — reusable combinators
 *       ({@code combine}, {@code oneOf}, {@code withDefault}, etc.)</li>
 *   <li>{@link net.unit8.raoh.decode.ObjectDecoders} — primitive value decoders
 *       ({@code string()}, {@code int_()}, {@code decimal()}, etc.)</li>
 *   <li>{@link net.unit8.raoh.decode.FieldDecoder} — field-level decoder abstraction</li>
 * </ul>
 *
 * <p>For built-in typed decoders with constraint APIs, see
 * {@link net.unit8.raoh.decode.builtin}. For {@code Map<String, Object>}
 * structure decoders, see {@link net.unit8.raoh.decode.map}.
 */
package net.unit8.raoh.decode;
