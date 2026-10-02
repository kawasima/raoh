package net.unit8.raoh.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The classes Raoh's documentation imports on demand together, read from the documentation itself.
 *
 * <p>A user who copies an example gets its imports, and Java puts the static methods of every class
 * imported on demand into one set of candidates. Which classes are imported together is therefore
 * what the documentation says, and is read from it rather than written down a second time: the
 * Javadoc of raoh, raoh-json and raoh-jooq, the README and {@code docs/}. A group is a run of
 * consecutive {@code import static net.unit8.raoh.….*;} lines of more than one class. Classes of
 * {@code net.unit8.raoh.examples} are not Raoh's API and are left out.
 */
public final class DocumentedImports {

    /** The modules whose Javadoc is documentation, under the repository root. */
    private static final List<String> MODULES = List.of("raoh", "raoh-json", "raoh-jooq");

    private static final Pattern IMPORT = Pattern.compile(
            "^\\s*(?:\\*\\s*)?import\\s+static\\s+(net\\.unit8\\.raoh\\.[\\w.]+)\\.\\*\\s*;");

    private static final Pattern BLANK = Pattern.compile("^\\s*(?:\\*\\s*)?$");

    private DocumentedImports() {
    }

    /**
     * The documentation of the repository whose root is given: the sources of raoh, raoh-json and
     * raoh-jooq, the README and the Markdown under {@code docs/}.
     *
     * @param root the repository root
     * @return the files
     * @throws UncheckedIOException if they cannot be listed
     */
    public static List<Path> documents(Path root) {
        List<Path> files = new ArrayList<>();
        files.add(root.resolve("README.md"));
        try {
            for (String module : MODULES) {
                files.addAll(walk(root.resolve(module).resolve("src/main/java"), ".java"));
            }
            files.addAll(walk(root.resolve("docs"), ".md"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files;
    }

    private static List<Path> walk(Path dir, String suffix) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.toString().endsWith(suffix)).sorted().toList();
        }
    }

