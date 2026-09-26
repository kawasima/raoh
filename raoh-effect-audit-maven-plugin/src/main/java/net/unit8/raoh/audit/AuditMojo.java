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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

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
        try (var loader = new URLClassLoader(urls(), ClassLoader.getPlatformClassLoader())) {
            var hierarchy = new Hierarchy(loader);
            // The internal classes of other modules (raoh, for raoh-json) join the call graph, so a
            // decoder here that reaches an ambient read through them is caught.
            var dependencies = classpathElements.stream()
                    .map(java.nio.file.Path::of)
                    .filter(p -> !p.toAbsolutePath().equals(classesDirectory.toPath().toAbsolutePath()))
                    .toList();
            var scan = new BytecodeScanner(name -> inPackages(name, internalPackages), hierarchy)
                    .scan(classesDirectory.toPath(), dependencies);
            var report = EffectAudit.check(scan, EffectCatalog.read(catalog.toPath()),
                    Approvals.read(approvals.toPath()), hierarchy, name -> inPackages(name, decoderPackages));
            if (!report.passed()) {
                throw new MojoFailureException("Effect audit failed. The rules are in "
                        + "raoh-effect-audit-maven-plugin's EffectAudit." + report.describe(
                                catalog.getName(), approvals.getName()));
            }
            getLog().info("Effect audit passed: " + scan.edges().size() + " external uses, "
                    + (Files.exists(approvals.toPath()) ? Approvals.read(approvals.toPath()).uses().size() : 0)
                    + " approved");
        } catch (IOException | IllegalArgumentException e) {
            throw new MojoExecutionException("Effect audit could not run: " + e.getMessage(), e);
        }
    }

    private URL[] urls() throws MojoExecutionException {
        var urls = new ArrayList<URL>();
        for (var element : classpathElements) {
            try {
                urls.add(new File(element).toURI().toURL());
            } catch (MalformedURLException e) {
                throw new MojoExecutionException("bad classpath element " + element, e);
            }
        }
        return urls.toArray(URL[]::new);
    }

    private static boolean inPackages(String className, List<String> packages) {
        for (var prefix : packages) {
            if (className.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }
}
