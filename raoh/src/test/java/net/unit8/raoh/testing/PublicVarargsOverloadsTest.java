package net.unit8.raoh.testing;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicVarargsOverloadsTest {

    /** As {@code containsAll} would be: a list and a string are both {@code T}s when T is Object. */
    @SuppressWarnings("unused")
    static class Generic<T> {
        @SafeVarargs
        public final void all(T... elements) {
        }

        public void all(List<? extends T> elements, String message) {
        }
    }

    /** As {@code oneOf} is: no String is a Collection, so no call fits both. */
    @SuppressWarnings("unused")
    static class Strings {
        public void any(String... values) {
        }

        public void any(Collection<? extends String> values, String message) {
        }
    }

    @Test
    void aGenericVarargsMethodCanBeCaptured() throws NoSuchMethodException {
        assertTrue(PublicVarargsOverloads.canCapture(
                Generic.class.getMethod("all", List.class, String.class),
                Generic.class.getMethod("all", Object[].class)));
    }

    @Test
    void aVarargsMethodOfAFinalTypeCannotBeCapturedByACollection() throws NoSuchMethodException {
        assertFalse(PublicVarargsOverloads.canCapture(
                Strings.class.getMethod("any", Collection.class, String.class),
                Strings.class.getMethod("any", String[].class)));
    }
}
