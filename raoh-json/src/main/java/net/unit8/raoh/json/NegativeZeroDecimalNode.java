package net.unit8.raoh.json;

import tools.jackson.databind.node.DecimalNode;

import java.math.BigDecimal;

/**
 * A negative zero written with a fraction or an exponent, such as {@code -0.0} or
 * {@code -0.000e10}: a {@link DecimalNode} holding the zero with the scale it was written with, so
 * {@code decimal()} reads {@code -0.000e10} as {@code 0E+7}, that is also a {@link NegativeZero}.
 *
 * <p>Jackson's {@code serialize} is final, so writing the node out gives the zero without its sign.
 */
final class NegativeZeroDecimalNode extends DecimalNode implements NegativeZero {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the node.
     *
     * @param zero the zero, with the scale the number was written with
     */
    NegativeZeroDecimalNode(BigDecimal zero) {
        super(zero);
    }
}
