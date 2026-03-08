package net.unit8.raoh.examples.spring;

import net.unit8.raoh.MessageResolver;
import org.springframework.context.MessageSource;

import java.util.Locale;
import java.util.Map;

/**
 * A {@link MessageResolver} adapter for Spring's {@link MessageSource}.
 *
 * <p>Looks up messages using the key {@code raoh.<code>} and replaces
 * named placeholders (e.g., {@code {min}}, {@code {max}}) with values
 * from the issue metadata. Falls back to {@link MessageResolver#DEFAULT}
 * when a key is not found.
 *
 * <p>This is a reference implementation. Copy and adapt it for your own
 * Spring project.
 */
public class SpringMessageResolver implements MessageResolver {

    private final MessageSource messageSource;

    /**
     * Creates a new resolver backed by the given message source.
     *
     * @param messageSource the Spring message source
     */
    public SpringMessageResolver(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Resolves using the JVM default locale.
     *
     * <p>In a Spring web application, always prefer
     * {@link #resolve(String, Map, Locale)} and pass the request locale
     * (injected from {@code Accept-Language} via Spring MVC's {@code Locale} argument).
     * This overload is provided for backward compatibility with non-locale-aware call sites.
     *
     * @param code the error code
     * @param meta additional metadata associated with the issue
     * @return the resolved message in the JVM default locale
     */
    @Override
    public String resolve(String code, Map<String, Object> meta) {
        return resolve(code, meta, Locale.getDefault());
    }

    @Override
    public String resolve(String code, Map<String, Object> meta, Locale locale) {
        String defaultMessage = MessageResolver.DEFAULT.resolve(code, meta);
        String template = messageSource.getMessage(
                KEY_PREFIX + code, null, defaultMessage, locale);
        return MessageResolver.interpolate(template, meta);
    }
}
