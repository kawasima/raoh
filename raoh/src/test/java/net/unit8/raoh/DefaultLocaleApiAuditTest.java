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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Keeps the forbidden-API list complete against the JDK itself.
 *
 * <p>The build bans JDK calls that read the JVM default locale, because a decoder's result must
 * not depend on it (#136). A hand-written list of such calls always has gaps, and so do the lists
 * of forbidden-apis ({@code jdk-unsafe}) and Error Prone ({@code DefaultLocale}): neither knows
 * {@code ListFormat.getInstance()}, {@code DateTimeFormatter.ofLocalizedPattern(String)},
 * {@code DecimalStyle.ofDefaultLocale()} or {@code Scanner(String)}. This test derives the set
 * from the JDK instead. It walks the bytecode of every {@code java.*} module back from
 * {@code Locale.getDefault()} and {@code Locale.getDefault(Locale.Category)}, the only ways to
 * read the default locale, and requires every public API method it reaches to be banned by
 * {@code forbidden-apis/default-locale.txt}.
 *
 * <p>The walk follows non-public JDK code without limit and passes through at most one other
 * public method. One public hop catches {@code String.format}, which reads the locale through
 * {@code new Formatter()}; with more hops the result is mostly exception-message paths such as
 * {@code Integer.parseInt} → {@code Objects.checkIndex} → {@code String.format}, which do not
 * reach a decoder's result. A call is resolved up the superclass chain to the class declaring the
 * method, since bytecode names the class it was compiled against.
 *
 * <p>Some paths read the locale without letting it reach the caller, such as a debug trace.
 * {@code forbidden-apis/default-locale-reviewed.txt} names such a JDK method with the reason;
 * the walk stops there. An entry no path reaches any more fails the test, so the file cannot
 * silently outlive the JDK code it describes.
 *
 * <p>Method bodies come from the JDK running the test, which may be newer than the compiler
 * release. Methods that release does not have cannot be called by the library, so they are
 * dropped by looking each one up in javac's {@code --release} symbol table. Raising the release
 * makes this test list the new release's additions. The walk itself is {@link JdkCallGraph}.
 */
class DefaultLocaleApiAuditTest {

    static final Set<MethodRef> ROOTS = Set.of(
            new MethodRef("java/util/Locale", "getDefault", "()Ljava/util/Locale;"),
            new MethodRef("java/util/Locale", "getDefault", "(Ljava/util/Locale$Category;)Ljava/util/Locale;"));

    static final int MAX_PUBLIC_HOPS = 1;

    static Scan scan;
    static Set<String> banned;
    static Map<String, String> reviewed;

    @BeforeAll
    static void scanTheJdk() throws IOException {
        int release = Integer.parseInt(requiredProperty("raoh.release"));
        var dir = Path.of(requiredProperty("raoh.forbiddenApisDir"));
        banned = JdkCallGraph.signatures(Files.readAllLines(dir.resolve("default-locale.txt"), StandardCharsets.UTF_8));
        reviewed = JdkCallGraph.reviewedEntries(Files.readAllLines(dir.resolve("default-locale-reviewed.txt"),
                StandardCharsets.UTF_8), "default-locale-reviewed.txt");
        var raw = JdkCallGraph.ofSystem().scanBackwards(ROOTS, reviewed.keySet(), MAX_PUBLIC_HOPS);
        var inRelease = JdkCallGraph.releaseApi(release);
        scan = new Scan(raw.readers().stream().filter(inRelease).collect(Collectors.toSet()), raw.reachedStops());
    }

    @Test
    void everyJdkApiThatReadsTheDefaultLocaleIsBanned() {
        var uncovered = scan.readers().stream().filter(sig -> !JdkCallGraph.isBanned(sig, banned)).sorted().toList();
        assertTrue(uncovered.isEmpty(), () -> "These JDK methods read the default locale but are not banned."
                + " Add each to forbidden-apis/default-locale.txt, or, if the locale cannot reach the"
                + " caller's result, add the JDK method on the path where it is dropped to"
                + " default-locale-reviewed.txt with the reason:\n  " + String.join("\n  ", uncovered));
    }

    @Test
    void reviewedStopsAreStillOnAPathAndNotBanned() {
        var stale = reviewed.keySet().stream()
                .filter(sig -> !scan.reachedStops().contains(sig) || JdkCallGraph.isBanned(sig, banned))
                .sorted()
                .toList();
        assertTrue(stale.isEmpty(), () -> "Remove these from forbidden-apis/default-locale-reviewed.txt;"
                + " no path from the default locale reaches them any more, or they are banned anyway:\n  "
                + String.join("\n  ", stale));
    }

    @Test
    void scanFindsKnownReaders() {
        // Pins what the walk must be able to see: a direct reader, a reader one public method
        // away, and a reader the bundled lists of forbidden-apis and Error Prone both miss. The
        // expected names come from the JDK documentation, not from this scan.
        assertAll(
                () -> assertTrue(scan.readers().contains("java.lang.String#toLowerCase()")),
                () -> assertTrue(scan.readers().contains("java.lang.String#format(java.lang.String,java.lang.Object[])")),
                () -> assertTrue(scan.readers().contains("java.text.ListFormat#getInstance()")));
    }

    static String requiredProperty(String name) {
        var value = System.getProperty(name);
        assertNotNull(value, "system property " + name + " is set by the surefire configuration in raoh/pom.xml");
        return value;
    }
}
