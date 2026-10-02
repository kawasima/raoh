package net.unit8.raoh.conformance;

import net.unit8.raoh.MessageResolver;
import net.unit8.raoh.Result;
import net.unit8.raoh.json.JsonDecoders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The Raoh Specification runner for raoh-java ({@code spec/conformance.md}).
 *
 * <p>It reads the cases of the {@code core} and {@code encode} profiles, runs every case whose
 * features it binds, and writes a runner result. It does not compare an outcome with what a case
 * expects, skip a case by its ID, or classify anything: that is the verifier's.
 *
 * <p>Usage: {@code RunnerMain --spec <dir> --revision <commit> --manifest-digest <digest>
 * --implementation-revision <commit> --out <file>}.
 */
public final class RunnerMain {

    private static final List<String> PROFILES = List.of("core", "encode");

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private RunnerMain() {
    }

    /**
     * Runs the suite and writes the runner result.
     *
     * @param args the options above
     * @throws IOException if the suite cannot be read or the result cannot be written
     */
    public static void main(String[] args) throws IOException {
        Map<String, String> options = options(args);
        Path spec = Path.of(options.get("--spec"));

        Bindings bindings = new Bindings();
        ObjectNode results = NODES.objectNode();
        Set<String> unbound = new TreeSet<>();
        for (String profile : PROFILES) {
            for (Path file : caseFiles(spec.resolve("suite").resolve(profile))) {
                for (JsonNode c : read(file)) {
                    try {
                        ObjectNode entry = NODES.objectNode();
                        entry.set("observed", run(bindings, c));
                        results.set(c.get("id").stringValue(), entry);
                    } catch (Bindings.UnboundFeature e) {
                        unbound.add(e.feature());
                    }
                }
            }
        }

        ObjectNode result = NODES.objectNode();
        result.put("format", "raoh-runner-result/v1");
        ObjectNode specification = result.putObject("specification");
        specification.put("version", read(spec.resolve("specification.json")).get("version").stringValue());
        specification.put("revision", options.get("--revision"));
        specification.put("manifest_digest", options.get("--manifest-digest"));
        ObjectNode implementation = result.putObject("implementation");
        implementation.put("name", "raoh-java");
        implementation.put("version", implementationVersion());
        implementation.put("revision", options.get("--implementation-revision"));
        ObjectNode environment = result.putObject("environment");
        environment.put("language", "java");
        environment.put("language_version", Runtime.version().toString());
        environment.put("os", System.getProperty("os.name"));
        environment.put("arch", System.getProperty("os.arch"));
        ArrayNode bound = result.putArray("bound_features");
        bindings.boundFeatures().forEach(bound::add);
        result.set("results", results);
        ObjectNode catalogs = result.putObject("catalogs");
        catalogs.set("en", catalog("messages.properties"));
        catalogs.set("ja", catalog("messages_ja.properties"));

        ObjectMapper mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        Path out = Path.of(options.get("--out"));
        Files.writeString(out, mapper.writeValueAsString(result) + "\n", StandardCharsets.UTF_8);
        System.err.printf("ran %d cases; wrote %s%n", results.size(), out);
        if (!unbound.isEmpty()) {
            System.err.println("cases not run, for features the runner does not bind: " + unbound);
        }
    }

    /**
     * Runs one case.
     *
     * @return its outcome
     * @throws Bindings.UnboundFeature if the case needs a feature the runner does not bind
     */
    private static ObjectNode run(Bindings bindings, JsonNode c) {
        try {
            if (c.get("decoder") != null) {
                Bindings.BoundDecoder decoder = bindings.decoder(c.get("decoder"));
                Result<?> result = decoder.decoder().decode(c.get("input"));
                return OutcomeWriter.decoded(decoder.type(), result);
            }
            Bindings.BoundEncoder encoder = bindings.encoder(c.get("encoder"));
            Object value = ValueCodec.materialize(encoder.input(), c.get("value"));
            return OutcomeWriter.encoded(encoder.encoder().encode(value));
        } catch (Bindings.UnboundFeature e) {
            throw e;
        } catch (RuntimeException | StackOverflowError e) {
            return OutcomeWriter.error(e);
        }
    }

    private static List<Path> caseFiles(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().toList();
        }
    }

    /**
     * Reads a JSON file with the reader the decoders are specified on, so that a case's input
     * reaches its decoder with its lexemes as the file writes them.
     */
    private static JsonNode read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonDecoders.readTree(reader);
        }
    }

    /**
     * Every message key a catalogue raoh-java ships has, with its template. The bundle stores the
     * template of a message key under {@link MessageResolver#KEY_PREFIX} followed by the key, which
     * is where {@code ResourceBundleMessageResolver} looks it up; a property without the prefix is
     * no message key, and is refused rather than left out.
     */
    private static ObjectNode catalog(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = net.unit8.raoh.Issue.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("raoh-java ships no " + resource);
            }
            properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        ObjectNode catalog = NODES.objectNode();
        for (Map.Entry<Object, Object> e : new TreeMap<>(properties).entrySet()) {
            String key = (String) e.getKey();
            if (!key.startsWith(MessageResolver.KEY_PREFIX)) {
                throw new IOException(resource + " has " + key + ", which is not a message key");
            }
            catalog.put(key.substring(MessageResolver.KEY_PREFIX.length()), (String) e.getValue());
        }
        return catalog;
    }

    private static String implementationVersion() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = RunnerMain.class.getResourceAsStream("runner.properties")) {
            if (in == null) {
                throw new IOException("runner.properties is missing");
            }
            properties.load(in);
        }
        return properties.getProperty("implementation.version");
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            options.put(args[i], args[i + 1]);
        }
        for (String required : List.of("--spec", "--revision", "--manifest-digest", "--implementation-revision", "--out")) {
            if (!options.containsKey(required)) {
                throw new IllegalArgumentException("missing " + required);
            }
        }
        return options;
    }
}
