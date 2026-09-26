package net.unit8.raoh.audit;

import org.junit.jupiter.api.Test;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemberTest {

    @Test
    void textFormRoundTrips() {
        for (var text : List.of("java.util.List#get(int):java.lang.Object", "java.lang.Object#<init>()",
                "java.util.Locale#ROOT:java.util.Locale", "java.util.Map$Entry#getKey():java.lang.Object",
                "java.lang.String#format(java.util.Locale,java.lang.String,java.lang.Object[]):java.lang.String",
                "java.util.Collections#reverse(java.util.List):void")) {
            assertEquals(text, Member.parse(text).toString());
        }
    }

    @Test
    void keepsTheReturnTypeSoABridgeIsADifferentMember() {
        // A covariant override leaves both descriptors in the class; the JVM tells them apart.
        var real = Member.parse("fixture.C#get():java.lang.String");
        var bridge = Member.parse("fixture.C#get():java.lang.Object");
        assertNotEquals(real, bridge);
    }

    @Test
    void namesTypesByBinaryNameAndArraysWithBrackets() {
        var member = Member.method(ClassDesc.of("java.util.Map$Entry"), "m",
                MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_int.arrayType(), ConstantDescs.CD_String.arrayType(2)));
        assertEquals("java.util.Map$Entry#m(int[],java.lang.String[][]):void", member.toString());
    }

    @Test
    void aFieldCarriesItsType() {
        var field = Member.parse("java.util.Locale#ROOT:java.util.Locale");
        assertTrue(field.isField());
        assertEquals("java.util.Locale", field.type());
        assertFalse(Member.parse("java.util.Locale#getDefault():java.util.Locale").isField());
    }

    @Test
    void rejectsAMethodWithoutItsReturnType() {
        assertThrows(IllegalArgumentException.class, () -> Member.parse("java.util.List#get(int)"));
        assertThrows(IllegalArgumentException.class, () -> Member.parse("java.util.Locale#ROOT"));
    }
}
