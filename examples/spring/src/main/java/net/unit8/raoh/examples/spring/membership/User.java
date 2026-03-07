package net.unit8.raoh.examples.spring.membership;

/**
 * A registered user.
 *
 * @param id    the user's database identifier
 * @param name  the display name
 * @param email the user's email address
 */
public record User(UserId id, String name, EmailAddress email) {}
