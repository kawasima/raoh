package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeErr;
import static net.unit8.raoh.decode.builtin.BuiltinTestSupport.decodeOk;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code refine} is overridden on every builtin decoder with a covariant return type, so a
 * refinement no longer has to come last in a chain. Each case below applies a builtin constraint
 * <em>after</em> the refinement — which only compiles if the refinement returned the specific
 * decoder type — and checks that both the refinement and the following constraint still fire.
 */
class CovariantRefineTest {

    @Test
    void string_() {
        StringDecoder<@Nullable Object> dec = string()
                .refine(s -> !s.startsWith("_"), "no_underscore", "must not start with _")
                .minLength(3);
        assertEquals("abc", decodeOk(dec, "abc"));
        assertEquals("no_underscore", decodeErr(dec, "_abc").code());
        assertEquals(ErrorCodes.TOO_SHORT, decodeErr(dec, "ab").code());
    }

    @Test
    void int_covariant() {
        IntDecoder<@Nullable Object> dec = int_()
                .refine(n -> n % 2 == 0, "must_be_even", "must be even")
                .max(10);
        assertEquals(4, decodeOk(dec, 4));
        assertEquals("must_be_even", decodeErr(dec, 3).code());
        assertEquals(ErrorCodes.OUT_OF_RANGE, decodeErr(dec, 12).code());
    }

    @Test
    void long_covariant() {
        LongDecoder<@Nullable Object> dec = long_()
                .refine(n -> n % 2 == 0, "must_be_even", "must be even")
                .max(10L);
        assertEquals(4L, decodeOk(dec, 4L));
        assertEquals("must_be_even", decodeErr(dec, 3L).code());
        assertEquals(ErrorCodes.OUT_OF_RANGE, decodeErr(dec, 12L).code());
    }

    @Test
    void double_covariant() {
        DoubleDecoder<@Nullable Object> dec = double_()
                .refine(n -> n > 0, "must_be_positive", "must be positive")
                .max(10.0);
        assertEquals(4.0, decodeOk(dec, 4.0));
        assertEquals("must_be_positive", decodeErr(dec, -1.0).code());
        assertEquals(ErrorCodes.OUT_OF_RANGE, decodeErr(dec, 12.0).code());
    }

    @Test
    void float_covariant() {
        FloatDecoder<@Nullable Object> dec = float_()
                .refine(n -> n > 0, "must_be_positive", "must be positive")
                .max(10.0f);
        assertEquals(4.0f, decodeOk(dec, 4.0f));
        assertEquals("must_be_positive", decodeErr(dec, -1.0f).code());
        assertEquals(ErrorCodes.OUT_OF_RANGE, decodeErr(dec, 12.0f).code());
    }

    @Test
    void decimal_covariant() {
        DecimalDecoder<@Nullable Object> dec = decimal()
                .refine(n -> n.signum() > 0, "must_be_positive", "must be positive")
                .max(new BigDecimal("10"));
        assertEquals(new BigDecimal("4"), decodeOk(dec, new BigDecimal("4")));
        assertEquals("must_be_positive", decodeErr(dec, new BigDecimal("-1")).code());
        assertEquals(ErrorCodes.OUT_OF_RANGE, decodeErr(dec, new BigDecimal("12")).code());
    }

    @Test
    void bool_covariant() {
        BoolDecoder<@Nullable Object> dec = bool()
                .refine(b -> b, "must_be_true", "must be true")
                .isTrue();
        assertEquals(true, decodeOk(dec, true));
        assertEquals("must_be_true", decodeErr(dec, false).code());

        // The refinement above rejects false before isTrue() ever runs, so a second decoder with a
        // predicate that accepts everything is what proves the following constraint still fires.
        BoolDecoder<@Nullable Object> constraintFires = bool()
                .refine(b -> true, "never_fails", "never fails")
                .isTrue();
        assertEquals(true, decodeOk(constraintFires, true));
        assertEquals(ErrorCodes.INVALID_VALUE, decodeErr(constraintFires, false).code());
    }

    @Test
    void list_covariant() {
        ListDecoder<@Nullable Object, String> dec = list(string())
                .refine(v -> v.size() % 2 == 0, "even_only", "size must be even")
                .nonempty();
        assertEquals(List.of("a", "b"), decodeOk(dec, List.of("a", "b")));
        assertEquals("even_only", decodeErr(dec, List.of("a")).code());
        assertEquals(ErrorCodes.TOO_SMALL, decodeErr(dec, List.of()).code());
    }

    @Test
    void record_covariant() {
        RecordDecoder<@Nullable Object, Integer> dec = map(int_())
                .refine(m -> m.values().stream().allMatch(v -> v > 0), "all_positive", "must all be positive")
                .minSize(1);
        assertEquals(Map.of("a", 1), decodeOk(dec, Map.of("a", 1)));
        assertEquals("all_positive", decodeErr(dec, Map.of("a", -1)).code());
        assertEquals(ErrorCodes.TOO_SMALL, decodeErr(dec, Map.of()).code());
    }

    @Test
    void temporal_covariant() {
        var epoch = LocalDate.parse("2000-01-01");
        var limit = LocalDate.parse("2030-01-01");
        TemporalDecoder<@Nullable Object, LocalDate> dec = date()
                .refine(d -> d.isAfter(epoch), "too_early", "must be after 2000-01-01")
                .before(limit);
        assertEquals(LocalDate.parse("2020-06-01"), decodeOk(dec, LocalDate.parse("2020-06-01")));
        assertEquals("too_early", decodeErr(dec, LocalDate.parse("1999-01-01")).code());
        assertEquals(ErrorCodes.OUT_OF_RANGE, decodeErr(dec, LocalDate.parse("2031-01-01")).code());
    }

    @Test
    void metaOverloadKeepsTheSpecificType() {
        StringDecoder<@Nullable Object> dec = string()
                .refine(s -> s.length() % 2 == 0, "even_length", "length must be even",
                        s -> Map.of("actual", s.length()))
                .toUpperCase();
        assertEquals("ABCD", decodeOk(dec, "abcd"));
        assertEquals(3, decodeErr(dec, "abc").meta().get("actual"));
    }

    @Test
    void onFailOverloadKeepsTheSpecificTypeAndItsFailurePath() {
        StringDecoder<@Nullable Object> dec = string()
                .refine(s -> s.length() % 2 == 0,
                        (s, path) -> Result.fail(path.append("len"), "even_length", "length must be even"))
                .toUpperCase();
        assertEquals("ABCD", decodeOk(dec, "abcd"));
        var issue = decodeErr(dec, "abc");
        assertEquals("even_length", issue.code());
        assertEquals("/len", issue.path().toJsonPointer());
    }

    @Test
    void refinementStillReportsAtTheCurrentPath() {
        var dec = string().refine(s -> false, "always_fails", "always fails");
        var result = dec.decode("x", Path.ROOT.append("a").append("b"));
        assertEquals("/a/b", switch (result) {
            case net.unit8.raoh.Err<?> err -> err.issues().asList().getFirst().path().toJsonPointer();
            default -> throw new AssertionError("expected Err but got: " + result);
        });
    }
}
