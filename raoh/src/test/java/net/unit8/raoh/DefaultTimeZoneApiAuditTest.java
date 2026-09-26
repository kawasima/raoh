package net.unit8.raoh;

import net.unit8.raoh.JdkCallGraph.MethodRef;
import net.unit8.raoh.JdkCallGraph.Scan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Keeps the forbidden-API list of default time zone readers complete against the JDK itself.
 *
 * <p>A decoder's result must not depend on the JVM default time zone (#141): the same
 * {@code java.sql.Date} decoded to a different {@code LocalDate} after {@code TimeZone.setDefault}.
 * The build bans JDK calls that read the default zone, listed in
 * {@code forbidden-apis/default-time-zone.txt}, and this test requires every public API method
 * that reaches the default zone to be on that list.
 *
 * <p>The walk starts from {@code TimeZone.getDefaultRef()}. It is package-private, and every read
 * of the default zone goes through it: {@code TimeZone.getDefault()} clones what it returns, and
 * {@code java.util.Date} and {@code Calendar} call it directly. The only other code that reads the
 * underlying field, {@code TimeZone.toZoneId0()}, uses it to reuse the default's {@code ZoneId}
 * when the IDs are equal, which gives an equal result.
 *
 * <p>Unlike {@link DefaultLocaleApiAuditTest}, the walk passes through any number of public
 * methods. The default zone reaches callers through chains of them, such as {@code LocalDate.now()}
 * → {@code Clock.systemDefaultZone()} → {@code ZoneId.systemDefault()} →
 * {@code TimeZone.getDefault()}, or {@code Timestamp.equals(Object)} → {@code Date.getTime()}, so a
 * limit would miss readers. Paths where the zone does not reach the caller's result are cut
 * instead, at the JDK method that drops it, named with the reason in
 * {@code forbidden-apis/default-time-zone-reviewed.txt}. An entry no path reaches any more fails
 * the test, so the file cannot silently outlive the JDK code it describes.
 */
class DefaultTimeZoneApiAuditTest {

    static final Set<MethodRef> ROOTS = Set.of(
            new MethodRef("java/util/TimeZone", "getDefaultRef", "()Ljava/util/TimeZone;"));

    static final int MAX_PUBLIC_HOPS = Integer.MAX_VALUE;

    static Scan scan;
    static Set<String> banned;
    static Map<String, String> reviewed;

    @BeforeAll
    static void scanTheJdk() throws IOException {
        banned = JdkCallGraph.signatures(JdkCallGraph.forbiddenApisFile("default-time-zone.txt"));
        reviewed = JdkCallGraph.reviewedEntries(JdkCallGraph.forbiddenApisFile("default-time-zone-reviewed.txt"),
                "default-time-zone-reviewed.txt");
        scan = JdkCallGraph.ofSystem().scanBackwards(ROOTS, reviewed.keySet(), MAX_PUBLIC_HOPS)
                .filterReaders(JdkCallGraph.releaseApi(JdkCallGraph.compilerRelease()));
    }

    @Test
    void everyJdkApiThatReadsTheDefaultTimeZoneIsBanned() {
        var uncovered = scan.readers().stream().filter(sig -> !JdkCallGraph.isBanned(sig, banned)).sorted()
                .map(scan::describe).toList();
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
        // reach the zone through the package-private getDefaultRef(); LocalDate.now(), which
        // reaches it through three public methods; and Timestamp.equals(Object), which reads it
        // only for a Timestamp changed through its deprecated setters. The expected names come
        // from the JDK documentation, not from this scan.
        assertAll(
                () -> assertTrue(scan.readers().contains("java.sql.Date#toLocalDate()")),
                () -> assertTrue(scan.readers().contains("java.sql.Timestamp#toLocalDateTime()")),
                () -> assertTrue(scan.readers().contains("java.sql.Timestamp#toInstant()")),
                () -> assertTrue(scan.readers().contains("java.time.LocalDate#now()")),
                () -> assertTrue(scan.readers().contains("java.sql.Timestamp#equals(java.lang.Object)")));
    }
}
