package net.unit8.raoh.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
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
 * one compilation unit imports on demand into one set of candidates, whatever stands between the
 * import declarations. Which classes are imported together is therefore what the documentation's
 * examples say, read from them rather than written down a second time: a group is every class one
 * example imports with {@code import static net.unit8.raoh.….*;}, if there are two or more. An
 * example is a code block:
 *
 * <ul>
 *   <li>in the published Javadoc of raoh, raoh-json and raoh-jooq, a {@code <pre>} block, or, for
 *       imports a Javadoc comment has outside any {@code <pre>}, the comment;</li>
 *   <li>in the README and the Markdown under {@code docs/}, a fenced code block ({@code ```} or
 *       {@code ~~~}), or an indented one.</li>
 * </ul>
 *
 * <p>This is the documentation of the current API, which a user follows now. The CHANGELOG records
 * what earlier versions were and how to move off them, and its examples may name classes this
 * version no longer has, so it is not read.
 *
 * <p>Only documentation is read: the source's own import declarations are how Raoh is written, not
 * what it tells a user to write. The published Javadoc is that of the public top-level types and the
 * {@code package-info} of every package but {@code net.unit8.raoh.internal}, which the release's
 * Javadoc leaves out; it is chosen by source file, so a non-public member's comment in such a file
 * is read too. Classes of {@code net.unit8.raoh.examples} are not Raoh's API and
 * are left out. A nested class may be imported by its canonical name, and an import may leave out its
 * semicolon, as jshell lets it.
 */
public final class DocumentedImports {

    /** The modules whose Javadoc is documentation, under the repository root. */
    private static final List<String> MODULES = List.of("raoh", "raoh-json", "raoh-jooq");

    private static final Pattern IMPORT = Pattern.compile(
            "import\\s+static\\s+(net\\.unit8\\.raoh\\.[\\w.]+?)\\s*\\.\\s*\\*\\s*;?");

    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})");

    private DocumentedImports() {
    }

    /**
     * The documentation of the repository whose root is given: the README, the Markdown under
     * {@code docs/}, and the sources of raoh, raoh-json and raoh-jooq whose Javadoc is published.
     * The CHANGELOG is a record of earlier versions, and CLAUDE.md and CONTRIBUTING.md are for
     * whoever works on Raoh, not for its users; none of them is read.
     *
     * @param root   the repository root
     * @param loader the loader of raoh, raoh-json and raoh-jooq, to tell which types are public
     * @return the files
     * @throws UncheckedIOException if they cannot be listed
     * @throws AssertionError       if a source's type does not load
     */
    public static List<Path> documents(Path root, ClassLoader loader) {
        List<Path> files = new ArrayList<>();
        files.add(root.resolve("README.md"));
        try {
            for (String module : MODULES) {
                Path sources = root.resolve(module).resolve("src/main/java");
                for (Path source : walk(sources, ".java")) {
                    if (published(sources.relativize(source), loader)) {
                        files.add(source);
                    }
                }
            }
            files.addAll(walk(root.resolve("docs"), ".md"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files;
    }

    /**
     * Whether a source's Javadoc is published: a {@code package-info} or a public top-level type,
     * outside {@code net.unit8.raoh.internal}.
     *
     * @param source the source's path under its source root
     * @param loader the loader of its module
     * @return {@code true} if it is published
     * @throws AssertionError if the source's type does not load
     */
    static boolean published(Path source, ClassLoader loader) {
        String name = source.toString().replace(source.getFileSystem().getSeparator(), ".")
                .replaceAll("\\.java$", "");
        String simple = name.substring(name.lastIndexOf('.') + 1);
        if (name.startsWith("net.unit8.raoh.internal.") || simple.equals("module-info")) {
            return false;
        }
        if (simple.equals("package-info")) {
            return true;
        }
        try {
            return Modifier.isPublic(Class.forName(name, false, loader).getModifiers());
        } catch (ClassNotFoundException e) {
            throw new AssertionError("the source " + source + " has no type " + name + " that loads", e);
        }
    }

    private static List<Path> walk(Path dir, String suffix) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.toString().endsWith(suffix)).sorted().toList();
        }
    }

    /**
     * The groups of classes the examples in these files import on demand together, by the names
     * the imports write, each group once.
     *
     * @param files the documentation, Java sources and Markdown
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
            var examples = file.toString().endsWith(".java") ? javadocExamples(lines) : markdownExamples(lines);
            for (List<String> example : examples) {
                List<String> group = imported(example);
                if (group.size() > 1) {
                    groups.add(group);
                }
            }
        }
        return List.copyOf(groups);
    }

    /**
     * The classes an example imports on demand, sorted, each once.
     *
     * @param example the lines of the example
     * @return the classes, by the names the imports write
     */
    static List<String> imported(List<String> example) {
        Set<String> imported = new LinkedHashSet<>();
        for (String line : example) {
            Matcher m = IMPORT.matcher(line);
            while (m.find()) {
                if (!m.group(1).startsWith("net.unit8.raoh.examples.")) {
                    imported.add(m.group(1));
                }
            }
        }
        return imported.stream().sorted().toList();
    }

    /**
     * The examples in a Java source's Javadoc: each {@code <pre>} block, and for each Javadoc
     * comment the text outside its {@code <pre>} blocks. Nothing outside a Javadoc comment is read.
     *
     * @param lines the source
     * @return the text of each example, by line
     */
    static List<List<String>> javadocExamples(List<String> lines) {
        List<List<String>> examples = new ArrayList<>();
        boolean inJavadoc = false;
        boolean inPre = false;
        List<String> pre = new ArrayList<>();
        List<String> outside = new ArrayList<>();
        for (String line : lines) {
            String text = line;
            if (!inJavadoc) {
                int open = text.indexOf("/**");
                if (open < 0) {
                    continue;
                }
                inJavadoc = true;
                text = text.substring(open + 3);
            }
            int close = text.indexOf("*/");
            if (close >= 0) {
                text = text.substring(0, close);
            }
            // A line may open or close a <pre> block, with text on either side of the tag.
            while (true) {
                String tag = inPre ? "</pre>" : "<pre>";
                int at = text.indexOf(tag);
                (inPre ? pre : outside).add(at < 0 ? text : text.substring(0, at));
                if (at < 0) {
                    break;
                }
                if (inPre) {
                    examples.add(pre);
                    pre = new ArrayList<>();
                }
                inPre = !inPre;
                text = text.substring(at + tag.length());
            }
            if (close >= 0) {
                if (inPre) {
                    examples.add(pre);
                    pre = new ArrayList<>();
                    inPre = false;
                }
                examples.add(outside);
                outside = new ArrayList<>();
                inJavadoc = false;
            }
        }
        return examples;
    }

    /**
     * The examples in Markdown: each fenced code block, closed by a fence of the same character at
     * least as long, and each indented code block outside a fence.
     *
     * @param lines the Markdown
     * @return the lines of each example
     */
    static List<List<String>> markdownExamples(List<String> lines) {
        List<List<String>> examples = new ArrayList<>();
        String fence = null;
        List<String> block = new ArrayList<>();
        for (String line : lines) {
            Matcher m = FENCE.matcher(line);
            if (fence == null) {
                if (m.find()) {
                    examples.add(block);
                    block = new ArrayList<>();
                    fence = m.group(1);
                } else if (line.startsWith("    ") || line.startsWith("\t") || line.isBlank() && !block.isEmpty()) {
                    block.add(line);
                } else if (!block.isEmpty()) {
                    examples.add(block);
                    block = new ArrayList<>();
                }
            } else if (m.find() && m.group(1).charAt(0) == fence.charAt(0) && m.group(1).length() >= fence.length()
                    && line.strip().equals(m.group(1))) {
                examples.add(block);
                block = new ArrayList<>();
                fence = null;
            } else {
                block.add(line);
            }
        }
        examples.add(block);
        return examples;
    }

    /**
     * Loads the groups' classes. A nested class is written in an import by its canonical name,
     * which is tried as a binary name with each trailing dot read as a {@code $} in turn.
     *
     * @param groups the documented groups, by the names the imports write
     * @param loader the loader of raoh, raoh-json and raoh-jooq
     * @return the groups, as classes
     * @throws AssertionError if a group names a class that does not load
     */
    public static List<List<Class<?>>> load(List<List<String>> groups, ClassLoader loader) {
        List<List<Class<?>>> loaded = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (List<String> group : groups) {
            List<Class<?>> classes = new ArrayList<>();
            for (String name : group) {
                Class<?> c = load(name, loader);
                if (c == null) {
                    problems.add(group + " names " + name + ", which is no class of raoh, raoh-json or raoh-jooq");
                } else {
                    classes.add(c);
                }
            }
            loaded.add(List.copyOf(classes));
        }
        if (!problems.isEmpty()) {
            throw new AssertionError("The documentation imports what is not there:\n  " + String.join("\n  ", problems));
        }
        return loaded;
    }

    private static Class<?> load(String canonicalName, ClassLoader loader) {
        String name = canonicalName;
        while (true) {
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException e) {
                int dot = name.lastIndexOf('.');
                if (dot < 0) {
                    return null;
                }
                name = name.substring(0, dot) + "$" + name.substring(dot + 1);
            }
        }
    }

    /**
     * Fails if two classes of a group have a static method of one name and the same parameter
     * types: with both imported on demand, a call fits both and neither is more specific, so
     * neither can be called without its class and the documented imports do not compile
     * ({@code ObjectDecoders.int_()} beside {@code ObjectEncoders.int_()}). Parameter types are
     * compared as written, a method's own type variables by their place and bounds, so two generic
     * methods of the same shape clash, while
     * {@code MapDecoders.combine(CombinePart<Map<String, Object>, A>, ...)} beside
     * {@code Decoders.combine(CombinePart<I, A>, ...)} does not: the first is more specific.
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
     * The static methods of one name and parameter types, as {@link #assertCallable} compares them,
     * that different classes of a group have.
     *
     * @param groups the groups
     * @return each clash, empty when there is none
     */
    static List<String> clashes(List<List<Class<?>>> groups) {
        Set<String> problems = new LinkedHashSet<>();
        for (List<Class<?>> group : groups) {
            List<Method> statics = PublicVarargsOverloads.staticMethods(group);
            for (int i = 0; i < statics.size(); i++) {
                for (int j = i + 1; j < statics.size(); j++) {
                    Method a = statics.get(i);
                    Method b = statics.get(j);
                    if (a.getDeclaringClass() != b.getDeclaringClass() && a.getName().equals(b.getName())
                            && shape(a).equals(shape(b))) {
                        problems.add(a.toGenericString() + " and " + b.toGenericString());
                    }
                }
            }
        }
        return List.copyOf(problems);
    }

    /**
     * A method's parameter types as written, its own type variables by their place and bounds, so
     * that two methods whose parameters differ only in the names of their type variables have the
     * same shape.
     *
     * @param m the method
     * @return its shape
     */
    static String shape(Method m) {
        List<TypeVariable<Method>> own = List.of(m.getTypeParameters());
        StringBuilder out = new StringBuilder("<");
        for (TypeVariable<Method> v : own) {
            out.append(Arrays.stream(v.getBounds()).map(b -> render(b, own)).toList());
        }
        out.append(">(");
        out.append(Arrays.stream(m.getGenericParameterTypes()).map(t -> render(t, own)).toList());
        return out.append(m.isVarArgs() ? "...)" : ")").toString();
    }

    private static String render(Type type, List<TypeVariable<Method>> own) {
        return switch (type) {
            case Class<?> k -> k.getName();
            case TypeVariable<?> v -> own.contains(v) ? "#" + own.indexOf(v) : v.getGenericDeclaration() + "." + v.getName();
            case ParameterizedType p -> render(p.getRawType(), own) + Arrays.stream(p.getActualTypeArguments())
                    .map(a -> render(a, own)).toList();
            case GenericArrayType a -> render(a.getGenericComponentType(), own) + "[]";
            case WildcardType w -> "?" + Arrays.stream(w.getUpperBounds()).map(b -> render(b, own)).toList()
                    + Arrays.stream(w.getLowerBounds()).map(b -> render(b, own)).toList();
            default -> throw new IllegalArgumentException("a type of no known kind: " + type);
        };
    }
}
