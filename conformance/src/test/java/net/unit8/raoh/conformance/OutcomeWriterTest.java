package net.unit8.raoh.conformance;

import net.unit8.raoh.Issue;
import net.unit8.raoh.Issues;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An encoder's output is a JSON value and an issue's metadata an observation of a value-model
 * type, so the same Java value is written differently in each. The suite has no encoder that gives
 * a number yet, so nothing else checks the difference.
 */
class OutcomeWriterTest {

    @Test
    void encoderOutputIsWrittenAsJson() {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("decimal", new BigDecimal("1.50"));
        output.put("double", -0.0d);
        output.put("float", 0.1f);
        output.put("list", List.of(1, 2L));

        JsonNode ok = OutcomeWriter.encoded(output).get("ok");

        assertTrue(ok.get("decimal").isNumber());
        assertEquals(new BigDecimal("1.50"), ok.get("decimal").decimalValue());
        assertTrue(ok.get("double").isNumber());
        assertEquals(new BigDecimal("0.1"), ok.get("float").decimalValue());
        assertEquals("[1,2]", ok.get("list").toString());
    }

    @Test
    void encoderOutputThatIsNotJsonIsRefused() {
        assertThrows(IllegalStateException.class, () -> OutcomeWriter.encoded(Double.NaN));
        assertThrows(IllegalStateException.class, () -> OutcomeWriter.encoded(new byte[] {1}));
    }

    @Test
    void metadataIsWrittenAsObservations() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("min", new BigDecimal("1.50"));
        meta.put("actual", -0.0f);
        Issue issue = Issue.of(Path.ROOT, "out_of_range", "out_of_range.minimum", "m", meta);

        JsonNode written = OutcomeWriter.decoded(SpecType.Scalar.DECIMAL, Result.err(new Issues(List.of(issue))))
                .get("issues").get(0).get("meta");

        assertEquals("\"1.50\"", written.get("min").toString());
        assertEquals("{\"float\":\"-0\"}", written.get("actual").toString());
    }
}
