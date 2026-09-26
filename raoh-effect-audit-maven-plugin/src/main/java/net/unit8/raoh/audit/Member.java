package net.unit8.raoh.audit;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.Objects;

/**
 * A method, constructor or field outside the audited code, as a call site names it.
 *
 * <p>The text form is the one the catalog and the approval files use:
 * {@code java.util.List#get(int)}, {@code java.math.BigDecimal#<init>(java.math.BigInteger)},
 * {@code java.util.Locale#ROOT} for a field. Types are binary names, arrays end in {@code []}.
 * The return type is left out, as in Java source: overloads differ by their parameters.
 *
 * @param owner the binary name of the class the member is looked up in
 * @param name the member name, {@code <init>} for a constructor
 * @param parameters the parameter type names, or {@code null} for a field
 */
public record Member(String owner, String name, List<String> parameters) {

    /**
     * Validates and copies the parameter list.
     *
     * @param owner the binary name of the class the member is looked up in
     * @param name the member name
     * @param parameters the parameter type names, or {@code null} for a field
     */
    public Member {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        parameters = parameters == null ? null : List.copyOf(parameters);
    }

    /**
     * A method or constructor.
     *
     * @param owner the class the method is looked up in
     * @param name the method name
     * @param type the method type; its return type is ignored
     * @return the member
     */
    public static Member method(ClassDesc owner, String name, MethodTypeDesc type) {
        return new Member(typeName(owner), name, type.parameterList().stream().map(Member::typeName).toList());
    }

    /**
     * A field.
     *
     * @param owner the class the field is looked up in
     * @param name the field name
     * @return the member
     */
    public static Member field(ClassDesc owner, String name) {
        return new Member(typeName(owner), name, null);
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
     * Parses the text form written by {@link #toString()}.
     *
     * @param text the text form
     * @return the member
     * @throws IllegalArgumentException if the text is not a member
     */
    public static Member parse(String text) {
        int hash = text.indexOf('#');
        if (hash <= 0) {
            throw new IllegalArgumentException("not a member (owner#name): " + text);
        }
        var owner = text.substring(0, hash);
        var rest = text.substring(hash + 1);
        int open = rest.indexOf('(');
        if (open < 0) {
            return new Member(owner, rest, null);
        }
        if (!rest.endsWith(")")) {
            throw new IllegalArgumentException("unclosed parameter list: " + text);
        }
        var inside = rest.substring(open + 1, rest.length() - 1);
        var params = inside.isEmpty() ? List.<String>of() : List.of(inside.split(",", -1));
        return new Member(owner, rest.substring(0, open), params);
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
        return owner + "#" + name + (parameters == null ? "" : "(" + String.join(",", parameters) + ")");
    }
}
