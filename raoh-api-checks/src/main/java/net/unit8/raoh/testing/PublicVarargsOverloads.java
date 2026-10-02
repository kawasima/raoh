package net.unit8.raoh.testing;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Keeps a public varargs method or constructor from sharing its name with another one, unless the
 * pair is listed with the reason no call the API's contract allows changes meaning because of it.
 *
 * <p>Java resolves a call by fixed-arity applicability first, strict and then loose, and only then
 * by variable arity, taking the most specific method at each phase. So a method added beside a
 * varargs method of the same name can take over existing calls: a fixed-arity one those it is
 * applicable to at an earlier phase than the varargs method was, boxing and primitive widening
 * included; another varargs one those it is more specific for. A call that passes the array itself
 * is fixed-arity for the varargs method too, so {@code f(new Object[0])} stays with
 * {@code f(Object...)} beside an {@code f(Serializable)}, while {@code f("x")} moves to it. A call
 * compiled against the old method keeps calling it, but the same source compiled again calls the
 * new one, with no error or warning.
 * {@code containsAll(T...)} beside a {@code containsAll(List, String)} would be one: with {@code T}
 * of {@code Object}, {@code containsAll(someList, "tag")}, two elements to require, would become a
 * list and a message. So would {@code f(long...)} beside an {@code f(int)}, and
 * {@code f(Object...)} beside an {@code f(String, Object...)}. Constructors are chosen the same way.
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
 * <p>It checks raoh, raoh-json and raoh-jooq, the modules Raoh's API compatibility covers, from the
 * tests of raoh-api-checks. It throws {@link AssertionError}, which a test framework reports as a
 * failure.
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
         * @throws IllegalArgumentException if the reason is blank, or the two signatures are the
         *                                  same or of different names
         */
        public Allowed {
            if (reason.isBlank()) {
                throw new IllegalArgumentException("an allowed pair needs its reason");
            }
            if (one.equals(other)) {
                throw new IllegalArgumentException("an allowed pair names one method twice: " + one);
            }
            if (!one.name().equals(other.name())) {
                throw new IllegalArgumentException("an allowed pair names methods of two names: " + one + ", " + other);
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
     * Fails if the public classes of the modules the anchors are in, or the static-import groups,
     * have a pair that is not allowed, if an allowed pair is not there, or if a pair is allowed
     * twice.
     *
     * @param anchors            a class of each module, whose classes are read from where it was loaded
     * @param staticImportGroups the groups of classes a user imports on demand together
     * @param allowed            the pairs found safe
     * @throws AssertionError        if a pair is not allowed, an allowed pair is gone or a pair is
     *                               allowed twice
     * @throws IllegalStateException if a module's classes cannot be read
     */
    public static void assertNone(List<Class<?>> anchors, List<List<Class<?>>> staticImportGroups,
                                  Allowed... allowed) {
        List<Class<?>> classes = anchors.stream().flatMap(a -> publicClasses(a).stream()).toList();
        List<String> problems = problems(classes, staticImportGroups, allowed);
        if (!problems.isEmpty()) {
            throw new AssertionError("A method beside a varargs method of its name can take over its calls, "
                    + "changing what they mean without an error. Give it a name of its own, or list the pair "
                    + "with the reason no call the API's contract allows changes meaning because of it:\n  "
                    + String.join("\n  ", problems));
        }
    }

    /**
     * What is wrong with these classes and groups: each pair not allowed, each allowed pair not
     * there, and each pair allowed twice.
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
        Set<Set<Signature>> listed = new LinkedHashSet<>();
        for (Allowed a : allowed) {
            if (!listed.add(Set.of(a.one(), a.other()))) {
                problems.add("allowed twice: " + a);
            }
        }
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
            if (!used.contains(a) && Arrays.stream(allowed).noneMatch(b -> b != a && used.contains(b)
                    && Set.of(a.one(), a.other()).equals(Set.of(b.one(), b.other())))) {
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
        Set<Pair> pairs = new LinkedHashSet<>();
        for (Pair pair : pairsIn(staticMethods(group))) {
            if (pair.one().getDeclaringClass() != pair.other().getDeclaringClass()) {
                pairs.add(pair);
            }
        }
        return pairs;
    }

    /**
     * The public static methods of a static-import group's classes, each once however many of them
     * have it.
     *
     * @param group the classes imported on demand together
     * @return the methods
     */
    static List<Method> staticMethods(List<Class<?>> group) {
        return group.stream()
                .flatMap(c -> methods(c).stream())
                .filter(m -> Modifier.isStatic(m.getModifiers()))
                .distinct()
                .toList();
    }

    /**
     * The public methods the source declares for a class: its own, and those it inherits from its
     * superclasses and interfaces, whether or not those are public. A method is left out when a
     * subtype of its class declares one that overrides it: of its name and of its parameter types
     * as the class sees them, with each type variable of a supertype replaced by the type the class
     * gives it, so {@code f(String...)} in a {@code Sub extends Base<String>} overrides
     * {@code Base<T>.f(T...)}. Of those unrelated types declare with one name and parameter types,
     * one varargs and one of fixed arity are kept, since a call can reach the class through either;
     * the same method reached along two paths is one. Static methods of
     * an interface are not inherited. No method the compiler generated is included, so a class
     * whose public methods come from a package-private superclass is seen through those methods,
     * not through the bridges the compiler adds for them.
     *
     * @param c the class
     * @return its methods
     */
    static List<Method> methods(Class<?> c) {
        Map<TypeVariable<?>, Type> bindings = bindings(c);
        Map<String, List<Method>> bySignature = new LinkedHashMap<>();
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
                String key = m.getName() + Arrays.stream(m.getGenericParameterTypes())
                        .map(type -> erasure(type, bindings).getName()).toList();
                List<Method> same = bySignature.computeIfAbsent(key, k -> new ArrayList<>());
                // A subtype's method overrides this one, and this one overrides a supertype's.
                if (same.stream().anyMatch(o -> t.isAssignableFrom(o.getDeclaringClass()))) {
                    continue;
                }
                same.removeIf(o -> o.getDeclaringClass().isAssignableFrom(t));
                // From unrelated types, one of each arity kind: the same method reached twice is one.
                if (same.stream().noneMatch(o -> o.isVarArgs() == m.isVarArgs())) {
                    same.add(m);
                }
            }
            if (t.getSuperclass() != null) {
                types.add(t.getSuperclass());
            }
            types.addAll(Arrays.asList(t.getInterfaces()));
        }
        return bySignature.values().stream().flatMap(List::stream).toList();
    }

    /**
     * The types a class gives the type variables of its supertypes, followed up the hierarchy.
     *
     * @param c the class
     * @return each type variable of a supertype, with the type {@code c} gives it
     */
    private static Map<TypeVariable<?>, Type> bindings(Class<?> c) {
        Map<TypeVariable<?>, Type> bindings = new LinkedHashMap<>();
        Deque<Type> types = new ArrayDeque<>();
        types.add(c);
        Set<Type> seen = new LinkedHashSet<>();
        while (!types.isEmpty()) {
            Type t = types.removeFirst();
            if (!seen.add(t)) {
                continue;
            }
            Class<?> raw = t instanceof ParameterizedType p ? (Class<?>) p.getRawType() : (Class<?>) t;
            if (t instanceof ParameterizedType p) {
                TypeVariable<?>[] variables = raw.getTypeParameters();
                Type[] arguments = p.getActualTypeArguments();
                for (int i = 0; i < variables.length; i++) {
                    bindings.putIfAbsent(variables[i], arguments[i]);
                }
            }
            if (raw.getGenericSuperclass() != null) {
                types.add(raw.getGenericSuperclass());
            }
            types.addAll(Arrays.asList(raw.getGenericInterfaces()));
        }
        return bindings;
    }

    /**
     * The class a type is erased to once the class's bindings are applied.
     *
     * @param type     the type
     * @param bindings the class's bindings
     * @return its erasure
     */
    private static Class<?> erasure(Type type, Map<TypeVariable<?>, Type> bindings) {
        return switch (type) {
            case Class<?> k -> k;
            case ParameterizedType p -> (Class<?>) p.getRawType();
            case GenericArrayType a -> erasure(a.getGenericComponentType(), bindings).arrayType();
            case TypeVariable<?> v -> bindings.containsKey(v) && bindings.get(v) != v
                    ? erasure(bindings.get(v), bindings)
                    : erasure(v.getBounds()[0], bindings);
            case WildcardType w -> erasure(w.getUpperBounds()[0], bindings);
            default -> throw new IllegalArgumentException("a type of no known kind: " + type);
        };
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

    /**
     * The public classes of the module a class was loaded from, a directory of classes or a jar.
     *
     * @param anchor a class of the module
     * @return its public classes, by name
     * @throws IllegalStateException if the module cannot be read or has no public class
     */
    private static List<Class<?>> publicClasses(Class<?> anchor) {
        Path root;
        try {
            root = Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        List<String> names;
        try {
            names = Files.isDirectory(root) ? classNamesIn(root) : classNamesInJar(root);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read the classes of " + anchor.getName() + " at " + root, e);
        }
        List<Class<?>> classes = new ArrayList<>();
        for (String name : names) {
            try {
                Class<?> c = Class.forName(name, false, anchor.getClassLoader());
                if (isPublic(c)) {
                    classes.add(c);
                }
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        if (classes.isEmpty()) {
            throw new IllegalStateException("no public class found in " + root);
        }
        return classes;
    }

    private static List<String> classNamesIn(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.map(root::relativize).map(Path::toString)
                    .filter(name -> name.endsWith(".class"))
                    .map(name -> name.replace(root.getFileSystem().getSeparator(), "/"))
                    .filter(PublicVarargsOverloads::isClassFile)
                    .sorted().map(PublicVarargsOverloads::className).toList();
        }
    }

    private static List<String> classNamesInJar(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            return file.stream().map(JarEntry::getName)
                    .filter(name -> name.endsWith(".class") && !name.startsWith("META-INF/"))
                    .filter(PublicVarargsOverloads::isClassFile)
                    .sorted().map(PublicVarargsOverloads::className).toList();
        }
    }

    /**
     * Whether a class file holds a class: {@code package-info} and {@code module-info} hold a
     * package's or a module's declaration instead.
     *
     * @param path the class file's path in the module, separated by {@code /}
     * @return {@code true} for a class
     */
    private static boolean isClassFile(String path) {
        return !path.endsWith("/package-info.class") && !path.equals("package-info.class")
                && !path.equals("module-info.class") && !path.endsWith("/module-info.class");
    }

    private static String className(String path) {
        return path.substring(0, path.length() - ".class".length()).replace('/', '.');
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
