/**
 * Encoder abstractions for converting domain objects into boundary representations.
 *
 * <p>This package contains the core types for encoding typed domain values
 * into untyped output such as {@code Map<String, Object>}:
 *
 * <ul>
 *   <li>{@link net.unit8.raoh.encode.Encoder} — the core encoding interface</li>
 *   <li>{@link net.unit8.raoh.encode.ObjectEncoders} — primitive value encoders</li>
 *   <li>{@link net.unit8.raoh.encode.MapEncoders} — encoders producing {@code Map<String, Object>}</li>
 *   <li>{@link net.unit8.raoh.encode.PropertyEncoder} — field-level encoder</li>
 * </ul>
 */
package net.unit8.raoh.encode;
