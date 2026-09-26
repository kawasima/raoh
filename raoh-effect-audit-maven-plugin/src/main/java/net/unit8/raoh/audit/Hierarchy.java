package net.unit8.raoh.audit;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;

/**
 * Answers questions about the class hierarchy of the audited code and its dependencies.
 *
 * <p>Classes are loaded without initialization from the loader given, which should see the
 * audited classes, their compile classpath and the JDK the build compiles against.
 */
public final class Hierarchy {

    private final ClassLoader loader;

    /**
     * Creates a hierarchy over the classes {@code loader} can see.
     *
     * @param loader the loader to look classes up with
     */
    public Hierarchy(ClassLoader loader) {
        this.loader = loader;
    }

    /**
     * Whether a virtual use of {@code member} can run code other than the implementation its
     * owner declares or inherits: the owner is not final, and the method is not static, private
     * or final. A constructor, a field and an array method are never overridable.
     *
     * @param member the member a call site names
     * @return {@code true} if a subclass of the owner can supply the implementation
     * @throws IllegalStateException if the owner or the method cannot be found
     */
    public boolean isOverridable(Member member) {
        if (member.isField() || member.name().equals("<init>") || member.owner().endsWith("[]")) {
            return false;
        }
        var owner = load(member.owner());
        if (Modifier.isFinal(owner.getModifiers())) {
            return false;
        }
        var method = find(owner, member).orElseThrow(() -> new IllegalStateException(
                "cannot resolve " + member + " in " + owner.getName()));
        int modifiers = method.getModifiers();
        return !Modifier.isStatic(modifiers) && !Modifier.isPrivate(modifiers) && !Modifier.isFinal(modifiers);
    }

    /**
     * The class that declares {@code member} when it is looked up from its owner: the owner
     * itself, or the superclass or interface it inherits the method from.
     *
     * @param member the member a call site names
     * @return the declaring class's binary name, or empty for a field or constructor, which
     *         are not inherited, or for a method the owner does not have
     */
    public Optional<String> declaringClass(Member member) {
        if (member.isField() || member.name().equals("<init>") || member.owner().endsWith("[]")) {
            return Optional.empty();
        }
        return find(load(member.owner()), member).map(m -> m.getDeclaringClass().getName());
    }

    private Class<?> load(String name) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            throw new IllegalStateException("cannot load " + name + " to inspect its hierarchy", e);
        }
    }

    /** Resolves a method the way the JVM does: the class chain first, then the interfaces. */
    private static Optional<Method> find(Class<?> owner, Member member) {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            var found = declared(c, member);
            if (found.isPresent()) {
                return found;
            }
        }
        var queue = new ArrayDeque<Class<?>>();
        var seen = new HashSet<Class<?>>();
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            queue.addAll(Arrays.asList(c.getInterfaces()));
        }
        while (!queue.isEmpty()) {
            var type = queue.poll();
            if (!seen.add(type)) {
                continue;
            }
            var found = declared(type, member);
            if (found.isPresent()) {
                return found;
            }
            queue.addAll(Arrays.asList(type.getInterfaces()));
        }
        // An interface type has Object's public methods too (JLS 9.2).
        return owner.isInterface() ? declared(Object.class, member) : Optional.empty();
    }

    /** The method {@code type} declares with the member's name and parameters, preferring a non-bridge one. */
    private static Optional<Method> declared(Class<?> type, Member member) {
        Method bridge = null;
        for (var method : type.getDeclaredMethods()) {
            if (method.getName().equals(member.name())
                    && Arrays.stream(method.getParameterTypes()).map(Class::getTypeName).toList()
                            .equals(member.parameters())) {
                if (!method.isBridge()) {
                    return Optional.of(method);
                }
                bridge = method;
            }
        }
        return Optional.ofNullable(bridge);
    }
}
