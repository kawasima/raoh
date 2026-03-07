package net.unit8.raoh;

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
