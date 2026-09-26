package net.unit8.raoh.audit;

import java.util.ArrayList;
import java.util.Collection;
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
 *   <li>a member that looks code or resources up by name ({@code Class.forName},
 *       {@code ServiceLoader}, {@code MethodHandles.Lookup#find*}, reflective invocation) is filed
 *       as anything but {@code AMBIENT}: what it finds depends on the class path;</li>
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

    /** Members whose result depends on what the class path holds under a name. */
    private static final List<String> LOOKUP_BY_NAME = List.of(
            "java.lang.Class#forName(", "java.lang.ClassLoader#loadClass(", "java.lang.ClassLoader#getResource",
            "java.lang.Class#getResource", "java.util.ServiceLoader#", "java.lang.invoke.MethodHandles$Lookup#find",
            "java.lang.reflect.Method#invoke(", "java.lang.reflect.Constructor#newInstance(",
            "java.lang.reflect.Field#get", "java.lang.reflect.Field#set", "java.lang.Class#getMethod",
            "java.lang.Class#getDeclaredMethod", "java.lang.Class#getField", "java.lang.Class#getDeclaredField",
            "java.lang.Class#getConstructor", "java.lang.Class#getDeclaredConstructor");

    /**
     * The outcome of an audit.
     *
     * @param unknown members not in the catalog, each with one of its callers
     * @param overridable {@code CLOSED} or {@code EXPLICIT} members called virtually where a
     *                    subclass can override them, each with one of its callers
     * @param lookupByName members that look something up by name but are not {@code AMBIENT},
     *                     each with one of its callers
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
            Map<Member, String> lookupByName,
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
            return unknown.isEmpty() && overridable.isEmpty() && lookupByName.isEmpty() && unapproved.isEmpty()
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
            section(out, "Members missing from " + catalogName + ". File each under the effect its API contract"
                    + " gives it (CLOSED, EXPLICIT, DELEGATED, AMBIENT):", entries(unknown, "used by"));
            section(out, "These are CLOSED or EXPLICIT in " + catalogName + ", but a subclass can override them"
                    + " and they are called virtually, so the receiver chooses what runs. Move them to [DELEGATED]:",
                    entries(overridable, "called by"));
            section(out, "These look something up by name, so what they find depends on the class path."
                    + " Move them to [AMBIENT] in " + catalogName + ":", entries(lookupByName, "used by"));
            section(out, "Decoder code must not read ambient state itself, nor through Raoh's own methods;"
                    + " these cannot be approved:", ambientInDecoder.entrySet().stream()
                    .map(e -> e.getKey() + "\n      reached by " + e.getValue()).toList());
            section(out, "Uses without an approval in " + approvalsName + ". File each under a [reason] that"
                    + " says why it is acceptable here:", entries(unapproved, ""));
            section(out, "Approvals in " + approvalsName + " that match no use any more; remove them:",
                    stale.stream().map(Object::toString).toList());
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
        var lookupByName = new TreeMap<Member, String>(BY_TEXT);
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
            if (effect.get() != Effect.AMBIENT && looksUpByName(edge.callee())) {
                lookupByName.putIfAbsent(edge.callee(), edge.caller());
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

        var reach = new AmbientReach(scan.graph(), hierarchy, isInternal);
        var starts = scan.graph().classes().entrySet().stream()
                .filter(c -> scan.audited().contains(c.getKey()) && isDecoderClass.test(c.getKey()))
                .flatMap(c -> c.getValue().stream())
                .sorted(BY_TEXT)
                .toList();
        var found = reach.reach(starts, catalog::effectOf);
        var ambientInDecoder = new TreeMap<Approvals.Use, String>(USE_ORDER);
        found.ambient().forEach((edge, path) -> ambientInDecoder.putIfAbsent(
                new Approvals.Use(edge.caller(), edge.callee()),
                path.stream().map(Member::toString).collect(Collectors.joining(" -> "))));
        // An ambient use no approval may cover is reported once, as reached from a decoder.
        ambientInDecoder.keySet().forEach(unapproved::remove);
        for (var edge : found.unknown()) {
            if (!used.contains(new Approvals.Use(edge.caller(), edge.callee()))) {
                // The audited module reports its own; this one is in a dependency its decoders reach.
                problems.add("decoder code reaches " + edge.caller() + " -> " + edge.callee()
                        + ", which the catalog does not list");
            }
        }
        problems.addAll(reach.problems());

        var stale = new TreeSet<Approvals.Use>(USE_ORDER);
        for (var use : approvals.uses()) {
            if (!used.contains(use)) {
                stale.add(use);
            }
        }
        return new Report(unknown, overridable, lookupByName, unapproved, ambientInDecoder, stale, needless, problems);
    }

    private static boolean looksUpByName(Member member) {
        var text = member.toString();
        return LOOKUP_BY_NAME.stream().anyMatch(text::startsWith);
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
