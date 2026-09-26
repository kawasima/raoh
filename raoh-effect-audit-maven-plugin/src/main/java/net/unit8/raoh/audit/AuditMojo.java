package net.unit8.raoh.audit;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Fails the build when the module's bytecode uses an external member whose effect is not
 * reviewed, or uses a {@code DELEGATED} or {@code AMBIENT} member without an approval for that
 * caller. See {@link EffectAudit} for the rules.
 *
 * <p>Runs after compilation, so {@code mvn test} fails too, not only {@code mvn verify}.
 */
@Mojo(name = "audit", defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class AuditMojo extends AbstractMojo {

    /** Creates a new mojo instance. */
    public AuditMojo() {}

    /** The compiler's output directory to audit. */
    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true, required = true)
    private File classesDirectory;

    /** The compile classpath, for resolving the class hierarchy. */
    @Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true, required = true)
    private List<String> classpathElements;

    /** The catalog of reviewed effects, shared by every audited module. */
    @Parameter(required = true)
    private File catalog;

    /** This module's approvals of {@code DELEGATED} and {@code AMBIENT} uses. May not exist yet. */
    @Parameter(required = true)
    private File approvals;

    /** Package prefixes of the audited code base; members of these are not external. */
    @Parameter(required = true)
    private List<String> internalPackages;

    /**
     * Package prefixes of decoder code, from which no {@code AMBIENT} use may be reached, directly
     * or through internal methods.
     */
    @Parameter(required = true)
    private List<String> decoderPackages;

    /**
     * The Java release the module compiles for. JDK classes are read from that release's API,
     * the data {@code javac --release} uses, so the audit gives the same answer on any JDK that
     * can build the release.
     */
    @Parameter(defaultValue = "${maven.compiler.release}", required = true)
    private int release;

    /** Skips the audit. */
    @Parameter(property = "raoh.effectAudit.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Effect audit skipped");
            return;
        }
        if (!classesDirectory.isDirectory()) {
            getLog().info("No classes to audit");
            return;
        }
        var classpath = classpathElements.stream().map(Path::of).toList();
        try (var hierarchy = Hierarchy.open(classpath, release)) {
            // The internal classes of other modules (raoh, for raoh-json) join the call graph, so a
            // decoder here that reaches an ambient read through them is caught.
            var dependencies = classpath.stream()
                    .filter(p -> !p.toAbsolutePath().equals(classesDirectory.toPath().toAbsolutePath()))
                    .toList();
            Predicate<String> isInternal = inPackages(internalPackages);
            var scan = new BytecodeScanner(internalPackages, hierarchy).scan(classesDirectory.toPath(), dependencies);
            var approved = Approvals.read(approvals.toPath());
            var report = EffectAudit.check(scan, EffectCatalog.read(catalog.toPath()), approved, hierarchy,
                    isInternal, inPackages(decoderPackages));
            if (!report.passed()) {
                throw new MojoFailureException("Effect audit failed. The rules are in "
                        + "raoh-effect-audit-maven-plugin's EffectAudit." + report.describe(
                                catalog.getName(), approvals.getName()));
            }
            getLog().info("Effect audit passed: " + scan.edges().size() + " external uses, "
                    + approved.uses().size() + " approved");
        } catch (IOException | RuntimeException | LinkageError e) {
            // Anything the scan throws, a class that cannot be loaded included, is a failed audit,
            // not an internal error of Maven.
            throw new MojoExecutionException("Effect audit could not run: " + e, e);
        }
    }

    /** Whether a class, by binary name, is in one of the packages or their subpackages. */
    private static Predicate<String> inPackages(List<String> packages) {
        var prefixes = packages.stream().map(p -> p + ".").toList();
        return className -> prefixes.stream().anyMatch(className::startsWith);
    }
}
