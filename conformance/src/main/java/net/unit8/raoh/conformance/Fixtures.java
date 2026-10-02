package net.unit8.raoh.conformance;

import net.unit8.raoh.Issues;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * The fixtures of {@code catalog/fixtures.json}, written as a user of raoh-java writes the
 * functions they stand for ({@code fixtures.md}).
 *
 * <p>A fixture's types are checked against the type it is used at only as far as the function
 * needs them; a fixture used at a type it does not take gives an error.
 */
final class Fixtures {

    /** A fixture of one of the kinds {@code fixtures.md} lists. */
    sealed interface Fixture {
    }

    /**
     * A {@code map} fixture.
     *
     * @param output the output type for an input type
     * @param fn     the function
     */
    record MapFixture(UnaryOperator<SpecType> output, Function<Object, @Nullable Object> fn) implements Fixture {
    }

    /**
     * A {@code refine} fixture: the predicate and the issue it gives when it does not hold.
     *
     * @param input   the type it takes
     * @param holds   the predicate
     * @param code    the issue's code, which raoh-java also makes its message key
     * @param message the issue's message
     * @param meta    the issue's metadata for the value
     */
    record RefineFixture(SpecType input, Predicate<Object> holds, String code, String message,
                         Function<Object, Map<String, Object>> meta) implements Fixture {
    }

    /**
     * A {@code flatMap} fixture.
     *
     * @param input  the type it takes
     * @param output the type it gives
     * @param fn     the function
     */
    record FlatMapFixture(SpecType input, SpecType output, Function<Object, Result<Object>> fn) implements Fixture {
    }

    /**
     * A {@code recover} fixture.
     *
     * @param output the type it gives
     * @param fn     the function from the issues of a failure
     */
    record RecoverFixture(SpecType output, Function<Issues, Object> fn) implements Fixture {
    }

    /**
     * A {@code getter} fixture.
     *
     * @param input the type it reads from, for the type {@code P} its output is {@code nullable<P>} of
     * @param fn    the function
     */
    record GetterFixture(UnaryOperator<SpecType> input, Function<Object, @Nullable Object> fn) implements Fixture {
    }

    private static final SpecType INT32 = SpecType.Scalar.INT32;
    private static final SpecType STRING = SpecType.Scalar.STRING;

    /** The fixtures, by feature ID. */
    static final Map<String, Fixture> ALL;

    static {
        Map<String, Fixture> all = new LinkedHashMap<>();
        all.put("fixture.first", new MapFixture(
                in -> product(in, 1).elements().getFirst(),
                p -> ((List<?>) p).getFirst()));
        all.put("fixture.square_side", new MapFixture(
                in -> exactly(in, new SpecType.Product(List.of(INT32)), INT32),
                p -> square(element(p, 0))));
        all.put("fixture.square", new MapFixture(
                in -> exactly(in, new SpecType.Product(List.of(INT32, STRING)), INT32),
                p -> square(element(p, 0))));
        all.put("fixture.area", new MapFixture(
                in -> exactly(in, new SpecType.Product(List.of(INT32, INT32)), INT32),
                p -> element(p, 0) * element(p, 1)));
        all.put("fixture.shift_add_10", shiftAdd(10));
        all.put("fixture.shift_add_100", shiftAdd(100));
        all.put("fixture.shift_add_1000", shiftAdd(1000));
        all.put("fixture.decimal_string", new MapFixture(
                in -> exactly(in, INT32, STRING),
                i -> Integer.toString((Integer) i)));
        all.put("fixture.even", new RefineFixture(
                INT32,
                i -> (Integer) i % 2 == 0,
                "must_be_even",
                "must be even",
                i -> Map.of("actual", i)));
        SpecType period = new SpecType.Product(List.of(INT32, INT32));
        all.put("fixture.ordered_period", new FlatMapFixture(
                period,
                period,
                p -> element(p, 0) <= element(p, 1)
                        ? Result.ok(p)
                        : Result.failCustom(Path.of("end"), "invalid_value", "end is before start", Map.of())));
        all.put("fixture.issue_count_plus_10", new RecoverFixture(
                INT32,
                issues -> issues.asList().size() + 10));
        all.put("fixture.identity", new GetterFixture(
                SpecType.NullableOf::new,
                v -> v));
        ALL = Map.copyOf(all);
    }

    private Fixtures() {
    }

    private static MapFixture shiftAdd(int factor) {
        return new MapFixture(
                in -> exactly(in, new SpecType.Product(List.of(INT32, INT32)), INT32),
                p -> element(p, 0) * factor + element(p, 1));
    }

    private static int square(int side) {
        return side * side;
    }

    private static int element(Object product, int index) {
        return (Integer) ((List<?>) product).get(index);
    }

    private static SpecType.Product product(SpecType in, int size) {
        if (in instanceof SpecType.Product p && p.elements().size() == size) {
            return p;
        }
        throw new IllegalArgumentException("the fixture takes a product of " + size + ", not " + in);
    }

    private static SpecType exactly(SpecType in, SpecType expected, SpecType output) {
        if (!in.equals(expected)) {
            throw new IllegalArgumentException("the fixture takes " + expected + ", not " + in);
        }
        return output;
    }
}
