package net.unit8.raoh;

import net.unit8.raoh.decode.builtin.BoolDecoder;
import net.unit8.raoh.decode.builtin.DecimalDecoder;
import net.unit8.raoh.decode.builtin.DoubleDecoder;
import net.unit8.raoh.decode.builtin.FloatDecoder;
import net.unit8.raoh.decode.builtin.IntDecoder;
import net.unit8.raoh.decode.builtin.ListDecoder;
import net.unit8.raoh.decode.builtin.LongDecoder;
import net.unit8.raoh.decode.builtin.RecordDecoder;
import net.unit8.raoh.decode.builtin.StringDecoder;
import net.unit8.raoh.decode.builtin.TemporalDecoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static net.unit8.raoh.decode.ObjectDecoders.bool;
import static net.unit8.raoh.decode.ObjectDecoders.date;
import static net.unit8.raoh.decode.ObjectDecoders.decimal;
import static net.unit8.raoh.decode.ObjectDecoders.double_;
import static net.unit8.raoh.decode.ObjectDecoders.float_;
import static net.unit8.raoh.decode.ObjectDecoders.int_;
import static net.unit8.raoh.decode.ObjectDecoders.list;
import static net.unit8.raoh.decode.ObjectDecoders.long_;
import static net.unit8.raoh.decode.ObjectDecoders.map;
import static net.unit8.raoh.decode.ObjectDecoders.string;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Every constraint of the known built-in decoder classes stores the message that resolving it
 * gives, with {@link MessageResolver#DEFAULT} and with the English bundle (#167).
 *
 * <p>A caller who never resolves reads the stored message, so it has to be the catalogue's. The
 * cases are one per declaring class and method, overloads together: {@code ListDecoder.minSize}
 * and {@code RecordDecoder.minSize} share a message key and are still two places a message is
 * written, which is how the map size messages came to say "entries" while the catalogue said
 * "elements". {@link #everyConstraintHasACase()} fails when a public method of one of these classes
 * has neither a case nor a reason to have none.
 */
class BuiltinConstraintMessageTest {

    private static final ResourceBundleMessageResolver BUNDLE =
            new ResourceBundleMessageResolver("net.unit8.raoh.messages");

    /** The built-in decoder classes whose constraints are checked; a new one is added here. */
    private static final List<Class<?>> KNOWN_BUILT_IN_DECODERS = List.of(
            StringDecoder.class, IntDecoder.class, LongDecoder.class, DoubleDecoder.class,
            FloatDecoder.class, DecimalDecoder.class, BoolDecoder.class, ListDecoder.class,
            RecordDecoder.class, TemporalDecoder.class);

    /**
     * Methods every decoder class has that are not constraints, and why. Only the protocol is
     * excluded by name alone; anything else is excluded class by class below.
     */
    private static final Map<String, String> PROTOCOL = Map.of(
            "decode", "the decoding protocol itself",
            "refine", "the caller supplies the check, the code and the message");

    /**
     * Methods of one class that are not constraints with a built-in message, and why. Keyed by
     * class and method, as the cases are, so a constraint of the same name added to another class
     * still needs a case.
     */
    private static final Map<String, String> NOT_A_CONSTRAINT = Map.of(
            "StringDecoder.trim", "a transformation that cannot fail",
            "StringDecoder.toLowerCase", "a transformation that cannot fail",
            "StringDecoder.toUpperCase", "a transformation that cannot fail",
            "StringDecoder.normalize", "a transformation that cannot fail",
            "ListDecoder.toSet", "a conversion that cannot fail");

    /**
     * A constraint and a decode it fails.
     *
     * @param type   the class that declares the constraint
     * @param method the constraint's name, overloads together
     * @param result a decode the constraint fails
     */
    record Case(Class<?> type, String method, Result<?> result) {
        @Override
        public String toString() {
            return type.getSimpleName() + "." + method;
        }
    }

    private static Case c(Class<?> type, String method, Result<?> result) {
        return new Case(type, method, result);
    }

    static List<Case> cases() {
        LocalDate past = LocalDate.of(2020, 1, 1);
        LocalDate future = LocalDate.of(2030, 1, 1);
        Class<?> s = StringDecoder.class;
        return List.of(
                c(s, "minLength", string().minLength(3).decode("ab", Path.ROOT)),
                c(s, "maxLength", string().maxLength(1).decode("ab", Path.ROOT)),
                c(s, "fixedLength", string().fixedLength(3).decode("ab", Path.ROOT)),
                c(s, "nonBlank", string().nonBlank().decode(" ", Path.ROOT)),
                c(s, "oneOf", string().oneOf("a", "b").decode("c", Path.ROOT)),
                c(s, "pattern", string().pattern("[0-9]+").decode("x", Path.ROOT)),
                c(s, "startsWith", string().startsWith("ab").decode("xy", Path.ROOT)),
                c(s, "endsWith", string().endsWith("yz").decode("xy", Path.ROOT)),
                c(s, "includes", string().includes("mid").decode("xy", Path.ROOT)),
                c(s, "email", string().email().decode("nope", Path.ROOT)),
                c(s, "url", string().url().decode("ftp://example.com", Path.ROOT)),
                c(s, "uri", string().uri().decode("not a uri", Path.ROOT)),
                c(s, "uuid", string().uuid().decode("nope", Path.ROOT)),
                c(s, "ulid", string().ulid().decode("nope", Path.ROOT)),
                c(s, "cuid", string().cuid().decode("!!", Path.ROOT)),
                c(s, "ip", string().ip().decode("nope", Path.ROOT)),
                c(s, "ipv4", string().ipv4().decode("nope", Path.ROOT)),
                c(s, "ipv6", string().ipv6().decode("nope", Path.ROOT)),
                c(s, "date", string().date().decode("x", Path.ROOT)),
                c(s, "time", string().time().decode("x", Path.ROOT)),
                c(s, "dateTime", string().dateTime().decode("x", Path.ROOT)),
                c(s, "offsetDateTime", string().offsetDateTime().decode("x", Path.ROOT)),
                c(s, "iso8601", string().iso8601().decode("x", Path.ROOT)),
                c(s, "toInt", string().toInt().decode("x", Path.ROOT)),
                c(s, "toLong", string().toLong().decode("x", Path.ROOT)),
                c(s, "toDecimal", string().toDecimal().decode("x", Path.ROOT)),
                c(s, "toBool", string().toBool().decode("x", Path.ROOT)),

                c(IntDecoder.class, "min", int_().min(0).decode(-1, Path.ROOT)),
                c(IntDecoder.class, "max", int_().max(0).decode(1, Path.ROOT)),
                c(IntDecoder.class, "range", int_().range(0, 1).decode(2, Path.ROOT)),
                c(IntDecoder.class, "positive", int_().positive().decode(0, Path.ROOT)),
                c(IntDecoder.class, "negative", int_().negative().decode(0, Path.ROOT)),
                c(IntDecoder.class, "nonNegative", int_().nonNegative().decode(-1, Path.ROOT)),
                c(IntDecoder.class, "nonPositive", int_().nonPositive().decode(1, Path.ROOT)),
                c(IntDecoder.class, "multipleOf", int_().multipleOf(3).decode(4, Path.ROOT)),
                c(IntDecoder.class, "oneOf", int_().oneOf(1, 2).decode(3, Path.ROOT)),

                c(LongDecoder.class, "min", long_().min(0).decode(-1L, Path.ROOT)),
                c(LongDecoder.class, "max", long_().max(0).decode(1L, Path.ROOT)),
                c(LongDecoder.class, "range", long_().range(0, 1).decode(2L, Path.ROOT)),
                c(LongDecoder.class, "positive", long_().positive().decode(0L, Path.ROOT)),
                c(LongDecoder.class, "negative", long_().negative().decode(0L, Path.ROOT)),
                c(LongDecoder.class, "nonNegative", long_().nonNegative().decode(-1L, Path.ROOT)),
                c(LongDecoder.class, "nonPositive", long_().nonPositive().decode(1L, Path.ROOT)),
                c(LongDecoder.class, "multipleOf", long_().multipleOf(3).decode(4L, Path.ROOT)),
                c(LongDecoder.class, "oneOf", long_().oneOf(1L, 2L).decode(3L, Path.ROOT)),

                c(DoubleDecoder.class, "min", double_().min(0).decode(-1.0, Path.ROOT)),
                c(DoubleDecoder.class, "max", double_().max(0).decode(1.0, Path.ROOT)),
                c(DoubleDecoder.class, "range", double_().range(0, 1).decode(2.0, Path.ROOT)),
                c(DoubleDecoder.class, "positive", double_().positive().decode(0.0, Path.ROOT)),
                c(DoubleDecoder.class, "negative", double_().negative().decode(0.0, Path.ROOT)),
                c(DoubleDecoder.class, "nonNegative", double_().nonNegative().decode(-1.0, Path.ROOT)),
                c(DoubleDecoder.class, "nonPositive", double_().nonPositive().decode(1.0, Path.ROOT)),
                c(DoubleDecoder.class, "oneOf", double_().oneOf(1.0, 2.0).decode(3.0, Path.ROOT)),

                c(FloatDecoder.class, "min", float_().min(0).decode(-1.0f, Path.ROOT)),
                c(FloatDecoder.class, "max", float_().max(0).decode(1.0f, Path.ROOT)),
                c(FloatDecoder.class, "range", float_().range(0, 1).decode(2.0f, Path.ROOT)),
                c(FloatDecoder.class, "positive", float_().positive().decode(0.0f, Path.ROOT)),
                c(FloatDecoder.class, "negative", float_().negative().decode(0.0f, Path.ROOT)),
                c(FloatDecoder.class, "nonNegative", float_().nonNegative().decode(-1.0f, Path.ROOT)),
                c(FloatDecoder.class, "nonPositive", float_().nonPositive().decode(1.0f, Path.ROOT)),
                c(FloatDecoder.class, "oneOf", float_().oneOf(1.0f, 2.0f).decode(3.0f, Path.ROOT)),

                c(DecimalDecoder.class, "min", decimal().min(BigDecimal.ZERO).decode(BigDecimal.valueOf(-1), Path.ROOT)),
                c(DecimalDecoder.class, "max", decimal().max(BigDecimal.ZERO).decode(BigDecimal.ONE, Path.ROOT)),
                c(DecimalDecoder.class, "range", decimal().range(BigDecimal.ZERO, BigDecimal.ONE).decode(BigDecimal.TEN, Path.ROOT)),
                c(DecimalDecoder.class, "positive", decimal().positive().decode(BigDecimal.ZERO, Path.ROOT)),
                c(DecimalDecoder.class, "negative", decimal().negative().decode(BigDecimal.ZERO, Path.ROOT)),
                c(DecimalDecoder.class, "nonNegative", decimal().nonNegative().decode(BigDecimal.valueOf(-1), Path.ROOT)),
                c(DecimalDecoder.class, "nonPositive", decimal().nonPositive().decode(BigDecimal.ONE, Path.ROOT)),
                c(DecimalDecoder.class, "multipleOf", decimal().multipleOf(new BigDecimal("0.5")).decode(new BigDecimal("0.3"), Path.ROOT)),
                c(DecimalDecoder.class, "scale", decimal().scale(1).decode(new BigDecimal("1.25"), Path.ROOT)),

                c(BoolDecoder.class, "isTrue", bool().isTrue().decode(false, Path.ROOT)),
                c(BoolDecoder.class, "isFalse", bool().isFalse().decode(true, Path.ROOT)),

                c(ListDecoder.class, "minSize", list(string()).minSize(2).decode(List.of("a"), Path.ROOT)),
                c(ListDecoder.class, "maxSize", list(string()).maxSize(1).decode(List.of("a", "b"), Path.ROOT)),
                c(ListDecoder.class, "fixedSize", list(string()).fixedSize(2).decode(List.of("a"), Path.ROOT)),
                c(ListDecoder.class, "nonempty", list(string()).nonempty().decode(List.of(), Path.ROOT)),
                c(ListDecoder.class, "unique", list(string()).unique().decode(List.of("a", "a"), Path.ROOT)),
                c(ListDecoder.class, "contains", list(string()).contains("z").decode(List.of("a"), Path.ROOT)),
                c(ListDecoder.class, "containsAll", list(string()).containsAll("y", "z").decode(List.of("a"), Path.ROOT)),

                c(RecordDecoder.class, "minSize", map(string()).minSize(2).decode(Map.of("a", "x"), Path.ROOT)),
                c(RecordDecoder.class, "maxSize", map(string()).maxSize(1).decode(Map.of("a", "x", "b", "y"), Path.ROOT)),
                c(RecordDecoder.class, "fixedSize", map(string()).fixedSize(2).decode(Map.of("a", "x"), Path.ROOT)),
                c(RecordDecoder.class, "nonempty", map(string()).nonempty().decode(Map.of(), Path.ROOT)),

                c(TemporalDecoder.class, "before", date().before(past).decode("2025-06-01", Path.ROOT)),
                c(TemporalDecoder.class, "after", date().after(future).decode("2025-06-01", Path.ROOT)),
                c(TemporalDecoder.class, "between", date().between(future, future).decode("2025-06-01", Path.ROOT)));
    }

    /**
     * Asserts that the stored message is what resolving gives, with {@link MessageResolver#DEFAULT}
     * and with the English bundle.
     *
     * @param testCase the constraint and a decode it fails
     */
    @ParameterizedTest
    @MethodSource("cases")
    void storedMessageIsTheCataloguesMessage(Case testCase) {
        Issue issue = assertInstanceOf(Err.class, testCase.result()).issues().asList().get(0);
        assertFalse(issue.customMessage(), "a built-in message is not custom");
        assertEquals(issue.message(), issue.resolve(MessageResolver.DEFAULT).message(), "DEFAULT");
        assertEquals(issue.message(), issue.resolve(BUNDLE, Locale.ENGLISH).message(), "English bundle");
    }

    /** Each public instance method of the known built-in decoder classes, as class.method. */
    private static Set<String> publicMethods() {
        var ids = new TreeSet<String>();
        for (Class<?> type : KNOWN_BUILT_IN_DECODERS) {
            for (Method method : type.getDeclaredMethods()) {
                int modifiers = method.getModifiers();
                if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)
                        && !method.isSynthetic() && !method.isBridge()
                        && !PROTOCOL.containsKey(method.getName())) {
                    ids.add(type.getSimpleName() + "." + method.getName());
                }
            }
        }
        return ids;
    }

    /**
     * Asserts that every public instance method of the known built-in decoder classes has a case
     * or a reason to have none, by declaring class and name.
     */
    @Test
    void everyConstraintHasACase() {
        var covered = new TreeSet<String>();
        cases().forEach(testCase -> covered.add(testCase.toString()));
        var missing = new TreeSet<>(publicMethods());
        missing.removeAll(covered);
        missing.removeAll(NOT_A_CONSTRAINT.keySet());
        assertEquals(Set.of(), missing, "constraints without a case");
    }

    /**
     * Asserts that the cases and the exclusions name methods that exist, so neither list keeps an
     * entry for a method renamed or removed.
     */
    @Test
    void casesAndExclusionsNameMethodsThatExist() {
        var named = new TreeSet<String>(NOT_A_CONSTRAINT.keySet());
        cases().forEach(testCase -> named.add(testCase.toString()));
        named.removeAll(publicMethods());
        assertEquals(Set.of(), named, "entries naming no public method");
    }
}
