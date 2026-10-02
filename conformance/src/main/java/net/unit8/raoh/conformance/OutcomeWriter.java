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
import java.math.BigInteger;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

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
     * The outcome of an encoder. Its output is a JSON value, not an observation of a value of the
     * value model ({@code observation.md}: "An encoder's outcome is always {@code {"ok": <JSON
     * value>}}"), so it is written by {@link #json}, never by {@link #meta}: a {@link BigDecimal}
     * an encoder gives is a JSON number, not a decimal's string, and a float is a number, never a
     * tag.
     *
     * @param output what the encoder gave
     * @return {@code {"ok": ...}}
     */
    static ObjectNode encoded(@Nullable Object output) {
        ObjectNode outcome = NODES.objectNode();
        outcome.set("ok", json(output));
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
            i.set("meta", meta(issue.meta()));
            node.add(i);
        }
        return node;
    }

    /**
     * Writes an issue's metadata value as the observation of its value-model type.
     *
     * <p>Unlike a decoder's result, a metadata value comes with no {@link SpecType}: raoh-java
     * keeps it as an {@code Object}. Its Java class stands for the type, so each class maps to the
     * observation {@link ValueCodec} writes for the type it represents ({@link BigDecimal} is a
     * {@code decimal}, written as a string; a {@link Float} a {@code float32}, with its tags). Only
     * the classes raoh-java gives metadata as are known; any other is refused rather than written
     * in some form of its own, such as its {@code toString}. The lists and maps are the ones the
     * value model has, and also the candidates of {@code one_of_failed}, which raoh-java gives as a
     * list of maps whose issues have no message key, as the specification writes them.
     *
     * @param value the value
     * @return its observation
     * @throws IllegalStateException if the value is of a class that is not known
     */
    private static JsonNode meta(@Nullable Object value) {
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
            case List<?> list -> array(list, OutcomeWriter::meta);
            case Map<?, ?> map -> object(map, OutcomeWriter::meta);
            default -> throw new IllegalStateException(
                    "no observation is known for a " + value.getClass().getName());
        };
    }

    /**
     * Writes an encoder's output as the JSON value it is.
     *
     * <p>raoh-java's encoders give JSON as Java values: {@code null}, a {@link Boolean}, a
     * {@link String}, a number, a {@link List} and a {@link Map} with string keys. A number is a
     * JSON number whatever its class. A float that is not finite has no JSON number, and any other
     * class (a {@code byte[]}, a temporal value an encoder did not turn into text) is not JSON;
     * both are refused rather than written in some form of their own.
     *
     * @param value the value
     * @return its JSON
     * @throws IllegalStateException if the value is not a JSON value
     */
    private static JsonNode json(@Nullable Object value) {
        return switch (value) {
            case null -> NODES.nullNode();
            case Boolean b -> NODES.booleanNode(b);
            case String s -> NODES.stringNode(s);
            case Integer i -> NODES.numberNode(i);
            case Long l -> NODES.numberNode(l);
            case BigInteger i -> NODES.numberNode(i);
            case BigDecimal d -> NODES.numberNode(d);
            case Float f when Float.isFinite(f) -> NODES.numberNode(new BigDecimal(Float.toString(f)));
            case Double d when Double.isFinite(d) -> NODES.numberNode(new BigDecimal(Double.toString(d)));
            case List<?> list -> array(list, OutcomeWriter::json);
            case Map<?, ?> map -> object(map, OutcomeWriter::json);
            default -> throw new IllegalStateException("not a JSON value: " + value
                    + " (" + value.getClass().getName() + ")");
        };
    }

    private static ArrayNode array(List<?> list, Function<@Nullable Object, JsonNode> element) {
        ArrayNode node = NODES.arrayNode();
        for (Object e : list) {
            node.add(element.apply(e));
        }
        return node;
    }

    private static ObjectNode object(Map<?, ?> map, Function<@Nullable Object, JsonNode> member) {
        ObjectNode node = NODES.objectNode();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!(e.getKey() instanceof String key)) {
                throw new IllegalStateException("a map key that is not a string: " + e.getKey());
            }
            node.set(key, member.apply(e.getValue()));
        }
        return node;
    }
}
