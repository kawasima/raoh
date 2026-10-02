package net.unit8.raoh.testing;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Keeps a public varargs method or constructor from sharing its name with another one, unless the
 * pair is listed with the reason no call the API's contract allows changes meaning because of it.
 *
 * <p>Java resolves a call by fixed-arity applicability first, strict and then loose, and only then
 * by variable arity, taking the most specific method at each phase. So a method added beside a
 * varargs method of the same name takes over every existing call that it also accepts: a
 * fixed-arity one at an earlier phase, boxing and primitive widening included, and another varargs
 * one when it is more specific. A call compiled against the old method keeps calling it, but the
 * same source compiled again calls the new one, with no error or warning. {@code containsAll(T...)}
 * beside a {@code containsAll(List, String)} would be one: with {@code T} of {@code Object},
 * {@code containsAll(someList, "tag")}, two elements to require, would become a list and a message.
 * So would {@code f(long...)} beside an {@code f(int)}, and {@code f(Object...)} beside an
 * {@code f(String, Object...)}. Constructors are chosen the same way.
 *
 * <p>This is a policy, not a model of overload resolution: every such pair is refused, and a pair
 * a reviewer has found safe is listed with {@link Allowed}, its reason written down. Safe means
 * that no call the API's contract allows changes meaning because of the pair: either no such call
 * fits both, or every one that does has gone to the same method since both have been there. The
 * contract is what counts, not every call that compiles: Raoh's packages are {@code @NullMarked},
 * so a call that passes {@code null} where the contract does not allow it is outside it, though
 * {@code null} fits every reference parameter. {@code oneOf(null, "m")} fits both
 * {@code oneOf(String...)} and {@code oneOf(Collection, String)}, and the pair is still safe.
 *
 * <p>The methods are the ones the source declares: a class's public methods, its own and those it
 * inherits from its superclasses and interfaces, whether or not those types are public, with no
 * method the compiler generated, such as a bridge. Pairs are looked for in two places, because
 * Java puts a call's candidates together in two ways:
 *
 * <ul>
 *   <li>a class: its methods of one name, static and instance together, and its constructors;</li>
 *   <li>a static-import group: the classes a user imports on demand together, such as
 *       {@code ObjectDecoders.*}, {@code MapDecoders.*} and {@code Decoders.*}, whose static
 *       methods of one name are one set of candidates. Only pairs from different classes are
 *       looked for there; a class's own are found with the class.</li>
 * </ul>
 *
 * <p>raoh, raoh-json and raoh-jooq, the modules Raoh's API compatibility covers, run this over
 * their own classes from a test. It throws {@link AssertionError}, which a test framework reports as
 * a failure.
 */
public final class PublicVarargsOverloads {

    /** The name a {@link Signature} gives the constructors of a class. */
    public static final String CONSTRUCTOR = "<init>";

    /**
     * A method or constructor as the source declares it.
     *
     * @param owner      the class that declares it
     * @param name       its name, or {@link #CONSTRUCTOR}
     * @param parameters its parameter types, a varargs parameter as its array type
     */
    public record Signature(Class<?> owner, String name, List<Class<?>> parameters) {
        /**
         * Copies the parameter types.
         *
         * @param owner      the class that declares it
         * @param name       its name, or {@link #CONSTRUCTOR}
         * @param parameters its parameter types, a varargs parameter as its array type
         */
        public Signature {
            parameters = List.copyOf(parameters);
        }

        /**
         * A signature.
         *
         * @param owner      the class that declares it
         * @param name       its name, or {@link #CONSTRUCTOR}
         * @param parameters its parameter types, a varargs parameter as its array type
         * @return the signature
         */
        public static Signature of(Class<?> owner, String name, Class<?>... parameters) {
            return new Signature(owner, name, List.of(parameters));
        }

        static Signature of(Executable e) {
            return new Signature(e.getDeclaringClass(), nameOf(e), List.of(e.getParameterTypes()));
        }

        @Override
        public String toString() {
            return owner.getName() + "#" + name + parameters.stream().map(Class::getSimpleName).toList();
        }
    }

