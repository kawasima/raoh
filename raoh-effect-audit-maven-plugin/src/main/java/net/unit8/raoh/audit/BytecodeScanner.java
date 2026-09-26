package net.unit8.raoh.audit;

import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Extracts what the audited class files depend on: their external members, and the calls
 * between internal methods.
 *
 * <p>An external edge is recorded for each external member a method uses: an invoke or field
 * instruction, a method handle constant (the target of a lambda or method reference), and the
 * bootstrap method of an {@code invokedynamic} or dynamic constant. Some bootstraps reach members
 * the bytecode does not name as a method handle, and the scanner adds those: {@code ObjectMethods}
 * (a record's {@code equals}, {@code hashCode} and {@code toString}) calls the same method on each
 * reference component; {@code StringConcatFactory} calls {@code toString()} on each reference
 * operand; {@code typeSwitch} calls {@code Number#intValue()} on the selector for an
 * {@code Integer} label; enum switch labels, {@code ConstantBootstraps.enumConstant}, {@code getStaticFinal} and
 * the field {@code VarHandle} bootstraps look a field up by name. A bootstrap without such an
 * expansion is reported as a problem rather than recorded, so a new kind of bootstrap fails the
 * audit until the scanner knows what it reaches.
 *
 * <p>A use of an internal member is not an external edge. It goes into the {@link CallGraph}
 * instead, so the audit can follow internal helpers: a call, a method handle (a lambda body is
 * reached from where the lambda is created), and a reference to another internal class, which
 * runs that class's static initializer. A method an internal class inherits from an external
 * one is external ({@code SomeEnum.name()} records {@code java.lang.Enum#name()}).
 *
 * <p>The caller an approval names is {@code ClassName#method}, without the descriptor, so
 * overloads share their approvals. Code whose name the compiler chooses is attributed to the
 * method it was written in, so adding a lambda does not rename the others: a lambda body
 * {@code lambda$list$3} to {@code list} ({@code lambda$static$0} to {@code <clinit>},
 * {@code lambda$new$0} to {@code <init>}), and every method of an anonymous or local class to
 * its enclosing method, or to {@code <initializer>} when it is written in a field initializer or
 * an initializer block. The call graph keeps every method under its exact name.
 */
public final class BytecodeScanner {

    private static final Pattern LAMBDA = Pattern.compile("lambda\\$(.+)\\$\\d+");

    private static final String LAMBDA_METAFACTORY = "java.lang.invoke.LambdaMetafactory#metafactory";
    private static final String LAMBDA_ALT_METAFACTORY = "java.lang.invoke.LambdaMetafactory#altMetafactory";
    private static final String CONCAT = "java.lang.invoke.StringConcatFactory#makeConcatWithConstants";
    private static final String CONCAT_PLAIN = "java.lang.invoke.StringConcatFactory#makeConcat";
    private static final String OBJECT_METHODS = "java.lang.runtime.ObjectMethods#bootstrap";
    private static final String TYPE_SWITCH = "java.lang.runtime.SwitchBootstraps#typeSwitch";
    private static final String ENUM_SWITCH = "java.lang.runtime.SwitchBootstraps#enumSwitch";
    private static final String NULL_CONSTANT = "java.lang.invoke.ConstantBootstraps#nullConstant";
    private static final String PRIMITIVE_CLASS = "java.lang.invoke.ConstantBootstraps#primitiveClass";
    private static final String INVOKE = "java.lang.invoke.ConstantBootstraps#invoke";
    private static final String EXPLICIT_CAST = "java.lang.invoke.ConstantBootstraps#explicitCast";
    private static final String ENUM_CONSTANT = "java.lang.invoke.ConstantBootstraps#enumConstant";
    private static final String GET_STATIC_FINAL = "java.lang.invoke.ConstantBootstraps#getStaticFinal";
    private static final String FIELD_VAR_HANDLE = "java.lang.invoke.ConstantBootstraps#fieldVarHandle";
    private static final String STATIC_FIELD_VAR_HANDLE = "java.lang.invoke.ConstantBootstraps#staticFieldVarHandle";

    /**
     * Bootstraps whose every reach the scanner records: the method handles among their
     * arguments, which it always follows, plus the expansion {@link Collector} applies for the
     * ones that look members up by name. Any other bootstrap is a problem.
     */
    private static final Set<String> KNOWN_BOOTSTRAPS = Set.of(
            LAMBDA_METAFACTORY, LAMBDA_ALT_METAFACTORY, CONCAT, CONCAT_PLAIN, OBJECT_METHODS,
            TYPE_SWITCH, ENUM_SWITCH, NULL_CONSTANT, PRIMITIVE_CLASS, INVOKE, EXPLICIT_CAST,
            ENUM_CONSTANT, GET_STATIC_FINAL, FIELD_VAR_HANDLE, STATIC_FIELD_VAR_HANDLE);

    private static final String STATIC_INITIALIZER = "<clinit>";

    private final List<String> internalPackages;
    private final Predicate<String> internal;
    private final Hierarchy hierarchy;

    /**
     * Creates a scanner.
     *
     * @param internalPackages the packages of the audited code base; their subpackages belong to it too
     * @param hierarchy resolves methods an internal class inherits from an external one
     */
    public BytecodeScanner(List<String> internalPackages, Hierarchy hierarchy) {
        this.internalPackages = List.copyOf(internalPackages);
        var prefixes = internalPackages.stream().map(p -> p + ".").toList();
        this.internal = name -> prefixes.stream().anyMatch(name::startsWith);
        this.hierarchy = hierarchy;
    }

    /**
     * What a scan found.
     *
     * @param edges the external members the audited classes use, sorted by caller and callee
     * @param graph the calls between internal methods, of the audited classes and of the
     *              internal classes they depend on
     * @param audited the binary names of the audited classes
     * @param problems code the scanner cannot account for, such as an unknown bootstrap method
     */
    public record Result(List<Edge> edges, CallGraph graph, Set<String> audited, List<String> problems) {}

    /**
     * Scans every class file under a directory.
     *
     * @param classesDirectory the compiler's output directory
     * @return the edges, call graph and problems found
     * @throws IOException if a class file cannot be read
     */
    public Result scan(Path classesDirectory) throws IOException {
        return scan(classesDirectory, List.of());
    }

    /**
     * Scans the audited classes, and the internal classes of their dependencies for the call graph.
     *
     * @param classesDirectory the audited module's output directory
     * @param dependencies directories or jars on the module's classpath; only their internal
     *                     classes are read, and only into the call graph
     * @return the edges of the audited classes, the call graph over all internal classes, and problems
     * @throws IOException if a class file cannot be read
     */
    public Result scan(Path classesDirectory, List<Path> dependencies) throws IOException {
        var own = read(classesDirectory, name -> true);
        var context = new ArrayList<ClassModel>();
        for (var dependency : dependencies) {
            if (Files.isDirectory(dependency)) {
                context.addAll(readInternal(dependency));
            } else if (Files.isRegularFile(dependency) && dependency.toString().endsWith(".jar")) {
                try (var jar = FileSystems.newFileSystem(dependency)) {
                    for (var root : jar.getRootDirectories()) {
                        context.addAll(readInternal(root));
                    }
                }
            }
        }

        var all = new ArrayList<ClassModel>(own);
        all.addAll(context);
        var attribution = attribution(all);
        var graph = new CallGraph();
        var edges = new LinkedHashSet<Edge>();
        var problems = new ArrayList<String>();
        var audited = new LinkedHashSet<String>();
        for (var model : own) {
            audited.add(Member.typeName(model.thisClass().asSymbol()));
        }
        for (var model : all) {
            // A dependency's classes feed the call graph only; their own module audits their edges.
            scanClass(model, attribution, graph, audited.contains(Member.typeName(model.thisClass().asSymbol())),
                    edges, problems);
        }
        var sorted = edges.stream()
                .sorted(Comparator.comparing(Edge::caller).thenComparing(e -> e.callee().toString())
                        .thenComparing(Edge::via))
                .toList();
        return new Result(sorted, graph, Set.copyOf(audited), List.copyOf(problems));
    }

    /**
     * Parses the internal classes of a dependency: only the directories of the internal packages
     * are walked, so a library jar such as jOOQ's is not listed entry by entry.
     */
    private List<ClassModel> readInternal(Path root) throws IOException {
        var models = new ArrayList<ClassModel>();
        for (var pkg : internalPackages) {
            var dir = root.resolve(pkg.replace(".", root.getFileSystem().getSeparator()));
            if (Files.isDirectory(dir)) {
                models.addAll(read(root, dir, internal));
            }
        }
        return models;
    }

    private static List<ClassModel> read(Path root, Predicate<String> wanted) throws IOException {
        return read(root, root, wanted);
    }

    /** Parses the class files under {@code dir} whose binary name, taken from the path below {@code root}, is wanted. */
    private static List<ClassModel> read(Path root, Path dir, Predicate<String> wanted) throws IOException {
        var models = new ArrayList<ClassModel>();
        try (var files = Files.walk(dir)) {
            for (var file : files.filter(f -> f.toString().endsWith(".class")).sorted().toList()) {
                var relative = root.relativize(file).toString();
                var name = relative.substring(0, relative.length() - ".class".length())
                        .replace(file.getFileSystem().getSeparator(), ".");
                if (name.endsWith("module-info") || name.startsWith("META-INF.") || !wanted.test(name)) {
                    continue;
                }
                models.add(ClassFile.of().parse(Files.readAllBytes(file)));
            }
        }
        return models;
    }

    private void scanClass(ClassModel model, Map<String, String> attribution, CallGraph graph,
                           boolean audited, Set<Edge> edges, List<String> problems) {
        var classDesc = model.thisClass().asSymbol();
        var className = Member.typeName(classDesc);
        var declared = new LinkedHashSet<Member>();
        for (var method : model.methods()) {
            var code = method.code();
            if (code.isEmpty()) {
                continue;
            }
            var self = Member.method(classDesc, method.methodName().stringValue(), method.methodTypeSymbol());
            declared.add(self);
            var collector = new Collector(caller(className, self.name(), attribution), self, className,
                    graph, audited, edges, problems);
            for (var element : code.get()) {
                switch (element) {
                    case InvokeInstruction i -> collector.add(
                            Member.method(i.owner().asSymbol(), i.name().stringValue(), i.typeSymbol()),
                            i.opcode() == Opcode.INVOKEVIRTUAL || i.opcode() == Opcode.INVOKEINTERFACE,
                            Edge.Via.CALL);
                    case FieldInstruction f -> collector.field(f);
                    case NewObjectInstruction n -> collector.touch(Member.typeName(n.className().asSymbol()));
                    case InvokeDynamicInstruction indy -> collector.invokedynamic(indy);
                    case ConstantInstruction c -> collector.constant(c.constantValue());
                    default -> { }
                }
            }
        }
        graph.addClass(className, declared);
    }

    /**
     * Maps each anonymous or local class to the {@code Class#method} it is written in, following
     * nested ones outwards.
     */
    private static Map<String, String> attribution(List<ClassModel> models) {
        var direct = new HashMap<String, String>();
        for (var model : models) {
            model.findAttribute(Attributes.enclosingMethod()).ifPresent(em -> direct.put(
                    Member.typeName(model.thisClass().asSymbol()),
                    Member.typeName(em.enclosingClass().asSymbol()) + "#"
                            // No method: the class is in a field or static initializer, which the
                            // attribute does not tell apart.
                            + em.enclosingMethodName().map(Utf8Entry::stringValue).orElse("<initializer>")));
        }
        var resolved = new HashMap<String, String>();
        for (var entry : direct.entrySet()) {
            var target = entry.getValue();
            // The enclosing class may itself be local: follow until a named class is reached.
            for (int depth = 0; depth < 64; depth++) {
                var outer = direct.get(target.substring(0, target.indexOf('#')));
                if (outer == null) {
                    break;
                }
                target = outer;
            }
            resolved.put(entry.getKey(), target);
        }
        return resolved;
    }

    private static String caller(String className, String methodName, Map<String, String> attribution) {
        var enclosing = attribution.get(className);
        if (enclosing != null) {
            return enclosing;
        }
        var lambda = LAMBDA.matcher(methodName);
        var name = !lambda.matches() ? methodName : switch (lambda.group(1)) {
            case "static" -> "<clinit>";
            case "new" -> "<init>";
            default -> lambda.group(1);
        };
        return className + "#" + name;
    }

    /** Collects what one method depends on. */
    private final class Collector {
        private final String caller;
        private final Member self;
        private final String className;
        private final CallGraph graph;
        private final boolean audited;
        private final Set<Edge> edges;
        private final List<String> problems;

        Collector(String caller, Member self, String className, CallGraph graph, boolean audited,
                  Set<Edge> edges, List<String> problems) {
            this.caller = caller;
            this.self = self;
            this.className = className;
            this.graph = graph;
            this.audited = audited;
            this.edges = edges;
            this.problems = problems;
        }

        private void problem(String message) {
            if (audited) {
                problems.add(caller + ": " + message);
            }
        }

        void field(FieldInstruction f) {
            var member = Member.field(f.owner().asSymbol(), f.name().stringValue(), f.typeSymbol());
            // A static field Raoh writes after class initialization is state every decoder shares,
            // set by whoever wrote last: ambient configuration the call graph cannot follow.
            if (f.opcode() == Opcode.PUTSTATIC && internal.test(member.owner())
                    && !(member.owner().equals(className) && self.name().equals(STATIC_INITIALIZER))) {
                problem("writes the static field " + member + " outside its class initializer;"
                        + " mutable static state is ambient");
            }
            add(member, false, Edge.Via.FIELD);
        }

        void add(Member member, boolean virtual, Edge.Via via) {
            if (internal.test(member.owner())) {
                touch(member.owner());
                if (member.isField()) {
                    return;
                }
                java.util.Optional<String> declaring;
                try {
                    declaring = member.isConstructor() ? java.util.Optional.empty() : hierarchy.declaringClass(member);
                } catch (IllegalStateException e) {
                    problem("cannot resolve " + member + ": " + e.getMessage());
                    return;
                }
                if (declaring.isEmpty() || internal.test(declaring.get())) {
                    graph.addCall(self, new CallGraph.Call(member, virtual));
                    return;
                }
                member = new Member(declaring.get(), member.name(), member.parameters(), member.type());
            }
            var edge = new Edge(caller, self, member, virtual, via);
            if (audited) {
                edges.add(edge);
            }
            graph.addExternal(self, edge);
        }

        /** A reference to an internal class runs its static initializer, if it has not run yet. */
        void touch(String type) {
            if (internal.test(type) && !type.equals(className)) {
                graph.addCall(self, new CallGraph.Call(
                        new Member(type, STATIC_INITIALIZER, List.of(), "void"), false));
            }
        }

        void invokedynamic(InvokeDynamicInstruction indy) {
            var bootstrap = bootstrap(indy.bootstrapMethod());
            if (bootstrap == null) {
                return;
            }
            indy.bootstrapArgs().forEach(this::constant);
            switch (bootstrap) {
                case CONCAT, CONCAT_PLAIN -> {
                    for (var operand : indy.typeSymbol().parameterList()) {
                        if (!operand.isPrimitive() && !operand.equals(ConstantDescs.CD_String)) {
                            add(Member.method(receiver(operand), "toString", MethodTypeDesc.of(ConstantDescs.CD_String)),
                                    true, Edge.Via.STRING_CONCAT);
                        }
                    }
                }
                case OBJECT_METHODS -> recordComponents(indy);
                case TYPE_SWITCH -> {
                    // The generated switch compares a String label with label.equals(selector), a
                    // Class label with isInstance, and an Integer label with the selector's own
                    // intValue(), which a caller's Number subclass supplies.
                    for (var label : indy.bootstrapArgs()) {
                        if (label instanceof Integer) {
                            add(Member.method(ConstantDescs.CD_Number, "intValue", MethodTypeDesc.of(ConstantDescs.CD_int)),
                                    true, Edge.Via.NAMED_BY_BOOTSTRAP);
                        } else if (label instanceof ClassDesc type && type.isPrimitive()) {
                            problem("typeSwitch on a primitive type pattern, whose conversions"
                                    + " the scanner does not expand");
                        }
                    }
                }
                case ENUM_SWITCH -> {
                    // String labels name constants of the enum being switched on.
                    var enumType = indy.typeSymbol().parameterList().getFirst();
                    for (var label : indy.bootstrapArgs()) {
                        if (label instanceof String constant) {
                            add(Member.field(enumType, constant, enumType), false, Edge.Via.NAMED_BY_BOOTSTRAP);
                        }
                    }
                }
                default -> { }
            }
        }

        private void recordComponents(InvokeDynamicInstruction indy) {
            var generated = switch (indy.name().stringValue()) {
                case "equals" -> MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_Object);
                case "hashCode" -> MethodTypeDesc.of(ConstantDescs.CD_int);
                case "toString" -> MethodTypeDesc.of(ConstantDescs.CD_String);
                default -> null;
            };
            if (generated == null) {
                problem("ObjectMethods bootstrap for unknown method " + indy.name().stringValue());
                return;
            }
            for (var arg : indy.bootstrapArgs()) {
                if (arg instanceof DirectMethodHandleDesc getter && getter.kind() == DirectMethodHandleDesc.Kind.GETTER) {
                    var component = getter.invocationType().returnType();
                    if (!component.isPrimitive()) {
                        add(Member.method(receiver(component), indy.name().stringValue(), generated),
                                true, Edge.Via.RECORD_COMPONENT);
                    }
                }
            }
        }

        void constant(ConstantDesc value) {
            switch (value) {
                case DirectMethodHandleDesc handle -> handle(handle);
                case DynamicConstantDesc<?> dynamic -> {
                    var bootstrap = bootstrap(dynamic.bootstrapMethod());
                    if (bootstrap == null) {
                        return;
                    }
                    dynamic.bootstrapArgsList().forEach(this::constant);
                    var args = dynamic.bootstrapArgsList();
                    switch (bootstrap) {
                        case ENUM_CONSTANT -> add(Member.field(dynamic.constantType(), dynamic.constantName(),
                                dynamic.constantType()), false, Edge.Via.NAMED_BY_BOOTSTRAP);
                        case GET_STATIC_FINAL -> {
                            var owner = !args.isEmpty() && args.getFirst() instanceof ClassDesc declaring
                                    ? declaring : dynamic.constantType();
                            add(Member.field(owner, dynamic.constantName(), dynamic.constantType()),
                                    false, Edge.Via.NAMED_BY_BOOTSTRAP);
                        }
                        case FIELD_VAR_HANDLE, STATIC_FIELD_VAR_HANDLE -> {
                            if (args.size() == 2 && args.get(0) instanceof ClassDesc declaring
                                    && args.get(1) instanceof ClassDesc fieldType) {
                                if (bootstrap.equals(STATIC_FIELD_VAR_HANDLE) && internal.test(Member.typeName(declaring))) {
                                    problem("takes a VarHandle for the static field " + Member.typeName(declaring) + "."
                                            + dynamic.constantName() + "; mutable static state is ambient");
                                }
                                add(Member.field(declaring, dynamic.constantName(), fieldType),
                                        false, Edge.Via.NAMED_BY_BOOTSTRAP);
                            } else {
                                problem("field VarHandle bootstrap with unexpected arguments " + args);
                            }
                        }
                        case NULL_CONSTANT, PRIMITIVE_CLASS, INVOKE, EXPLICIT_CAST -> { }
                        default -> problem("bootstrap " + bootstrap
                                + " used for a dynamic constant, which the scanner does not expand");
                    }
                }
                default -> { }
            }
        }

        /**
         * Records a bootstrap method and returns its {@code Class#name} key, or {@code null} after
         * reporting it when the scanner does not know what it reaches.
         */
        private String bootstrap(DirectMethodHandleDesc bootstrap) {
            var key = Member.typeName(bootstrap.owner()) + "#" + bootstrap.methodName();
            if (!KNOWN_BOOTSTRAPS.contains(key)) {
                problem("unknown bootstrap method " + key + "; teach BytecodeScanner which members it reaches");
                return null;
            }
            add(Member.method(bootstrap.owner(), bootstrap.methodName(),
                    MethodTypeDesc.ofDescriptor(bootstrap.lookupDescriptor())), false, Edge.Via.BOOTSTRAP);
            return key;
        }

        private void handle(DirectMethodHandleDesc handle) {
            if (handle.kind() == DirectMethodHandleDesc.Kind.STATIC_SETTER && internal.test(Member.typeName(handle.owner()))) {
                problem("takes a setter handle for the static field " + handle.owner().displayName() + "."
                        + handle.methodName() + "; mutable static state is ambient");
            }
            switch (handle.kind()) {
                case GETTER, SETTER, STATIC_GETTER, STATIC_SETTER -> add(Member.field(handle.owner(),
                        handle.methodName(), ClassDesc.ofDescriptor(handle.lookupDescriptor())),
                        false, Edge.Via.METHOD_HANDLE);
                // methodName() is "<init>" for a constructor handle.
                default -> add(Member.method(handle.owner(), handle.methodName(),
                        MethodTypeDesc.ofDescriptor(handle.lookupDescriptor())),
                        handle.kind() == DirectMethodHandleDesc.Kind.VIRTUAL
                                || handle.kind() == DirectMethodHandleDesc.Kind.INTERFACE_VIRTUAL,
                        Edge.Via.METHOD_HANDLE);
            }
        }

        /** The class whose method runs for a value of {@code type}: {@code Object} for an array. */
        private static ClassDesc receiver(ClassDesc type) {
            return type.isArray() ? ConstantDescs.CD_Object : type;
        }
    }
}
