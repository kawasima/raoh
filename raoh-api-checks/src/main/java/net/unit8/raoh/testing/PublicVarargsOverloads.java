package net.unit8.raoh.testing;

import java.io.IOException;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Keeps a public varargs method or constructor from sharing its name with a fixed-arity one,
 * unless the pair is listed with the reason no call can fit both.
 *
 * <p>Java resolves a call without varargs first, by strict and then loose invocation, so a
 * fixed-arity method takes over every call to a varargs method of the same name that it also
 * accepts, boxing and primitive widening included. A call compiled against the varargs method
 * keeps calling it, but the same source compiled again calls the other one, with no error or
 * warning. {@code containsAll(T...)} beside a {@code containsAll(List, String)} would be one: with
 * {@code T} of {@code Object}, {@code containsAll(someList, "tag")}, two elements to require,
 * would become a list and a message. So would {@code f(long...)} beside an {@code f(int)}.
 * Constructors are chosen the same way.
 *
 * <p>This is a policy, not a model of overload resolution: every such pair is refused, and a pair
 * a reviewer has found safe is listed with {@link Allowed}, its reason written down. Whether a
 * call can fit both turns on invocation conversions and type inference, which a check here would
 * have to reimplement to answer, and could answer wrongly. Methods the compiler generates, such as
 * bridges, are not part of the source and are not considered. Static and instance methods are
 * considered together, as Java considers them.
 *
 * <p>Each module that publishes an API runs this over its own classes, from a test. It throws
 * {@link AssertionError}, which a test framework reports as a failure.
 */
public final class PublicVarargsOverloads {

    /** The name {@link Allowed} gives the constructors of a class. */
    public static final String CONSTRUCTOR = "<init>";

    /**
     * A varargs method or constructor and a fixed-arity one of its name that may share the name,
     * and why no call can fit both.
     *
     * @param owner   the class that declares the varargs one
     * @param name    the methods' name, or {@link #CONSTRUCTOR}
     * @param varargs the varargs one's parameter types, its array last
     * @param fixed   the fixed-arity one's parameter types
     * @param reason  why no call of the varargs one can fit the fixed-arity one
     */
    public record Allowed(Class<?> owner, String name, List<Class<?>> varargs, List<Class<?>> fixed,
                          String reason) {
        /**
         * Copies the parameter types and requires a reason.
         *
         * @param owner   the class that declares the varargs one
         * @param name    the methods' name, or {@link #CONSTRUCTOR}
         * @param varargs the varargs one's parameter types, its array last
         * @param fixed   the fixed-arity one's parameter types
         * @param reason  why no call of the varargs one can fit the fixed-arity one
         */
        public Allowed {
            varargs = List.copyOf(varargs);
            fixed = List.copyOf(fixed);
            if (reason.isBlank()) {
                throw new IllegalArgumentException("an allowed pair needs its reason");
            }
        }

        private boolean is(Pair pair) {
            return pair.varargs().getDeclaringClass() == owner && nameOf(pair.varargs()).equals(name)
                    && Arrays.asList(pair.varargs().getParameterTypes()).equals(varargs)
                    && Arrays.asList(pair.fixed().getParameterTypes()).equals(fixed);
        }

        @Override
        public String toString() {
            return owner.getName() + "#" + name + " " + varargs + " beside " + fixed;
        }
    }

    /**
     * A varargs method or constructor and a fixed-arity one of its name.
     *
     * @param varargs the varargs one
     * @param fixed   the fixed-arity one
     */
    record Pair(Executable varargs, Executable fixed) {
    }

    private PublicVarargsOverloads() {
    }

    /**
     * Fails if a public class of the module {@code anchor} is in has a varargs method or
     * constructor sharing its name with a fixed-arity one, unless the pair is allowed, and if an
     * allowed pair is not there.
     *
     * @param anchor  a class of the module, whose classes are read from where it was loaded
     * @param allowed the pairs found safe
     * @throws AssertionError if a pair is not allowed or an allowed pair is gone
     */
    public static void assertNone(Class<?> anchor, Allowed... allowed) {
        List<String> problems = problems(publicClasses(anchor), allowed);
        if (!problems.isEmpty()) {
            throw new AssertionError("A fixed-arity method of a varargs method's name can take over its "
                    + "calls, changing what they mean without an error. Give it a name of its own, or list "
                    + "the pair with the reason no call can fit both:\n  " + String.join("\n  ", problems));
        }
    }

    /**
     * What is wrong with these classes: each pair not allowed, and each allowed pair not there.
     *
     * @param classes the classes
     * @param allowed the pairs found safe
     * @return the problems, empty when there are none
     */
    static List<String> problems(List<Class<?>> classes, Allowed... allowed) {
        List<String> problems = new ArrayList<>();
        Set<Allowed> used = new LinkedHashSet<>();
        for (Pair pair : pairs(classes)) {
            Allowed match = Arrays.stream(allowed).filter(a -> a.is(pair)).findFirst().orElse(null);
            if (match == null) {
                problems.add(pair.fixed().toGenericString() + " shares its name with "
                        + pair.varargs().toGenericString());
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
     * Every varargs method or constructor and fixed-arity one of its name among the public
     * methods and constructors of these classes, each pair once however many classes inherit it.
     *
     * @param classes the classes
     * @return the pairs
     */
    static List<Pair> pairs(List<Class<?>> classes) {
        Set<Pair> pairs = new LinkedHashSet<>();
        for (Class<?> c : classes) {
            addPairs(Arrays.asList(c.getMethods()), pairs);
            addPairs(Arrays.asList(c.getConstructors()), pairs);
        }
        return List.copyOf(pairs);
    }

    private static void addPairs(List<? extends Executable> executables, Set<Pair> pairs) {
        List<Executable> written = executables.stream()
                .filter(e -> !e.isSynthetic() && !(e instanceof Method m && m.isBridge()))
                .map(Executable.class::cast)
                .toList();
        for (Executable varargs : written) {
            if (!varargs.isVarArgs()) {
                continue;
            }
            for (Executable fixed : written) {
                if (!fixed.isVarArgs() && nameOf(fixed).equals(nameOf(varargs))) {
                    pairs.add(new Pair(varargs, fixed));
                }
            }
        }
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