    /**
     * The groups of classes these files import on demand together, by binary name, each once.
     *
     * @param files the documentation
     * @return the groups
     * @throws UncheckedIOException if a file cannot be read
     */
    public static List<List<String>> groups(List<Path> files) {
        Set<List<String>> groups = new LinkedHashSet<>();
        for (Path file : files) {
            List<String> lines;
            try {
                lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            Set<String> run = new LinkedHashSet<>();
            for (String line : lines) {
                Matcher m = IMPORT.matcher(line);
                if (m.find()) {
                    if (!m.group(1).startsWith("net.unit8.raoh.examples.")) {
                        run.add(m.group(1));
                    }
                } else if (!BLANK.matcher(line).matches()) {
                    addGroup(run, groups);
                    run = new LinkedHashSet<>();
                }
            }
            addGroup(run, groups);
        }
        return List.copyOf(groups);
    }

    private static void addGroup(Set<String> run, Set<List<String>> groups) {
        if (run.size() > 1) {
            groups.add(run.stream().sorted().toList());
        }
    }

    /**
     * The groups the module {@code anchor} is in checks, as classes. A group is checked by the one
     * module that can load all of it: raoh-json for a group with a class of
     * {@code net.unit8.raoh.json}, raoh-jooq for one with a class of {@code net.unit8.raoh.jooq},
     * and raoh for any other. A group with classes of both raoh-json and raoh-jooq can be checked
     * by none, and fails.
     *
     * @param anchor a class of the module
     * @param groups the documented groups, by binary name
     * @return the module's groups
     * @throws AssertionError if a group is the module's and names a class it cannot load, or if a
     *                        group belongs to no one module
     */
    public static List<List<Class<?>>> ofModule(Class<?> anchor, List<List<String>> groups) {
        String module = moduleOf(anchor.getName());
        List<List<Class<?>>> own = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (List<String> group : groups) {
            Set<String> modules = new LinkedHashSet<>();
            for (String name : group) {
                String m = moduleOf(name);
                if (!m.equals("raoh")) {
                    modules.add(m);
                }
            }
            if (modules.size() > 1) {
                problems.add(group + " has classes of " + modules + ", which no one module can load");
                continue;
            }
            if (!module.equals(modules.isEmpty() ? "raoh" : modules.iterator().next())) {
                continue;
            }
            List<Class<?>> loaded = new ArrayList<>();
            for (String name : group) {
                try {
                    loaded.add(Class.forName(name, false, anchor.getClassLoader()));
                } catch (ClassNotFoundException e) {
                    problems.add(group + " names " + name + ", which " + module + " cannot load");
                }
            }
            own.add(List.copyOf(loaded));
        }
        if (!problems.isEmpty()) {
            throw new AssertionError("The documentation imports classes together that cannot be checked:\n  "
                    + String.join("\n  ", problems));
        }
        return own;
    }

    private static boolean mentionsTypeVariable(Type type) {
        return switch (type) {
            case TypeVariable<?> v -> true;
            case ParameterizedType p -> Arrays.stream(p.getActualTypeArguments()).anyMatch(DocumentedImports::mentionsTypeVariable);
            case GenericArrayType a -> mentionsTypeVariable(a.getGenericComponentType());
            case WildcardType w -> Stream.concat(Arrays.stream(w.getUpperBounds()), Arrays.stream(w.getLowerBounds()))
                    .anyMatch(DocumentedImports::mentionsTypeVariable);
            default -> false;
        };
    }

    private static String moduleOf(String className) {
        if (className.startsWith("net.unit8.raoh.json.")) {
            return "raoh-json";
        }
        if (className.startsWith("net.unit8.raoh.jooq.")) {
            return "raoh-jooq";
        }
        return "raoh";
    }

    /**
     * Fails if two classes of a group have a static method of one name and the same parameter
     * types, none of which mentions a type variable: with both imported on demand, a call fits
     * both and neither is more specific, so neither can be called without its class and the
     * documented imports do not work ({@code ObjectDecoders.int_()} beside
     * {@code ObjectEncoders.int_()}). Two generic methods are not compared: which is more specific
     * turns on type inference, and a pair neither of which is would fail to compile at the call,
     * not change its meaning. {@code MapDecoders.combine(CombinePart<Map<String, Object>, A>, ...)}
     * beside {@code Decoders.combine(CombinePart<I, A>, ...)} is fine: the first is more specific.
     *
     * @param groups the groups
     * @throws AssertionError if a group has such a pair
     */
    public static void assertCallable(List<List<Class<?>>> groups) {
        List<String> problems = clashes(groups);
        if (!problems.isEmpty()) {
            throw new AssertionError("The documentation imports on demand together classes with the same static "
                    + "method, which then cannot be called without its class. Import them apart:\n  "
                    + String.join("\n  ", problems));
        }
    }

    /**
     * The static methods of one name and parameter types, with no type variable in them, that
     * different classes of a group have.
     *
     * @param groups the groups
     * @return each clash, empty when there is none
     */
    static List<String> clashes(List<List<Class<?>>> groups) {
        Set<String> problems = new LinkedHashSet<>();
        for (List<Class<?>> group : groups) {
            List<Method> statics = group.stream()
                    .flatMap(c -> PublicVarargsOverloads.methods(c).stream())
                    .filter(m -> Modifier.isStatic(m.getModifiers()))
                    .distinct()
                    .toList();
            for (int i = 0; i < statics.size(); i++) {
                for (int j = i + 1; j < statics.size(); j++) {
                    Method a = statics.get(i);
                    Method b = statics.get(j);
                    if (a.getDeclaringClass() != b.getDeclaringClass() && a.getName().equals(b.getName())
                            && Arrays.equals(a.getGenericParameterTypes(), b.getGenericParameterTypes())
                            && Arrays.stream(a.getGenericParameterTypes()).noneMatch(DocumentedImports::mentionsTypeVariable)) {
                        problems.add(a.toGenericString() + " and " + b.toGenericString());
                    }
                }
            }
        }
        return List.copyOf(problems);
    }

}
