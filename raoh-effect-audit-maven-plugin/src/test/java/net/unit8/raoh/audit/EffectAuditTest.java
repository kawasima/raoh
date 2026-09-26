package net.unit8.raoh.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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
            java.lang.String#trim():java.lang.String
            java.lang.invoke.LambdaMetafactory#metafactory(java.lang.invoke.MethodHandles$Lookup,java.lang.String,java.lang.invoke.MethodType,java.lang.invoke.MethodType,java.lang.invoke.MethodHandle,java.lang.invoke.MethodType):java.lang.invoke.CallSite
            java.util.Locale#toLanguageTag():java.lang.String
            [AMBIENT]
            java.util.Locale#getDefault():java.util.Locale
            """;

    private static final Member GET_DEFAULT = Member.parse("java.util.Locale#getDefault():java.util.Locale");

    private EffectAudit.Report audit(Map<String, String> sources, String catalog, String approvals) throws IOException {
        return Fixtures.audit(Fixtures.compile(dir, sources), dir, catalog, approvals);
    }

    @Test
    void passesWhenEveryMemberIsClosed() throws IOException {
        var report = audit(Map.of("A", "class A { String t(String s) { return s.trim(); } }"), BASE_CATALOG, "");
        assertTrue(report.passed(), () -> report.describe("catalog", "approvals"));
    }

    @Test
    void failsOnAMemberTheCatalogDoesNotList() throws IOException {
        var report = audit(Map.of("A", "class A { String t(String s) { return s.strip(); } }"), BASE_CATALOG, "");
        assertEquals(Set.of(Member.parse("java.lang.String#strip():java.lang.String")), report.unknown().keySet());
    }

    @Test
    void refusesClosedForAnOverridableMethodCalledVirtually() throws IOException {
        // Whatever List#get does depends on the receiver's class, which the caller chooses.
        var report = audit(Map.of("A", "class A { Object g(java.util.List<?> l) { return l.get(0); } }"),
                BASE_CATALOG.replace("[AMBIENT]", "java.util.List#get(int):java.lang.Object\n[AMBIENT]"), "");
        assertEquals(Set.of(Member.parse("java.util.List#get(int):java.lang.Object")), report.overridable().keySet());
    }

    @Test
    void requiresAnApprovalForEachDelegatedUse() throws IOException {
        var source = Map.of("A", "class A { Object g(java.util.List<?> l) { return l.get(0); } }");
        var catalog = BASE_CATALOG + "[DELEGATED]\njava.util.List#get(int):java.lang.Object\n";
        var missing = audit(source, catalog, "");
        assertEquals(Set.of(new Approvals.Use("fixture.A#g", Member.parse("java.util.List#get(int):java.lang.Object"))),
                missing.unapproved().keySet());

        var approved = audit(source, catalog, "[observes the input]\nfixture.A#g -> java.util.List#get(int):java.lang.Object\n");
        assertTrue(approved.passed(), () -> approved.describe("catalog", "approvals"));
    }

    @Test
    void approvesAnAmbientUseOutsideDecoderCode() throws IOException {
        var report = audit(Map.of("R", "class R { Object l() { return java.util.Locale.getDefault(); } }"), BASE_CATALOG,
                "[chooses a display locale]\nfixture.R#l():java.lang.Object -> java.util.Locale#getDefault():java.util.Locale\n");
        assertTrue(report.passed(), () -> report.describe("catalog", "approvals"));
    }

    @Test
    void neverAllowsADecoderToReadAmbientStateItself() throws IOException {
        var report = audit(Map.of("decode/D",
                "package fixture.decode; public class D { Object l() { return java.util.Locale.getDefault(); } }"),
                BASE_CATALOG, "[approved anyway]\nfixture.decode.D#l():java.lang.Object -> java.util.Locale#getDefault():java.util.Locale\n");
        assertEquals(Set.of(new Approvals.Use("fixture.decode.D#l():java.lang.Object", GET_DEFAULT)), report.ambientInDecoder().keySet());
    }

    @Test
    void followsAnInternalHelperFromADecoderToAnAmbientRead() throws IOException {
        // The helper's own use is approved, which alone would pass; the decoder reaching it must not.
        var report = audit(Map.of(
                "Environment", "public final class Environment { public static Object locale() { return java.util.Locale.getDefault(); } }",
                "decode/D", "package fixture.decode; public class D { Object l() { return fixture.Environment.locale(); } }"),
                BASE_CATALOG, "[chooses a display locale]\nfixture.Environment#locale():java.lang.Object -> java.util.Locale#getDefault():java.util.Locale\n");
        var use = new Approvals.Use("fixture.Environment#locale():java.lang.Object", GET_DEFAULT);
        assertEquals(Set.of(use), report.ambientInDecoder().keySet());
        assertEquals("fixture.decode.D#l():java.lang.Object -> fixture.Environment#locale():java.lang.Object",
                report.ambientInDecoder().get(use));
    }

    @Test
    void followsAVirtualCallToEveryInternalImplementation() throws IOException {
        var report = audit(Map.of(
                "Resolver", "public interface Resolver { Object resolve(); }",
                "Plain", "public final class Plain implements Resolver { public Object resolve() { return \"x\"; } }",
                "Localized", "public final class Localized implements Resolver { public Object resolve() { return java.util.Locale.getDefault(); } }",
                "decode/D", "package fixture.decode; public class D { Object l(fixture.Resolver r) { return r.resolve(); } }"),
                BASE_CATALOG, "[chooses a display locale]\nfixture.Localized#resolve():java.lang.Object -> java.util.Locale#getDefault():java.util.Locale\n");
        assertEquals(Set.of(new Approvals.Use("fixture.Localized#resolve():java.lang.Object", GET_DEFAULT)), report.ambientInDecoder().keySet());
    }

    @Test
    void followsAVirtualCallToAnImplementationTheReceiverInherits() throws IOException {
        // Base is not a Resolver, but for an Impl receiver Resolver#resolve() runs Base#resolve().
        var report = audit(Map.of(
                "Resolver", "public interface Resolver { Object resolve(); }",
                "Base", "public class Base { public Object resolve() { return java.util.Locale.getDefault(); } }",
                "Impl", "public final class Impl extends Base implements Resolver {}",
                "decode/D", "package fixture.decode; public class D { Object l(fixture.Resolver r) { return r.resolve(); } }"),
                BASE_CATALOG, "[chooses a display locale]\nfixture.Base#resolve():java.lang.Object -> java.util.Locale#getDefault():java.util.Locale\n");
        assertEquals(Set.of(new Approvals.Use("fixture.Base#resolve():java.lang.Object", GET_DEFAULT)), report.ambientInDecoder().keySet());
    }

    @Test
    void reportsACallThroughAnInternalTypeThatRunsAnExternalImplementation() throws IOException {
        // Sized#size() on a Bag runs ArrayList#size(), which the call site does not name.
        var report = audit(Map.of(
                "Sized", "public interface Sized { int size(); }",
                "Bag", "public final class Bag extends java.util.ArrayList<Object> implements Sized {}",
                "decode/D", "package fixture.decode; public class D { int l(fixture.Sized s) { return s.size(); } }"),
                BASE_CATALOG, "");
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("fixture.Sized#size():int runs java.util.ArrayList")),
                report.problems()::toString);
    }

    @Test
    void followsALambdaBodyFromWhereTheLambdaIsCreated() throws IOException {
        var report = audit(Map.of(
                "Lazy", "public final class Lazy { public static java.util.function.Supplier<Object> locale() { return () -> java.util.Locale.getDefault(); } }",
                "decode/D", "package fixture.decode; public class D { Object l() { return fixture.Lazy.locale(); } }"),
                BASE_CATALOG, "[chooses a display locale]\nfixture.Lazy#lambda$locale$0():java.lang.Object -> java.util.Locale#getDefault():java.util.Locale\n");
        assertEquals(Set.of(new Approvals.Use("fixture.Lazy#lambda$locale$0():java.lang.Object", GET_DEFAULT)), report.ambientInDecoder().keySet());
    }

    @Test
    void followsAReferenceToAClassIntoItsStaticInitializer() throws IOException {
        var report = audit(Map.of(
                "Constants", "public final class Constants { public static final Object LOCALE = java.util.Locale.getDefault(); }",
                "decode/D", "package fixture.decode; public class D { Object l() { return fixture.Constants.LOCALE; } }"),
                BASE_CATALOG, "[chooses a display locale]\nfixture.Constants#<clinit>():void -> java.util.Locale#getDefault():java.util.Locale\n");
        assertEquals(Set.of(new Approvals.Use("fixture.Constants#<clinit>():void", GET_DEFAULT)), report.ambientInDecoder().keySet());
    }

    @Test
    void followsInitializationIntoTheSuperclassThatDeclaresAStaticMember() throws IOException {
        // Sub.LOCALE is declared in Base: the JVM initializes Base, whose initializer reads the locale.
        var report = audit(Map.of(
                "Base", "public class Base { public static final Object LOCALE = java.util.Locale.getDefault(); }",
                "Sub", "public class Sub extends Base {}",
                "decode/D", "package fixture.decode; public class D { Object l() { return fixture.Sub.LOCALE; } }"),
                BASE_CATALOG, "[chooses a display locale]\nfixture.Base#<clinit>():void -> java.util.Locale#getDefault():java.util.Locale\n");
        assertEquals(Set.of(new Approvals.Use("fixture.Base#<clinit>():void", GET_DEFAULT)), report.ambientInDecoder().keySet());
    }

    @Test
    void approvesAnAmbientUseForTheExactMethodOnly() throws IOException {
        // The approval names resolve(); the overload resolve(Locale) reading the default is a new use.
        var report = audit(Map.of("R", """
                class R {
                    Object resolve() { return java.util.Locale.getDefault(); }
                    Object resolve(java.util.Locale l) { return l == null ? java.util.Locale.getDefault() : l; }
                }
                """), BASE_CATALOG, "[chooses a display locale]\nfixture.R#resolve():java.lang.Object -> java.util.Locale#getDefault():java.util.Locale\n");
        assertEquals(Set.of(new Approvals.Use("fixture.R#resolve(java.util.Locale):java.lang.Object", GET_DEFAULT)),
                report.unapproved().keySet());
    }

    @Test
    void requiresANoteForAClosedMemberThatTakesAnOpenArgument() throws IOException {
        var source = Map.of("A", "class A { Object n(Object o) { return java.util.Objects.requireNonNull(o); } }");
        var member = "java.util.Objects#requireNonNull(java.lang.Object):java.lang.Object";
        var bare = audit(source, BASE_CATALOG.replace("[AMBIENT]", member + "\n[AMBIENT]"), "");
        assertEquals(Set.of(Member.parse(member)), bare.unexplained().keySet());
        var noted = audit(source, BASE_CATALOG.replace("[AMBIENT]", member + "  -- null-checks only\n[AMBIENT]"), "");
        assertTrue(noted.passed(), () -> noted.describe("catalog", "approvals"));
    }

    @Test
    void followsInternalCallsIntoAnotherModule() throws IOException {
        // raoh-json's decoders call raoh's helpers: the walk reads the dependency's internal classes.
        var core = Fixtures.compile(dir, "core", Map.of(
                "Environment", "public final class Environment { public static Object locale() { return java.util.Locale.getDefault(); } }"));
        var json = Fixtures.compile(dir, "json", Map.of(
                "decode/J", "package fixture.decode; public class J { Object l() { return fixture.Environment.locale(); } }"), core);
        var report = Fixtures.audit(json, dir, BASE_CATALOG, "");
        assertEquals(Set.of(new Approvals.Use("fixture.Environment#locale():java.lang.Object", GET_DEFAULT)), report.ambientInDecoder().keySet());
        assertTrue(report.unapproved().isEmpty(), "the dependency's own uses are audited in its own module");
    }

    @Test
    void followsExternalCallbacksIntoAClassADecoderConstructs() throws IOException {
        // The decoder never calls Key#hashCode; HashMap does, once the decoder hands it a Key.
        var report = audit(Map.of(
                "Key", "public final class Key { @Override public int hashCode() { return java.util.Locale.getDefault().hashCode(); } }",
                "decode/D", "package fixture.decode; public class D { Object l() { var m = new java.util.HashMap<Object, Object>(); m.put(new fixture.Key(), 1); return m; } }"),
                BASE_CATALOG, "[hashes a key]\nfixture.Key#hashCode():int -> java.util.Locale#getDefault():java.util.Locale\n");
        assertEquals(Set.of(new Approvals.Use("fixture.Key#hashCode():int", GET_DEFAULT)), report.ambientInDecoder().keySet());
        assertTrue(report.ambientInDecoder().values().iterator().next().endsWith("fixture.Key#hashCode():int"));
    }

    @Test
    void reportsAWriteToInternalStaticStateAfterInitialization() throws IOException {
        var report = audit(Map.of("Settings", """
                public final class Settings {
                    static Object current;
                    public static void set(Object value) { current = value; }
                }
                """), BASE_CATALOG, "");
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("fixture.Settings#set")
                && p.contains("mutable static state")), report.problems()::toString);
    }

    @Test
    void reportsAStaticFieldThatIsNotFinalEvenIfRaohNeverWritesIt() throws IOException {
        // A caller could write CURRENT and change what every decoder reads.
        var report = audit(Map.of("Config", "public final class Config { public static Object CURRENT = \"x\"; }"),
                BASE_CATALOG, "");
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("fixture.Config: declares the static field CURRENT")),
                report.problems()::toString);
    }

    @Test
    void reportsAnUncataloguedMemberADecoderReachesInADependency() throws IOException {
        var core = Fixtures.compile(dir, "core", Map.of(
                "Helper", "public final class Helper { public static String t(String s) { return s.strip(); } }"));
        var json = Fixtures.compile(dir, "json", Map.of(
                "decode/J", "package fixture.decode; public class J { Object l() { return fixture.Helper.t(\"x\"); } }"), core);
        var report = Fixtures.audit(json, dir, BASE_CATALOG, "");
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("fixture.Helper#t -> java.lang.String#strip()")),
                report.problems()::toString);
    }

    @Test
    void passesWhenNoDecoderReachesTheAmbientRead() throws IOException {
        var report = audit(Map.of(
                "Environment", "public final class Environment { public static Object locale() { return java.util.Locale.getDefault(); } }",
                "decode/D", "package fixture.decode; public class D { String t(String s) { return s.trim(); } }"),
                BASE_CATALOG, "[chooses a display locale]\nfixture.Environment#locale():java.lang.Object -> java.util.Locale#getDefault():java.util.Locale\n");
        assertTrue(report.passed(), () -> report.describe("catalog", "approvals"));
    }

    @Test
    void failsOnAnApprovalNoUseMatches() throws IOException {
        var report = audit(Map.of("A", "class A { String t(String s) { return s.trim(); } }"), BASE_CATALOG,
                "[observes the input]\nfixture.A#gone -> java.util.List#get(int):java.lang.Object\n");
        assertEquals(Set.of(new Approvals.Use("fixture.A#gone", Member.parse("java.util.List#get(int):java.lang.Object"))),
                report.stale());
    }

    @Test
    void failsOnAnApprovalOfAClosedMember() throws IOException {
        var report = audit(Map.of("A", "class A { String t(String s) { return s.trim(); } }"), BASE_CATALOG,
                "[no reason needed]\nfixture.A#t -> java.lang.String#trim():java.lang.String\n");
        assertEquals(Set.of(new Approvals.Use("fixture.A#t", Member.parse("java.lang.String#trim():java.lang.String"))),
                report.needless());
    }
}
