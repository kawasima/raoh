package net.unit8.raoh;

/**
 * A successful decoding result containing the decoded value.
 *
 * @param value the decoded value
 * @param <T>   the type of the decoded value
 */
public record Ok<T>(T value) implements Result<T> {
    /**
     * Returns a concise string representation of this successful result.
     *
     * @return a string of the form {@code Ok[value]}
     */
    @Override
    public String toString() {
        return "Ok[" + value + "]";
    }
}
