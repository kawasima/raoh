package net.unit8.raoh.audit;

/**
 * What an external member can do beyond reading what it is given, by its API contract.
 *
 * <p>The contract the audit protects: a built-in decoder does not itself acquire ambient
 * capabilities (default locale, default time zone, clock, randomness, class path, filesystem,
 * network, ...) except those passed to it in its input or explicit configuration.
 */
public enum Effect {

    /**
     * Runs no code a caller of Raoh can supply and acquires nothing outside its receiver and
     * arguments: {@code Integer.valueOf(int)}, {@code String#trim()}. Not through its receiver,
     * not through an argument whose type a caller can implement, not through a class it
     * initializes. Only a member no subclass can override may be {@code CLOSED}.
     */
    CLOSED,

    /**
     * Like {@link #CLOSED}, except that the ambient value it depends on is an explicit argument:
     * {@code String#toLowerCase(Locale)}, {@code LocalDate.now(Clock)}.
     */
    EXPLICIT,

    /**
     * May run code its receiver, an argument, or a class it initializes chooses: every
     * overridable method called virtually ({@code List#get(int)}, {@code Function#apply}), and
     * members that call into an argument ({@code List.copyOf(Collection)}, {@code Map.of} on its
     * keys). Each use needs an approval whose reason says whether it only observes what Raoh
     * accepted, which is allowed, or adopts a caller's behaviour as the meaning of a conversion,
     * which is not.
     */
    DELEGATED,

    /**
     * Acquires ambient state itself: {@code Locale.getDefault()}, {@code System.currentTimeMillis()},
     * and any lookup by name in the class path ({@code Class.forName}, {@code ServiceLoader},
     * {@code MethodHandles.Lookup#find*}). Each use needs an approval, and decoder code must not
     * reach one, directly or through the audited code base's own methods.
     */
    AMBIENT
}
