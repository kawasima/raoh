package net.unit8.raoh;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
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
 * (e.g., {@code net/unit8/raoh/messages_ja.properties}). A locale bundle may translate
 * only some keys; keys it leaves out are looked up in the less specific bundles.
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

    /**
     * Resolves a message in the JVM default locale.
     *
     * <p>This is the one place in Raoh that reads {@link Locale#getDefault()} on purpose:
     * choosing a display locale is the job of a message resolver, while decoders never
     * depend on it. Pass the locale explicitly with
     * {@link #resolve(String, Map, Locale)} when it should not follow the JVM setting.
     *
     * @param code the error code
     * @param meta the issue metadata used to fill the template placeholders
     * @return the resolved message for {@link Locale#getDefault()}
     */
    @Override
    public String resolve(String code, Map<String, Object> meta) {
        return resolve(code, meta, Locale.getDefault());
    }

    /**
     * Resolves the message for an issue in the JVM default locale.
     *
     * <p>Like {@link #resolve(String, Map)}, this reads {@link Locale#getDefault()} on
     * purpose. Use {@link #resolve(Issue, Locale)} to choose the locale explicitly.
     *
     * @param issue the issue to resolve
     * @return the resolved message for {@link Locale#getDefault()}
     */
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
     * <p>The locale takes precedence over the key. Both keys are tried in the bundle for
     * the requested locale before either is tried in a less specific one, so a locale
     * bundle that translates only {@code raoh.invalid_format} is used for an
     * {@code invalid_format.email} issue even when the base bundle defines
     * {@code raoh.invalid_format.email}.
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
     * Returns the first template the metadata can fill completely, or {@code null} if no
     * bundle layer defines a usable template for any of the keys.
     *
     * <p>Layers are searched from the most specific locale to the base bundle, and every
     * key is tried within a layer before moving to the next one. A bundle returned by
     * {@link ResourceBundle#getBundle} answers for its parents too, so searching key by
     * key over it would let a specific key in the base bundle win over a code-level key
     * a locale bundle translated; a partial translation would then come out in the base
     * bundle's language. The same flattening would also let a locale template that the
     * metadata cannot fill hide a usable template for the same key further down.
     */
    private @Nullable String firstFilled(Locale locale, Map<String, Object> meta, String... keys) {
        for (PropertyResourceBundle layer : layers(locale)) {
            for (String key : keys) {
                Object template = layer.handleGetObject(KEY_PREFIX + key);
                if (!(template instanceof String s)) {
                    continue;
                }
                String filled = MessageResolver.interpolateFully(s, meta);
                if (filled != null) {
                    return filled;
                }
            }
        }
        return null;
    }

    /**
     * Returns the bundles that exist for the candidate locales of {@code locale}, most
     * specific first. {@link PropertyResourceBundle#handleGetObject} on each one sees only
     * the keys its own file defines, not its parents'.
     */
    private List<PropertyResourceBundle> layers(Locale locale) {
        List<PropertyResourceBundle> layers = new ArrayList<>();
        for (Locale candidate : NO_FALLBACK.getCandidateLocales(baseName, locale)) {
            ResourceBundle bundle;
            try {
                bundle = ResourceBundle.getBundle(baseName, candidate, NO_FALLBACK);
            } catch (MissingResourceException ignored) {
                continue;
            }
            // getBundle falls back to a less specific bundle when the candidate's own file
            // is missing; that bundle is picked up under its own candidate instead.
            if (bundle.getLocale().equals(candidate) && bundle instanceof PropertyResourceBundle layer) {
                layers.add(layer);
            }
        }
        return layers;
    }
}
