package net.unit8.raoh.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
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
        return result.edges().stream()
                .map(e -> e.caller() + " -> " + e.callee() + " " + e.via() + (e.virtual() ? " virtual" : ""))
                .toList();
    }

    @Test
    void recordsInvokeInstructionsWithTheNamedOwner() throws IOException {
        var edges = edges("A", """
                class A {
                    int size(java.util.List<String> list) { return list.size(); }
                    static Integer box(int i) { return Integer.valueOf(i); }
                }
                """);
        assertTrue(edges.contains("fixture.A#size -> java.util.List#size() CALL virtual"), edges::toString);
        assertTrue(edges.contains("fixture.A#box -> java.lang.Integer#valueOf(int) CALL"), edges::toString);
    }

    @Test
    void attributesALambdaBodyToTheMethodItIsWrittenIn() throws IOException {
        var edges = edges("A", """
                class A {
                    java.util.function.Supplier<String> make() { return () -> "x".trim(); }
                    static final Runnable INIT = () -> "y".strip();
                }
                """);
        assertTrue(edges.contains("fixture.A#make -> java.lang.String#trim() CALL virtual"), edges::toString);
        assertTrue(edges.contains("fixture.A#<clinit> -> java.lang.String#strip() CALL virtual"), edges::toString);
        assertTrue(edges.stream().noneMatch(e -> e.contains("lambda$")), edges::toString);
    }

    @Test
    void recordsTheTargetOfAMethodReference() throws IOException {
        var edges = edges("A", """
                class A {
                    java.util.function.Function<String, String> trim() { return String::trim; }
                }
                """);
        assertTrue(edges.contains("fixture.A#trim -> java.lang.String#trim() METHOD_HANDLE virtual"), edges::toString);
        assertTrue(edges.stream().anyMatch(e -> e.startsWith(
                "fixture.A#trim -> java.lang.invoke.LambdaMetafactory#metafactory(")), edges::toString);
    }

    @Test
    void attributesAnAnonymousClassToItsEnclosingMethod() throws IOException {
        var edges = edges("A", """
                class A {
                    Runnable task() {
                        return new Runnable() {
                            public void run() { "x".trim(); }
                        };
                    }
                }
                """);
        assertTrue(edges.contains("fixture.A#task -> java.lang.String#trim() CALL virtual"), edges::toString);
        assertTrue(edges.stream().noneMatch(e -> e.startsWith("fixture.A$1")), edges::toString);
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
        assertTrue(edges.contains("fixture.A#describe -> java.lang.String#valueOf(java.lang.Object) CALL"),
                edges::toString);
        assertTrue(edges.stream().anyMatch(e -> e.startsWith(
                "fixture.A#describe -> java.lang.invoke.StringConcatFactory#makeConcatWithConstants(")), edges::toString);
    }

    @Test
    void addsTheComponentMethodsARecordsGeneratedMethodsCall() throws IOException {
        var edges = edges("R", "record R(Object value, String name, int count) {}");
        assertTrue(edges.contains("fixture.R#equals -> java.lang.Object#equals(java.lang.Object) RECORD_COMPONENT virtual"),
                edges::toString);
        assertTrue(edges.contains("fixture.R#hashCode -> java.lang.Object#hashCode() RECORD_COMPONENT virtual"),
                edges::toString);
        assertTrue(edges.contains("fixture.R#toString -> java.lang.String#toString() RECORD_COMPONENT virtual"),
                edges::toString);
        assertTrue(edges.stream().noneMatch(e -> e.contains("int#")), edges::toString);
    }

    @Test
    void recordsAMethodAnInternalClassInheritsFromAnExternalOne() throws IOException {
        var result = Fixtures.compile(dir, Map.of(
                "E", "enum E { X }",
                "A", "class A { String name(E e) { return e.name(); } int local() { return new B().value(); } }",
                "B", "class B { int value() { return 1; } }")).scan();
        var edges = result.edges().stream().map(e -> e.caller() + " -> " + e.callee()).toList();
        assertTrue(edges.contains("fixture.A#name -> java.lang.Enum#name()"), edges::toString);
        assertTrue(edges.stream().noneMatch(e -> e.contains("-> fixture.")), edges::toString);
    }

    @Test
    void reportsABootstrapMethodItDoesNotKnow() throws IOException {
        var bootstrap = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC, ClassDesc.of("fixture.Boot"),
                "bsm", MethodTypeDesc.of(ConstantDescs.CD_CallSite, ConstantDescs.CD_MethodHandles_Lookup,
                        ConstantDescs.CD_String, ConstantDescs.CD_MethodType));
        var bytes = ClassFile.of().build(ClassDesc.of("fixture.Indy"), cb -> cb.withMethodBody("run",
                MethodTypeDesc.of(ConstantDescs.CD_void), ClassFile.ACC_STATIC, code -> code
                        .invokedynamic(DynamicCallSiteDesc.of(bootstrap, "x", MethodTypeDesc.of(ConstantDescs.CD_void)))
                        .return_()));
        var classes = Files.createDirectories(dir.resolve("classes/fixture"));
        Files.write(classes.resolve("Indy.class"), bytes);
        var scanner = new BytecodeScanner(name -> name.startsWith("fixture."),
                new Hierarchy(ClassLoader.getPlatformClassLoader()));
        var result = scanner.scan(dir.resolve("classes"));
        assertEquals(1, result.problems().size(), result.problems()::toString);
        assertTrue(result.problems().getFirst().contains("unknown bootstrap method fixture.Boot#bsm"));
    }
}
