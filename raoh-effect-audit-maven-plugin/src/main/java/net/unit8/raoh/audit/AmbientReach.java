package net.unit8.raoh.audit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Finds {@link Effect#AMBIENT} uses that decoder code reaches through the audited code base's
 * own methods.
 *
 * <p>A decoder that calls an internal helper which reads the default locale acquires the locale
 * as surely as one that reads it itself, so the walk starts from every method of an audited
 * decoder class and follows the {@link CallGraph}:
 * <ul>
 *   <li>a direct call to its target, resolved up the internal superclass chain;</li>
 *   <li>a virtual call on an internal type to every internal class that is a subtype of it and
 *       declares the method with the same descriptor;</li>
 *   <li>a virtual call on an external type ({@code Object#toString()}, {@code Function#apply}) to
 *       every internal class that is a subtype of it and declares the method, since the receiver
 *       may be one of Raoh's own objects;</li>
 *   <li>a lambda's body from the place the lambda is created, and a class's static initializer
 *       from any reference to the class.</li>
 * </ul>
 * The walk never enters external code, so it stays exact and finite. Code a caller of Raoh
 * supplies, such as a decoder they pass in, is configuration and is not followed.
 */
final class AmbientReach {

    private final CallGraph graph;
    private final Hierarchy hierarchy;
    private final Map<List<String>, List<Member>> implementations = new HashMap<>();
    private final Map<String, Boolean> subtypes = new HashMap<>();
    private final List<String> problems = new ArrayList<>();

    AmbientReach(CallGraph graph, Hierarchy hierarchy) {
        this.graph = graph;
        this.hierarchy = hierarchy;
        for (var info : graph.classes().values()) {
            for (var method : info.methods()) {
                implementations.computeIfAbsent(signature(method), k -> new ArrayList<>()).add(method);
            }
        }
    }

    /**
     * Walks from the given methods and returns each ambient use reached, with the path to it.
     *
     * @param starts the methods to walk from
     * @param isAmbient whether an external edge uses an {@code AMBIENT} member
     * @return each reached ambient use, keyed by the edge, with the methods from a start to its caller
     */
    Map<Edge, List<Member>> reach(List<Member> starts, Predicate<Edge> isAmbient) {
        var parent = new LinkedHashMap<Member, Member>();
        var queue = new ArrayDeque<Member>();
        for (var start : starts) {
            if (!parent.containsKey(start)) {
                parent.put(start, start);
                queue.add(start);
            }
        }
        var found = new LinkedHashMap<Edge, List<Member>>();
        while (!queue.isEmpty()) {
            var method = queue.poll();
            for (var edge : graph.externalOf(method)) {
                if (isAmbient.test(edge)) {
                    found.putIfAbsent(edge, path(parent, method));
                }
                if (edge.virtual()) {
                    for (var target : implementationsOf(edge.callee())) {
                        visit(parent, queue, method, target);
                    }
                }
            }
            for (var call : graph.callsOf(method)) {
                for (var target : resolve(call)) {
                    visit(parent, queue, method, target);
                }
            }
        }
        return found;
    }

    /**
     * What the walk could not resolve; the audit fails on any of these.
     *
     * @return the problems found so far
     */
    List<String> problems() {
        return List.copyOf(problems);
    }

    private static void visit(Map<Member, Member> parent, ArrayDeque<Member> queue, Member from, Member to) {
        if (!parent.containsKey(to)) {
            parent.put(to, from);
            queue.add(to);
        }
    }

    private static List<Member> path(Map<Member, Member> parent, Member end) {
        var path = new ArrayList<Member>();
        for (var at = end; ; at = parent.get(at)) {
            path.addFirst(at);
            if (Objects.equals(parent.get(at), at)) {
                return path;
            }
        }
    }

    /** The internal methods a call can run. */
    private List<Member> resolve(CallGraph.Call call) {
        var targets = new ArrayList<Member>();
        declaredOrInherited(call.target()).ifPresent(targets::add);
        if (call.virtual()) {
            targets.addAll(implementationsOf(call.target()));
        }
        return targets;
    }

    /** The internal method a direct call runs: the target, or the one it inherits from an internal superclass. */
    private Optional<Member> declaredOrInherited(Member target) {
        var info = graph.classes().get(target.owner());
        if (info == null) {
            return Optional.empty();
        }
        if (info.methods().contains(target)) {
            return Optional.of(target);
        }
        if (target.name().equals("<clinit>") || target.isConstructor()) {
            return Optional.empty();
        }
        try {
            return hierarchy.declaringClass(target)
                    .map(owner -> new Member(owner, target.name(), target.parameters(), target.type()))
                    .filter(m -> graph.classes().containsKey(m.owner()));
        } catch (IllegalStateException e) {
            // Fail closed: a call the walk cannot resolve is reported, not assumed harmless.
            problems.add("cannot resolve " + target + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /** Every internal method with the member's descriptor whose class is a subtype of the member's owner. */
    private List<Member> implementationsOf(Member member) {
        if (member.isField() || member.isConstructor()) {
            return List.of();
        }
        var result = new ArrayList<Member>();
        for (var candidate : implementations.getOrDefault(signature(member), List.of())) {
            if (isSubtype(candidate.owner(), member.owner())) {
                result.add(candidate);
            }
        }
        return result;
    }

    private boolean isSubtype(String sub, String sup) {
        return subtypes.computeIfAbsent(sub + " <: " + sup, k -> {
            try {
                return hierarchy.isSubtype(sub, sup);
            } catch (IllegalStateException e) {
                problems.add("cannot tell whether " + sub + " is a " + sup + ": " + e.getMessage());
                return false;
            }
        });
    }

    private static List<String> signature(Member method) {
        var key = new ArrayList<String>();
        key.add(method.name());
        key.add(method.type());
        key.addAll(method.parameters());
        return key;
    }
}
