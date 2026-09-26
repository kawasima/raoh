package net.unit8.raoh.audit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Finds what decoder code reaches through the audited code base's own methods.
 *
 * <p>A decoder that calls an internal helper which reads the default locale acquires the locale
 * as surely as one that reads it itself, so the walk starts from every method of an audited
 * decoder class and follows the {@link CallGraph}:
 * <ul>
 *   <li>a direct call to its target, resolved up the internal superclass chain;</li>
 *   <li>a virtual call on an internal type to every internal class that is a subtype of it and
 *       declares the method with the same descriptor;</li>
 *   <li>a lambda's body from the place the lambda is created, and a class's static initializer
 *       from any reference to the class;</li>
 *   <li>once a reached method constructs an internal class, every method of that class (or of an
 *       internal superclass) that overrides one an external supertype declares, since the object
 *       may be handed to external code that calls it back: a {@code HashMap} calls a key's
 *       {@code hashCode}, a sort calls a comparator's {@code compare}.</li>
 * </ul>
 * The walk never enters external code, so it stays exact and finite. Code a caller of Raoh
 * supplies, such as a decoder they pass in, is configuration and is not followed. What the walk
 * cannot resolve is reported rather than assumed harmless.
 */
final class AmbientReach {

    /**
     * What a walk found.
     *
     * @param ambient each reached {@code AMBIENT} use, with the methods from a start to its caller
     * @param unknown reached uses of members the catalog does not list
     */
    record Found(Map<Edge, List<Member>> ambient, Set<Edge> unknown) {}

    private final CallGraph graph;
    private final Hierarchy hierarchy;
    private final Predicate<String> internal;
    private final Map<List<String>, List<Member>> bySignature = new HashMap<>();
    private final Map<Member, List<Member>> implementations = new HashMap<>();
    private final List<String> problems = new ArrayList<>();

    AmbientReach(CallGraph graph, Hierarchy hierarchy, Predicate<String> internal) {
        this.graph = graph;
        this.hierarchy = hierarchy;
        this.internal = internal;
        for (var methods : graph.classes().values()) {
            for (var method : methods) {
                bySignature.computeIfAbsent(signature(method), k -> new ArrayList<>()).add(method);
            }
        }
    }

    /**
     * Walks from the given methods.
     *
     * @param starts the methods to walk from
     * @param effect the catalog's effect of a member, empty if it does not list it
     * @return the ambient and uncatalogued uses reached
     */
    Found reach(List<Member> starts, Function<Member, Optional<Effect>> effect) {
        var parent = new LinkedHashMap<Member, Member>();
        var queue = new ArrayDeque<Member>();
        var constructed = new HashSet<String>();
        for (var start : starts) {
            visit(parent, queue, start, start);
        }
        var ambient = new LinkedHashMap<Edge, List<Member>>();
        var unknown = new LinkedHashSet<Edge>();
        while (!queue.isEmpty()) {
            var method = queue.poll();
            for (var edge : graph.externalOf(method)) {
                var e = effect.apply(edge.callee());
                if (e.isEmpty()) {
                    unknown.add(edge);
                } else if (e.get() == Effect.AMBIENT) {
                    ambient.putIfAbsent(edge, path(parent, method));
                }
            }
            for (var call : graph.callsOf(method)) {
                for (var target : resolve(call)) {
                    visit(parent, queue, method, target);
                }
                if (call.target().isConstructor() && constructed.add(call.target().owner())) {
                    for (var callback : callbacks(call.target().owner())) {
                        visit(parent, queue, method, callback);
                    }
                }
            }
        }
        return new Found(ambient, unknown);
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
            path.add(at);
            if (Objects.equals(parent.get(at), at)) {
                return path.reversed();
            }
        }
    }

    /** The internal methods a call can run. */
    private List<Member> resolve(CallGraph.Call call) {
        var targets = new ArrayList<Member>();
        declaredOrInherited(call).ifPresent(targets::add);
        if (call.virtual()) {
            targets.addAll(implementationsOf(call.target()));
        }
        return targets;
    }

    /**
     * The code a call runs when the receiver is exactly the named class: the target itself, or
     * the method it inherits from an internal superclass. Empty for a class without a static
     * initializer, and for a virtual call to a method without code, whose implementations come
     * from {@link #implementationsOf}.
     */
    private Optional<Member> declaredOrInherited(CallGraph.Call call) {
        var target = call.target();
        var methods = graph.classes().get(target.owner());
        if (methods == null) {
            problems.add("calls " + target + ", but " + target.owner() + " is not among the classes scanned");
            return Optional.empty();
        }
        if (methods.contains(target) || target.name().equals("<clinit>")) {
            return methods.contains(target) ? Optional.of(target) : Optional.empty();
        }
        Optional<String> declaring;
        try {
            declaring = hierarchy.declaringClass(target);
        } catch (IllegalStateException e) {
            problems.add("cannot resolve " + target + ": " + e.getMessage());
            return Optional.empty();
        }
        if (declaring.isEmpty()) {
            problems.add("cannot find the method " + target + " calls");
            return Optional.empty();
        }
        var inherited = new Member(declaring.get(), target.name(), target.parameters(), target.type());
        if (graph.classes().getOrDefault(inherited.owner(), Set.of()).contains(inherited)) {
            return Optional.of(inherited);
        }
        if (!call.virtual()) {
            problems.add("cannot find the code " + target + " runs");
        }
        return Optional.empty();
    }

    /** Every internal method with the member's descriptor whose class is a subtype of the member's owner. */
    private List<Member> implementationsOf(Member member) {
        return implementations.computeIfAbsent(member, m -> {
            var result = new ArrayList<Member>();
            for (var candidate : bySignature.getOrDefault(signature(m), List.of())) {
                if (isSubtype(candidate.owner(), m.owner())) {
                    result.add(candidate);
                }
            }
            return result;
        });
    }

    /**
     * The methods external code can call on an instance of a constructed internal class: those
     * it or an internal superclass declares that override a method of an external supertype.
     */
    private List<Member> callbacks(String type) {
        var result = new ArrayList<Member>();
        for (var entry : graph.classes().entrySet()) {
            if (!isSubtype(type, entry.getKey())) {
                continue;
            }
            for (var method : entry.getValue()) {
                try {
                    if (hierarchy.overridesExternal(type, method, internal)) {
                        result.add(method);
                    }
                } catch (IllegalStateException e) {
                    problems.add("cannot tell whether " + method + " overrides an external method: " + e.getMessage());
                }
            }
        }
        return result;
    }

    private boolean isSubtype(String sub, String sup) {
        try {
            return hierarchy.isSubtype(sub, sup);
        } catch (IllegalStateException e) {
            problems.add("cannot tell whether " + sub + " is a " + sup + ": " + e.getMessage());
            return false;
        }
    }

    private static List<String> signature(Member method) {
        var key = new ArrayList<String>();
        key.add(method.name());
        key.add(method.type());
        key.addAll(method.parameters());
        return key;
    }
}
