package net.unit8.raoh.decode.combinator;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TraverseTest {

    /** Parses a String as an int, failing with a descriptive issue on parse error. */
    private static final Decoder<String, Integer> INT_DECODER = (in, path) -> {
        try {
            return Result.ok(Integer.parseInt(in));
        } catch (NumberFormatException e) {
            return Result.fail(path, "invalid_int", "not a valid integer");
        }
    };

    @Test
    void allSucceed() {
        var result = Result.traverse(List.of("1", "2", "3"), INT_DECODER::decode);
        assertInstanceOf(Ok.class, result);
        assertEquals(List.of(1, 2, 3), result.getOrThrow());
    }

    @Test
    void emptyList() {
        var result = Result.<String, Integer>traverse(List.of(), INT_DECODER::decode);
        assertInstanceOf(Ok.class, result);
        assertEquals(List.of(), result.getOrThrow());
    }

    @Test
    void singleFailure() {
        var result = Result.traverse(List.of("bad"), INT_DECODER::decode);
        assertInstanceOf(Err.class, result);
        var issues = ((Err<?>) result).issues().asList();
        assertEquals(1, issues.size());
        assertEquals(Path.ROOT.append("0"), issues.getFirst().path());
    }

    @Test
    void multipleFailuresAccumulated() {
        var result = Result.traverse(List.of("1", "bad", "3", "nope"), INT_DECODER::decode);
        assertInstanceOf(Err.class, result);
        var issues = ((Err<?>) result).issues().asList();
        // Both element 1 and element 3 failed — all errors must be present
        assertEquals(2, issues.size());
        assertEquals(Path.ROOT.append("1"), issues.get(0).path());
        assertEquals(Path.ROOT.append("3"), issues.get(1).path());
    }

    @Test
    void explicitBasePath() {
        var base = Path.ROOT.append("items");
        var result = Result.traverse(List.of("bad"), INT_DECODER::decode, base);
        assertInstanceOf(Err.class, result);
        var issue = ((Err<?>) result).issues().asList().getFirst();
        assertEquals(base.append("0"), issue.path());
    }

    @Test
    void traverseResultsAllSucceed() {
        var result = Result.traverseResults(
                List.of("1", "2", "3"),
                s -> {
                    try {
                        return Result.ok(Integer.parseInt(s));
                    } catch (NumberFormatException e) {
                        return Result.fail("invalid_int", "not a valid integer");
                    }
                });
        assertInstanceOf(Ok.class, result);
        assertEquals(List.of(1, 2, 3), result.getOrThrow());
    }

    @Test
    void traverseResultsAccumulatesErrors() {
        var result = Result.traverseResults(
                List.of("1", "bad", "nope"),
                s -> {
                    try {
                        return Result.ok(Integer.parseInt(s));
                    } catch (NumberFormatException e) {
                        return Result.fail("invalid_int", "not a valid integer");
                    }
                });
        assertInstanceOf(Err.class, result);
        var issues = ((Err<?>) result).issues().asList();
        assertEquals(2, issues.size());
    }

    /**
     * A decoder over a supertype must apply to a list of a subtype without any copy or cast.
     * {@code Result.traverse}'s element type {@code I} is inferred from the arguments, so a
     * {@code List<String>} decodes fine with a {@code Decoder<CharSequence, ...>}. This mirrors
     * the raoh-jooq idiom {@code Result.traverse(query.fetch(), dec::decode)} where
     * {@code query.fetch()} is an {@code org.jooq.Result<SomeRecordSubtype>} (a {@code List} of a
     * subtype of {@code org.jooq.Record}) and {@code dec} is a {@code Decoder<org.jooq.Record, T>}.
     * If this stops compiling, the jOOQ list-decode idiom has regressed.
     */
    @Test
    void traverseAcceptsSubtypeElementList() {
        Decoder<CharSequence, Integer> lengthDecoder = (in, path) -> Result.ok(in.length());
        List<String> subtypeList = List.of("a", "bb", "ccc");

        var result = Result.traverse(subtypeList, lengthDecoder::decode);
        assertInstanceOf(Ok.class, result);
        assertEquals(List.of(1, 2, 3), result.getOrThrow());
    }

    @Test
    void decoderListConvenience() {
        Decoder<List<String>, List<Integer>> listDecoder = INT_DECODER.list();

        var ok = listDecoder.decode(List.of("10", "20"));
        assertInstanceOf(Ok.class, ok);
        assertEquals(List.of(10, 20), ok.getOrThrow());

        var err = listDecoder.decode(List.of("10", "oops"));
        assertInstanceOf(Err.class, err);
        var issues = ((Err<?>) err).issues().asList();
        assertEquals(1, issues.size());
        assertEquals(Path.ROOT.append("1"), issues.getFirst().path());
    }
}
