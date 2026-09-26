package net.unit8.raoh;

import com.sun.source.util.JavacTask;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.reflect.AccessFlag;
import java.net.URI;
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
 * The call graph of the {@code java.*} modules of the JDK running the tests, walked backwards to
 * find the public API methods that reach a given JDK method.
 *
 * <p>This is the mechanism shared by the audits that keep a forbidden-API list complete, such as
 * {@link DefaultLocaleApiAuditTest} and {@link DefaultTimeZoneApiAuditTest}. What to start from,
 * how far to walk and which list to check against is each audit's own policy.
 *
 * <p>The graph is built once per JVM, since reading every class of the JDK takes seconds.
 */
final class JdkCallGraph {

    /** A method reference in JVM internal form. */
    record MethodRef(String owner, String name, String descriptor) {}

    /** The public API methods that reach a root, and the reviewed stops the walk reached. */
    record Scan(Set<String> readers, Set<String> reachedStops) {}

    private static JdkCallGraph system;

    private final Map<MethodRef, Set<MethodRef>> callers;
    private final Set<MethodRef> api;

    private JdkCallGraph(Map<MethodRef, Set<MethodRef>> callers, Set<MethodRef> api) {
        this.callers = callers;
        this.api = api;
    }

    /**
     * Returns the call graph of the {@code java.*} modules of the running JDK.
     *
     * @return the graph, built on the first call
     */
    static synchronized JdkCallGraph ofSystem() {
        if (system == null) {
            try {
                system = build();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return system;
    }

    /**
     * Walks the graph backwards from the roots.
     *
     * <p>The walk follows non-public JDK code without limit and passes through at most
     * {@code maxPublicHops} public methods other than a root.
     *
     * @param roots         the JDK methods to start from
     * @param stops         JDK methods, as signatures, where the walk stops because what is read
     *                      below them does not reach the caller
     * @param maxPublicHops how many public methods the walk may pass through
     * @return the public API methods reached, and the stops the walk reached
     */
    Scan scanBackwards(Set<MethodRef> roots, Set<String> stops, int maxPublicHops) {
        var readers = new HashSet<String>();
        var reachedStops = new HashSet<String>();
        for (var root : roots) {
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
                if (next > maxPublicHops) continue;
                for (var caller : callers.getOrDefault(method, Set.of())) {
                    queue.add(Map.entry(caller, next));
                }
            }
        }
        return new Scan(readers, reachedStops);
    }

    private static JdkCallGraph build() throws IOException {
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
        return new JdkCallGraph(callers, api);
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

    /**
     * Formats a method the way forbidden-apis signatures name it.
     *
     * @param method the method
     * @return the signature, such as {@code java.lang.String#format(java.lang.String,java.lang.Object[])}
     */
    static String signature(MethodRef method) {
        var params = MethodTypeDesc.ofDescriptor(method.descriptor()).parameterList().stream()
                .map(JdkCallGraph::typeName)
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
     *
     * <p>Method bodies come from the JDK running the test, which may be newer than the compiler
     * release. Methods that release does not have cannot be called by the library.
     *
     * @param release the {@code --release} the library is compiled for
     * @return the predicate
     */
    static Predicate<String> releaseApi(int release) {
        var probe = new SimpleJavaFileObject(URI.create("string:///Probe.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return "class Probe {}";
            }
        };
        var task = (JavacTask) ToolProvider.getSystemJavaCompiler().getTask(null, null, null,
                List.of("--release", Integer.toString(release), "-proc:none"), null, List.of(probe));
        try {
            task.analyze();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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

    /**
     * Tells whether a forbidden-apis signature file bans a method, by its exact signature, by
     * name, by class or by a {@code .**} package pattern.
     *
     * @param signature the method's signature
     * @param banned    the signature lines of the file
     * @return whether the method is banned
     */
    static boolean isBanned(String signature, Set<String> banned) {
        var owner = signature.substring(0, signature.indexOf('#'));
        var nameOnly = signature.substring(0, signature.indexOf('('));
        if (banned.contains(signature) || banned.contains(nameOnly) || banned.contains(owner)) return true;
        for (var entry : banned) {
            if (entry.endsWith(".**") && owner.startsWith(entry.substring(0, entry.length() - 2))) return true;
        }
        return false;
    }

    /**
     * Returns the signature lines of a forbidden-apis file, without comments, directives or messages.
     *
     * @param lines the file's lines
     * @return the signatures
     */
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

    /**
     * Parses {@code signature  # reason} lines; a reason is required.
     *
     * @param lines the file's lines
     * @param file  the file's name, for the failure message
     * @return the reason for each signature
     */
    static Map<String, String> reviewedEntries(List<String> lines, String file) {
        var result = new LinkedHashMap<String, String>();
        for (var line : lines) {
            var trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int hash = trimmed.indexOf('#', trimmed.indexOf('(') + 1);
            assertTrue(hash > 0 && !trimmed.substring(hash + 1).isBlank(),
                    file + " needs a reason after '#': " + trimmed);
            result.put(trimmed.substring(0, hash).strip(), trimmed.substring(hash + 1).strip());
        }
        return result;
    }
}
