package net.unit8.raoh.conformance;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Java enums the runner gives {@code enum} forms.
 *
 * <p>raoh-java decodes a symbol into a constant of a Java enum, which is a class a user declares.
 * A case names its symbols at run time, so the runner declares an enum for each list of symbols
 * the suite uses. A case whose symbols none of them has gives an error, which the verifier counts
 * as a failure: the fix is to add the enum here.
 */
final class Symbols {

    /** {@code ["RED", "GREEN"]}. */
    enum RedGreen { RED, GREEN }

    private static final List<Class<? extends Enum<?>>> ENUMS = List.of(RedGreen.class);

    private Symbols() {
    }

    /**
     * The enum whose constants are exactly the given symbols.
     *
     * @param symbols the symbols a form lists
     * @return the enum
     * @throws IllegalStateException if the runner declares none
     */
    static Class<? extends Enum<?>> enumOf(List<String> symbols) {
        Set<String> wanted = new HashSet<>(symbols);
        for (Class<? extends Enum<?>> e : ENUMS) {
            Set<String> names = new HashSet<>();
            for (Enum<?> constant : e.getEnumConstants()) {
                names.add(constant.name());
            }
            if (names.equals(wanted) && names.size() == symbols.size()) {
                return e;
            }
        }
        throw new IllegalStateException("the runner declares no Java enum with the constants " + symbols);
    }
}
