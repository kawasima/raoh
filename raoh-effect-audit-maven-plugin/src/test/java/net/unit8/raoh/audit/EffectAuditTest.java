package net.unit8.raoh.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EffectAuditTest {

    @TempDir
    Path dir;

    private static final String BASE_CATALOG = """
            [CLOSED]
            java.lang.Object#<init>()
            java.lang.String#trim()
            """;

    private EffectAudit.Report audit(String source, String catalog, String approvals) throws IOException {
        var compiled = Fixtures.compile(dir, Map.of("A", source));
        var catalogFile = Files.writeString(dir.resolve("catalog.txt"), catalog);
        var approvalFile = Files.writeString(dir.resolve("approvals.txt"), approvals);
        return EffectAudit.check(compiled.scan(), EffectCatalog.read(catalogFile), Approvals.read(approvalFile),
                compiled.hierarchy(), caller -> caller.startsWith("fixture.decode."));
    }

    @Test
    void passesWhenEveryMemberIsClosed() throws IOException {
        var report = audit("class A { String t(String s) { return s.trim(); } }", BASE_CATALOG, "");
        assertTrue(report.passed(), () -> report.describe("catalog", "approvals"));
    }

    @Test
    void failsOnAMemberTheCatalogDoesNotList() throws IOException {
        var report = audit("class A { String t(String s) { return s.strip(); } }", BASE_CATALOG, "");
        assertEquals(Set.of(Member.parse("java.lang.String#strip()")), report.unknown().keySet());
        assertTrue(report.describe("catalog", "approvals").contains("java.lang.String#strip()  -- used by fixture.A#t"));
    }

    @Test
    void refusesClosedForAnOverridableMethodCalledVirtually() throws IOException {
        // Whatever List#get does depends on the receiver's class, which the caller chooses.
        var report = audit("class A { Object g(java.util.List<?> l) { return l.get(0); } }",
                BASE_CATALOG + "java.util.List#get(int)\n", "");
        assertEquals(Set.of(Member.parse("java.util.List#get(int)")), report.overridable().keySet());
    }

    @Test
    void requiresAnApprovalForEachDelegatedUse() throws IOException {
        var source = "class A { Object g(java.util.List<?> l) { return l.get(0); } }";
        var catalog = BASE_CATALOG + "[DELEGATED]\njava.util.List#get(int)\n";
        var missing = audit(source, catalog, "");
        assertEquals(Set.of(new Approvals.Use("fixture.A#g", Member.parse("java.util.List#get(int)"))),
                missing.unapproved().keySet());

        var approved = audit(source, catalog, "[observes the input]\nfixture.A#g -> java.util.List#get(int)\n");
        assertTrue(approved.passed(), () -> approved.describe("catalog", "approvals"));
    }

    @Test
    void neverAllowsAnAmbientMemberInADecoder() throws IOException {
        var catalog = BASE_CATALOG + "[AMBIENT]\njava.util.Locale#getDefault()\n";
        var compiled = Fixtures.compile(dir, Map.of(
                "decode/D", "package fixture.decode; public class D { Object l() { return java.util.Locale.getDefault(); } }",
                "R", "class R { Object l() { return java.util.Locale.getDefault(); } }"));
        var catalogFile = Files.writeString(dir.resolve("catalog.txt"), catalog);
        var approvalFile = Files.writeString(dir.resolve("approvals.txt"), """
                [chooses a display locale]
                fixture.decode.D#l -> java.util.Locale#getDefault()
                fixture.R#l -> java.util.Locale#getDefault()
                """);
        var report = EffectAudit.check(compiled.scan(), EffectCatalog.read(catalogFile),
                Approvals.read(approvalFile), compiled.hierarchy(), caller -> caller.startsWith("fixture.decode."));
        assertEquals(Set.of(new Approvals.Use("fixture.decode.D#l", Member.parse("java.util.Locale#getDefault()"))),
                report.ambientInDecoder());
        assertTrue(report.unapproved().isEmpty(), report.unapproved()::toString);
    }

    @Test
    void failsOnAnApprovalNoUseMatches() throws IOException {
        var report = audit("class A { String t(String s) { return s.trim(); } }", BASE_CATALOG,
                "[observes the input]\nfixture.A#gone -> java.util.List#get(int)\n");
        assertEquals(Set.of(new Approvals.Use("fixture.A#gone", Member.parse("java.util.List#get(int)"))),
                report.stale());
    }

    @Test
    void failsOnAnApprovalOfAClosedMember() throws IOException {
        var report = audit("class A { String t(String s) { return s.trim(); } }", BASE_CATALOG,
                "[no reason needed]\nfixture.A#t -> java.lang.String#trim()\n");
        assertEquals(Set.of(new Approvals.Use("fixture.A#t", Member.parse("java.lang.String#trim()"))),
                report.needless());
    }
}
