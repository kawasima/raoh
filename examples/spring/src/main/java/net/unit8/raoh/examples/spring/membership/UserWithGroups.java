package net.unit8.raoh.examples.spring.membership;

import java.util.List;

/**
 * An aggregate view of a user together with all the groups they belong to.
 *
 * <p>Constructed by decoding rows from two separate queries (users table and
 * memberships JOIN groups) and combining them with {@link Result#map2}.
 *
 * @param user   the user record
 * @param groups the groups the user belongs to, with their roles
 */
public record UserWithGroups(User user, List<GroupMembership> groups) {}
