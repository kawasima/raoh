package net.unit8.raoh.audit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The text format shared by the catalog and the approval files.
 *
 * <p>A line {@code [heading]} starts a section; every other non-blank line is an entry of the
 * section above it. A line starting with {@code #} is a comment. An entry may end with a note
 * after {@code "  -- "}, which the audit ignores.
 */
final class SectionedFile {

    /**
     * One entry.
     *
     * @param heading the heading of its section
     * @param text the entry without its note
     * @param line the 1-based line number, for messages
     */
    record Entry(String heading, String text, int line) {}

    private SectionedFile() {}

    static List<Entry> read(Path file) throws IOException {
        var entries = new ArrayList<Entry>();
        String heading = null;
        int number = 0;
        for (var raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            number++;
            var line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                heading = line.substring(1, line.length() - 1).strip();
                continue;
            }
            if (heading == null) {
                throw new IllegalArgumentException(file + ":" + number + ": entry before any [heading]");
            }
            int note = line.indexOf("  -- ");
            entries.add(new Entry(heading, (note < 0 ? line : line.substring(0, note)).strip(), number));
        }
        return entries;
    }
}
