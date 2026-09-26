package net.unit8.raoh.audit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The reviewed effect of each external member the audited code may use.
 *
 * <p>The file has one section per {@link Effect}, headed {@code [CLOSED]}, {@code [EXPLICIT]},
 * {@code [DELEGATED]} or {@code [AMBIENT]}, listing members in {@link Member} text form:
 *
 * <pre>
 * [CLOSED]
 * java.lang.Integer#valueOf(int):java.lang.Integer
 *
 * [DELEGATED]
 * java.util.List#get(int):java.lang.Object
 * java.util.List#copyOf(java.util.Collection):java.util.List  -- calls the argument's toArray()
 * </pre>
 *
 * <p>The effect is a property of the member, so a member keeps it wherever it is used. A note
 * after {@code "  -- "} records why; the audit requires one where a {@code CLOSED} or
 * {@code EXPLICIT} member takes an argument whose type a caller can implement, since that is
 * where running a caller's code hides.
 */
public final class EffectCatalog {

    private final Map<Member, Effect> effects;
    private final Set<Member> noted;

    private EffectCatalog(Map<Member, Effect> effects, Set<Member> noted) {
        this.effects = Map.copyOf(effects);
        this.noted = Set.copyOf(noted);
    }

    /**
     * Reads a catalog file.
     *
     * @param file the catalog
     * @return the catalog
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if a heading is not an effect, an entry is not a member,
     *         or a member is listed twice
     */
    public static EffectCatalog read(Path file) throws IOException {
        var effects = new HashMap<Member, Effect>();
        var noted = new HashSet<Member>();
        for (var entry : SectionedFile.read(file)) {
            Effect effect;
            try {
                effect = Effect.valueOf(entry.heading());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(file + ":" + entry.line() + ": [" + entry.heading()
                        + "] is not one of CLOSED, EXPLICIT, DELEGATED, AMBIENT", e);
            }
            var member = Member.parse(entry.text());
            if (effects.put(member, effect) != null) {
                throw new IllegalArgumentException(file + ":" + entry.line() + ": " + member + " is listed twice");
            }
            if (!entry.note().isEmpty()) {
                noted.add(member);
            }
        }
        return new EffectCatalog(effects, noted);
    }

    /**
     * The reviewed effect of a member.
     *
     * @param member the member
     * @return its effect, or empty if the catalog does not list it
     */
    public Optional<Effect> effectOf(Member member) {
        return Optional.ofNullable(effects.get(member));
    }

    /**
     * Whether the catalog gives a reason for a member's effect.
     *
     * @param member the member
     * @return {@code true} if its entry has a note
     */
    public boolean hasNote(Member member) {
        return noted.contains(member);
    }
}
