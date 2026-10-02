package net.unit8.raoh.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalsTest {

    @TempDir
    Path dir;

    private static final String USE = "fixture.A#f -> java.util.List#iterator():java.util.Iterator";

    /** A use has one reason, which covers every call the caller makes to the member. */
    @Test
    void aUseListedUnderTwoReasonsIsRefused() throws IOException {
        var file = dir.resolve("approvals.txt");
        Files.writeString(file, """
                [inspects the decoded value]
                %s

                [reads what the caller configured]
                %s
                """.formatted(USE, USE));

        var e = assertThrows(IllegalArgumentException.class, () -> Approvals.read(file));
        assertTrue(e.getMessage().contains("is listed twice"), e.getMessage());
    }
}
