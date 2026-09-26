package net.unit8.raoh.audit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The uses of {@link Effect#DELEGATED} and {@link Effect#AMBIENT} members that were reviewed in
 * their context.
 *
 * <p>Each section heading states the reason, and the section lists the uses it covers as
 * {@code caller -> member}, the caller in the form {@link BytecodeScanner} gives it:
 *
 * <pre>
 * [observes the input through its own interface]
 * net.unit8.raoh.decode.ObjectDecoders#list -> java.util.List#get(int)
 * </pre>
 *
 * <p>A reviewer reads the few reasons and checks that each use fits the one it is filed under.
 */
public final class Approvals {

    /**
     * One approved use.
     *
     * @param caller the calling method
     * @param callee the member it uses
     */
    public record Use(String caller, Member callee) {
        @Override
        public String toString() {
            return caller + " -> " + callee;
        }
    }

    private final Map<Use, String> reasons;

    private Approvals(Map<Use, String> reasons) {
        this.reasons = reasons;
    }

    private static Approvals none() {
        return new Approvals(Map.of());
    }

    /**
     * Reads an approval file. A missing file means no approvals.
     *
     * @param file the approval file
     * @return the approvals
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if an entry is malformed or listed twice
     */
    public static Approvals read(Path file) throws IOException {
        if (!Files.exists(file)) {
            return none();
        }
        var reasons = new LinkedHashMap<Use, String>();
        for (var entry : SectionedFile.read(file)) {
            var parts = entry.text().split(" -> ", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException(file + ":" + entry.line() + ": expected 'caller -> member'");
            }
            var use = new Use(parts[0].strip(), Member.parse(parts[1].strip()));
            if (reasons.put(use, entry.heading()) != null) {
                throw new IllegalArgumentException(file + ":" + entry.line() + ": " + use + " is listed twice");
            }
        }
        return new Approvals(reasons);
    }

    /**
     * Whether a use is approved.
     *
     * @param use the use
     * @return {@code true} if some section lists it
     */
    public boolean contains(Use use) {
        return reasons.containsKey(use);
    }

    /**
     * Every approved use, in file order.
     *
     * @return the approved uses
     */
    public List<Use> uses() {
        return List.copyOf(reasons.keySet());
    }
}
