package net.unit8.raoh.examples.versioning.order;

/**
 * A customer's name, split into first and last components.
 *
 * @param firstName the first (given) name
 * @param lastName  the last (family) name
 */
public record CustomerName(String firstName, String lastName) {}
