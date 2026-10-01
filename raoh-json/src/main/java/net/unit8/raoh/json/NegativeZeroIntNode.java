package net.unit8.raoh.json;

import tools.jackson.databind.node.IntNode;

/**
 * The integer {@code -0}: an {@link IntNode} holding {@code 0}, so {@code int_()} and
 * {@code long_()} read it as {@code 0}, that is also a {@link NegativeZero}.
 *
 * <p>Jackson's {@code serialize} is final, so writing the node out gives {@code 0}.
 */
final class NegativeZeroIntNode extends IntNode implements NegativeZero {

    private static final long serialVersionUID = 1L;

    /** The only instance; the node holds nothing but the zero. */
    static final NegativeZeroIntNode INSTANCE = new NegativeZeroIntNode();

    private NegativeZeroIntNode() {
        super(0);
    }
}
