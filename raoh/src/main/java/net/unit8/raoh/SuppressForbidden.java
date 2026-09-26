package net.unit8.raoh;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exempts a method from the build's forbidden-API check.
 *
 * <p>The check (the {@code forbiddenapis} plugin, configured in the parent POM, with the list in
 * {@code forbidden-apis/default-locale.txt}) rejects JDK calls that read the JVM default locale,
 * because a decoder's result must not depend on it. Use this only where depending on the default
 * locale is the method's documented job, and say why in {@link #value()}. The plugin matches any
 * annotation named {@code SuppressForbidden}, so the library needs no dependency on it.
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
