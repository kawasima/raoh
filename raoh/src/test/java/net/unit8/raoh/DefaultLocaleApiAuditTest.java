package net.unit8.raoh;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.sun.source.util.JavacTask;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.reflect.AccessFlag;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Keeps the forbidden-API list complete against the JDK itself.
 *
 * <p>The build bans JDK calls that read the JVM default locale, because a decoder's result must
 * not depend on it (#136). A hand-written list of such calls always has gaps, and so do the lists
 * of forbidden-apis ({@code jdk-unsafe}) and Error Prone ({@code DefaultLocale}): neither knows
 * {@code ListFormat.getInstance()}, {@code DateTimeFormatter.ofLocalizedPattern(String)},
 * {@code DecimalStyle.ofDefaultLocale()} or {@code Scanner(String)}. This test derives the set
 * from the JDK instead. It walks the bytecode of every {@code java.*} module back from
 * {@code Locale.getDefault()} and {@code Locale.getDefault(Locale.Category)}, the only ways to
 * read the default locale, and requires every public API method it reaches to be banned by
 * {@code forbidden-apis/default-locale.txt}.
 *
 * <p>The walk follows non-public JDK code without limit and passes through at most one other
 * public method. One public hop catches {@code String.format}, which reads the locale through
 * {@code new Formatter()}; with more hops the result is mostly exception-message paths such as
 * {@code Integer.parseInt} → {@code Objects.checkIndex} → {@code String.format}, which do not
 * reach a decoder's result. A call is resolved up the superclass chain to the class declaring the
 * method, since bytecode names the class it was compiled against.
 *
 * <p>Some paths read the locale without letting it reach the caller, such as a debug trace.
 * {@code forbidden-apis/default-locale-reviewed.txt} names such a JDK method with the reason;
 * the walk stops there. An entry no path reaches any more fails the test, so the file cannot
 * silently outlive the JDK code it describes.
 *
 * <p>Method bodies come from the JDK running the test, which may be newer than the compiler
 * release. Methods that release does not have cannot be called by the library, so they are
 * dropped by looking each one up in javac's {@code --release} symbol table. Raising the release
 * makes this test list the new release's additions.
 */
class DefaultLocaleApiAuditTest {

    /** A method reference in JVM internal form. */
    record MethodRef(String owner, String name, String descriptor) {}

    /** The public API methods that reach a root, and the reviewed stops the walk reached. */
    record Scan(Set<String> readers, Set<String> reachedStops) {}

    static final Set<MethodRef> ROOTS = Set.of(
            new MethodRef("java/util/Locale", "getDefault", "()Ljava/util/Locale;"),
            new MethodRef("java/util/Locale", "getDefault", "(Ljava/util/Locale$Category;)Ljava/util/Locale;"));

    static final int MAX_PUBLIC_HOPS = 1;

    static Scan scan;
    static Set<String> banned;
    static Map<String, String> reviewed;

    @BeforeAll
    static void scanTheJdk() throws IOException {
        int release = Integer.parseInt(requiredProperty("raoh.release"));
        var dir = Path.of(requiredProperty("raoh.forbiddenApisDir"));
        banned = signatures(Files.readAllLines(dir.resolve("default-locale.txt"), StandardCharsets.UTF_8));
        reviewed = reviewedEntries(Files.readAllLines(dir.resolve("default-locale-reviewed.txt"), StandardCharsets.UTF_8));
        var raw = scanJdk(reviewed.keySet());
        var inRelease = releaseApi(release);
        scan = new Scan(raw.readers().stream().filter(inRelease).collect(Collectors.toSet()), raw.reachedStops());
    }

    @Test
    void everyJdkApiThatReadsTheDefaultLocaleIsBanned() {
        var uncovered = scan.readers().stream().filter(sig -> !isBanned(sig)).sorted().toList();
        assertTrue(uncovered.isEmpty(), () -> "These JDK methods read the default locale but are not banned."
                + " Add each to forbidden-apis/default-locale.txt, or, if the locale cannot reach the"
                + " caller's result, add the JDK method on the path where it is dropped to"
                + " default-locale-reviewed.txt with the reason:\n  " + String.join("\n  ", uncovered));
    }

    @Test
    void reviewedStopsAreStillOnAPathAndNotBanned() {
        var stale = reviewed.keySet().stream()
                .filter(sig -> !scan.reachedStops().contains(sig) || isBanned(sig))
                .sorted()
                .toList();
        assertTrue(stale.isEmpty(), () -> "Remove these from forbidden-apis/default-locale-reviewed.txt;"
                + " no path from the default locale reaches them any more, or they are banned anyway:\n  "
                + String.join("\n  ", stale));
    }

    @Test
    void scanFindsKnownReaders() {
        // Pins what the walk must be able to see: a direct reader, a reader one public method
        // away, and a reader the bundled lists of forbidden-apis and Error Prone both miss. The
        // expected names come from the JDK documentation, not from this scan.
        assertAll(
                () -> assertTrue(scan.readers().contains("java.lang.String#toLowerCase()")),
                () -> assertTrue(scan.readers().contains("java.lang.String#format(java.lang.String,java.lang.Object[])")),
                () -> assertTrue(scan.readers().contains("java.text.ListFormat#getInstance()")));
    }

    static boolean isBanned(String signature) {
        var owner = signature.substring(0, signature.indexOf('#'));
        var nameOnly = signature.substring(0, signature.indexOf('('));
        if (banned.contains(signature) || banned.contains(nameOnly) || banned.contains(owner)) return true;
        for (var entry : banned) {
            if (entry.endsWith(".**") && owner.startsWith(entry.substring(0, entry.length() - 2))) return true;
        }
        return false;
    }

    /**
     * Walks the {@code java.*} modules backwards from the roots.
     *
     * @param stops JDK methods, as signatures, where the walk stops because the locale read
     *              there does not reach the caller
     * @return the public API methods reached within {@link #MAX_PUBLIC_HOPS} public methods, and
     *         the stops the walk reached
     */
    static Scan scanJdk(Set<String> stops) throws IOException {
        var calls = new HashMap<MethodRef, Set<MethodRef>>();
        var api = new HashSet<MethodRef>();
        var declared = new HashSet<MethodRef>();
        var superclass = new HashMap<String, String>();
        for (var module : ModuleFinder.ofSystem().findAll()) {
            var descriptor = module.descriptor();
            if (!descriptor.name().startsWith("java.")) continue;
            var exported = descriptor.exports().stream()
                    .filter(e -> !e.isQualified())
                    .map(e -> e.source().replace('.', '/'))
                    .collect(Collectors.toSet());
            scanModule(module, exported, calls, api, declared, superclass);
        }
        // Bytecode names the class a call was compiled against, not the class declaring the
        // method: java.sql.Date#toLocalDate() calls getYear() on java/sql/Date, which inherits it
        // from java.util.Date. Resolve each call up the superclass chain to its declaration.
        var callers = new HashMap<MethodRef, Set<MethodRef>>();
        calls.forEach((callee, from) -> callers
                .computeIfAbsent(resolve(callee, declared, superclass), k -> new HashSet<>())
                .addAll(from));

        var readers = new HashSet<String>();
        var reachedStops = new HashSet<String>();
        for (var root : ROOTS) {
            var fewestHops = new HashMap<MethodRef, Integer>();
            var queue = new ArrayDeque<Map.Entry<MethodRef, Integer>>();
            queue.add(Map.entry(root, 0));
            while (!queue.isEmpty()) {
                var entry = queue.poll();
                var method = entry.getKey();
                int hops = entry.getValue();
                var known = fewestHops.get(method);
                if (known != null && known <= hops) continue;
                fewestHops.put(method, hops);
                var signature = signature(method);
                if (stops.contains(signature)) {
                    reachedStops.add(signature);
                    continue;
                }
                boolean isApi = api.contains(method);
                if (isApi) readers.add(signature);
                int next = isApi && !method.equals(root) ? hops + 1 : hops;
                if (next > MAX_PUBLIC_HOPS) continue;
                for (var caller : callers.getOrDefault(method, Set.of())) {
                    queue.add(Map.entry(caller, next));
                }
            }
        }
        return new Scan(readers, reachedStops);
    }

    private static MethodRef resolve(MethodRef callee, Set<MethodRef> declared, Map<String, String> superclass) {
        for (var owner = callee.owner(); owner != null; owner = superclass.get(owner)) {
            var candidate = new MethodRef(owner, callee.name(), callee.descriptor());
            if (declared.contains(candidate)) return candidate;
        }
        return callee;
    }

    private static void scanModule(ModuleReference module, Set<String> exported,
                                   Map<MethodRef, Set<MethodRef>> calls, Set<MethodRef> api,
                                   Set<MethodRef> declared, Map<String, String> superclass)
            throws IOException {
        try (var reader = module.open()) {
            for (var resource : reader.list().filter(r -> r.endsWith(".class") && !r.endsWith("module-info.class")).toList()) {
                byte[] bytes;
                try (var in = reader.open(resource).orElseThrow()) {
                    bytes = in.readAllBytes();
                }
                var model = ClassFile.of().parse(bytes);
                var owner = model.thisClass().asInternalName();
                var pkg = owner.substring(0, Math.max(0, owner.lastIndexOf('/')));
                boolean apiClass = model.flags().has(AccessFlag.PUBLIC) && exported.contains(pkg);
                model.superclass().ifPresent(s -> superclass.put(owner, s.asInternalName()));
                for (var method : model.methods()) {
                    var self = new MethodRef(owner, method.methodName().stringValue(), method.methodType().stringValue());
                    declared.add(self);
                    var flags = method.flags();
                    if (apiClass && (flags.has(AccessFlag.PUBLIC) || flags.has(AccessFlag.PROTECTED))) {
                        api.add(self);
                    }
                    method.code().ifPresent(code -> {
                        for (var element : code) {
                            if (element instanceof InvokeInstruction invoke) {
                                var callee = new MethodRef(invoke.owner().asInternalName(),
                                        invoke.name().stringValue(), invoke.type().stringValue());
                                calls.computeIfAbsent(callee, k -> new HashSet<>()).add(self);
                            }
                        }
                    });
                }
            }
        }
    }

    /** Formats a method the way forbidden-apis signatures name it. */
    static String signature(MethodRef method) {
        var params = MethodTypeDesc.ofDescriptor(method.descriptor()).parameterList().stream()
                .map(DefaultLocaleApiAuditTest::typeName)
                .collect(Collectors.joining(","));
        return method.owner().replace('/', '.') + "#" + method.name() + "(" + params + ")";
    }

    private static String typeName(ClassDesc type) {
        if (type.isArray()) return typeName(Objects.requireNonNull(type.componentType())) + "[]";
        if (type.isPrimitive()) return type.displayName();
        var descriptor = type.descriptorString();
        return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
    }

    /**
     * Returns a predicate telling whether a signature names a method or constructor that exists
     * in the given release's API, as {@code javac --release} sees it.
     */
    static Predicate<String> releaseApi(int release) throws IOException {
        var probe = new SimpleJavaFileObject(URI.create("string:///Probe.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return "class Probe {}";
            }
        };
        var task = (JavacTask) ToolProvider.getSystemJavaCompiler().getTask(null, null, null,
                List.of("--release", Integer.toString(release), "-proc:none"), null, List.of(probe));
        task.analyze();
        var elements = task.getElements();
        var types = task.getTypes();
        var members = new HashMap<String, Set<String>>();
        return signature -> {
            var owner = signature.substring(0, signature.indexOf('#'));
            var known = members.computeIfAbsent(owner, name -> {
                var type = elements.getTypeElement(name.replace('$', '.'));
                if (type == null) return Set.of();
                var result = new HashSet<String>();
                for (var member : type.getEnclosedElements()) {
                    if (member instanceof ExecutableElement executable
                            && (member.getKind() == ElementKind.METHOD || member.getKind() == ElementKind.CONSTRUCTOR)) {
                        var params = executable.getParameters().stream()
                                .map(p -> types.erasure(p.asType()).toString())
                                .collect(Collectors.joining(","));
                        result.add(executable.getSimpleName() + "(" + params + ")");
                    }
                }
                return result;
            });
            return known.contains(signature.substring(signature.indexOf('#') + 1).replace('$', '.'));
        };
    }

    /** Returns the signature lines of a forbidden-apis file, without comments, directives or messages. */
    static Set<String> signatures(List<String> lines) {
        var result = new HashSet<String>();
        for (var line : lines) {
            var trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("@")) continue;
            int message = trimmed.indexOf(" @ ");
            result.add((message >= 0 ? trimmed.substring(0, message) : trimmed).strip());
        }
        return result;
    }

    /** Parses {@code signature  # reason} lines; a reason is required. */
    static Map<String, String> reviewedEntries(List<String> lines) {
        var result = new LinkedHashMap<String, String>();
        for (var line : lines) {
            var trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int hash = trimmed.indexOf('#', trimmed.indexOf('(') + 1);
            assertTrue(hash > 0 && !trimmed.substring(hash + 1).isBlank(),
                    "default-locale-reviewed.txt needs a reason after '#': " + trimmed);
            result.put(trimmed.substring(0, hash).strip(), trimmed.substring(hash + 1).strip());
        }
        return result;
    }

    private static String requiredProperty(String name) {
        var value = System.getProperty(name);
        assertNotNull(value, "system property " + name + " is set by the surefire configuration in raoh/pom.xml");
        return value;
    }
}
