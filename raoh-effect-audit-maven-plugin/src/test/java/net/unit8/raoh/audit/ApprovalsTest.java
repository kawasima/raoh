package net.unit8.raoh.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalsTest {

    @TempDir
    Path dir;

    private static final String USE = "fixture.A#f -> java.util.List#iterator():java.util.Iterator";

    @Test
    void aUseMadeForTwoReasonsIsListedUnderEach() throws IOException {
        var file = dir.resolve("approvals.txt");
        Files.writeString(file, """
                [inspects the decoded value]
                %s

                [reads what the caller configured]
                %s
                """.formatted(USE, USE));

        var approvals = Approvals.read(file);

        var use = new Approvals.Use("fixture.A#f", Member.parse("java.util.List#iterator():java.util.Iterator"));
        assertTrue(approvals.contains(use));
        assertEquals(List.of(use), approvals.uses());
    }

    @Test
    void aUseListedTwiceUnderOneReasonIsRefused() throws IOException {
        var file = dir.resolve("approvals.txt");
        Files.writeString(file, """
                [inspects the decoded value]
                %s
                %s
                """.formatted(USE, USE));

        var e = assertThrows(IllegalArgumentException.class, () -> Approvals.read(file));
        assertTrue(e.getMessage().contains("listed twice under [inspects the decoded value]"), e.getMessage());
    }
}
