package net.unit8.raoh.gsh.maven;

import net.unit8.raoh.gsh.GuardConfig;
import net.unit8.raoh.gsh.GuardWeaver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Maven Mojo that weaves domain construction guards into compiled class files.
 *
 * <p><strong>Configuration example:</strong></p>
 * <pre>{@code
 * <plugin>
 *     <groupId>net.unit8.raoh</groupId>
 *     <artifactId>raoh-gsh-maven-plugin</artifactId>
 *     <version>${raoh.version}</version>
 *     <configuration>
 *         <packages>com.example.domain.**,com.example.model.*</packages>
 *         <exclude>com.example.domain.internal.**</exclude>
 *     </configuration>
 *     <executions>
 *         <execution>
 *             <goals>
 *                 <goal>weave</goal>
 *             </goals>
 *         </execution>
 *     </executions>
 * </plugin>
 * }</pre>
 */
@Mojo(name = "weave", defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES)
public class GuardWeaveMojo extends AbstractMojo {

    /**
     * Comma-separated package glob patterns for classes to weave.
     */
    @Parameter(property = "raoh.gsh.packages")
    private String packages;

    /**
     * Comma-separated fully qualified class names to weave.
     */
    @Parameter(property = "raoh.gsh.classes")
    private String classes;

    /**
     * Comma-separated exclusion glob patterns.
     */
    @Parameter(property = "raoh.gsh.exclude")
    private String exclude;

    /**
     * Path to a {@code raoh-gsh.properties} configuration file.
     * If set, {@code packages}, {@code classes}, and {@code exclude} parameters are ignored.
     */
    @Parameter(property = "raoh.gsh.config")
    private String config;

    /**
     * Whether to weave main classes in addition to test classes.
     * Defaults to {@code false} to avoid leaking guard calls into packaged artifacts.
     */
    @Parameter(defaultValue = "false", property = "raoh.gsh.weaveMain")
    private boolean weaveMain;

    /**
     * The main classes directory to weave (only when {@code weaveMain} is {@code true}).
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", property = "raoh.gsh.target")
    private String target;

    /**
     * The test classes directory to weave.
     */
    @Parameter(defaultValue = "${project.build.testOutputDirectory}", property = "raoh.gsh.testTarget")
    private String testTarget;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            GuardConfig guardConfig = buildConfig();

            int total = 0;
            if (weaveMain) {
                Path targetPath = Path.of(target);
                if (Files.isDirectory(targetPath)) {
                    int count = GuardWeaver.weaveDirectory(targetPath, guardConfig);
                    getLog().info("Wove " + count + " class(es) in " + target);
                    total += count;
                }
            }

            Path testTargetPath = Path.of(testTarget);
            if (Files.isDirectory(testTargetPath)) {
                int count = GuardWeaver.weaveDirectory(testTargetPath, guardConfig);
                getLog().info("Wove " + count + " class(es) in " + testTarget);
                total += count;
            }

            if (total == 0) {
                getLog().warn("No classes were woven. Check your package/class configuration.");
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to weave classes", e);
        }
    }

    private GuardConfig buildConfig() throws IOException {
        if (config != null && !config.isBlank()) {
            return GuardConfig.load(Path.of(config));
        }

        StringBuilder agentArgs = new StringBuilder();
        if (packages != null && !packages.isBlank()) {
            agentArgs.append("packages=").append(packages);
        }
        if (classes != null && !classes.isBlank()) {
            if (!agentArgs.isEmpty()) agentArgs.append(";");
            agentArgs.append("classes=").append(classes);
        }
        if (exclude != null && !exclude.isBlank()) {
            if (!agentArgs.isEmpty()) agentArgs.append(";");
            agentArgs.append("exclude=").append(exclude);
        }

        if (agentArgs.isEmpty()) {
            return GuardConfig.loadFromClasspath("raoh-gsh.properties");
        }
        return GuardConfig.parse(agentArgs.toString());
    }
}
