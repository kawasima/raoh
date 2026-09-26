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
 * audited classes, their compile classpath and the JDK the build compiles against. Methods are
 * matched by their whole descriptor, return type included, as the JVM matches them.
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
        if (member.isField() || member.isConstructor() || member.owner().endsWith("[]")) {
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
        if (member.isField() || member.isConstructor() || member.owner().endsWith("[]")) {
            return Optional.empty();
        }
        return find(load(member.owner()), member).map(m -> m.getDeclaringClass().getName());
    }

    /**
     * Whether a value of class {@code sub} is also a {@code sup}.
     *
     * @param sub the binary name of the class
     * @param sup the binary name of the possible supertype
     * @return {@code true} if {@code sup} is {@code sub} or one of its supertypes
     */
    public boolean isSubtype(String sub, String sup) {
        if (sup.endsWith("[]") || sub.endsWith("[]")) {
            return sub.equals(sup) || sup.equals("java.lang.Object");
        }
        return load(sup).isAssignableFrom(load(sub));
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

    /** The method {@code type} declares with the member's exact descriptor. */
    private static Optional<Method> declared(Class<?> type, Member member) {
        for (var method : type.getDeclaredMethods()) {
            if (method.getName().equals(member.name())
                    && method.getReturnType().getTypeName().equals(member.type())
                    && Arrays.stream(method.getParameterTypes()).map(Class::getTypeName).toList()
                            .equals(member.parameters())) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }
}
