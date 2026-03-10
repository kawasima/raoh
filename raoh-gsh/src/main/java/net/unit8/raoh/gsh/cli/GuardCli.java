package net.unit8.raoh.gsh.cli;

import net.unit8.raoh.gsh.GuardConfig;
import net.unit8.raoh.gsh.GuardWeaver;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Command-line tool for weaving construction guards into compiled class files.
 *
 * <p><strong>Usage:</strong></p>
 * <pre>
 * java -jar raoh-gsh.jar --packages "com.example.domain.**" --target target/classes
 * java -jar raoh-gsh.jar --config raoh-gsh.properties --target target/classes
 * </pre>
 */
public final class GuardCli {

    private GuardCli() {
    }

    /**
     * CLI entry point.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String packages = null;
        String classes = null;
        String exclude = null;
        String configFile = null;
        String target = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--packages" -> packages = nextArg(args, i++);
                case "--classes" -> classes = nextArg(args, i++);
                case "--exclude" -> exclude = nextArg(args, i++);
                case "--config" -> configFile = nextArg(args, i++);
                case "--target" -> target = nextArg(args, i++);
                case "--help", "-h" -> {
                    printUsage();
                    System.exit(0);
                }
                default -> {
                    System.err.println("Unknown option: " + args[i]);
                    printUsage();
                    System.exit(1);
                }
            }
        }

        if (target == null) {
            System.err.println("Error: --target is required");
            printUsage();
            System.exit(1);
        }

        try {
            GuardConfig config;
            if (configFile != null) {
                config = GuardConfig.load(Path.of(configFile));
            } else {
                StringBuilder agentArgs = new StringBuilder();
                if (packages != null) {
                    agentArgs.append("packages=").append(packages);
                }
                if (classes != null) {
                    if (!agentArgs.isEmpty()) agentArgs.append(";");
                    agentArgs.append("classes=").append(classes);
                }
                if (exclude != null) {
                    if (!agentArgs.isEmpty()) agentArgs.append(";");
                    agentArgs.append("exclude=").append(exclude);
                }
                config = GuardConfig.parse(agentArgs.toString());
            }

            int count = GuardWeaver.weaveDirectory(Path.of(target), config);
            System.out.println("Wove " + count + " class(es) in " + target);
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String nextArg(String[] args, int i) {
        if (i + 1 >= args.length) {
            System.err.println("Error: " + args[i] + " requires a value");
            System.exit(1);
        }
        return args[i + 1];
    }

    private static void printUsage() {
        System.err.println("Usage: java -jar raoh-gsh.jar [options]");
        System.err.println();
        System.err.println("Options:");
        System.err.println("  --packages <patterns>   Comma-separated package glob patterns");
        System.err.println("  --classes <names>        Comma-separated fully qualified class names");
        System.err.println("  --exclude <patterns>     Comma-separated exclusion glob patterns");
        System.err.println("  --config <file>          Path to raoh-gsh.properties file");
        System.err.println("  --target <dir>           Target directory containing .class files (required)");
        System.err.println("  --help, -h               Show this help");
    }
}
