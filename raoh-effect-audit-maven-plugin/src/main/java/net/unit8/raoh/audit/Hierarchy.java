package net.unit8.raoh.audit;

import java.io.Closeable;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.reflect.AccessFlag;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Answers questions about the class hierarchy of the audited code and its dependencies, read
 * from class files rather than from the JVM running the build.
 *
 * <p>JDK classes come from the API of the release the code compiles for: {@code lib/ct.sym},
 * the data {@code javac --release} uses, or the running JDK's own image when it is that
 * release. A later JDK may make a class final or move a method to a supertype; reading the
 * release's API keeps the answers the same on any JDK that can build the release. Other
 * classes come from the classpath given. Nothing is loaded. Methods are matched by their whole
 * descriptor, return type included, as the JVM matches them, and answers are cached.
 */
public final class Hierarchy implements Closeable {

    /** What the hierarchy needs to know about one class. */
    private record TypeInfo(String name, boolean isFinal, boolean isInterface, String superclass,
                            List<String> interfaces, Map<Member, Set<AccessFlag>> methods) {}

    private static final Set<String> PRIMITIVES =
            Set.of("boolean", "byte", "char", "short", "int", "long", "float", "double", "void");

    private final List<Path> roots;
    private final List<FileSystem> openedFileSystems;
    private final JdkApi jdk;
    private final Map<String, Optional<TypeInfo>> types = new HashMap<>();
    private final Map<Member, Optional<Member>> resolved = new HashMap<>();
    private final Map<List<String>, Boolean> subtypes = new HashMap<>();

    private Hierarchy(List<Path> roots, List<FileSystem> openedFileSystems, JdkApi jdk) {
        this.roots = roots;
        this.openedFileSystems = openedFileSystems;
        this.jdk = jdk;
    }

    /**
     * Opens a hierarchy over a classpath and a JDK release's API.
     *
     * @param classpath directories and jars holding every non-JDK class the audited code refers to
     * @param release the Java release the code compiles for
     * @return the hierarchy; close it to release the jars it opened
     * @throws IOException if a jar or the JDK's API data cannot be opened
     */
    public static Hierarchy open(List<Path> classpath, int release) throws IOException {
        var roots = new ArrayList<Path>();
        var opened = new ArrayList<FileSystem>();
        for (var element : classpath) {
            if (Files.isDirectory(element)) {
                roots.add(element);
            } else if (Files.isRegularFile(element) && element.toString().endsWith(".jar")) {
                var jar = FileSystems.newFileSystem(element);
                opened.add(jar);
                jar.getRootDirectories().forEach(roots::add);
            }
        }
        var jdk = JdkApi.open(release);
        jdk.fileSystem().ifPresent(opened::add);
        return new Hierarchy(List.copyOf(roots), List.copyOf(opened), jdk);
    }

    @Override
    public void close() throws IOException {
        for (var fs : openedFileSystems) {
            fs.close();
        }
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
        if (!isInheritable(member)) {
            return false;
        }
        var owner = type(member.owner());
        if (owner.isFinal()) {
            return false;
        }
        var method = find(member).orElseThrow(() -> new IllegalStateException(
                "cannot resolve " + member + " in " + owner.name()));
        var flags = type(method.owner()).methods().get(method);
        return !flags.contains(AccessFlag.STATIC) && !flags.contains(AccessFlag.PRIVATE)
                && !flags.contains(AccessFlag.FINAL);
    }

    /**
     * The class that declares {@code member} when it is looked up from its owner: the owner
     * itself, or the superclass or interface it inherits the method from.
     *
     * @param member the member a call site names
     * @return the declaring class's binary name, or empty for a field or constructor, which
     *         are not inherited, or for a method the owner does not have
     * @throws IllegalStateException if a class on the way cannot be found
     */
    public Optional<String> declaringClass(Member member) {
        if (!isInheritable(member)) {
            return Optional.empty();
        }
        return find(member).map(Member::owner);
    }

    /**
     * Whether a value of class {@code sub} is also a {@code sup}.
     *
     * @param sub the binary name of the class
     * @param sup the binary name of the possible supertype
     * @return {@code true} if {@code sup} is {@code sub} or one of its supertypes
     * @throws IllegalStateException if a class on the way cannot be found
     */
    public boolean isSubtype(String sub, String sup) {
        if (sup.endsWith("[]") || sub.endsWith("[]")) {
            return sub.equals(sup) || sup.equals("java.lang.Object");
        }
        var key = List.of(sub, sup);
        var cached = subtypes.get(key);
        if (cached == null) {
            cached = sup.equals("java.lang.Object") || supertypes(sub).anyMatch(t -> t.name().equals(sup));
            subtypes.put(key, cached);
        }
        return cached;
    }

