package net.unit8.raoh.audit;

/**
 * One use of an external member by an audited method.
 *
 * @param caller the audited method, as {@code binary.ClassName#method}; see {@link BytecodeScanner}
 *               for how lambdas and local classes are attributed
 * @param callee the external member used
 * @param virtual whether the member is chosen at run time by the receiver: an
 *                {@code invokevirtual} / {@code invokeinterface} call, a virtual method handle, or
 *                a call the JDK makes on the caller's behalf, such as the {@code toString()} of a
 *                string concatenation operand
 * @param via how the bytecode reaches the member, for messages
 */
public record Edge(String caller, Member callee, boolean virtual, Via via) {

    /** How the bytecode reaches an external member. */
    public enum Via {
        /** An invoke instruction. */
        CALL,
        /** A field instruction. */
        FIELD,
        /** A method handle constant, such as the target of a lambda or method reference. */
        METHOD_HANDLE,
        /** The bootstrap method of an {@code invokedynamic} or dynamic constant. */
        BOOTSTRAP,
        /** The {@code toString()} a string concatenation calls on a reference operand. */
        STRING_CONCAT,
        /** The {@code equals} / {@code hashCode} / {@code toString} a record's generated method calls on a component. */
        RECORD_COMPONENT
    }
}
