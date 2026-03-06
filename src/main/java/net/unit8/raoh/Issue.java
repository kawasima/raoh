package net.unit8.raoh;

import java.util.Map;

public record Issue(Path path, String code, String message, Map<String, Object> meta, boolean customMessage) {

    public static Issue of(Path path, String code, String message, Map<String, Object> meta) {
        return new Issue(path, code, message, meta, false);
    }

    public static Issue of(Path path, String code, String message) {
        return new Issue(path, code, message, Map.of(), false);
    }

    public Issue withCustomMessage(String message) {
        return new Issue(path, code, message, meta, true);
    }

    public Issue resolve(MessageResolver resolver) {
        return customMessage ? this
                : new Issue(path, code, resolver.resolve(code, meta), meta, true);
    }

    public Issue rebase(Path prefix) {
        return new Issue(prefix.append(path), code, message, meta, customMessage);
    }
}
