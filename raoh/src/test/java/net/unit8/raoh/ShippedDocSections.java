package net.unit8.raoh;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The section names a shipped guide declares, read the way Souther reads them.
 *
 * <p>A guide names a part of itself by writing {@code <!-- souther-section: name -->} on the line
 * directly above a heading, and Souther then serves that part as {@code raoh/<topic>/<name>}.
 * Souther refuses a malformed declaration when it reads the jar, and the refusal fails the whole of
 * {@code souther doc}, not just the raoh set. This checks raoh's own docs against the same rules
 * before a release carries them.
 *
 * <p>It checks only what raoh writes. Where a part ends and how a part is searched are Souther's to
 * decide, so they are not computed here. The name grammar is narrower than Souther's
 * {@code [A-Za-z0-9][A-Za-z0-9._-]*}: lower-case words joined by single hyphens. Raoh has no use
 * for the other spellings, and two of them could collide with each other under Souther's folds.
 */
final class ShippedDocSections {

    /** Anything that looks like a declaration, so a misspelt one is reported instead of skipped. */
    private static final Pattern ATTEMPTED = Pattern.compile("^\\s*<!--\\s*souther-section\\b.*$");

    /** A declaration in raoh's convention: the whole line, one space on each side of the name. */
    private static final Pattern DECLARED =
            Pattern.compile("^<!-- souther-section: ([a-z0-9]+(?:-[a-z0-9]+)*) -->$");

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(\\S.*?)\\s*$");

    /** A markdown fence, as Souther's {@code TakenAsItStands} recognizes one. */
    private static final Pattern FENCED = Pattern.compile("^(`{3,}|~{3,})(.*)$");

    /** What separates one word of a name from the next under Souther's {@code DocName.asWords}. */
    private static final Pattern NOT_A_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    private ShippedDocSections() {}

    /**
     * A heading in a guide, and the section name declared above it if there is one.
     *
     * @param name    the declared name, or {@code null} for a heading the guide leaves unnamed
     * @param level   the number of {@code #} the heading starts with
     * @param heading the heading text
     * @param line    the 1-based line number of the heading
     */
    record Heading(String name, int level, String heading, int line) {}

    /**
     * Reads every heading outside fenced blocks, with the name declared above it.
     *
     * @param topic the topic the text is served as, for error messages
     * @param text  the markdown source
     * @return the headings in document order
     * @throws IllegalArgumentException if a declaration is malformed, sits inside a fenced block,
     *                                  or is not directly above a heading
     */
    static List<Heading> headings(String topic, String text) {
        String[] lines = text.split("\n", -1);
        List<Heading> headings = new ArrayList<>();
        char fenced = 0;
        int fence = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String stripped = line.strip();
            if (fenced != 0) {
                if (ATTEMPTED.matcher(line).matches()) {
                    throw new IllegalArgumentException(at(topic, i)
                            + "a section declaration inside a fenced block is not published");
                }
                Matcher closing = FENCED.matcher(stripped);
                if (closing.matches() && closing.group(1).charAt(0) == fenced
                        && closing.group(1).length() >= fence && closing.group(2).isBlank()) {
                    fenced = 0;
                }
                continue;
            }
            Matcher opening = FENCED.matcher(stripped);
            if (opening.matches()) {
                fenced = opening.group(1).charAt(0);
                fence = opening.group(1).length();
                continue;
            }
            if (ATTEMPTED.matcher(line).matches()) {
                Matcher declared = DECLARED.matcher(line);
                if (!declared.matches()) {
                    throw new IllegalArgumentException(at(topic, i) + "`" + line
                            + "` is not `<!-- souther-section: lower-case-words -->`");
                }
                Matcher heading = i + 1 < lines.length ? HEADING.matcher(lines[i + 1]) : null;
                if (heading == null || !heading.matches()) {
                    throw new IllegalArgumentException(at(topic, i) + "section `"
                            + declared.group(1) + "` is not directly above a heading");
                }
                headings.add(new Heading(declared.group(1), heading.group(1).length(),
                        heading.group(2), i + 2));
                i++;
                continue;
            }
            Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                headings.add(new Heading(null, heading.group(1).length(), heading.group(2), i + 1));
            }
        }
        return headings;
    }

    /**
     * The names a guide declares, in document order.
     *
     * @param topic the topic the text is served as, for error messages
     * @param text  the markdown source
     * @return the declared section names
     * @throws IllegalArgumentException if {@link #headings} rejects the text
     */
    static List<String> names(String topic, String text) {
        return headings(topic, text).stream()
                .map(Heading::name)
                .filter(name -> name != null)
                .toList();
    }

    /**
     * Checks that no two names in a doc set are asked for as one, under either of Souther's folds:
     * letter case ({@code DocName.canonical}) and the words a name is made of
     * ({@code DocName.asWords}, where {@code a-b}, {@code a_b} and {@code a.b} are the same).
     *
     * @param names every name the set publishes, topics and sections alike, as
     *              {@code set/topic} or {@code set/topic/section}
     * @throws IllegalArgumentException naming the first two names that collide
     */
    static void requireDistinct(Collection<String> names) {
        Map<String, String> byName = new HashMap<>();
        Map<String, String> byWords = new HashMap<>();
        for (String name : names) {
            String canonical = name.toLowerCase(Locale.ROOT);
            String taken = byName.put(canonical, name);
            if (taken != null) {
                throw new IllegalArgumentException("`" + taken + "` and `" + name
                        + "` are asked for by the same name");
            }
            String words = NOT_A_WORD.matcher(canonical).replaceAll(" ").strip();
            String said = byWords.put(words, name);
            if (said != null) {
                throw new IllegalArgumentException("`" + said + "` and `" + name
                        + "` are the same words");
            }
        }
    }

    private static String at(String topic, int index) {
        return topic + ":" + (index + 1) + ": ";
    }
}
