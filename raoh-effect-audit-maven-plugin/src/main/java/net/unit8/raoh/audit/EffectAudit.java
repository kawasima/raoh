package net.unit8.raoh.audit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Checks the external edges of the audited code against the catalog and the approvals.
 *
 * <p>The audit fails when:
 * <ul>
 *   <li>a used member is not in the catalog;</li>
 *   <li>a member the catalog calls {@code CLOSED} or {@code EXPLICIT} is called virtually and a
 *       subclass can override it, so the implementation that runs is chosen by the receiver;</li>
 *   <li>a use of a {@code DELEGATED} or {@code AMBIENT} member has no approval;</li>
 *   <li>decoder code reaches an {@code AMBIENT} use, directly or through the audited code base's
 *       own methods ({@link AmbientReach}), approved or not;</li>
 *   <li>an approval matches no use any more, or covers a member that needs none;</li>
 *   <li>the scanner or the walk met code it cannot account for.</li>
 * </ul>
 *
 * <p>A {@code CLOSED} or {@code EXPLICIT} member used in a new place passes without review:
 * what it can do does not depend on where it is called.
 */
public final class EffectAudit {

    private EffectAudit() {}

    /**
     * The outcome of an audit.
     *
     * @param unknown members not in the catalog, each with one of its callers
     * @param overridable {@code CLOSED} or {@code EXPLICIT} members called virtually where a
     *                    subclass can override them, each with one of its callers
     * @param unapproved uses that need an approval and have none, with the member's effect and
     *                   how the bytecode reaches it
     * @param ambientInDecoder {@code AMBIENT} uses decoder code reaches, each with the chain of
     *                         methods from the decoder to the use
     * @param stale approvals that match no use
     * @param needless approvals of a member whose effect needs none
     * @param problems code the scanner, the hierarchy or the walk could not account for
     */
    public record Report(
            Map<Member, String> unknown,
            Map<Member, String> overridable,
            Map<Approvals.Use, String> unapproved,
            Map<Approvals.Use, String> ambientInDecoder,
            Set<Approvals.Use> stale,
            Set<Approvals.Use> needless,
            List<String> problems) {

        /**
         * Whether the audit found nothing to fix.
         *
         * @return {@code true} if every list is empty
         */
        public boolean passed() {
            return unknown.isEmpty() && overridable.isEmpty() && unapproved.isEmpty()
                    && ambientInDecoder.isEmpty() && stale.isEmpty() && needless.isEmpty() && problems.isEmpty();
        }

        /**
         * A description of what to fix, with entries ready to paste into the files.
         *
         * @param catalogName how to refer to the catalog file
         * @param approvalsName how to refer to the approval file
         * @return the description, empty if the audit passed
         */
        public String describe(String catalogName, String approvalsName) {
            var out = new StringBuilder();
            if (!unknown.isEmpty()) {
                out.append("\nMembers missing from ").append(catalogName)
                        .append(". File each under the effect its API contract gives it (CLOSED, EXPLICIT, DELEGATED, AMBIENT):\n");
                unknown.forEach((member, caller) ->
                        out.append("  ").append(member).append("  -- used by ").append(caller).append('\n'));
            }
            if (!overridable.isEmpty()) {
                out.append("\nThese are CLOSED or EXPLICIT in ").append(catalogName)
                        .append(", but a subclass can override them and they are called virtually,")
                        .append(" so the receiver chooses what runs. Move them to [DELEGATED]:\n");
                overridable.forEach((member, caller) ->
                        out.append("  ").append(member).append("  -- called by ").append(caller).append('\n'));
            }
            if (!ambientInDecoder.isEmpty()) {
                out.append("\nDecoder code must not read ambient state itself, nor through Raoh's own helpers;")
                        .append(" these cannot be approved:\n");
                ambientInDecoder.forEach((use, path) ->
                        out.append("  ").append(use).append("\n      reached by ").append(path).append('\n'));
            }
            if (!unapproved.isEmpty()) {
                out.append("\nUses without an approval in ").append(approvalsName)
                        .append(". File each under a [reason] that says why it is acceptable here:\n");
                unapproved.forEach((use, how) ->
                        out.append("  ").append(use).append("  -- ").append(how).append('\n'));
            }
            if (!stale.isEmpty()) {
                out.append("\nApprovals in ").append(approvalsName).append(" that match no use any more; remove them:\n");
                stale.forEach(use -> out.append("  ").append(use).append('\n'));
            }
            if (!needless.isEmpty()) {
                out.append("\nApprovals in ").append(approvalsName)
                        .append(" of members that are CLOSED or EXPLICIT; remove them:\n");
                needless.forEach(use -> out.append("  ").append(use).append('\n'));
            }
            if (!problems.isEmpty()) {
                out.append("\nCode the audit cannot account for:\n");
                problems.forEach(p -> out.append("  ").append(p).append('\n'));
            }
            return out.toString();
        }
    }

