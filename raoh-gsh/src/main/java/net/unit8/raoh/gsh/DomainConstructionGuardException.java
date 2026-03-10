package net.unit8.raoh.gsh;

/**
 * Thrown when a guarded domain object is constructed without going through a Decoder.
 *
 * <p>This exception indicates that within a {@link DomainConstructionScope},
 * a domain object was instantiated directly (via {@code new}) instead of
 * through an allowed decode scope.
 */
public class DomainConstructionGuardException extends RuntimeException {

    private final String className;

    /**
     * Creates a new exception for the given domain class.
     *
     * @param className the fully qualified name of the domain class that was constructed without a Decoder
     */
    public DomainConstructionGuardException(String className) {
        super(className + " was constructed outside of an allowed decode scope. "
                + "Use a Decoder to create this domain object instead of calling new directly.");
        this.className = className;
    }

    /**
     * Returns the fully qualified class name of the domain object that triggered this exception.
     *
     * @return the class name
     */
    public String getClassName() {
        return className;
    }
}
