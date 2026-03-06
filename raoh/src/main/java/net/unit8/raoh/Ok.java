package net.unit8.raoh;

/**
 * A successful decoding result containing the decoded value.
 *
 * @param value the decoded value
 * @param <T>   the type of the decoded value
 */
public record Ok<T>(T value) implements Result<T> {
}
