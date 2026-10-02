package net.unit8.raoh.testing;

import net.unit8.raoh.testing.PublicVarargsOverloads.Allowed;
import net.unit8.raoh.testing.PublicVarargsOverloads.Signature;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicVarargsOverloadsTest {

    /** As containsAll would be: a list and a string are both Ts when T is Object. */
    @SuppressWarnings("unused")
    public static class Generic<T> {
        @SafeVarargs
        public final void all(T... elements) {
        }

        public void all(List<? extends T> elements, String message) {
        }
    }

    /** f(1) went to f(long...), widening 1 to a long, and goes to f(int), which takes it as it is. */
    @SuppressWarnings("unused")
    public static class Widening {
        public void f(long... values) {
        }

        public void f(int value) {
        }
    }

    /** f(1) went to f(Integer...) and goes to f(int) by strict invocation. */
    @SuppressWarnings("unused")
    public static class Unboxing {
        public void f(Integer... values) {
        }

        public void f(int value) {
        }
    }

    /** f("a", "b") went to f(Object...) and goes to the more specific f(String, Object...). */
    @SuppressWarnings("unused")
    public static class TwoVarargs {
        public void f(Object... values) {
        }

        public void f(String first, Object... rest) {
        }
    }

    /** Java puts static and instance methods of one name in one set of candidates. */
    @SuppressWarnings("unused")
    public static class StaticAndInstance {
        public static void f(Object... values) {
        }

        public void f(String value) {
        }
    }

    /** new Constructors("a") went to the varargs constructor and goes to the String one. */
    @SuppressWarnings("unused")
    public static class Constructors {
        public Constructors(Object... values) {
        }

        public Constructors(String value) {
        }
    }

    /** Public methods of a package-private class, which a public subclass offers through bridges. */
    @SuppressWarnings("unused")
    abstract static class Hidden {
        public void all(Object... elements) {
        }

        public void all(List<?> elements, String message) {
        }
    }

    public static class Visible extends Hidden {
    }

    /** f(String[]) from one interface, f(String...) from another: f("x") reaches the second. */
    public interface Fixed {
        void f(String[] values);
    }

    public interface Varargs {
        void f(String... values);
    }

    /** f(Object) takes over f("x"), whichever interface comes first. */
    public abstract static class FixedFirst implements Fixed, Varargs {
        public void f(Object value) {
        }
    }

    public abstract static class VarargsFirst implements Varargs, Fixed {
        public void f(Object value) {
        }
    }

    /** One method, which C inherits from S as the implementation of I's: not a pair. */
    public interface Declares {
        void f(String... values);
    }

    @SuppressWarnings("unused")
    public static class Implements {
        public void f(String... values) {
        }
    }

    public static class InheritsTheImplementation extends Implements implements Declares {
    }

    public interface AlsoDeclares {
        void f(String... values);
    }

    public abstract static class DeclaredTwice implements Declares, AlsoDeclares {
    }

    /** As oneOf is: no String is a Collection, so a reviewer may allow the pair. */
    @SuppressWarnings("unused")
    public static class Strings {
        public void any(String... values) {
        }

        public void any(Collection<? extends String> values, String message) {
        }
    }

    /** A covariant override of a varargs method, for which javac generates a bridge. */
    @SuppressWarnings("unused")
    public static class Base {
        public Object m(Object... values) {
            return null;
        }
    }

    @SuppressWarnings("unused")
    public static class Covariant extends Base {
        @Override
        public String m(Object... values) {
            return null;
        }
    }

    /** A generic varargs method, which a subclass overrides at a type argument it gives. */
    @SuppressWarnings("unused")
    public static class GenericBase<T> {
        @SafeVarargs
        public final void g(T... values) {
        }

        @SuppressWarnings("unchecked")
        public void f(T... values) {
        }
    }

    public static class StringSub extends GenericBase<String> {
        @Override
        public void f(String... values) {
        }
    }

    /** Two classes whose static methods meet once both are imported on demand. */
    @SuppressWarnings("unused")
    public static class ImportedVarargs {
        public static void f(Object... values) {
        }
    }

    @SuppressWarnings("unused")
    public static class ImportedFixed {
        public static void f(String value) {
        }
    }

    @Test
    void everyMethodBesideAVarargsMethodOfItsNameIsReported() {
        for (Class<?> c : List.of(Generic.class, Widening.class, Unboxing.class, TwoVarargs.class,
                StaticAndInstance.class, Constructors.class, Strings.class)) {
            assertEquals(1, PublicVarargsOverloads.problems(List.of(c), List.of()).size(), c.getSimpleName());
        }
    }

    @Test
    void methodsInheritedFromAPackagePrivateClassAreSeen() {
        assertEquals(1, PublicVarargsOverloads.problems(List.of(Visible.class), List.of()).size());
    }

    @Test
    void aVarargsMethodFromOneInterfaceIsKeptBesideAFixedOneFromAnother() {
        for (Class<?> c : List.of(FixedFirst.class, VarargsFirst.class)) {
            var problems = PublicVarargsOverloads.problems(List.of(c), List.of());
            assertTrue(problems.stream().anyMatch(p -> p.contains("f(java.lang.Object)") && p.contains("java.lang.String...")),
                    c.getSimpleName() + ": " + problems);
        }
    }

    @Test
    void anOverrideOfAGenericVarargsMethodIsOneMethod() {
        var fs = PublicVarargsOverloads.methods(StringSub.class).stream().filter(m -> m.getName().equals("f")).toList();
        assertEquals(1, fs.size(), fs.toString());
        assertEquals(StringSub.class, fs.getFirst().getDeclaringClass());
        assertEquals(List.of(), PublicVarargsOverloads.problems(List.of(StringSub.class), List.of()));
    }

    @Test
    void oneMethodReachedAlongTwoPathsIsOne() {
        for (Class<?> c : List.of(InheritsTheImplementation.class, DeclaredTwice.class)) {
            assertEquals(List.of(), PublicVarargsOverloads.problems(List.of(c), List.of()), c.getSimpleName());
        }
    }

    @Test
    void anAllowedPairNamesTwoMethodsOfOneName() {
        var any = Signature.of(Strings.class, "any", String[].class);
        assertThrows(IllegalArgumentException.class, () -> new Allowed(any, any, "the same method twice"));
        assertThrows(IllegalArgumentException.class, () -> new Allowed(any,
                Signature.of(Strings.class, "other", String.class), "two names"));
    }

    @Test
    void aBridgeIsNotAMethodOfTheSource() {
        var methods = PublicVarargsOverloads.methods(Covariant.class);
        assertEquals(1, methods.size());
        assertEquals(String.class, methods.getFirst().getReturnType());
        assertEquals(List.of(), PublicVarargsOverloads.problems(List.of(Covariant.class), List.of()));
    }

    @Test
    void classesImportedTogetherAreOneSetOfCandidates() {
        var classes = List.<Class<?>>of(ImportedVarargs.class, ImportedFixed.class);

        assertEquals(List.of(), PublicVarargsOverloads.problems(classes, List.of()));
        assertEquals(1, PublicVarargsOverloads.problems(classes, List.of(classes)).size());
    }

    @Test
    void anAllowedPairIsNotReported() {
        var allowed = new Allowed(Signature.of(Strings.class, "any", String[].class),
                Signature.of(Strings.class, "any", Collection.class, String.class), "no String is a Collection");

        assertEquals(List.of(), PublicVarargsOverloads.problems(List.of(Strings.class), List.of(), allowed));
    }

    @Test
    void aPairAllowedTwiceIsReportedAsSuch() {
        var once = new Allowed(Signature.of(Strings.class, "any", String[].class),
                Signature.of(Strings.class, "any", Collection.class, String.class), "no String is a Collection");
        var again = new Allowed(Signature.of(Strings.class, "any", Collection.class, String.class),
                Signature.of(Strings.class, "any", String[].class), "reworded");

        assertEquals(List.of("allowed twice: " + again),
                PublicVarargsOverloads.problems(List.of(Strings.class), List.of(), once, again));
    }

    @Test
    void anAllowedPairThatIsGoneIsReported() {
        var allowed = new Allowed(Signature.of(Strings.class, "any", String[].class),
                Signature.of(Strings.class, "any", List.class, String.class), "no String is a List");

        var problems = PublicVarargsOverloads.problems(List.of(Strings.class), List.of(), allowed);

        assertEquals(2, problems.size());
        assertTrue(problems.get(1).startsWith("allowed, but no such pair is there any more"), problems.get(1));
    }
}
