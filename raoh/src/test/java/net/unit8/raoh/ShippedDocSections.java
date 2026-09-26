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
 * Souther refuses some guides when it reads the jar, and the refusal fails the whole of
 * {@code souther doc}, not just the raoh set. This checks raoh's own docs before a release carries
 * them.
 *
 * <p>Souther reads a guide in stages, and this follows the same stages so that each check applies
 * to the text Souther applies it to:
 *
 * <ol>
 *   <li>Affordances. {@code Affordance.materialize} rewrites every {@code {{name}}} and
 *       {@code {{name:argument}}} in the whole text, fenced samples included, and refuses a name it
 *       does not declare. Affordances are how Souther's own docs spell a command per caller; raoh
 *       has no use for them, so any is refused here.</li>
 *   <li>Opaque lines. {@code TakenAsItStands.markdown} marks the lines of fenced blocks. Nothing on
 *       them is a heading or a declaration: a sample showing {@code ## Example} or a
 *       {@code souther-section} line is just sample text, and is neither read nor checked.</li>
 *   <li>Declarations and headings, on the remaining lines. {@code LibraryDocs} refuses a
 *       declaration that is not directly above a heading.</li>
 *   <li>Names. {@code LibraryDocs.Names} refuses two topics or sections that fold together
 *       ({@link #requireDistinct}).</li>
 * </ol>
 *
 * <p>Where a part ends and how a part is searched are Souther's to decide, so they are not computed
 * here.
 *
 * <p>On the lines Souther reads as structure, raoh's convention is narrower than what Souther
 * accepts. A declaration is exactly {@code <!-- souther-section: name -->} with lower-case words
 * joined by single hyphens, and a line that starts like a declaration but is not one is refused
 * rather than skipped: Souther would silently not publish it.
 */
final class ShippedDocSections {

    /** {@code Affordance.WRITTEN}: what Souther rewrites before reading a guide. */
    private static final Pattern AFFORDANCE = Pattern.compile("\\{\\{([a-z-]+)(?::([^}]*))?}}");

    /** A markdown fence, as {@code TakenAsItStands} recognizes one on a stripped line. */
    private static final Pattern FENCED = Pattern.compile("^(`{3,}|~{3,})(.*)$");

    /** {@code LibraryDocs.HEADING}. */
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(\\S.*?)\\s*$");

    /** Anything that starts like a declaration, so a misspelt one is reported instead of skipped. */
    private static final Pattern ATTEMPTED = Pattern.compile("^\\s*<!--\\s*souther-section\\b.*$");

    /** A declaration in raoh's convention: the whole line, one space on each side of the name. */
    private static final Pattern DECLARED =
            Pattern.compile("^<!-- souther-section: ([a-z0-9]+(?:-[a-z0-9]+)*) -->$");

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
     * @throws IllegalArgumentException if the text holds an affordance, or a line outside a fenced
     *                                  block starts a declaration that is malformed or not
     *                                  directly above a heading
     */
    static List<Heading> headings(String topic, String text) {
        requireNoAffordance(topic, text);
        String[] lines = text.split("\n", -1);
        boolean[] opaque = opaque(lines);
        List<Heading> headings = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            if (opaque[i]) {
                continue;
            }
            if (ATTEMPTED.matcher(lines[i]).matches()) {
                Matcher declared = DECLARED.matcher(lines[i]);
                if (!declared.matches()) {
                    throw new IllegalArgumentException(at(topic, i) + "`" + lines[i]
                            + "` is not `<!-- souther-section: lower-case-words -->`");
                }
                Matcher heading = i + 1 < lines.length && !opaque[i + 1]
                        ? HEADING.matcher(lines[i + 1]) : null;
                if (heading == null || !heading.matches()) {
                    throw new IllegalArgumentException(at(topic, i) + "section `"
                            + declared.group(1) + "` is not directly above a heading");
                }
                headings.add(new Heading(declared.group(1), heading.group(1).length(),
                        heading.group(2), i + 2));
                i++;
                continue;
            }
            Matcher heading = HEADING.matcher(lines[i]);
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

    private static void requireNoAffordance(String topic, String text) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher written = AFFORDANCE.matcher(lines[i]);
            if (written.find()) {
                throw new IllegalArgumentException(at(topic, i) + "`" + written.group()
                        + "` is a Souther affordance, which Souther rewrites or refuses even"
                        + " inside a fenced block");
            }
        }
    }

    /** {@code TakenAsItStands.markdown}: which lines are inside a fenced block, fences included. */
    private static boolean[] opaque(String[] lines) {
        boolean[] opaque = new boolean[lines.length];
        char fenced = 0;
        int fence = 0;
        for (int i = 0; i < lines.length; i++) {
            Matcher line = FENCED.matcher(lines[i].strip());
            if (fenced != 0) {
                opaque[i] = true;
                if (line.matches() && line.group(1).charAt(0) == fenced
                        && line.group(1).length() >= fence && line.group(2).isBlank()) {
                    fenced = 0;
                }
            } else if (line.matches()) {
                fenced = line.group(1).charAt(0);
                fence = line.group(1).length();
                opaque[i] = true;
            }
        }
        return opaque;
    }

    private static String at(String topic, int index) {
        return topic + ":" + (index + 1) + ": ";
    }
}
