package net.unit8.raoh;

public record Err<T>(Issues issues) implements Result<T> {
    @SuppressWarnings("unchecked")
    public <U> Err<U> coerce() {
        return (Err<U>) this;
    }
}
