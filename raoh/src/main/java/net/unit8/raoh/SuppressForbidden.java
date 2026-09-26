package net.unit8.raoh;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exempts a method from the build's forbidden-API check.
 *
 * <p>The check (the {@code forbiddenapis} plugin, configured in the parent POM, with the list in
 * {@code forbidden-apis/ambient-state.txt}) rejects well-known JDK calls that read ambient state,
 * because a built-in decoder does not itself read it. Use this only where depending on ambient
 * state is the method's documented job, and say why in {@link #value()}. The use must also be
 * approved in {@code effect-audit/raoh.txt}. The plugin matches any
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