    /**
     * Two methods or constructors of one name, at least one varargs, that may share the name, and
     * why no call the API's contract allows changes meaning because of them. The order of the two
     * does not matter.
     *
     * @param one    one of them
     * @param other  the other
     * @param reason why no call the API's contract allows changes meaning because of them
     */
    public record Allowed(Signature one, Signature other, String reason) {
        /**
         * Requires a reason.
         *
         * @param one    one of them
         * @param other  the other
         * @param reason why no call the API's contract allows changes meaning because of them
         * @throws IllegalArgumentException if the reason is blank
         */
        public Allowed {
            if (reason.isBlank()) {
                throw new IllegalArgumentException("an allowed pair needs its reason");
            }
        }

        private boolean is(Pair pair) {
            var a = Signature.of(pair.one());
            var b = Signature.of(pair.other());
            return a.equals(one) && b.equals(other) || a.equals(other) && b.equals(one);
        }

        @Override
        public String toString() {
            return one + " beside " + other;
        }
    }

    /**
     * Two executables of one name, at least one of them varargs, in a canonical order.
     *
     * @param one   the first, by its generic signature
     * @param other the second
     */
    record Pair(Executable one, Executable other) {
        static Pair of(Executable a, Executable b) {
            return a.toGenericString().compareTo(b.toGenericString()) <= 0 ? new Pair(a, b) : new Pair(b, a);
        }

        @Override
        public String toString() {
            return one.toGenericString() + " beside " + other.toGenericString();
        }
    }

    private PublicVarargsOverloads() {
    }

    /**
     * Fails if the public classes of the module {@code anchor} is in, or the static-import groups,
     * have a pair that is not allowed, or if an allowed pair is not there.
     *
     * @param anchor             a class of the module, whose classes are read from where it was loaded
     * @param staticImportGroups the groups of classes a user imports on demand together
     * @param allowed            the pairs found safe
     * @throws AssertionError        if a pair is not allowed or an allowed pair is gone
     * @throws IllegalStateException if the module's classes cannot be read
     */
    public static void assertNone(Class<?> anchor, List<List<Class<?>>> staticImportGroups, Allowed... allowed) {
        List<String> problems = problems(publicClasses(anchor), staticImportGroups, allowed);
        if (!problems.isEmpty()) {
            throw new AssertionError("A method beside a varargs method of its name can take over its calls, "
                    + "changing what they mean without an error. Give it a name of its own, or list the pair "
                    + "with the reason no call the API's contract allows changes meaning because of it:\n  "
                    + String.join("\n  ", problems));
        }
    }

    /**
     * What is wrong with these classes and groups: each pair not allowed, and each allowed pair
     * not there.
     *
     * @param classes            the classes
     * @param staticImportGroups the groups of classes a user imports on demand together
     * @param allowed            the pairs found safe
     * @return the problems, empty when there are none
     */
    static List<String> problems(List<Class<?>> classes, List<List<Class<?>>> staticImportGroups,
                                 Allowed... allowed) {
        Set<Pair> pairs = new LinkedHashSet<>();
        for (Class<?> c : classes) {
            pairs.addAll(pairsIn(methods(c)));
            pairs.addAll(pairsIn(constructors(c)));
        }
        for (List<Class<?>> group : staticImportGroups) {
            pairs.addAll(staticImportPairs(group));
        }
        List<String> problems = new ArrayList<>();
        Set<Allowed> used = new LinkedHashSet<>();
        for (Pair pair : pairs) {
            Allowed match = Arrays.stream(allowed).filter(a -> a.is(pair)).findFirst().orElse(null);
            if (match == null) {
                problems.add(pair.toString());
            } else {
                used.add(match);
            }
        }
        for (Allowed a : allowed) {
            if (!used.contains(a)) {
                problems.add("allowed, but no such pair is there any more: " + a);
            }
        }
        return problems;
    }

