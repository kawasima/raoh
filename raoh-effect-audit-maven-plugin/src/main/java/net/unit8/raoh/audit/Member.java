package net.unit8.raoh.audit;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.Objects;

/**
 * A method, constructor or field as a class file refers to it: owner, name and descriptor.
 *
 * <p>The descriptor is kept whole, return type included, because the JVM tells members apart
 * by it: a covariant override leaves a bridge {@code get()Object} next to {@code get()String} in
 * the same class. The text form the catalog and the approval files use spells it out in binary
 * names: {@code java.util.List#get(int):java.lang.Object},
 * {@code java.math.BigDecimal#<init>(java.math.BigInteger)} (a constructor returns nothing, so
 * no type follows), and {@code java.util.Locale#ROOT:java.util.Locale} for a field. Arrays end
 * in {@code []}.
 *
 * @param owner the binary name of the class the member is looked up in
 * @param name the member name, {@code <init>} for a constructor
 * @param parameters the parameter type names, or {@code null} for a field
 * @param type the return type name ({@code void} for a constructor), or the field's type
 */
public record Member(String owner, String name, List<String> parameters, String type) {

    /**
     * Validates and copies the parameter list.
     *
     * @param owner the binary name of the class the member is looked up in
     * @param name the member name
     * @param parameters the parameter type names, or {@code null} for a field
     * @param type the return type name, or the field's type
     */
    public Member {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        parameters = parameters == null ? null : List.copyOf(parameters);
    }

    /**
     * A method or constructor.
     *
     * @param owner the class the method is looked up in
     * @param name the method name
     * @param type the method descriptor
     * @return the member
     */
    public static Member method(ClassDesc owner, String name, MethodTypeDesc type) {
        return new Member(typeName(owner), name, type.parameterList().stream().map(Member::typeName).toList(),
                typeName(type.returnType()));
    }

    /**
     * A field.
     *
     * @param owner the class the field is looked up in
     * @param name the field name
     * @param type the field's type
     * @return the member
     */
    public static Member field(ClassDesc owner, String name, ClassDesc type) {
        return new Member(typeName(owner), name, null, typeName(type));
    }

    /**
     * Whether this is a field rather than a method or constructor.
     *
     * @return {@code true} for a field
     */
    public boolean isField() {
        return parameters == null;
    }

    /**
     * Whether this is a constructor.
     *
     * @return {@code true} for {@code <init>}
     */
    public boolean isConstructor() {
        return name.equals("<init>");
    }

    /**
     * Parses the text form written by {@link #toString()}.
     *
     * @param text the text form
     * @return the member
     * @throws IllegalArgumentException if the text is not a member
     */
    public static Member parse(String text) {
        int hash = text.indexOf('#');
        if (hash <= 0) {
            throw new IllegalArgumentException("not a member (owner#name...): " + text);
        }
        var owner = text.substring(0, hash);
        var rest = text.substring(hash + 1);
        int open = rest.indexOf('(');
        if (open < 0) {
            int colon = rest.indexOf(':');
            if (colon <= 0) {
                throw new IllegalArgumentException("a field needs its type (owner#name:type): " + text);
            }
            return new Member(owner, rest.substring(0, colon), null, rest.substring(colon + 1));
        }
        int close = rest.indexOf(')', open);
        if (close < 0) {
            throw new IllegalArgumentException("unclosed parameter list: " + text);
        }
        var name = rest.substring(0, open);
        var inside = rest.substring(open + 1, close);
        var params = inside.isEmpty() ? List.<String>of() : List.of(inside.split(",", -1));
        var after = rest.substring(close + 1);
        String type;
        if (name.equals("<init>")) {
            if (!after.isEmpty()) {
                throw new IllegalArgumentException("a constructor has no return type: " + text);
            }
            type = "void";
        } else if (after.startsWith(":") && after.length() > 1) {
            type = after.substring(1);
        } else {
            throw new IllegalArgumentException("a method needs its return type (owner#name(params):type): " + text);
        }
        return new Member(owner, name, params, type);
    }

    /**
     * The binary name of a type, with {@code []} for each array dimension.
     *
     * @param type the type
     * @return its name, such as {@code java.util.Map$Entry} or {@code int[]}
     */
    public static String typeName(ClassDesc type) {
        if (type.isArray()) {
            return typeName(type.componentType()) + "[]";
        }
        if (type.isPrimitive()) {
            return type.displayName();
        }
        var descriptor = type.descriptorString();
        return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
    }

    @Override
    public String toString() {
        if (parameters == null) {
            return owner + "#" + name + ":" + type;
        }
        var head = owner + "#" + name + "(" + String.join(",", parameters) + ")";
        return isConstructor() ? head : head + ":" + type;
    }
}
