package net.unit8.raoh.examples.spring.membership;

/**
 * A named group that users can belong to.
 *
 * @param id          the group's database identifier
 * @param name        the group name (unique)
 * @param description a human-readable description
 */
public record Group(GroupId id, String name, String description) {}
