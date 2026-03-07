package net.unit8.raoh.examples.spring.membership;

/**
 * Typed wrapper for a user's database identifier.
 *
 * @param value the underlying numeric ID
 */
public record UserId(long value) {}
