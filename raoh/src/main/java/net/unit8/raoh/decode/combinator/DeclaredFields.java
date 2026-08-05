package net.unit8.raoh.decode.combinator;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What a combine component says it consumes from the input.
 *
 * <p>{@link Known} with an empty set and {@link Unknown} are different answers: the first says the
 * component consumes nothing, the second says there is no way to tell. A plain {@code Set} could
 * not express the difference, and collapsing them is how {@code strict()} would silently start
 * accepting fields it should reject.
 */
sealed interface DeclaredFields {

    /**
     * The component declares exactly these field names.
     *
     * @param names the declared field names
     */
    record Known(Set<String> names) implements DeclaredFields {
        public Known {
            names = Set.copyOf(names);
        }
    }

    /** The component reads the input opaquely, so its field set cannot be determined. */
    record Unknown() implements DeclaredFields {}

    /**
     * Combines two declared sets. Unknown is absorbing: a schema is only fully declared when every
     * one of its components is.
     *
     * @param left  the first declared set
     * @param right the second declared set
     * @return their union, or {@link Unknown} if either is unknown
     */
    static DeclaredFields merge(DeclaredFields left, DeclaredFields right) {
        if (!(left instanceof Known(var leftNames)) || !(right instanceof Known(var rightNames))) {
            return new Unknown();
        }
        // Declaration order is kept so that a diagnostic can list the fields as they were written.
        var names = new LinkedHashSet<String>(leftNames);
        names.addAll(rightNames);
        return new Known(names);
    }
}
