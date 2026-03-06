package net.unit8.raoh;

/**
 * A tri-state type representing the presence of a field in the input.
 *
 * <ul>
 *   <li>{@link Absent} — the field is not present in the input</li>
 *   <li>{@link PresentNull} — the field is present but its value is {@code null}</li>
 *   <li>{@link Present} — the field is present with a non-null value</li>
 * </ul>
 *
 * @param <T> the type of the value when present
 */
public sealed interface Presence<T> {
    /** The field is absent from the input. @param <T> the value type */
    record Absent<T>() implements Presence<T> {}
    /** The field is present with an explicit {@code null} value. @param <T> the value type */
    record PresentNull<T>() implements Presence<T> {}
    /**
     * The field is present with a non-null value.
     * @param value the decoded value
     * @param <T> the value type
     */
    record Present<T>(T value) implements Presence<T> {}
}
