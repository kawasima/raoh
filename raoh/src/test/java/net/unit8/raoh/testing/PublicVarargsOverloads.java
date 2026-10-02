package net.unit8.raoh.testing;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Checks that adding an overload to a public API cannot change what an existing call means.
 *
 * <p>Java resolves a call without varargs first, so a fixed-arity method takes over every call to
 * a varargs method of the same name whose arguments it also accepts. Such a call compiled against
 * the varargs method keeps calling it, but the same source compiled again calls the other one,
 * with no error or warning. {@code containsAll(T...)} beside a {@code containsAll(List, String)}
 * would be one: with {@code T} of {@code Object}, {@code containsAll(someList, "tag")}, two
 * elements to require, would become a list and a message.
 *
 * <p>The check is on applicability, not on names: a varargs method and a fixed-arity one of the
 * same name are reported only when some call could fit both, each fixed parameter and each
 * varargs element being of a type the other method's parameter in that place may share. A type
 * parameter is read as its erasure, so {@code T...} may share anything. Two types may share an
 * instance when one is a subtype of the other, or one is an interface and the other a class that
 * is not final. {@code oneOf(String...)} beside {@code oneOf(Collection, String)} is therefore
 * fine: no {@code String} is a {@code Collection}.
 *
 * <p>Each module that publishes an API runs this over its own classes.
 */
public final class PublicVarargsOverloads {

    private PublicVarargsOverloads() {
    }

    /**
     * Fails if a public class of the module {@code anchor} is in has a varargs method that a
     * fixed-arity overload could capture a call of.
     *
     * @param anchor a class of the module, whose classes are read from where it was loaded
     */
    public static void assertNoneCapturable(Class<?> anchor) {
        List<String> found = new ArrayList<>();
        for (Class<?> c : publicClasses(anchor)) {
            Method[] methods = c.getMethods();
            for (Method varargs : methods) {
                if (!varargs.isVarArgs()) {
                    continue;
                }
                for (Method fixed : methods) {
                    if (!fixed.isVarArgs() && fixed.getName().equals(varargs.getName())
                            && Modifier.isStatic(fixed.getModifiers()) == Modifier.isStatic(varargs.getModifiers())
                            && canCapture(fixed, varargs)) {
                        found.add(c.getName() + ": " + fixed.toGenericString()
                                + " can capture a call of " + varargs.toGenericString());
                    }
                }
            }
        }
        if (!found.isEmpty()) {
            fail("A fixed-arity overload can take over calls of a varargs method, changing what they "
                    + "mean without an error. Give the overload a name of its own:\n  "
                    + String.join("\n  ", found));
        }
    }

    /**
     * Whether a call of {@code varargs} could also fit {@code fixed}.
     *
     * @param fixed   the fixed-arity method
     * @param varargs the varargs method
     * @return {@code true} if some call could fit both
     */
    static boolean canCapture(Method fixed, Method varargs) {
        Class<?>[] v = varargs.getParameterTypes();
        Class<?>[] f = fixed.getParameterTypes();
        int leading = v.length - 1;
        if (f.length < leading) {
            return false;
        }
        Class<?> element = v[leading].getComponentType();
        for (int i = 0; i < f.length; i++) {
            if (!mayShare(i < leading ? v[i] : element, f[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a value can be of both types.
     *
     * @param a one type
     * @param b the other
     * @return {@code true} if some value can be of both
     */
    static boolean mayShare(Class<?> a, Class<?> b) {
        if (a.isPrimitive() || b.isPrimitive()) {
            return a == b;
        }
        if (a.isAssignableFrom(b) || b.isAssignableFrom(a)) {
            return true;
        }
        return a.isInterface() && open(b) || b.isInterface() && open(a);
    }

    private static boolean open(Class<?> c) {
        return !c.isArray() && !Modifier.isFinal(c.getModifiers());
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
                if (Modifier.isPublic(c.getModifiers()) && !name.contains("$") || isPublicNested(c)) {
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

    private static boolean isPublicNested(Class<?> c) {
        for (Class<?> k = c; k != null; k = k.getEnclosingClass()) {
            if (!Modifier.isPublic(k.getModifiers())) {
                return false;
            }
        }
        return c.getEnclosingClass() != null;
    }
}
