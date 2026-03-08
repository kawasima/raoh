package net.unit8.raoh;

import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * A {@link MessageResolver} backed by {@link ResourceBundle} for locale-aware
 * message resolution.
 *
 * <p>Looks up messages using the key {@code raoh.<code>} (e.g., {@code raoh.required},
 * {@code raoh.too_short}). If a key is not found in the bundle, falls back to
 * {@link MessageResolver#DEFAULT}.
 *
 * <p>Message templates use named placeholders that correspond to
 * {@link Issue#meta()} keys. For example:
 *
 * <pre>
 * raoh.too_short=must be at least {min} characters
 * raoh.out_of_range=must be between {min} and {max}
 * </pre>
 *
 * <p>A default English bundle is included at
 * {@code net/unit8/raoh/messages.properties} and can be loaded with:
 *
 * <pre>{@code
 * var resolver = new ResourceBundleMessageResolver("net.unit8.raoh.messages");
 * }</pre>
 *
 * <p>To add locale-specific messages, place additional bundles on the classpath
 * (e.g., {@code net/unit8/raoh/messages_ja.properties}).
 */
public class ResourceBundleMessageResolver implements MessageResolver {

    private final String baseName;

    /**
     * Creates a new resolver backed by the given resource bundle base name.
     *
     * @param baseName the base name of the resource bundle
     *                 (e.g., {@code "net.unit8.raoh.messages"})
     */
    public ResourceBundleMessageResolver(String baseName) {
        this.baseName = baseName;
    }

    @Override
    public String resolve(String code, Map<String, Object> meta) {
        return resolve(code, meta, Locale.getDefault());
    }

    @Override
    public String resolve(String code, Map<String, Object> meta, Locale locale) {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(baseName, locale);
            String template = bundle.getString(KEY_PREFIX + code);
            return MessageResolver.interpolate(template, meta);
        } catch (MissingResourceException ignored) {
            return MessageResolver.DEFAULT.resolve(code, meta);
        }
    }
}
