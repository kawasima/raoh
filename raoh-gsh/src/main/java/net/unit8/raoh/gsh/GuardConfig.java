package net.unit8.raoh.gsh;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Configuration for the domain construction guard, specifying which classes to weave.
 *
 * <p>Classes are matched by package patterns (glob-style) and/or explicit class names.
 * Exclusion patterns take precedence over inclusion patterns.
 *
 * <p><strong>Properties file format ({@code raoh-gsh.properties}):</strong></p>
 * <pre>
 * guard.packages=com.example.domain.**,com.example.model.*
 * guard.classes=com.example.special.Money
 * guard.exclude=com.example.domain.internal.**
 * </pre>
 *
 * <p><strong>Agent argument format:</strong></p>
 * <pre>
 * packages=com.example.domain.**;exclude=com.example.domain.internal.**
 * </pre>
 */
public final class GuardConfig {

    private final List<Pattern> packagePatterns;
    private final List<String> classes;
    private final List<Pattern> excludePatterns;

    private GuardConfig(List<Pattern> packagePatterns, List<String> classes, List<Pattern> excludePatterns) {
        this.packagePatterns = packagePatterns;
        this.classes = classes;
        this.excludePatterns = excludePatterns;
    }

    /**
     * Determines whether the given class name is a weaving target.
     *
     * <p>A class is a target if it matches any package pattern or is listed explicitly,
     * and does not match any exclusion pattern.
     *
     * @param className the fully qualified class name (dot-separated, e.g. {@code com.example.Email})
     * @return {@code true} if the class should be woven
     */
    public boolean isTarget(String className) {
        for (Pattern exclude : excludePatterns) {
            if (exclude.matcher(className).matches()) {
                return false;
            }
        }
        if (classes.contains(className)) {
            return true;
        }
        for (Pattern pattern : packagePatterns) {
            if (pattern.matcher(className).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines whether the given internal class name (slash-separated) is a weaving target.
     *
     * @param internalName the internal class name (e.g. {@code com/example/Email})
     * @return {@code true} if the class should be woven
     */
    public boolean isTargetInternal(String internalName) {
        return isTarget(internalName.replace('/', '.'));
    }

    /**
     * Parses a Java Agent argument string.
     *
     * <p>Format: {@code key=value;key=value} where keys are {@code packages},
     * {@code classes}, and {@code exclude}. Values within each key are comma-separated.
     *
     * @param args the agent argument string, may be {@code null}
     * @return the parsed configuration
     */
    public static GuardConfig parse(String args) {
        if (args == null || args.isBlank()) {
            return new GuardConfig(List.of(), List.of(), List.of());
        }
        List<Pattern> packagePatterns = new ArrayList<>();
        List<String> classes = new ArrayList<>();
        List<Pattern> excludePatterns = new ArrayList<>();

        for (String part : args.split(";")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim();
            String value = kv[1].trim();
            switch (key) {
                case "packages" -> {
                    for (String p : value.split(",")) {
                        packagePatterns.add(globToPattern(p.trim()));
                    }
                }
                case "classes" -> {
                    for (String c : value.split(",")) {
                        classes.add(c.trim());
                    }
                }
                case "exclude" -> {
                    for (String e : value.split(",")) {
                        excludePatterns.add(globToPattern(e.trim()));
                    }
                }
                default -> { /* ignore unknown keys */ }
            }
        }
        return new GuardConfig(packagePatterns, classes, excludePatterns);
    }

    /**
     * Loads configuration from a properties file.
     *
     * @param path the path to the properties file
     * @return the loaded configuration
     * @throws IOException if the file cannot be read
     */
    public static GuardConfig load(Path path) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        }
        return fromProperties(props);
    }

    /**
     * Loads configuration from a properties file on the classpath.
     *
     * @param resourceName the classpath resource name (e.g. {@code raoh-gsh.properties})
     * @return the loaded configuration, or an empty config if the resource is not found
     */
    public static GuardConfig loadFromClasspath(String resourceName) {
        try (InputStream in = GuardConfig.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                return new GuardConfig(List.of(), List.of(), List.of());
            }
            Properties props = new Properties();
            props.load(in);
            return fromProperties(props);
        } catch (IOException e) {
            return new GuardConfig(List.of(), List.of(), List.of());
        }
    }

    private static GuardConfig fromProperties(Properties props) {
        List<Pattern> packagePatterns = new ArrayList<>();
        List<String> classes = new ArrayList<>();
        List<Pattern> excludePatterns = new ArrayList<>();

        String packages = props.getProperty("guard.packages", "");
        for (String p : packages.split(",")) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                packagePatterns.add(globToPattern(trimmed));
            }
        }
        String classNames = props.getProperty("guard.classes", "");
        for (String c : classNames.split(",")) {
            String trimmed = c.trim();
            if (!trimmed.isEmpty()) {
                classes.add(trimmed);
            }
        }
        String excludes = props.getProperty("guard.exclude", "");
        for (String e : excludes.split(",")) {
            String trimmed = e.trim();
            if (!trimmed.isEmpty()) {
                excludePatterns.add(globToPattern(trimmed));
            }
        }
        return new GuardConfig(packagePatterns, classes, excludePatterns);
    }

    /**
     * Converts a glob pattern to a regex pattern.
     *
     * <p>Supports:
     * <ul>
     *   <li>{@code **} — matches any number of package segments (including zero)</li>
     *   <li>{@code *} — matches a single package segment (no dots)</li>
     * </ul>
     *
     * @param glob the glob pattern (e.g. {@code com.example.domain.**})
     * @return the compiled regex pattern
     */
    static Pattern globToPattern(String glob) {
        StringBuilder regex = new StringBuilder();
        int i = 0;
        while (i < glob.length()) {
            if (i + 1 < glob.length() && glob.charAt(i) == '*' && glob.charAt(i + 1) == '*') {
                regex.append(".*");
                i += 2;
                if (i < glob.length() && glob.charAt(i) == '.') {
                    i++; // skip trailing dot after **
                }
            } else if (glob.charAt(i) == '*') {
                regex.append("[^.]*");
                i++;
            } else if (glob.charAt(i) == '.') {
                regex.append("\\.");
                i++;
            } else {
                regex.append(Pattern.quote(String.valueOf(glob.charAt(i))));
                i++;
            }
        }
        return Pattern.compile(regex.toString());
    }
}
