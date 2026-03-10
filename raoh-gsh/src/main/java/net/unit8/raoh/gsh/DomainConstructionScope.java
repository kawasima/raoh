package net.unit8.raoh.gsh;

import java.util.List;
import java.util.Set;

/**
 * Marks a guarded execution zone where domain object construction is monitored.
 *
 * <p>Within a {@code DomainConstructionScope}, any guarded domain object construction
 * is checked against the call stack. If the constructor was invoked through a method
 * whose name matches the configured decoder method (default: {@code decode}), construction
 * is allowed. If not (i.e., a direct {@code new}), a {@link DomainConstructionGuardException}
 * is thrown.
 *
 * <p>Outside of a scope, no checking occurs — this makes the guard safe to leave
 * woven into bytecode even if the scope is never activated (e.g., in production).
 *
 * <p>The stack check matches both the method name and the declaring class name.
 * By default, only methods named {@code decode} in classes whose name contains
 * {@code Decoder} (case-sensitive) are recognized. This prevents false positives
 * from unrelated {@code decode} methods (e.g., {@code java.util.Base64.Decoder}).
 *
 * <p>This class uses {@link ScopedValue} (JEP 506) and is safe for use with virtual threads.
 *
 * <p><strong>Usage example:</strong></p>
 * <pre>{@code
 * // Enable the guard for a test
 * DomainConstructionScope.run(() -> {
 *     // Decoder-based construction — OK (decode is on the stack)
 *     var result = decoder.decode(input);
 *
 *     // Direct construction — throws DomainConstructionGuardException
 *     var email = new EmailAddress("test@example.com");
 * });
 * }</pre>
 */
public final class DomainConstructionScope {

    private static final ScopedValue<Boolean> ACTIVE = ScopedValue.newInstance();
    private static final ScopedValue<List<DecoderMethodSpec>> DECODER_METHODS = ScopedValue.newInstance();

    private static final StackWalker STACK_WALKER = StackWalker.getInstance(Set.of(), 32);

    private static final List<DecoderMethodSpec> DEFAULT_SPECS = List.of(
            new DecoderMethodSpec("decode", "Decoder"));

    private DomainConstructionScope() {
    }

    /**
     * Specifies a decoder method to recognize on the call stack.
     *
     * @param methodName       the method name to match (e.g. {@code decode})
     * @param classNameContains a substring the declaring class name must contain (e.g. {@code Decoder})
     */
    public record DecoderMethodSpec(String methodName, String classNameContains) {

        /**
         * Checks whether the given stack frame matches this spec.
         *
         * @param frame the stack frame to check
         * @return {@code true} if the frame matches
         */
        boolean matches(StackWalker.StackFrame frame) {
            return methodName.equals(frame.getMethodName())
                    && frame.getClassName().contains(classNameContains);
        }
    }

    /**
     * Checks whether the current construction is occurring through a Decoder.
     *
     * <p>This method is injected into guarded domain class constructors by {@link GuardWeaver}.
     * It should not normally be called by user code.
     *
     * <p>The check is only performed when a {@code DomainConstructionScope} is active.
     * Outside of a scope, this method does nothing.
     *
     * @param className the fully qualified name of the class being constructed
     * @throws DomainConstructionGuardException if the scope is active and no decoder
     *         method is found on the call stack
     */
    public static void checkActive(String className) {
        if (!ACTIVE.isBound()) {
            return;
        }
        List<DecoderMethodSpec> specs = DECODER_METHODS.isBound()
                ? DECODER_METHODS.get()
                : DEFAULT_SPECS;
        boolean throughDecoder = STACK_WALKER.walk(frames ->
                frames.anyMatch(f -> specs.stream().anyMatch(s -> s.matches(f))));
        if (!throughDecoder) {
            throw new DomainConstructionGuardException(className);
        }
    }

    /**
     * Executes the given task within a domain construction guard scope.
     *
     * <p>While inside this scope, any guarded domain object constructed without
     * a decoder method on the call stack will throw {@link DomainConstructionGuardException}.
     *
     * <p>Uses the default decoder method spec: methods named {@code decode} in classes
     * whose name contains {@code Decoder}.
     *
     * @param task the task to execute within the guarded scope
     */
    public static void run(Runnable task) {
        ScopedValue.where(ACTIVE, Boolean.TRUE).run(task);
    }

    /**
     * Executes the given task within a domain construction guard scope,
     * with custom decoder method specifications.
     *
     * @param decoderMethods the decoder methods to recognize on the call stack
     * @param task           the task to execute within the guarded scope
     */
    public static void run(List<DecoderMethodSpec> decoderMethods, Runnable task) {
        ScopedValue.where(ACTIVE, Boolean.TRUE)
                .where(DECODER_METHODS, decoderMethods)
                .run(task);
    }

    /**
     * Executes the given task within a domain construction guard scope, returning a result.
     *
     * <p>Uses the default decoder method spec: methods named {@code decode} in classes
     * whose name contains {@code Decoder}.
     *
     * @param <T>  the result type
     * @param <X>  the exception type that may be thrown
     * @param task the task to execute within the guarded scope
     * @return the result of the task
     * @throws X if the task throws an exception
     */
    public static <T, X extends Throwable> T call(ScopedValue.CallableOp<T, X> task) throws X {
        return ScopedValue.where(ACTIVE, Boolean.TRUE).call(task);
    }

    /**
     * Executes the given task within a domain construction guard scope,
     * with custom decoder method specifications, returning a result.
     *
     * @param <T>            the result type
     * @param <X>            the exception type that may be thrown
     * @param decoderMethods the decoder methods to recognize on the call stack
     * @param task           the task to execute within the guarded scope
     * @return the result of the task
     * @throws X if the task throws an exception
     */
    public static <T, X extends Throwable> T call(List<DecoderMethodSpec> decoderMethods,
                                                   ScopedValue.CallableOp<T, X> task) throws X {
        return ScopedValue.where(ACTIVE, Boolean.TRUE)
                .where(DECODER_METHODS, decoderMethods)
                .call(task);
    }

    /**
     * Returns whether a domain construction guard scope is currently active.
     *
     * @return {@code true} if a guard scope is active
     */
    public static boolean isActive() {
        return ACTIVE.isBound();
    }
}
