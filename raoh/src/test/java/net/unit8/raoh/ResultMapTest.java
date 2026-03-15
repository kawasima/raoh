package net.unit8.raoh;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultMapTest {

    @Test
    void map3AllSuccess() {
        Result<String> ra = Result.ok("a");
        Result<Integer> rb = Result.ok(1);
        Result<Boolean> rc = Result.ok(true);

        var result = Result.map3(ra, rb, rc, (a, b, c) -> a + b + c);
        switch (result) {
            case Ok(var v) -> assertEquals("a1true", v);
            case Err(var issues) -> fail("Expected Ok, got: " + issues);
        }
    }

    @Test
    void map3AccumulatesErrors() {
        Result<String> ra = Result.fail(Path.ROOT.append("a"), "required", "is required");
        Result<Integer> rb = Result.ok(1);
        Result<Boolean> rc = Result.fail(Path.ROOT.append("c"), "required", "is required");

        var result = Result.map3(ra, rb, rc, (a, b, c) -> "should not reach");
        switch (result) {
            case Ok(_) -> fail("Expected Err");
            case Err(var issues) -> assertEquals(2, issues.asList().size());
        }
    }

    @Test
    void map3AllFail() {
        Result<String> ra = Result.fail(Path.ROOT.append("a"), "required", "is required");
        Result<Integer> rb = Result.fail(Path.ROOT.append("b"), "required", "is required");
        Result<Boolean> rc = Result.fail(Path.ROOT.append("c"), "required", "is required");

        var result = Result.map3(ra, rb, rc, (a, b, c) -> "should not reach");
        switch (result) {
            case Ok(_) -> fail("Expected Err");
            case Err(var issues) -> assertEquals(3, issues.asList().size());
        }
    }

    @Test
    void map4AllSuccess() {
        Result<String> ra = Result.ok("x");
        Result<Integer> rb = Result.ok(2);
        Result<Boolean> rc = Result.ok(false);
        Result<Double> rd = Result.ok(3.14);

        var result = Result.map4(ra, rb, rc, rd, (a, b, c, d) -> a + b + c + d);
        switch (result) {
            case Ok(var v) -> assertEquals("x2false3.14", v);
            case Err(var issues) -> fail("Expected Ok, got: " + issues);
        }
    }

    @Test
    void map4AccumulatesAllErrors() {
        Result<String> ra = Result.fail(Path.ROOT.append("a"), "required", "is required");
        Result<Integer> rb = Result.fail(Path.ROOT.append("b"), "required", "is required");
        Result<Boolean> rc = Result.fail(Path.ROOT.append("c"), "required", "is required");
        Result<Double> rd = Result.fail(Path.ROOT.append("d"), "required", "is required");

        var result = Result.map4(ra, rb, rc, rd, (a, b, c, d) -> "should not reach");
        switch (result) {
            case Ok(_) -> fail("Expected Err");
            case Err(var issues) -> assertEquals(4, issues.asList().size());
        }
    }
}
