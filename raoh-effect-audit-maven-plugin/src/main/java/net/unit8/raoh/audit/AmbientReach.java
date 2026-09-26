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
 *   <li>a virtual call on an internal type to the method each internal subtype of it runs for
 *       that call, found the way the JVM finds it, so an implementation a subtype inherits from
 *       a superclass that is not itself a subtype of the called type is included;</li>
 *   <li>a lambda's body from the place the lambda is created;</li>
 *   <li>from any reference to a class, the static initializers the JVM runs to initialize it:
 *       its own and those of its internal superclasses and superinterfaces, so a static member
 *       reached through a subclass still reaches the initializer of the class declaring it;</li>
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
    private final Map<Member, List<Member>> implementations = new HashMap<>();
    private final Map<String, List<Member>> callbacks = new HashMap<>();
    private final List<String> problems = new ArrayList<>();

    AmbientReach(CallGraph graph, Hierarchy hierarchy, Predicate<String> internal) {
        this.graph = graph;
        this.hierarchy = hierarchy;
        this.internal = internal;
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
        if (call.target().name().equals("<clinit>")) {
            return initializers(call.target().owner());
        }
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
        if (methods.contains(target)) {
            return Optional.of(target);
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

    /**
     * The internal code a virtual call can run: for every internal class that is a subtype of
     * the called type, the method the JVM selects when that class is the receiver. The lookup
     * starts at the receiver class, not at the classes that declare the method: in
     * {@code final class Impl extends Base implements Resolver}, a call to
     * {@code Resolver#resolve()} runs {@code Base#resolve()} although {@code Base} is not a
     * {@code Resolver}.
     */
    private List<Member> implementationsOf(Member member) {
        return implementations.computeIfAbsent(member, m -> {
            var result = new LinkedHashSet<Member>();
            for (var receiver : graph.classes().keySet()) {
                if (!isSubtype(receiver, m.owner())) {
                    continue;
                }
                Optional<String> declaring;
                try {
                    declaring = hierarchy.declaringClass(new Member(receiver, m.name(), m.parameters(), m.type()));
                } catch (IllegalStateException e) {
                    problems.add("cannot resolve " + m + " for receiver " + receiver + ": " + e.getMessage());
                    continue;
                }
                if (declaring.isEmpty()) {
                    continue;
                }
                if (!internal.test(declaring.get())) {
                    // The code that runs is external, but the call site names an internal type, so
                    // neither the catalog nor this walk has seen it.
                    problems.add(m + " runs " + declaring.get() + "'s implementation for receiver " + receiver
                            + ", which the audit does not see; call it through the external type");
                    continue;
                }
                var impl = new Member(declaring.get(), m.name(), m.parameters(), m.type());
                // A method without code (abstract, or an interface's) is not what runs.
                if (graph.classes().getOrDefault(impl.owner(), Set.of()).contains(impl)) {
                    result.add(impl);
                }
            }
            return List.copyOf(result);
        });
    }

    /**
     * The static initializers that initializing {@code type} can run: its own, and those of every
     * internal class or interface it is a subtype of. The JVM initializes a class's superclasses
     * first and some of its superinterfaces; taking all of them keeps the walk on the safe side.
     */
    private List<Member> initializers(String type) {
        if (!graph.classes().containsKey(type)) {
            problems.add("initializes " + type + ", which is not among the classes scanned");
            return List.of();
        }
        var result = new ArrayList<Member>();
        for (var entry : graph.classes().entrySet()) {
            if (isSubtype(type, entry.getKey())) {
                var initializer = new Member(entry.getKey(), "<clinit>", List.of(), "void");
                if (entry.getValue().contains(initializer)) {
                    result.add(initializer);
                }
            }
        }
        return result;
    }

    /**
     * The methods external code can call on an instance of a constructed internal class: those
     * it or an internal superclass declares that override a method of an external supertype.
     */
    private List<Member> callbacks(String type) {
        return callbacks.computeIfAbsent(type, this::findCallbacks);
    }

    private List<Member> findCallbacks(String type) {
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
}
