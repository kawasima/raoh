package net.unit8.raoh.audit;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URL;
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

    /**
     * Compiled fixture classes and a hierarchy that can load them.
     *
     * @param classes the output directory
     * @param dependencies directories compiled earlier that these classes depend on
     * @param hierarchy a hierarchy over the classes and their dependencies
     */
    record Compiled(Path classes, List<Path> dependencies, Hierarchy hierarchy) {
        BytecodeScanner.Result scan() throws IOException {
            return new BytecodeScanner(name -> name.startsWith("fixture."), hierarchy).scan(classes, dependencies);
        }
    }

    /**
     * Compiles sources, keyed by class path under {@code fixture/}, into {@code dir/<name>}.
     *
     * @param dir a temporary directory
     * @param sources file name (such as {@code "A"} or {@code "decode/D"}) to source text; a source
     *                without a package declaration is put in package {@code fixture}
     * @return the compiled classes
     * @throws IOException if a file cannot be written
     */
    static Compiled compile(Path dir, Map<String, String> sources) throws IOException {
        return compile(dir, "classes", sources);
    }

    /**
     * Compiles sources into {@code dir/<name>}, against classes compiled earlier.
     *
     * @param dir a temporary directory
     * @param name the output directory name under {@code dir}
     * @param sources file name to source text
     * @param dependencies classes compiled earlier, on the classpath
     * @return the compiled classes
     * @throws IOException if a file cannot be written
     */
    static Compiled compile(Path dir, String name, Map<String, String> sources, Compiled... dependencies)
            throws IOException {
        var src = Files.createDirectories(dir.resolve("src-" + name + "/fixture"));
        var out = Files.createDirectories(dir.resolve(name));
        var deps = new ArrayList<Path>();
        for (var dependency : dependencies) {
            deps.add(dependency.classes());
        }
        var args = new ArrayList<String>(List.of("-d", out.toString(), "--release", "25"));
        if (!deps.isEmpty()) {
            args.addAll(List.of("-cp", String.join(java.io.File.pathSeparator, deps.stream().map(Path::toString).toList())));
        }
        for (var entry : sources.entrySet()) {
            var file = src.resolve(entry.getKey() + ".java");
            Files.createDirectories(file.getParent());
            var text = entry.getValue().startsWith("package ") ? entry.getValue() : "package fixture;\n" + entry.getValue();
            Files.writeString(file, text, StandardCharsets.UTF_8);
            args.add(file.toString());
        }
        int status = ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new));
        if (status != 0) {
            throw new IllegalStateException("fixture does not compile");
        }
        var urls = new ArrayList<URL>();
        urls.add(out.toUri().toURL());
        for (var dep : deps) {
            urls.add(dep.toUri().toURL());
        }
        var loader = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
        return new Compiled(out, List.copyOf(deps), new Hierarchy(loader));
    }

    /**
     * Audits compiled fixtures with a decoder package of {@code fixture.decode}.
     *
     * @param compiled the fixtures
     * @param dir a directory to write the catalog and approvals to
     * @param catalog the catalog text
     * @param approvals the approvals text
     * @return the report
     * @throws IOException if a file cannot be written
     */
    static EffectAudit.Report audit(Compiled compiled, Path dir, String catalog, String approvals) throws IOException {
        var catalogFile = Files.writeString(dir.resolve("catalog.txt"), catalog);
        var approvalFile = Files.writeString(dir.resolve("approvals.txt"), approvals);
        return EffectAudit.check(compiled.scan(), EffectCatalog.read(catalogFile), Approvals.read(approvalFile),
                compiled.hierarchy(), name -> name.startsWith("fixture.decode."));
    }
}
