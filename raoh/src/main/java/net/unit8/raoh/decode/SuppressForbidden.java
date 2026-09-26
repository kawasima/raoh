package net.unit8.raoh.decode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exempts a method from the build's forbidden-API check.
 *
 * <p>The check (the {@code forbiddenapis} plugin, configured in the parent POM, with the list in
 * {@code forbidden-apis/ambient-state.txt}) rejects JDK calls whose result depends on the JVM or
 * host, such as the default locale, because a decoder's result must not. Use this only where
 * depending on that state is the method's documented job, and say why in {@link #value()}.
 *
 * <p>The plugin matches any annotation named {@code SuppressForbidden}, so each package that
 * needs one declares its own package-private copy and the library gains no public type.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE})
@interface SuppressForbidden {
    /**
     * Why the forbidden call is intended here.
     *
     * @return the reason
     */
    String value();
}
