package net.unit8.raoh;

import org.jspecify.annotations.Nullable;

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
    public String resolve(Issue issue) {
        return resolve(issue, Locale.getDefault());
    }

    /**
     * {@link ResourceBundle.Control} that disables default-locale fallback.
     * Without this, a request for {@code Locale.ENGLISH} on a JVM whose default
     * locale is, e.g., {@code ja_JP} could unexpectedly resolve to a {@code _ja}
     * bundle when no {@code _en} bundle exists. The lookup chain becomes:
     * requested locale → base bundle → {@link MessageResolver#DEFAULT}.
     */
    private static final ResourceBundle.Control NO_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    @Override
    public String resolve(String code, Map<String, Object> meta, Locale locale) {
        String filled = firstFilled(locale, meta, code);
        return filled != null ? filled : MessageResolver.DEFAULT.resolve(code, meta);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Tries {@code raoh.<messageKey>} first and {@code raoh.<code>} second, so a bundle
     * that only defines code-level templates keeps working while a bundle that
     * distinguishes constraints can.
     *
     * <p>A template is used only when the issue's metadata supplies every placeholder it
     * asks for; one that does not is skipped rather than half-filled, and the next key is
     * tried. A bundle can therefore define a specific template that suits some constraints
     * under a code and let the rest fall through to the code's own template. When neither
     * key yields a usable template, the message stored at decode time is returned
     * unchanged — it already describes the constraint, whereas a partially filled template
     * would name a bound that does not exist.
     *
     * <p>The check runs before substitution, since afterwards a brace that came from a
     * metadata value is indistinguishable from one the template wrote.
     */
    @Override
    public String resolve(Issue issue, Locale locale) {
        String filled = firstFilled(locale, issue.meta(), issue.messageKey(), issue.code());
        return filled != null ? filled : issue.message();
    }

    /**
     * Returns the first of the given keys whose template the metadata can fill completely,
     * or {@code null} if the bundle is missing, defines none of them, or defines only
     * templates asking for placeholders {@code meta} does not supply.
     */
    private @Nullable String firstFilled(Locale locale, Map<String, Object> meta, String... keys) {
        ResourceBundle bundle;
        try {
            bundle = ResourceBundle.getBundle(baseName, locale, NO_FALLBACK);
        } catch (MissingResourceException ignored) {
            return null;
        }
        for (String key : keys) {
            if (!bundle.containsKey(KEY_PREFIX + key)) {
                continue;
            }
            String filled = MessageResolver.interpolateFully(bundle.getString(KEY_PREFIX + key), meta);
            if (filled != null) {
                return filled;
            }
        }
        return null;
    }
}
