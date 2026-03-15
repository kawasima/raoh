package net.unit8.raoh;

import net.unit8.raoh.combinator.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static net.unit8.raoh.ObjectDecoders.*;
import static net.unit8.raoh.map.MapDecoders.*;
import static org.junit.jupiter.api.Assertions.*;

class TupleTest {

    @Test
    void tuple2WithCombineAndPatternMatch() {
        var dec = combine(
                field("name", string()),
                field("age", int_())
        ).map(Tuple2::new);

        var result = dec.decode(Map.of("name", "Alice", "age", 30));
        switch (result) {
            case Ok(Tuple2(var name, var age)) -> {
                assertEquals("Alice", name);
                assertEquals(30, age);
            }
            case Err(var issues) -> fail("Expected Ok, got: " + issues);
        }
    }

    @Test
    void tuple3WithCombine() {
        var dec = combine(
                field("a", string()),
                field("b", int_()),
                field("c", bool())
        ).map(Tuple3::new);

        var result = dec.decode(Map.of("a", "x", "b", 1, "c", true));
        switch (result) {
            case Ok(Tuple3(var a, var b, var c)) -> {
                assertEquals("x", a);
                assertEquals(1, b);
                assertTrue(c);
            }
            case Err(var issues) -> fail("Expected Ok, got: " + issues);
        }
    }

    @Test
    void tuple4WithCombine() {
        var dec = combine(
                field("a", string()),
                field("b", string()),
                field("c", string()),
                field("d", string())
        ).map(Tuple4::new);

        var result = dec.decode(Map.of("a", "1", "b", "2", "c", "3", "d", "4"));
        switch (result) {
            case Ok(Tuple4(var a, var b, var c, var d)) -> {
                assertEquals("1", a);
                assertEquals("4", d);
            }
            case Err(var issues) -> fail("Expected Ok, got: " + issues);
        }
    }

    @Test
    void tuple5WithCombine() {
        var dec = combine(
                field("a", string()), field("b", string()), field("c", string()),
                field("d", string()), field("e", string())
        ).map(Tuple5::new);

        var result = dec.decode(Map.of("a", "1", "b", "2", "c", "3", "d", "4", "e", "5"));
        assertInstanceOf(Ok.class, result);
        var t = ((Ok<Tuple5<String, String, String, String, String>>) result).value();
        assertEquals("5", t._5());
    }

    @Test
    void tuple6WithCombine() {
        var dec = combine(
                field("a", string()), field("b", string()), field("c", string()),
                field("d", string()), field("e", string()), field("f", string())
        ).map(Tuple6::new);

        var result = dec.decode(Map.of("a", "1", "b", "2", "c", "3", "d", "4", "e", "5", "f", "6"));
        assertInstanceOf(Ok.class, result);
        var t = ((Ok<Tuple6<String, String, String, String, String, String>>) result).value();
        assertEquals("6", t._6());
    }

    @Test
    void tuple7WithCombine() {
        var dec = combine(
                field("a", string()), field("b", string()), field("c", string()),
                field("d", string()), field("e", string()), field("f", string()),
                field("g", string())
        ).map(Tuple7::new);

        var result = dec.decode(Map.of("a", "1", "b", "2", "c", "3", "d", "4", "e", "5", "f", "6", "g", "7"));
        assertInstanceOf(Ok.class, result);
        var t = ((Ok<Tuple7<String, String, String, String, String, String, String>>) result).value();
        assertEquals("7", t._7());
    }

    @Test
    void tuple8WithCombine() {
        var dec = combine(
                field("a", string()), field("b", string()), field("c", string()),
                field("d", string()), field("e", string()), field("f", string()),
                field("g", string()), field("h", string())
        ).map(Tuple8::new);

        var input = Map.<String, Object>of("a", "1", "b", "2", "c", "3", "d", "4",
                "e", "5", "f", "6", "g", "7", "h", "8");
        var result = dec.decode(input);
        assertInstanceOf(Ok.class, result);
        var t = ((Ok<Tuple8<String, String, String, String, String, String, String, String>>) result).value();
        assertEquals("8", t._8());
    }

    @Test
    void tupleErrorAccumulation() {
        var dec = combine(
                field("name", string().nonBlank()),
                field("age", int_().positive())
        ).map(Tuple2::new);

        var result = dec.decode(Map.of("name", "  ", "age", -1));
        switch (result) {
            case Ok(_) -> fail("Expected Err");
            case Err(var issues) -> assertEquals(2, issues.asList().size());
        }
    }

    @Test
    void tupleEquality() {
        var t1 = new Tuple2<>("a", 1);
        var t2 = new Tuple2<>("a", 1);
        assertEquals(t1, t2);
        assertEquals(t1.hashCode(), t2.hashCode());
    }
}
