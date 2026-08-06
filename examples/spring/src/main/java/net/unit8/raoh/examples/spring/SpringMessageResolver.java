package net.unit8.raoh.examples.spring;

import net.unit8.raoh.Issue;
import net.unit8.raoh.MessageResolver;
import org.springframework.context.MessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A {@link MessageResolver} adapter for Spring's {@link MessageSource}.
 *
 * <p>Looks up messages using the key {@code raoh.<messageKey>}, then
 * {@code raoh.<code>}, and replaces named placeholders (e.g., {@code {min}},
 * {@code {max}}) with values from the issue metadata.
 *
 * <p>A template is applied only when the metadata supplies every placeholder it
 * asks for. Several constraints share one error code — {@code min()} and
 * {@code range()} are both {@code out_of_range} — so a template written for one
 * of them can name a bound the other never had. When that happens, or when no key
 * matches, the message stored at decode time is kept: it already describes the
 * constraint correctly.
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
        String template = template(code, locale);
        String filled = template == null ? null : MessageResolver.interpolateFully(template, meta);
        return filled != null ? filled : MessageResolver.DEFAULT.resolve(code, meta);
    }

    /**
     * Resolves using the JVM default locale.
     *
     * <p>In a Spring web application, prefer {@link #resolve(Issue, Locale)} and pass
     * the request locale.
     *
     * @param issue the issue to describe
     * @return the resolved message in the JVM default locale
     */
    @Override
    public String resolve(Issue issue) {
        return resolve(issue, Locale.getDefault());
    }

    /**
     * Resolves an issue, preferring the template written for the specific constraint
     * that failed and keeping the stored message when no template fits.
     *
     * @param issue  the issue to describe
     * @param locale the target locale for the message
     * @return the resolved message
     */
    @Override
    public String resolve(Issue issue, Locale locale) {
        for (String key : List.of(issue.messageKey(), issue.code())) {
            String template = template(key, locale);
            if (template == null) {
                continue;
            }
            String filled = MessageResolver.interpolateFully(template, issue.meta());
            if (filled != null) {
                return filled;
            }
        }
        return issue.message();
    }

    /**
     * Looks up a template by key, returning {@code null} when the message source has none.
     *
     * @param key    the key, without the {@link MessageResolver#KEY_PREFIX} prefix
     * @param locale the target locale
     * @return the template, or {@code null} if the message source has no entry for the key
     */
    private String template(String key, Locale locale) {
        return messageSource.getMessage(KEY_PREFIX + key, null, null, locale);
    }
}
