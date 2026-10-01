package net.unit8.raoh.json;

/**
 * A number node read from a JSON number that has a minus sign and the value zero, such as
 * {@code -0}, {@code -0.0} or {@code -0.000e10}.
 *
 * <p>The node holds the value zero as Jackson's own node of that kind does, so every reader of the
 * value sees {@code 0}, with the scale it was written with; only the sign of the zero is carried
 * by the type. {@link JsonDecoders#double_()} and {@link JsonDecoders#float_()} read it as
 * {@code -0.0}.
 */
sealed interface NegativeZero permits NegativeZeroIntNode, NegativeZeroDecimalNode {
}