    /**
     * Whether code outside the audited code base can call {@code method} on an instance of
     * {@code type}: the method is an instance method that overrides or implements one an
     * external supertype of {@code type} declares ({@code toString()}, {@code compare}, a
     * {@code Function}'s {@code apply}).
     *
     * @param type the binary name of an audited class
     * @param method a method {@code type} or an internal superclass of it declares
     * @param internal whether a class belongs to the audited code base
     * @return {@code true} if an external supertype declares a method it overrides
     * @throws IllegalStateException if a class on the way cannot be found
     */
    public boolean overridesExternal(String type, Member method, Predicate<String> internal) {
        if (!isInheritable(method) || method.name().equals("<clinit>")) {
            return false;
        }
        return Stream.concat(supertypes(type), Stream.of(type("java.lang.Object")))
                .filter(t -> !internal.test(t.name()))
                .anyMatch(t -> t.methods().entrySet().stream().anyMatch(e ->
                        e.getKey().name().equals(method.name())
                                && e.getKey().parameters().equals(method.parameters())
                                && !e.getValue().contains(AccessFlag.STATIC)
                                && !e.getValue().contains(AccessFlag.PRIVATE)));
    }

    /**
     * Whether a method or constructor takes an argument whose class a caller can subclass or
     * implement, so that calling it may run the caller's code: a {@code CharSequence}, a
     * {@code Collection}, an {@code Object}, or an array of such.
     *
     * @param member a method or constructor
     * @return {@code true} if a parameter's type (or element type) is neither primitive nor final
     * @throws IllegalStateException if a parameter's class cannot be found
     */
    public boolean hasOpenParameter(Member member) {
        if (member.isField()) {
            return false;
        }
        for (var parameter : member.parameters()) {
            var base = parameter.replace("[]", "");
            if (!PRIMITIVES.contains(base) && !type(base).isFinal()) {
                return true;
            }
        }
        return false;
    }

    /** A method, as opposed to a field, a constructor or a method of an array type. */
    private static boolean isInheritable(Member member) {
        return !member.isField() && !member.isConstructor() && !member.owner().endsWith("[]");
    }

    /** {@code name} and every class and interface above it, each once. */
    private Stream<TypeInfo> supertypes(String name) {
        var seen = new HashSet<String>();
        var order = new ArrayList<TypeInfo>();
        var queue = new ArrayDeque<String>();
        queue.add(name);
        while (!queue.isEmpty()) {
            var next = queue.poll();
            if (!seen.add(next)) {
                continue;
            }
            var info = type(next);
            order.add(info);
            if (info.superclass() != null) {
                queue.add(info.superclass());
            }
            queue.addAll(info.interfaces());
        }
        return order.stream();
    }

    /** Resolves a method the way the JVM does: the class chain first, then the interfaces. */
    private Optional<Member> find(Member member) {
        var cached = resolved.get(member);
        if (cached != null) {
            return cached;
        }
        var owner = type(member.owner());
        Optional<Member> result = Optional.empty();
        for (var c = owner; c != null; c = c.superclass() == null ? null : type(c.superclass())) {
            var candidate = new Member(c.name(), member.name(), member.parameters(), member.type());
            if (c.methods().containsKey(candidate)) {
                result = Optional.of(candidate);
                break;
            }
        }
        if (result.isEmpty()) {
            var all = supertypes(member.owner()).toList();
            if (owner.isInterface()) {
                all = Stream.concat(all.stream(), Stream.of(type("java.lang.Object"))).toList();
            }
            for (var t : all) {
                var candidate = new Member(t.name(), member.name(), member.parameters(), member.type());
                if (t.methods().containsKey(candidate)) {
                    result = Optional.of(candidate);
                    break;
                }
            }
        }
        resolved.put(member, result);
        return result;
    }

    private TypeInfo type(String name) {
        return types.computeIfAbsent(name, this::read).orElseThrow(() -> new IllegalStateException(
                "cannot find the class file of " + name + " on the classpath or in the JDK " + jdk.release() + " API"));
    }

