package net.unit8.raoh.audit;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Compiles small Java sources in package {@code fixture} (or a subpackage) and scans them. */
final class Fixtures {

    private Fixtures() {}

    /** Compiled fixture classes and a hierarchy that can load them. */
    record Compiled(Path classes, Hierarchy hierarchy) {
        BytecodeScanner.Result scan() throws IOException {
            return new BytecodeScanner(name -> name.startsWith("fixture."), hierarchy).scan(classes);
        }
    }

    /**
     * Compiles sources, keyed by simple class name, into a fresh directory.
     *
     * @param dir a temporary directory
     * @param sources class name to source text, each in package {@code fixture}
     * @return the compiled classes
     * @throws IOException if a file cannot be written
     */
    static Compiled compile(Path dir, Map<String, String> sources) throws IOException {
        var src = Files.createDirectories(dir.resolve("src/fixture"));
        var out = Files.createDirectories(dir.resolve("classes"));
        var files = new ArrayList<String>(List.of("-d", out.toString(), "--release", "25"));
        for (var entry : sources.entrySet()) {
            // A key such as "decode/D" puts the class in a subpackage; its source declares the package.
            var file = src.resolve(entry.getKey() + ".java");
            Files.createDirectories(file.getParent());
            var text = entry.getValue().startsWith("package ") ? entry.getValue() : "package fixture;\n" + entry.getValue();
            Files.writeString(file, text, StandardCharsets.UTF_8);
            files.add(file.toString());
        }
        int status = ToolProvider.getSystemJavaCompiler().run(null, null, null, files.toArray(String[]::new));
        if (status != 0) {
            throw new IllegalStateException("fixture does not compile");
        }
        var loader = new URLClassLoader(new java.net.URL[] {out.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
        return new Compiled(out, new Hierarchy(loader));
    }
}
