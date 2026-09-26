package net.unit8.raoh.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BytecodeScannerTest {

    @TempDir
    Path dir;

    private List<String> edges(String className, String body) throws IOException {
        var result = Fixtures.compile(dir, Map.of(className, body)).scan();
        assertEquals(List.of(), result.problems());
        return describe(result);
    }

    private static List<String> describe(BytecodeScanner.Result result) {
        return result.edges().stream()
                .map(e -> e.caller() + " -> " + e.callee() + " " + e.via() + (e.virtual() ? " virtual" : ""))
                .toList();
    }

    @Test
    void recordsInvokeInstructionsWithTheNamedOwnerAndDescriptor() throws IOException {
        var edges = edges("A", """
                class A {
                    int size(java.util.List<String> list) { return list.size(); }
                    static Integer box(int i) { return Integer.valueOf(i); }
                }
                """);
        assertTrue(edges.contains("fixture.A#size -> java.util.List#size():int CALL virtual"), edges::toString);
        assertTrue(edges.contains("fixture.A#box -> java.lang.Integer#valueOf(int):java.lang.Integer CALL"), edges::toString);
    }

    @Test
    void attributesALambdaBodyToTheMethodItIsWrittenIn() throws IOException {
        var edges = edges("A", """
                class A {
                    java.util.function.Supplier<String> make() { return () -> "x".trim(); }
                    static final Runnable INIT = () -> "y".strip();
                }
                """);
        assertTrue(edges.contains("fixture.A#make -> java.lang.String#trim():java.lang.String CALL virtual"), edges::toString);
        assertTrue(edges.contains("fixture.A#<clinit> -> java.lang.String#strip():java.lang.String CALL virtual"), edges::toString);
        assertTrue(edges.stream().noneMatch(e -> e.contains("lambda$")), edges::toString);
    }

    @Test
    void recordsTheTargetOfAMethodReference() throws IOException {
        var edges = edges("A", """
                class A {
                    java.util.function.Function<String, String> trim() { return String::trim; }
                }
                """);
        assertTrue(edges.contains("fixture.A#trim -> java.lang.String#trim():java.lang.String METHOD_HANDLE virtual"),
                edges::toString);
        assertTrue(edges.stream().anyMatch(e -> e.startsWith(
                "fixture.A#trim -> java.lang.invoke.LambdaMetafactory#metafactory(")), edges::toString);
    }

    @Test
    void attributesAnAnonymousClassToItsEnclosingMethodOrInitializer() throws IOException {
        var edges = edges("A", """
                class A {
                    static final Runnable FIELD = new Runnable() { public void run() { "f".strip(); } };
                    Runnable task() {
                        return new Runnable() { public void run() { "x".trim(); } };
                    }
                }
                """);
        assertTrue(edges.contains("fixture.A#task -> java.lang.String#trim():java.lang.String CALL virtual"), edges::toString);
        assertTrue(edges.contains("fixture.A#<initializer> -> java.lang.String#strip():java.lang.String CALL virtual"),
                edges::toString);
        assertTrue(edges.stream().noneMatch(e -> e.startsWith("fixture.A$")), edges::toString);
    }

    @Test
    void seesTheConversionOfAStringConcatenationOperand() throws IOException {
        // javac (since JDK 19) converts a non-String reference operand with String.valueOf before
        // the invokedynamic, so the conversion is an ordinary call. The scanner would add an
        // Object#toString() edge if an operand reached StringConcatFactory unconverted.
        var edges = edges("A", """
                class A {
                    String describe(Object o, String s, int n) { return "o=" + o + " s=" + s + " n=" + n; }
                }
                """);
        assertTrue(edges.contains("fixture.A#describe -> java.lang.String#valueOf(java.lang.Object):java.lang.String CALL"),
                edges::toString);
    }

    @Test
    void addsTheComponentMethodsARecordsGeneratedMethodsCall() throws IOException {
        var edges = edges("R", "record R(Object value, String name, int count) {}");
        assertTrue(edges.contains("fixture.R#equals -> java.lang.Object#equals(java.lang.Object):boolean RECORD_COMPONENT virtual"),
                edges::toString);
        assertTrue(edges.contains("fixture.R#hashCode -> java.lang.Object#hashCode():int RECORD_COMPONENT virtual"),
                edges::toString);
        assertTrue(edges.contains("fixture.R#toString -> java.lang.String#toString():java.lang.String RECORD_COMPONENT virtual"),
                edges::toString);
        assertTrue(edges.stream().noneMatch(e -> e.contains("int#")), edges::toString);
    }

    @Test
    void addsTheIntValueATypeSwitchCallsForAnIntegerLabel() throws IOException {
        // The generated switch compares an Integer label with the selector's own intValue().
        var edges = edges("A", """
                class A {
                    static int f(Number n) {
                        return switch (n) {
                            case Integer i when i > 5 -> 1;
                            case Long l -> 2;
                            default -> 0;
                        };
                    }
                    static int g(Integer x) {
                        return switch (x) {
                            case 1 -> 10;
                            case Integer i when i > 5 -> 20;
                            default -> 0;
                        };
                    }
                }
                """);
        assertTrue(edges.contains("fixture.A#g -> java.lang.Number#intValue():int NAMED_BY_BOOTSTRAP virtual"),
                edges::toString);
        assertTrue(edges.stream().noneMatch(e -> e.startsWith("fixture.A#f -> java.lang.Number#intValue")),
                edges::toString);
    }

    @Test
    void recordsTheEnumConstantsAnEnumSwitchLooksUpByName() throws IOException {
        var edges = edges("A", """
                class A {
                    static int f(java.time.DayOfWeek d) {
                        return switch (d) {
                            case MONDAY -> 1;
                            case java.time.DayOfWeek x when x.getValue() > 5 -> 2;
                            default -> 0;
                        };
                    }
                }
                """);
        assertTrue(edges.contains("fixture.A#f -> java.time.DayOfWeek#MONDAY:java.time.DayOfWeek NAMED_BY_BOOTSTRAP"),
                edges::toString);
    }

    @Test
    void recordsTheFieldAGetStaticFinalConstantReads() throws IOException {
        var constant = DynamicConstantDesc.ofNamed(ConstantDescs.BSM_GET_STATIC_FINAL, "ROOT",
                ClassDesc.of("java.util.Locale"));
        writeClass(ClassFile.of().build(ClassDesc.of("fixture.Condy"), cb -> cb.withMethodBody("run",
                MethodTypeDesc.of(ConstantDescs.CD_Object), ClassFile.ACC_STATIC, code -> code
                        .ldc(constant).areturn())));
        var result = scanWritten();
        assertEquals(List.of(), result.problems());
        assertTrue(describe(result).contains("fixture.Condy#run -> java.util.Locale#ROOT:java.util.Locale NAMED_BY_BOOTSTRAP"),
                describe(result)::toString);
    }

    @Test
    void reportsAVarHandleForInternalStaticState() throws IOException {
        var handle = DynamicConstantDesc.ofNamed(ConstantDescs.BSM_VARHANDLE_STATIC_FIELD, "current",
                ConstantDescs.CD_VarHandle, ClassDesc.of("fixture.Settings"), ConstantDescs.CD_Object);
        writeClass(ClassFile.of().build(ClassDesc.of("fixture.Settings"), cb -> cb
                .withField("current", ConstantDescs.CD_Object, ClassFile.ACC_STATIC)
                .withMethodBody("handle", MethodTypeDesc.of(ConstantDescs.CD_Object), ClassFile.ACC_STATIC,
                        code -> code.ldc(handle).areturn())));
        var result = scanWritten();
        assertTrue(result.problems().stream().anyMatch(p -> p.contains("VarHandle for the static field fixture.Settings.current")),
                result.problems()::toString);
    }

    @Test
    void reportsABootstrapMethodItDoesNotKnow() throws IOException {
        var bootstrap = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC, ClassDesc.of("fixture.Boot"),
                "bsm", MethodTypeDesc.of(ConstantDescs.CD_CallSite, ConstantDescs.CD_MethodHandles_Lookup,
                        ConstantDescs.CD_String, ConstantDescs.CD_MethodType));
        writeClass(ClassFile.of().build(ClassDesc.of("fixture.Indy"), cb -> cb.withMethodBody("run",
                MethodTypeDesc.of(ConstantDescs.CD_void), ClassFile.ACC_STATIC, code -> code
                        .invokedynamic(DynamicCallSiteDesc.of(bootstrap, "x", MethodTypeDesc.of(ConstantDescs.CD_void)))
                        .return_())));
        var result = scanWritten();
        assertEquals(1, result.problems().size(), result.problems()::toString);
        assertTrue(result.problems().getFirst().contains("unknown bootstrap method fixture.Boot#bsm"));
    }

    @Test
    void recordsAMethodAnInternalClassInheritsFromAnExternalOne() throws IOException {
        var result = Fixtures.compile(dir, Map.of(
                "E", "enum E { X }",
                "A", "class A { String name(E e) { return e.name(); } int local() { return new B().value(); } }",
                "B", "class B { int value() { return 1; } }")).scan();
        var edges = result.edges().stream().map(e -> e.caller() + " -> " + e.callee()).toList();
        assertTrue(edges.contains("fixture.A#name -> java.lang.Enum#name():java.lang.String"), edges::toString);
        assertTrue(edges.stream().noneMatch(e -> e.contains("-> fixture.")), edges::toString);
        // The internal call is kept in the call graph instead.
        var local = Member.parse("fixture.A#local():int");
        assertTrue(result.graph().callsOf(local).stream()
                .anyMatch(c -> c.target().equals(Member.parse("fixture.B#value():int"))), result.graph().callsOf(local)::toString);
    }

    private void writeClass(byte[] bytes) throws IOException {
        var classes = Files.createDirectories(dir.resolve("classes/fixture"));
        var file = classes.resolve(ClassFile.of().parse(bytes).thisClass().asSymbol().displayName() + ".class");
        Files.write(file, bytes);
    }

    private BytecodeScanner.Result scanWritten() throws IOException {
        return new BytecodeScanner(List.of("fixture"), Hierarchy.open(List.of(dir.resolve("classes")), Fixtures.RELEASE))
                .scan(dir.resolve("classes"));
    }
}