    /**
     * The pairs of one name, at least one varargs, among these executables.
     *
     * @param executables methods or constructors
     * @return the pairs
     */
    static Set<Pair> pairsIn(List<? extends Executable> executables) {
        Set<Pair> pairs = new LinkedHashSet<>();
        for (int i = 0; i < executables.size(); i++) {
            for (int j = i + 1; j < executables.size(); j++) {
                Executable a = executables.get(i);
                Executable b = executables.get(j);
                if (nameOf(a).equals(nameOf(b)) && (a.isVarArgs() || b.isVarArgs())) {
                    pairs.add(Pair.of(a, b));
                }
            }
        }
        return pairs;
    }

    /**
     * The pairs a static-import group adds: static methods of one name from different classes, at
     * least one varargs.
     *
     * @param group the classes imported on demand together
     * @return the pairs
     */
    static Set<Pair> staticImportPairs(List<Class<?>> group) {
        List<Method> statics = group.stream()
                .flatMap(c -> methods(c).stream())
                .filter(m -> Modifier.isStatic(m.getModifiers()))
                .distinct()
                .toList();
        Set<Pair> pairs = new LinkedHashSet<>();
        for (Pair pair : pairsIn(statics)) {
            if (pair.one().getDeclaringClass() != pair.other().getDeclaringClass()) {
                pairs.add(pair);
            }
        }
        return pairs;
    }

    /**
     * The public methods the source declares for a class: its own, and those it inherits from its
     * superclasses and interfaces, whether or not those are public, an override replacing what it
     * overrides. Static methods of an interface are not inherited. No method the compiler generated
     * is included, so a class whose public methods come from a package-private superclass is seen
     * through those methods, not through the bridges the compiler adds for them.
     *
     * @param c the class
     * @return its methods
     */
    static List<Method> methods(Class<?> c) {
        Map<String, Method> bySignature = new LinkedHashMap<>();
        Deque<Class<?>> types = new ArrayDeque<>();
        Set<Class<?>> seen = new LinkedHashSet<>();
        types.add(c);
        while (!types.isEmpty()) {
            Class<?> t = types.removeFirst();
            if (t == Object.class || !seen.add(t)) {
                continue;
            }
            Method[] declared = t.getDeclaredMethods();
            Arrays.sort(declared, Comparator.comparing(Method::toGenericString));
            for (Method m : declared) {
                boolean inherited = t != c;
                if (!Modifier.isPublic(m.getModifiers()) || m.isSynthetic() || m.isBridge()
                        || inherited && t.isInterface() && Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                bySignature.putIfAbsent(m.getName() + Arrays.toString(m.getParameterTypes()), m);
            }
            if (t.getSuperclass() != null) {
                types.add(t.getSuperclass());
            }
            types.addAll(Arrays.asList(t.getInterfaces()));
        }
        return List.copyOf(bySignature.values());
    }

    private static List<Constructor<?>> constructors(Class<?> c) {
        return Arrays.stream(c.getConstructors())
                .filter(k -> !k.isSynthetic())
                .sorted(Comparator.comparing(Constructor::toGenericString))
                .<Constructor<?>>map(k -> k)
                .toList();
    }

    private static String nameOf(Executable executable) {
        return executable instanceof Method ? executable.getName() : CONSTRUCTOR;
    }

    private static List<Class<?>> publicClasses(Class<?> anchor) {
        Path root;
        try {
            root = Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("the classes of " + anchor.getName() + " are not in a directory: " + root);
        }
        List<Class<?>> classes = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
                String name = root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), ".")
                        .replaceAll("\\.class$", "");
                Class<?> c = Class.forName(name, false, anchor.getClassLoader());
                if (isPublic(c)) {
                    classes.add(c);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        if (classes.isEmpty()) {
            throw new IllegalStateException("no public class found under " + root);
        }
        return classes;
    }

    /**
     * Whether a class is reachable from outside.
     *
     * @param c the class
     * @return {@code true} if it is public, named, and every class it is nested in is public
     */
    private static boolean isPublic(Class<?> c) {
        for (Class<?> k = c; k != null; k = k.getEnclosingClass()) {
            if (!Modifier.isPublic(k.getModifiers()) || k.isAnonymousClass() || k.isLocalClass()) {
                return false;
            }
        }
        return true;
    }
}
