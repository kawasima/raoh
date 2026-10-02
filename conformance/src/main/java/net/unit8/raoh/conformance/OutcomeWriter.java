package net.unit8.raoh.conformance;

import net.unit8.raoh.Err;
import net.unit8.raoh.Issue;
import net.unit8.raoh.Issues;
import net.unit8.raoh.MessageResolver;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes what raoh-java gave as the outcome a runner result records ({@code observation.md},
 * {@code issues.md}).
 *
 * <p>Issues are written once raoh-java has resolved their messages with its default English
 * catalogue, {@link MessageResolver#DEFAULT}, never with one the JVM's locale picks.
 */
final class OutcomeWriter {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private OutcomeWriter() {
    }

    /**
     * The outcome of a decoder.
     *
     * @param type   the type of the decoder's result
     * @param result what the decoder gave
     * @return {@code {"ok": ...}} or {@code {"issues": [...]}}
     */
    static ObjectNode decoded(SpecType type, Result<?> result) {
        ObjectNode outcome = NODES.objectNode();
        switch (result) {
            case Ok<?> ok -> outcome.set("ok", ValueCodec.observe(type, ok.value()));
            case Err<?> err -> outcome.set("issues", issues(err.issues()));
        }
        return outcome;
    }

    /**
     * The outcome of an encoder, whose output is compared as JSON.
     *
     * @param output what the encoder gave
     * @return {@code {"ok": ...}}
     */
    static ObjectNode encoded(@Nullable Object output) {
        ObjectNode outcome = NODES.objectNode();
        outcome.set("ok", value(output));
        return outcome;
    }

    /**
     * The outcome of a case raoh-java gave none for.
     *
     * @param e what it threw instead
     * @return {@code {"error": ...}}
     */
    static ObjectNode error(Throwable e) {
        ObjectNode outcome = NODES.objectNode();
        outcome.put("error", e.toString());
        return outcome;
    }

    private static ArrayNode issues(Issues issues) {
        ArrayNode node = NODES.arrayNode();
        for (Issue issue : issues.resolve(MessageResolver.DEFAULT).asList()) {
            ObjectNode i = NODES.objectNode();
            i.put("path", issue.path().toJsonPointer());
            i.put("code", issue.code());
            i.put("message_key", issue.messageKey());
            i.put("message", issue.message());
            i.set("meta", value(issue.meta()));
            node.add(i);
        }
        return node;
    }

    /**
     * Writes a metadata value, or an encoder's output, by its Java class.
     *
     * <p>Only the classes raoh-java gives such values as are known; any other is refused rather
     * than written in some form of its own, such as its {@code toString}. The lists and maps are
     * the ones the value model has, and also the candidates of {@code one_of_failed}, which
     * raoh-java gives as a list of maps whose issues have no message key, as the specification
     * writes them.
     *
     * @param value the value
     * @return its JSON
     * @throws IllegalStateException if the value is of a class that is not known
     */
    private static JsonNode value(@Nullable Object value) {
        return switch (value) {
            case null -> NODES.nullNode();
            case Boolean b -> NODES.booleanNode(b);
            case Integer i -> NODES.numberNode(i);
            case Long l -> NODES.numberNode(l);
            case Float f -> ValueCodec.float32(f);
            case Double d -> ValueCodec.float64(d);
            case BigDecimal d -> NODES.stringNode(d.toString());
            case String s -> NODES.stringNode(s);
            case Enum<?> e -> NODES.stringNode(e.name());
            case UUID u -> NODES.stringNode(u.toString());
            case URI u -> NODES.stringNode(u.toString());
            case LocalDate d -> NODES.stringNode(d.toString());
            case LocalTime t -> NODES.stringNode(t.toString());
            case LocalDateTime d -> NODES.stringNode(d.toString());
            case OffsetDateTime d -> NODES.stringNode(d.toString());
            case Instant i -> NODES.stringNode(i.toString());
            case List<?> list -> {
                ArrayNode node = NODES.arrayNode();
                for (Object e : list) {
                    node.add(value(e));
                }
                yield node;
            }
            case Map<?, ?> map -> {
                ObjectNode node = NODES.objectNode();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (!(e.getKey() instanceof String key)) {
                        throw new IllegalStateException("a map key that is not a string: " + e.getKey());
                    }
                    node.set(key, value(e.getValue()));
                }
                yield node;
            }
            default -> throw new IllegalStateException(
                    "no observation is known for a " + value.getClass().getName());
        };
    }
}
