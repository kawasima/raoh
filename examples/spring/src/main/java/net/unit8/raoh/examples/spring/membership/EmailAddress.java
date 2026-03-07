package net.unit8.raoh.examples.spring.membership;

/**
 * A validated email address value object.
 *
 * @param value the normalized (lowercased, trimmed) email string
 */
public record EmailAddress(String value) {}
