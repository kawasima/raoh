package net.unit8.raoh.gsh;

/**
 * Thrown when a guarded domain object is constructed outside of a {@link DomainConstructionScope}.
 *
 * <p>This exception indicates that a domain object annotated or configured for construction
 * guarding was instantiated without going through a Decoder.
 */
public class DomainConstructionGuardException extends RuntimeException {

    private final String className;

    /**
     * Creates a new exception for the given domain class.
     *
     * @param className the fully qualified name of the domain class that was constructed outside scope
     */
    public DomainConstructionGuardException(String className) {
        super(className + " was constructed outside of a decoder scope. "
                + "Wrap the call site in DomainConstructionScope.run(() -> ...) "
                + "or ensure your Decoder.decode() is invoked within a scope.");
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
