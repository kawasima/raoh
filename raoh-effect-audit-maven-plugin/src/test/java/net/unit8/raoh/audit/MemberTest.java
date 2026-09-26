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
        for (var text : List.of("java.util.List#get(int)", "java.lang.Object#<init>()", "java.util.Locale#ROOT",
                "java.util.Map$Entry#getKey()", "java.lang.String#format(java.util.Locale,java.lang.String,java.lang.Object[])")) {
            assertEquals(text, Member.parse(text).toString());
        }
    }

    @Test
    void namesTypesByBinaryNameAndArraysWithBrackets() {
        var member = Member.method(ClassDesc.of("java.util.Map$Entry"), "m",
                MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_int.arrayType(), ConstantDescs.CD_String.arrayType(2)));
        assertEquals("java.util.Map$Entry#m(int[],java.lang.String[][])", member.toString());
    }

    @Test
    void aFieldHasNoParameterList() {
        var field = Member.parse("java.util.Locale#ROOT");
        assertTrue(field.isField());
        assertFalse(Member.parse("java.util.Locale#getDefault()").isField());
    }
}
