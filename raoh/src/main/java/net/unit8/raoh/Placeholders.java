package net.unit8.raoh;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads and fills {@code {name}} placeholders in message templates.
 *
 * <p>Both operations scan the template once. Scanning the template rather than the
 * metadata is what lets a caller ask whether a template can be filled at all, and it
 * keeps braces that arrive inside a metadata value from being mistaken for placeholders
 * of their own.
 *
 * <p>A placeholder name is a letter or underscore followed by letters, digits,
 * underscores, dots or hyphens — the shape a metadata key has. Anything else between
 * braces is prose the template author wrote, such as the JSON in
 * {@code expected an object like {"id": 1}}, and is left alone rather than reported as a
 * placeholder no metadata can fill.
 */
final class Placeholders {

    private Placeholders() {}

    private static final Pattern PATTERN = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_.\\-]*)}");

    /**
     * Returns the placeholder names the template asks for, in order of appearance.
     *
     * @param template the message template
     * @return the set of placeholder names, empty if the template has none
     */
    static Set<String> namesIn(String template) {
        var names = new LinkedHashSet<String>();
        var matcher = PATTERN.matcher(template);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /**
     * Replaces each placeholder with the matching metadata value, leaving placeholders
     * with no matching key as written.
     *
     * @param template the message template
     * @param meta     the metadata supplying placeholder values
     * @return the filled template
     */
    static String fill(String template, Map<String, Object> meta) {
        var matcher = PATTERN.matcher(template);
        var out = new StringBuilder();
        while (matcher.find()) {
            var replacement = meta.containsKey(matcher.group(1))
                    ? String.valueOf(meta.get(matcher.group(1)))
                    : matcher.group();
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
