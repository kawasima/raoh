package net.unit8.raoh;

import net.unit8.raoh.JdkCallGraph.MethodRef;
import net.unit8.raoh.JdkCallGraph.Scan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static net.unit8.raoh.DefaultLocaleApiAuditTest.requiredProperty;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Keeps the forbidden-API list of default time zone readers complete against the JDK itself.
 *
 * <p>A decoder's result must not depend on the JVM default time zone (#141): the same
 * {@code java.sql.Date} decoded to a different {@code LocalDate} after {@code TimeZone.setDefault}.
 * The build bans JDK calls that read the default zone, listed in
 * {@code forbidden-apis/default-time-zone.txt}, and this test requires every public API method
 * that reaches one of the roots to be on that list.
 *
 * <p>The roots are the places where the default zone becomes visible to a caller, not only the
 * field it is stored in. {@code TimeZone.getDefaultRef()} is package-private and is what
 * {@code java.util.Date} and {@code Calendar} call directly, so {@code TimeZone.getDefault()}
 * alone would miss them. {@code TimeZone.getDefault()}, {@code ZoneId.systemDefault()} and
 * {@code Clock.systemDefaultZone()} are roots too: {@code LocalDate.now()} reaches
 * {@code getDefaultRef()} only through three public methods, and walking through that many
 * public methods from one root picks up paths where the zone does not reach the result, as
 * {@link DefaultLocaleApiAuditTest} found for the locale. Each root restarts the count.
 *
 * <p>{@code TimeZone.toZoneId0()} reads the default zone's field directly but only to reuse the
 * default's {@code ZoneId} when the IDs are equal, which gives an equal result, so it is not a root.
 *
 * <p>{@code forbidden-apis/default-time-zone-reviewed.txt} names JDK methods where the walk stops
 * because the zone read below them does not reach the caller, with the reason. An entry no path
 * reaches any more fails the test.
 */
class DefaultTimeZoneApiAuditTest {

    static final Set<MethodRef> ROOTS = Set.of(
            new MethodRef("java/util/TimeZone", "getDefaultRef", "()Ljava/util/TimeZone;"),
            new MethodRef("java/util/TimeZone", "getDefault", "()Ljava/util/TimeZone;"),
            new MethodRef("java/time/ZoneId", "systemDefault", "()Ljava/time/ZoneId;"),
            new MethodRef("java/time/Clock", "systemDefaultZone", "()Ljava/time/Clock;"));

    static final int MAX_PUBLIC_HOPS = 1;

    static Scan scan;
    static Set<String> banned;
    static Map<String, String> reviewed;

    @BeforeAll
    static void scanTheJdk() throws IOException {
        int release = Integer.parseInt(requiredProperty("raoh.release"));
        var dir = Path.of(requiredProperty("raoh.forbiddenApisDir"));
        banned = JdkCallGraph.signatures(Files.readAllLines(dir.resolve("default-time-zone.txt"), StandardCharsets.UTF_8));
        reviewed = JdkCallGraph.reviewedEntries(Files.readAllLines(dir.resolve("default-time-zone-reviewed.txt"),
                StandardCharsets.UTF_8), "default-time-zone-reviewed.txt");
        var raw = JdkCallGraph.ofSystem().scanBackwards(ROOTS, reviewed.keySet(), MAX_PUBLIC_HOPS);
        var inRelease = JdkCallGraph.releaseApi(release);
        scan = new Scan(raw.readers().stream().filter(inRelease).collect(Collectors.toSet()), raw.reachedStops());
    }

    @Test
    void everyJdkApiThatReadsTheDefaultTimeZoneIsBanned() {
        var uncovered = scan.readers().stream().filter(sig -> !JdkCallGraph.isBanned(sig, banned)).sorted().toList();
        assertTrue(uncovered.isEmpty(), () -> "These JDK methods read the default time zone but are not banned."
                + " Add each to forbidden-apis/default-time-zone.txt, or, if the zone cannot reach the"
                + " caller's result, add the JDK method on the path where it is dropped to"
                + " default-time-zone-reviewed.txt with the reason:\n  " + String.join("\n  ", uncovered));
    }

    @Test
    void reviewedStopsAreStillOnAPathAndNotBanned() {
        var stale = reviewed.keySet().stream()
                .filter(sig -> !scan.reachedStops().contains(sig) || JdkCallGraph.isBanned(sig, banned))
                .sorted()
                .toList();
        assertTrue(stale.isEmpty(), () -> "Remove these from forbidden-apis/default-time-zone-reviewed.txt;"
                + " no path from the default time zone reaches them any more, or they are banned anyway:\n  "
                + String.join("\n  ", stale));
    }

    @Test
    void scanFindsKnownReaders() {
        // Pins what the walk must be able to see: the java.sql conversions #141 removed, which
        // reach the zone through the package-private getDefaultRef(), and LocalDate.now(), which
        // reaches it through three public methods. The expected names come from the JDK
        // documentation, not from this scan.
        assertAll(
                () -> assertTrue(scan.readers().contains("java.sql.Date#toLocalDate()")),
                () -> assertTrue(scan.readers().contains("java.sql.Timestamp#toLocalDateTime()")),
                () -> assertTrue(scan.readers().contains("java.sql.Timestamp#toInstant()")),
                () -> assertTrue(scan.readers().contains("java.time.LocalDate#now()")));
    }
}
