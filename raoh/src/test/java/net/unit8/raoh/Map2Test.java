package net.unit8.raoh;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Map2Test {

    record Name(String value) {}
    record Age(int value) {}
    record Person(Name name, Age age) {}

    private static Result<Name> decodeName(String s) {
        if (s == null || s.isBlank()) return Result.fail(Path.ROOT.append("name"), "blank", "required");
        return Result.ok(new Name(s));
    }

    private static Result<Age> decodeAge(int n) {
        if (n < 0) return Result.fail(Path.ROOT.append("age"), "negative", "must be non-negative");
        return Result.ok(new Age(n));
    }

    @Test
    void bothSucceed() {
        var result = Result.map2(decodeName("Alice"), decodeAge(30), Person::new);
        assertInstanceOf(Ok.class, result);
        assertEquals(new Person(new Name("Alice"), new Age(30)), result.getOrThrow());
    }

    @Test
    void firstFails() {
        var result = Result.map2(decodeName(""), decodeAge(30), Person::new);
        assertInstanceOf(Err.class, result);
        var issues = ((Err<?>) result).issues().asList();
        assertEquals(1, issues.size());
        assertEquals("name", issues.getFirst().path().segments().getFirst());
    }

    @Test
    void secondFails() {
        var result = Result.map2(decodeName("Alice"), decodeAge(-1), Person::new);
        assertInstanceOf(Err.class, result);
        var issues = ((Err<?>) result).issues().asList();
        assertEquals(1, issues.size());
        assertEquals("age", issues.getFirst().path().segments().getFirst());
    }

    @Test
    void bothFailAccumulated() {
        var result = Result.map2(decodeName(""), decodeAge(-1), Person::new);
        assertInstanceOf(Err.class, result);
        var issues = ((Err<?>) result).issues().asList();
        assertEquals(2, issues.size());
        assertEquals("name", issues.get(0).path().segments().getFirst());
        assertEquals("age", issues.get(1).path().segments().getFirst());
    }
}