    private Optional<TypeInfo> read(String name) {
        var path = name.replace('.', '/');
        try {
            var bytes = jdk.classFile(path);
            if (bytes.isEmpty()) {
                for (var root : roots) {
                    var file = root.resolve(path + ".class");
                    if (Files.isRegularFile(file)) {
                        bytes = Optional.of(Files.readAllBytes(file));
                        break;
                    }
                }
            }
            return bytes.map(b -> describe(ClassFile.of().parse(b)));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read the class file of " + name, e);
        }
    }

    private static TypeInfo describe(ClassModel model) {
        var name = Member.typeName(model.thisClass().asSymbol());
        var flags = model.flags().flags();
        var methods = new HashMap<Member, Set<AccessFlag>>();
        for (var method : model.methods()) {
            methods.put(Member.method(model.thisClass().asSymbol(), method.methodName().stringValue(),
                    method.methodTypeSymbol()), method.flags().flags());
        }
        return new TypeInfo(name, flags.contains(AccessFlag.FINAL), flags.contains(AccessFlag.INTERFACE),
                model.superclass().map(c -> Member.typeName(c.asSymbol())).orElse(null),
                model.interfaces().stream().map(c -> Member.typeName(c.asSymbol())).toList(),
                Map.copyOf(methods));
    }

    /**
     * Where the class files of one JDK release's API are: the running JDK's image for its own
     * release, {@code lib/ct.sym} for an earlier one.
     */
    private static final class JdkApi {
        private final int release;
        private final Path packages;
        private final Map<String, Path> signatures;
        private final FileSystem fileSystem;

        private JdkApi(int release, Path packages, Map<String, Path> signatures, FileSystem fileSystem) {
            this.release = release;
            this.packages = packages;
            this.signatures = signatures;
            this.fileSystem = fileSystem;
        }

        static JdkApi open(int release) throws IOException {
            int running = Runtime.version().feature();
            if (release == running) {
                var jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
                return new JdkApi(release, jrt.getPath("/packages"), null, null);
            }
            if (release > running) {
                throw new IOException("cannot read the JDK " + release + " API on JDK " + running);
            }
            // ct.sym holds <releases>/<module>/<path>.sig, the directory naming every release
            // (8, 9, A for 10, ...) that shares that version of the class. Index this release's.
            var letter = Character.toUpperCase(Character.forDigit(release, 36));
            var fs = FileSystems.newFileSystem(Path.of(System.getProperty("java.home"), "lib", "ct.sym"));
            var index = new HashMap<String, Path>();
            var root = fs.getRootDirectories().iterator().next();
            try (var releases = Files.list(root)) {
                for (var dir : releases.toList()) {
                    if (dir.getFileName().toString().replace("/", "").indexOf(letter) < 0) {
                        continue;
                    }
                    try (var files = Files.walk(dir)) {
                        files.filter(f -> f.toString().endsWith(".sig")).forEach(f -> {
                            var relative = dir.relativize(f).toString();
                            // Drop the module directory: <module>/<path>.sig
                            var path = relative.substring(relative.indexOf('/') + 1, relative.length() - ".sig".length());
                            index.putIfAbsent(path, f);
                        });
                    }
                }
            }
            return new JdkApi(release, null, Map.copyOf(index), fs);
        }

        int release() {
            return release;
        }

        Optional<FileSystem> fileSystem() {
            return Optional.ofNullable(fileSystem);
        }

        /**
         * The class file of a JDK class in this release, or empty if the release has no such
         * class, which makes it a classpath class.
         *
         * @param path the class's internal name, such as {@code java/util/List}
         */
        Optional<byte[]> classFile(String path) throws IOException {
            if (signatures != null) {
                var file = signatures.get(path);
                return file == null ? Optional.empty() : Optional.of(Files.readAllBytes(file));
            }
            int slash = path.lastIndexOf('/');
            if (slash < 0) {
                return Optional.empty();
            }
            var pkg = packages.resolve(path.substring(0, slash).replace('/', '.'));
            if (!Files.isDirectory(pkg)) {
                return Optional.empty();
            }
            try (var modules = Files.list(pkg)) {
                for (var module : modules.toList()) {
                    // /packages/<package>/<module> links to the module's root.
                    var file = module.resolve(path + ".class");
                    if (Files.isRegularFile(file)) {
                        return Optional.of(Files.readAllBytes(file));
                    }
                }
            }
            return Optional.empty();
        }
    }
}
