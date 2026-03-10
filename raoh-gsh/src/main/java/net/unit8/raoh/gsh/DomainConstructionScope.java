package net.unit8.raoh.gsh;

import java.util.Set;

/**
 * Marks a guarded execution zone where domain object construction is monitored.
 *
 * <p>Within a {@code DomainConstructionScope}, any guarded domain object construction
 * is checked against the call stack. If the constructor was invoked through a Decoder's
 * {@code decode} method, construction is allowed. If not (i.e., a direct {@code new}),
 * a {@link DomainConstructionGuardException} is thrown.
 *
 * <p>Outside of a scope, no checking occurs — this makes the guard safe to leave
 * woven into bytecode even if the scope is never activated (e.g., in production).
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

    private static final StackWalker STACK_WALKER = StackWalker.getInstance(Set.of(), 16);

    /** The method name that marks a Decoder invocation on the call stack. */
    private static final String DECODE_METHOD = "decode";

    private DomainConstructionScope() {
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
     * @throws DomainConstructionGuardException if the scope is active and no {@code decode}
     *         method is found on the call stack
     */
    public static void checkActive(String className) {
        if (!ACTIVE.isBound()) {
            return;
        }
        boolean throughDecoder = STACK_WALKER.walk(frames ->
                frames.anyMatch(f -> DECODE_METHOD.equals(f.getMethodName())));
        if (!throughDecoder) {
            throw new DomainConstructionGuardException(className);
        }
    }

    /**
     * Executes the given task within a domain construction guard scope.
     *
     * <p>While inside this scope, any guarded domain object constructed without
     * a Decoder's {@code decode} method on the call stack will throw
     * {@link DomainConstructionGuardException}.
     *
     * @param task the task to execute within the guarded scope
     */
    public static void run(Runnable task) {
        ScopedValue.where(ACTIVE, Boolean.TRUE).run(task);
    }

    /**
     * Executes the given task within a domain construction guard scope, returning a result.
     *
     * <p>While inside this scope, any guarded domain object constructed without
     * a Decoder's {@code decode} method on the call stack will throw
     * {@link DomainConstructionGuardException}.
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
     * Returns whether a domain construction guard scope is currently active.
     *
     * @return {@code true} if a guard scope is active
     */
    public static boolean isActive() {
        return ACTIVE.isBound();
    }
}
