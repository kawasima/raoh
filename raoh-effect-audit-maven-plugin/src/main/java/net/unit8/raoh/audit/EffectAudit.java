package net.unit8.raoh.audit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
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
 *   <li>a member the catalog calls {@code CLOSED} or {@code EXPLICIT} takes an argument whose type
 *       a caller can implement, and its entry gives no note saying why no caller code runs;</li>
 *   <li>a use of a {@code DELEGATED} or {@code AMBIENT} member has no approval;</li>
 *   <li>decoder code reaches an {@code AMBIENT} use, directly or through the audited code base's
 *       own methods ({@link AmbientReach}), approved or not;</li>
 *   <li>an approval matches no use any more, or covers a member that needs none;</li>
 *   <li>the scanner or the walk met code it cannot account for, including a use of an
 *       uncatalogued member in a dependency that decoder code reaches.</li>
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
     * @param unexplained {@code CLOSED} or {@code EXPLICIT} members with an argument a caller can
     *                    implement and no note saying why no caller code runs, each with one of its callers
     * @param unapproved uses that need an approval and have none, with the member's effect and
     *                   how the bytecode reaches it
     * @param ambientInDecoder {@code AMBIENT} uses decoder code reaches, each with the chain of
     *                         methods from the decoder to the use
     * @param stale approvals that match no use
     * @param overlisted uses listed under more reasons than the places the caller uses the member,
     *                   so that at least one reason no longer has a use of its own, with the counts
     * @param needless approvals of a member whose effect needs none
     * @param problems code the scanner, the hierarchy or the walk could not account for
     */
    public record Report(
            Map<Member, String> unknown,
            Map<Member, String> overridable,
            Map<Member, String> unexplained,
            Map<Approvals.Use, String> unapproved,
            Map<Approvals.Use, String> ambientInDecoder,
            Set<Approvals.Use> stale,
            Map<Approvals.Use, String> overlisted,
            Set<Approvals.Use> needless,
            List<String> problems) {

        /**
         * Whether the audit found nothing to fix.
         *
         * @return {@code true} if every list is empty
         */
        public boolean passed() {
            return unknown.isEmpty() && overridable.isEmpty() && unexplained.isEmpty() && unapproved.isEmpty()
                    && ambientInDecoder.isEmpty() && stale.isEmpty() && overlisted.isEmpty() && needless.isEmpty()
                    && problems.isEmpty();
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
            section(out, "Members missing from " + catalogName + ". File each under the effect its API contract"
                    + " gives it (CLOSED, EXPLICIT, DELEGATED, AMBIENT):", entries(unknown, "used by"));
            section(out, "These are CLOSED or EXPLICIT in " + catalogName + ", but a subclass can override them"
                    + " and they are called virtually, so the receiver chooses what runs. Move them to [DELEGATED]:",
                    entries(overridable, "called by"));
            section(out, "These are CLOSED or EXPLICIT in " + catalogName + " but take an argument whose type a"
                    + " caller can implement. Add a note saying why they run none of its code, or move them"
                    + " to [DELEGATED]:", entries(unexplained, "used by"));
            section(out, "Decoder code must not read ambient state itself, nor through Raoh's own methods;"
                    + " these cannot be approved:", ambientInDecoder.entrySet().stream()
                    .map(e -> e.getKey() + "\n      reached by " + e.getValue()).toList());
            section(out, "Uses without an approval in " + approvalsName + ". File each under a [reason] that"
                    + " says why it is acceptable here:", entries(unapproved, ""));
            section(out, "Approvals in " + approvalsName + " that match no use any more; remove them:",
                    stale.stream().map(Object::toString).toList());
            section(out, "Approvals in " + approvalsName + " listed under more reasons than the places the caller"
                    + " uses the member, so a reason no longer has a use of its own; remove the reason that no"
                    + " longer applies:", entries(overlisted, ""));
            section(out, "Approvals in " + approvalsName + " of members that are CLOSED or EXPLICIT; remove them:",
                    needless.stream().map(Object::toString).toList());
            section(out, "Code the audit cannot account for:", problems);
            return out.toString();
        }

        private static List<String> entries(Map<?, String> map, String label) {
            return map.entrySet().stream()
                    .map(e -> e.getKey() + "  -- " + (label.isEmpty() ? "" : label + " ") + e.getValue())
                    .toList();
        }

        private static void section(StringBuilder out, String header, Collection<String> lines) {
            if (!lines.isEmpty()) {
                out.append('\n').append(header).append('\n');
                lines.forEach(line -> out.append("  ").append(line).append('\n'));
            }
        }
    }

    /**
     * Audits the edges of one module.
     *
     * @param scan what the scanner found in the module and its internal dependencies
     * @param catalog the reviewed effects
     * @param approvals the module's approvals
     * @param hierarchy the class hierarchy the module compiles against
     * @param isInternal whether a class, by binary name, belongs to the audited code base
     * @param isDecoderClass whether a class, by binary name, is decoder code
     * @return the report
     */
    public static Report check(BytecodeScanner.Result scan, EffectCatalog catalog, Approvals approvals,
                               Hierarchy hierarchy, Predicate<String> isInternal, Predicate<String> isDecoderClass) {
        var unknown = new TreeMap<Member, String>(BY_TEXT);
        var overridable = new TreeMap<Member, String>(BY_TEXT);
        var unexplained = new TreeMap<Member, String>(BY_TEXT);
        var unapproved = new TreeMap<Approvals.Use, String>(USE_ORDER);
        var needless = new TreeSet<Approvals.Use>(USE_ORDER);
        var used = new LinkedHashSet<Approvals.Use>();
        var places = new HashMap<Approvals.Use, Integer>();
        var problems = new ArrayList<>(scan.problems());

        for (var edge : scan.edges()) {
            var effect = catalog.effectOf(edge.callee());
            var use = useOf(edge, effect.orElse(null));
            used.add(use);
            places.merge(use, scan.sites().getOrDefault(edge, 1), Integer::sum);
            if (effect.isEmpty()) {
                unknown.putIfAbsent(edge.callee(), edge.caller());
                continue;
            }
            switch (effect.get()) {
                case CLOSED, EXPLICIT -> {
                    if (edge.virtual() && isOverridable(hierarchy, edge.callee(), problems)) {
                        overridable.putIfAbsent(edge.callee(), edge.caller());
                    }
                    if (!catalog.hasNote(edge.callee()) && hasOpenParameter(hierarchy, edge.callee(), problems)) {
                        unexplained.putIfAbsent(edge.callee(), edge.caller());
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

        var reach = new AmbientReach(scan.graph(), hierarchy, isInternal);
        var starts = scan.graph().classes().entrySet().stream()
                .filter(c -> scan.audited().contains(c.getKey()) && isDecoderClass.test(c.getKey()))
                .flatMap(c -> c.getValue().stream())
                .sorted(BY_TEXT)
                .toList();
        var found = reach.reach(starts, catalog::effectOf);
        var ambientInDecoder = new TreeMap<Approvals.Use, String>(USE_ORDER);
        found.ambient().forEach((edge, path) -> ambientInDecoder.putIfAbsent(
                useOf(edge, Effect.AMBIENT),
                path.stream().map(Member::toString).collect(Collectors.joining(" -> "))));
        // An ambient use no approval may cover is reported once, as reached from a decoder.
        ambientInDecoder.keySet().forEach(unapproved::remove);
        for (var edge : found.unknown()) {
            if (!used.contains(useOf(edge, null))) {
                // The audited module reports its own; this one is in a dependency its decoders reach.
                problems.add("decoder code reaches " + edge.caller() + " -> " + edge.callee()
                        + ", which the catalog does not list");
            }
        }
        problems.addAll(reach.problems());

        var stale = new TreeSet<Approvals.Use>(USE_ORDER);
        var overlisted = new TreeMap<Approvals.Use, String>(USE_ORDER);
        for (var use : approvals.uses()) {
            if (!used.contains(use)) {
                stale.add(use);
                continue;
            }
            // A use is listed under each reason it is made for, and each reason needs a place of its
            // own. The audit cannot tell which place a reason is for, but it can tell when there are
            // fewer places than reasons.
            int reasons = approvals.reasonsOf(use).size();
            int at = places.getOrDefault(use, 0);
            if (reasons > at) {
                overlisted.put(use, "listed under " + reasons + " reasons, used at " + at
                        + (at == 1 ? " place" : " places"));
            }
        }
        return new Report(unknown, overridable, unexplained, unapproved, ambientInDecoder, stale, overlisted,
                needless, problems);
    }

    /**
     * The approval key of an edge: the exact method for an {@code AMBIENT} member, so one method's
     * approval does not cover its overloads, and the attributed caller for everything else.
     */
    private static Approvals.Use useOf(Edge edge, Effect effect) {
        return new Approvals.Use(effect == Effect.AMBIENT ? edge.method().toString() : edge.caller(), edge.callee());
    }

    private static boolean hasOpenParameter(Hierarchy hierarchy, Member member, List<String> problems) {
        try {
            return hierarchy.hasOpenParameter(member);
        } catch (IllegalStateException e) {
            problems.add(e.getMessage());
            return false;
        }
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