    /**
     * Audits the edges of one module.
     *
     * @param scan what the scanner found in the module and its internal dependencies
     * @param catalog the reviewed effects
     * @param approvals the module's approvals
     * @param hierarchy the class hierarchy the module compiles against
     * @param isDecoderClass whether a class, by binary name, is decoder code
     * @return the report
     */
    public static Report check(BytecodeScanner.Result scan, EffectCatalog catalog, Approvals approvals,
                               Hierarchy hierarchy, Predicate<String> isDecoderClass) {
        var unknown = new TreeMap<Member, String>(BY_TEXT);
        var overridable = new TreeMap<Member, String>(BY_TEXT);
        var unapproved = new TreeMap<Approvals.Use, String>(USE_ORDER);
        var needless = new TreeSet<Approvals.Use>(USE_ORDER);
        var used = new LinkedHashSet<Approvals.Use>();
        var problems = new ArrayList<>(scan.problems());

        for (var edge : scan.edges()) {
            var use = new Approvals.Use(edge.caller(), edge.callee());
            used.add(use);
            var effect = catalog.effectOf(edge.callee());
            if (effect.isEmpty()) {
                unknown.putIfAbsent(edge.callee(), edge.caller());
                continue;
            }
            switch (effect.get()) {
                case CLOSED, EXPLICIT -> {
                    if (edge.virtual() && isOverridable(hierarchy, edge.callee(), problems)) {
                        overridable.putIfAbsent(edge.callee(), edge.caller());
                    }
                    if (approvals.contains(use)) {
                        needless.add(use);
                    }
                }
                case AMBIENT, DELEGATED -> {
                    if (!approvals.contains(use)) {
                        unapproved.putIfAbsent(use, effect.get() + " via " + edge.via());
                    }
                }
            }
        }

        var reach = new AmbientReach(scan.graph(), hierarchy);
        var starts = scan.graph().classes().values().stream()
                .filter(c -> scan.audited().contains(c.name()) && isDecoderClass.test(c.name()))
                .flatMap(c -> c.methods().stream())
                .sorted(BY_TEXT)
                .toList();
        var ambientInDecoder = new TreeMap<Approvals.Use, String>(USE_ORDER);
        reach.reach(starts, edge -> catalog.effectOf(edge.callee()).orElse(null) == Effect.AMBIENT)
                .forEach((edge, path) -> ambientInDecoder.putIfAbsent(
                        new Approvals.Use(edge.caller(), edge.callee()),
                        path.stream().map(Member::toString).collect(Collectors.joining(" -> "))));
        // An ambient use no approval may cover is reported once, as reached from a decoder.
        ambientInDecoder.keySet().forEach(unapproved::remove);
        problems.addAll(reach.problems());

        var stale = new TreeSet<Approvals.Use>(USE_ORDER);
        for (var use : approvals.uses()) {
            if (!used.contains(use)) {
                stale.add(use);
            }
        }
        return new Report(unknown, overridable, unapproved, ambientInDecoder, stale, needless, problems);
    }

    private static boolean isOverridable(Hierarchy hierarchy, Member member, List<String> problems) {
        try {
            return hierarchy.isOverridable(member);
        } catch (IllegalStateException e) {
            problems.add(e.getMessage());
            return false;
        }
    }

    private static final Comparator<Member> BY_TEXT = Comparator.comparing(Member::toString);
    private static final Comparator<Approvals.Use> USE_ORDER = Comparator.comparing(Approvals.Use::toString);
}
