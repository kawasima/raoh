package net.unit8.raoh.audit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The calls between methods of the audited code base itself, across modules.
 *
 * <p>Only code the build owns is here; the JDK and other libraries are never entered, which is
 * what keeps the graph finite and exact. Calls leave it through external {@link Edge}s.
 */
public final class CallGraph {

    /**
     * A call from one internal method to another.
     *
     * @param target the method the bytecode names
     * @param virtual whether the implementation is chosen by the receiver at run time
     */
    public record Call(Member target, boolean virtual) {}

    /**
     * What the graph knows about one internal class.
     *
     * @param name its binary name
     * @param methods the methods it declares with code
     */
    public record ClassInfo(String name, Set<Member> methods) {}

    private final Map<Member, Set<Call>> calls = new HashMap<>();
    private final Map<Member, List<Edge>> external = new HashMap<>();
    private final Map<String, ClassInfo> classes = new HashMap<>();

    void addClass(ClassInfo info) {
        classes.put(info.name(), info);
    }

    void addCall(Member caller, Call call) {
        calls.computeIfAbsent(caller, k -> new LinkedHashSet<>()).add(call);
    }

    void addExternal(Member caller, Edge edge) {
        external.computeIfAbsent(caller, k -> new ArrayList<>()).add(edge);
    }

    /**
     * The internal calls a method makes.
     *
     * @param method an internal method
     * @return its calls, empty if it makes none or is unknown
     */
    public Set<Call> callsOf(Member method) {
        return calls.getOrDefault(method, Set.of());
    }

    /**
     * The external uses a method makes.
     *
     * @param method an internal method
     * @return its external edges, empty if none
     */
    public List<Edge> externalOf(Member method) {
        return external.getOrDefault(method, List.of());
    }

    /**
     * The internal classes known to the graph.
     *
     * @return class name to class info
     */
    public Map<String, ClassInfo> classes() {
        return Map.copyOf(classes);
    }
}
