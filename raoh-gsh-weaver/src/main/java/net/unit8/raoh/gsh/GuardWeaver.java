package net.unit8.raoh.gsh;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Weaves construction guard checks into domain class bytecode using the ClassFile API (JEP 484).
 *
 * <p>For each target class, all {@code <init>} methods are transformed to call
 * {@code net.unit8.raoh.gsh.DomainConstructionScope#checkActive(String)} immediately after the super
 * constructor invocation. Constructors that delegate to another constructor of the
 * same class via {@code this(...)} are left untouched, since the delegated-to
 * constructor already contains the guard.
 */
public final class GuardWeaver {

    private static final ClassDesc CD_SCOPE = ClassDesc.of("net.unit8.raoh.gsh.DomainConstructionScope");
    private static final MethodTypeDesc MTD_CHECK = MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)V");

    private GuardWeaver() {
    }

    /**
     * Weaves a guard check into the given class bytes.
     *
     * @param classBytes the original class file bytes
     * @return the transformed class file bytes with guard checks injected
     */
    public static byte[] weave(byte[] classBytes) {
        var cf = ClassFile.of();
        ClassModel classModel = cf.parse(classBytes);
        String thisClassName = classModel.thisClass().asInternalName();
        String superClassName = classModel.superclass()
                .map(entry -> entry.asInternalName())
                .orElse("java/lang/Object");
        String dotClassName = thisClassName.replace('/', '.');

        return cf.transformClass(classModel, (cb, ce) -> {
            if (ce instanceof MethodModel mm && mm.methodName().equalsString("<init>")) {
                cb.transformMethod(mm, (mb, me) -> {
                    if (me instanceof CodeModel code) {
                        boolean[] injected = {false};
                        // Check if this constructor delegates to this(...) rather than super(...)
                        boolean isDelegating = isDelegatingConstructor(code, thisClassName);
                        // Track whether the previous instruction was aload_0 (loading 'this')
                        boolean[] prevWasAload0 = {false};

                        mb.transformCode(code, (cob, coe) -> {
                            cob.with(coe);
                            if (!isDelegating
                                    && !injected[0]
                                    && prevWasAload0[0]
                                    && coe instanceof InvokeInstruction inv
                                    && inv.opcode() == Opcode.INVOKESPECIAL
                                    && inv.name().equalsString("<init>")
                                    && inv.owner().asInternalName().equals(superClassName)) {
                                injected[0] = true;
                                cob.ldc(dotClassName);
                                cob.invokestatic(CD_SCOPE, "checkActive", MTD_CHECK);
                            }
                            prevWasAload0[0] = isAload0(coe);
                        });
                    } else {
                        mb.with(me);
                    }
                });
            } else {
                cb.with(ce);
            }
        });
    }

    /**
     * Checks if a constructor delegates to another constructor of the same class via {@code this(...)}.
     *
     * <p>Only considers {@code invokespecial <init>} calls on the uninitialized {@code this}
     * (i.e., preceded by {@code aload_0}). A {@code new ThisClass(...)} inside the constructor
     * body is not treated as delegation.
     *
     * @param code          the code model of the constructor
     * @param thisClassName the internal name of the class
     * @return {@code true} if the constructor delegates to {@code this(...)}
     */
    private static boolean isDelegatingConstructor(CodeModel code, String thisClassName) {
        boolean prevWasAload0 = false;
        for (var element : code) {
            if (prevWasAload0
                    && element instanceof InvokeInstruction inv
                    && inv.opcode() == Opcode.INVOKESPECIAL
                    && inv.name().equalsString("<init>")
                    && inv.owner().asInternalName().equals(thisClassName)) {
                return true;
            }
            prevWasAload0 = isAload0(element);
        }
        return false;
    }

    /**
     * Checks if a code element is an {@code aload_0} instruction (loading {@code this}).
     *
     * @param element the code element to check
     * @return {@code true} if the element loads local variable slot 0
     */
    private static boolean isAload0(CodeElement element) {
        return element instanceof LoadInstruction load
                && load.slot() == 0
                && load.typeKind() == java.lang.classfile.TypeKind.REFERENCE;
    }

    /**
     * Weaves all target class files in the given directory.
     *
     * @param classesDir the root directory containing compiled {@code .class} files
     * @param config     the guard configuration specifying which classes to weave
     * @return the number of classes that were woven
     * @throws IOException if an I/O error occurs
     */
    public static int weaveDirectory(Path classesDir, GuardConfig config) throws IOException {
        int[] count = {0};
        Files.walkFileTree(classesDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.toString().endsWith(".class")) {
                    return FileVisitResult.CONTINUE;
                }
                String relativePath = classesDir.relativize(file).toString()
                        .replace(file.getFileSystem().getSeparator(), "/");
                // Convert path to internal class name: com/example/Foo.class -> com/example/Foo
                String internalName = relativePath.substring(0, relativePath.length() - ".class".length());
                if (config.isTargetInternal(internalName)) {
                    byte[] original = Files.readAllBytes(file);
                    byte[] transformed = weave(original);
                    Files.write(file, transformed);
                    count[0]++;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return count[0];
    }
}
