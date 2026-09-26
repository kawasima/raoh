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
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodTypeDesc;
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
 * Extracts the external members the audited class files depend on.
 *
 * <p>An edge is recorded for each external member a method uses: an invoke or field instruction,
 * a method handle constant (the target of a lambda or method reference), and the bootstrap
 * method of an {@code invokedynamic} or dynamic constant. Two bootstraps also call members the
 * bytecode does not name, and the scanner adds those as edges:
 * {@code StringConcatFactory} calls {@code toString()} on each reference operand of a string
 * concatenation, and {@code ObjectMethods} (a record's {@code equals}, {@code hashCode} and
 * {@code toString}) calls the same method on each reference component. A bootstrap outside the
 * set the scanner knows is reported as a problem rather than recorded, because it may call
 * members the scanner cannot see.
 *
 * <p>A member of an internal class is not recorded, unless it is a method the internal class
 * inherits from an external one ({@code SomeEnum.name()} records {@code java.lang.Enum#name()}).
 *
 * <p>The caller is {@code ClassName#method}, without the descriptor, so overloads share their
 * approvals. Code whose name the compiler chooses is attributed to the method it was written in,
 * so adding a lambda does not rename the others: a lambda body {@code lambda$list$3} to
 * {@code list} ({@code lambda$static$0} to {@code <clinit>}, {@code lambda$new$0} to
 * {@code <init>}), and every method of an anonymous or local class to its enclosing method, or
 * to {@code <initializer>} when it is written in a field initializer or an initializer block.
 */
public final class BytecodeScanner {

    private static final Pattern LAMBDA = Pattern.compile("lambda\\$(.+)\\$\\d+");

    /** Bootstraps that call nothing beyond the method handles in their arguments. */
    private static final Set<String> PLAIN_BOOTSTRAPS = Set.of(
            "java.lang.invoke.LambdaMetafactory#metafactory",
            "java.lang.invoke.LambdaMetafactory#altMetafactory",
            "java.lang.runtime.SwitchBootstraps#typeSwitch",
            "java.lang.runtime.SwitchBootstraps#enumSwitch",
            "java.lang.invoke.ConstantBootstraps#nullConstant",
            "java.lang.invoke.ConstantBootstraps#primitiveClass",
            "java.lang.invoke.ConstantBootstraps#enumConstant",
            "java.lang.invoke.ConstantBootstraps#getStaticFinal",
            "java.lang.invoke.ConstantBootstraps#invoke",
            "java.lang.invoke.ConstantBootstraps#fieldVarHandle",
            "java.lang.invoke.ConstantBootstraps#staticFieldVarHandle",
            "java.lang.invoke.ConstantBootstraps#arrayVarHandle",
            "java.lang.invoke.ConstantBootstraps#explicitCast");

    private static final Set<String> CONCAT_BOOTSTRAPS = Set.of(
            "java.lang.invoke.StringConcatFactory#makeConcatWithConstants",
            "java.lang.invoke.StringConcatFactory#makeConcat");

    private static final String OBJECT_METHODS_BOOTSTRAP = "java.lang.runtime.ObjectMethods#bootstrap";

    private final Predicate<String> internal;
    private final Hierarchy hierarchy;

    /**
     * Creates a scanner.
     *
     * @param internal whether a class, by binary name, belongs to the audited code base
     * @param hierarchy resolves methods an internal class inherits from an external one
     */
    public BytecodeScanner(Predicate<String> internal, Hierarchy hierarchy) {
        this.internal = internal;
        this.hierarchy = hierarchy;
    }

    /**
     * What a scan found.
     *
     * @param edges the external members used, sorted by caller and then callee
     * @param problems code the scanner cannot account for, such as an unknown bootstrap method
     */
    public record Result(List<Edge> edges, List<String> problems) {}

    /**
     * Scans every class file under a directory.
     *
     * @param classesDirectory the compiler's output directory
     * @return the edges and problems found
     * @throws IOException if a class file cannot be read
     */
    public Result scan(Path classesDirectory) throws IOException {
        var models = new ArrayList<ClassModel>();
        try (var files = Files.walk(classesDirectory)) {
            for (var file : files.filter(f -> f.toString().endsWith(".class")).sorted().toList()) {
                models.add(ClassFile.of().parse(Files.readAllBytes(file)));
            }
        }
        var attribution = attribution(models);
        var edges = new LinkedHashSet<Edge>();
        var problems = new ArrayList<String>();
        for (var model : models) {
            var className = Member.typeName(model.thisClass().asSymbol());
            for (var method : model.methods()) {
                var code = method.code();
                if (code.isEmpty()) {
                    continue;
                }
                var caller = caller(className, method.methodName().stringValue(), attribution);
                var collector = new Collector(caller, edges, problems);
                for (var element : code.get()) {
                    switch (element) {
                        case InvokeInstruction i -> collector.add(
                                Member.method(i.owner().asSymbol(), i.name().stringValue(), i.typeSymbol()),
                                i.opcode() == Opcode.INVOKEVIRTUAL || i.opcode() == Opcode.INVOKEINTERFACE,
                                Edge.Via.CALL);
                        case FieldInstruction f -> collector.add(
                                Member.field(f.owner().asSymbol(), f.name().stringValue()), false, Edge.Via.FIELD);
                        case InvokeDynamicInstruction indy -> collector.invokedynamic(indy);
                        case ConstantInstruction c -> collector.constant(c.constantValue());
                        default -> { }
                    }
                }
            }
        }
        var sorted = edges.stream()
                .sorted(Comparator.comparing(Edge::caller).thenComparing(e -> e.callee().toString())
                        .thenComparing(Edge::via))
                .toList();
        return new Result(sorted, List.copyOf(problems));
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
                var outerClass = target.substring(0, target.indexOf('#'));
                var outer = direct.get(outerClass);
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

    /** Collects the edges of one method. */
    private final class Collector {
        private final String caller;
        private final Set<Edge> edges;
        private final List<String> problems;

        Collector(String caller, Set<Edge> edges, List<String> problems) {
            this.caller = caller;
            this.edges = edges;
            this.problems = problems;
        }

        void add(Member member, boolean virtual, Edge.Via via) {
            if (internal.test(member.owner())) {
                if (member.isField() || member.name().equals("<init>")) {
                    return;
                }
                var declaring = hierarchy.declaringClass(member);
                if (declaring.isEmpty() || internal.test(declaring.get())) {
                    return;
                }
                member = new Member(declaring.get(), member.name(), member.parameters());
            }
            edges.add(new Edge(caller, member, virtual, via));
        }

        void invokedynamic(InvokeDynamicInstruction indy) {
            var bootstrap = bootstrap(indy.bootstrapMethod());
            if (bootstrap == null) {
                return;
            }
            indy.bootstrapArgs().forEach(this::constant);
            if (CONCAT_BOOTSTRAPS.contains(bootstrap)) {
                for (var operand : indy.typeSymbol().parameterList()) {
                    if (!operand.isPrimitive() && !operand.equals(ConstantDescs.CD_String)) {
                        add(Member.method(receiver(operand), "toString", MethodTypeDesc.of(ConstantDescs.CD_String)),
                                true, Edge.Via.STRING_CONCAT);
                    }
                }
            } else if (bootstrap.equals(OBJECT_METHODS_BOOTSTRAP)) {
                var generated = switch (indy.name().stringValue()) {
                    case "equals" -> MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_Object);
                    case "hashCode" -> MethodTypeDesc.of(ConstantDescs.CD_int);
                    case "toString" -> MethodTypeDesc.of(ConstantDescs.CD_String);
                    default -> null;
                };
                if (generated == null) {
                    problems.add(caller + ": ObjectMethods bootstrap for unknown method " + indy.name().stringValue());
                    return;
                }
                for (var arg : indy.bootstrapArgs()) {
                    if (arg instanceof DirectMethodHandleDesc getter
                            && getter.kind() == DirectMethodHandleDesc.Kind.GETTER) {
                        var component = getter.invocationType().returnType();
                        if (!component.isPrimitive()) {
                            add(Member.method(receiver(component), indy.name().stringValue(), generated),
                                    true, Edge.Via.RECORD_COMPONENT);
                        }
                    }
                }
            }
        }

        void constant(ConstantDesc value) {
            switch (value) {
                case DirectMethodHandleDesc handle -> handle(handle);
                case DynamicConstantDesc<?> dynamic -> {
                    if (bootstrap(dynamic.bootstrapMethod()) != null) {
                        dynamic.bootstrapArgsList().forEach(this::constant);
                    }
                }
                default -> { }
            }
        }

        /**
         * Records a bootstrap method and returns its {@code Class#name} key, or {@code null} after
         * reporting it when the scanner does not know what it calls.
         */
        private String bootstrap(DirectMethodHandleDesc bootstrap) {
            var key = Member.typeName(bootstrap.owner()) + "#" + bootstrap.methodName();
            if (!PLAIN_BOOTSTRAPS.contains(key) && !CONCAT_BOOTSTRAPS.contains(key)
                    && !key.equals(OBJECT_METHODS_BOOTSTRAP)) {
                problems.add(caller + ": unknown bootstrap method " + key
                        + "; teach BytecodeScanner which members it calls");
                return null;
            }
            add(Member.method(bootstrap.owner(), bootstrap.methodName(),
                    MethodTypeDesc.ofDescriptor(bootstrap.lookupDescriptor())), false, Edge.Via.BOOTSTRAP);
            return key;
        }

        private void handle(DirectMethodHandleDesc handle) {
            switch (handle.kind()) {
                case GETTER, SETTER, STATIC_GETTER, STATIC_SETTER ->
                        add(Member.field(handle.owner(), handle.methodName()), false, Edge.Via.METHOD_HANDLE);
                case CONSTRUCTOR -> add(Member.method(handle.owner(), "<init>",
                        MethodTypeDesc.ofDescriptor(handle.lookupDescriptor())), false, Edge.Via.METHOD_HANDLE);
                case VIRTUAL, INTERFACE_VIRTUAL -> add(Member.method(handle.owner(), handle.methodName(),
                        MethodTypeDesc.ofDescriptor(handle.lookupDescriptor())), true, Edge.Via.METHOD_HANDLE);
                case STATIC, INTERFACE_STATIC, SPECIAL, INTERFACE_SPECIAL -> add(Member.method(handle.owner(),
                        handle.methodName(), MethodTypeDesc.ofDescriptor(handle.lookupDescriptor())),
                        false, Edge.Via.METHOD_HANDLE);
            }
        }

        /** The class whose method runs for a value of {@code type}: {@code Object} for an array. */
        private static ClassDesc receiver(ClassDesc type) {
            return type.isArray() ? ConstantDescs.CD_Object : type;
        }
    }
}
