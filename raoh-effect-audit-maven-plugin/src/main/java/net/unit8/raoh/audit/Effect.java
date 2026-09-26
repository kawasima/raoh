package net.unit8.raoh.audit;

/**
 * What an external member can do beyond reading what it is given.
 *
 * <p>The contract the audit protects: a built-in decoder does not itself acquire ambient
 * capabilities (default locale, default time zone, clock, randomness, filesystem, network, ...)
 * except those passed to it in its input or explicit configuration.
 */
public enum Effect {

    /**
     * Acquires nothing outside its receiver and arguments and what is explicitly reachable from
     * them, and runs no code a caller of Raoh can supply. {@code Integer.valueOf(int)},
     * {@code String#trim()}. Only a member no subclass can override may be {@code CLOSED}.
     */
    CLOSED(false),

    /**
     * The ambient value it depends on is an explicit argument: {@code String#toLowerCase(Locale)},
     * {@code LocalDate.now(Clock)}. Only a member no subclass can override may be {@code EXPLICIT}.
     */
    EXPLICIT(false),

    /**
     * Runs an implementation chosen by its receiver, an argument or a provider: every
     * overridable method called virtually ({@code List#get(int)}, {@code Function#apply}), and
     * methods that call into their arguments ({@code List.copyOf(Collection)},
     * {@code String.valueOf(Object)}). Each use needs an approval that says why the delegation is
     * acceptable there, such as observing the input through its own interface.
     */
    DELEGATED(true),

    /**
     * Reads ambient state itself: {@code Locale.getDefault()}, {@code System.currentTimeMillis()}.
     * Each use needs an approval, and none is allowed in a decoder package.
     */
    AMBIENT(true);

    private final boolean needsApproval;

    Effect(boolean needsApproval) {
        this.needsApproval = needsApproval;
    }

    /**
     * Whether each use of a member with this effect needs a caller-specific approval.
     *
     * @return {@code true} for {@link #DELEGATED} and {@link #AMBIENT}
     */
    public boolean needsApproval() {
        return needsApproval;
    }
}
