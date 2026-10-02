package net.unit8.raoh.conformance;

import net.unit8.raoh.json.JsonDecoders;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Whether a case runs depends on the features its form needs, never on how far building the
 * decoder got: a form that needs a feature the runner does not bind is not run even when raoh-java
 * would refuse an earlier part of it. A result for such a case makes the verifier reject the run.
 *
 * <p>The forms name features no binding has, an operation and a message facet the decoder language
 * does not define, so that what they check does not change as the runner binds more.
 */
class BindingsTest {

    private final Bindings bindings = new Bindings();

    @Test
    void anUnboundOperationIsFoundBeforeAnEarlierPartIsBuilt() {
        // minSize's argument is not an int32, which building would refuse.
        var form = JsonDecoders.readTree("""
                ["list", ["int"], ["minSize", "not a size"], ["noSuchOperation"]]
                """);

        var unbound = assertThrows(Bindings.UnboundFeature.class, () -> bindings.decoder(form));

        assertEquals("operation.list.noSuchOperation", unbound.feature());
    }

    @Test
    void anUnboundMessageFacetIsFoundBeforeAnEarlierPartIsBuilt() {
        // trim declares no issue, so it takes no message and has no facet.
        var form = JsonDecoders.readTree("""
                ["string", ["minLength", "not a length"], ["trim", "a message"]]
                """);

        var unbound = assertThrows(Bindings.UnboundFeature.class, () -> bindings.decoder(form));

        assertEquals("operation.string.trim.message", unbound.feature());
    }

    @Test
    void aFormWhoseFeaturesAreBoundIsBuilt() {
        var form = JsonDecoders.readTree("""
                ["list", ["int"], ["minSize", "not a size"]]
                """);

        assertThrows(IllegalArgumentException.class, () -> bindings.decoder(form));
    }
}
