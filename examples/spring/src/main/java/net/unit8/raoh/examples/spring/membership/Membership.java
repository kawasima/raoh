package net.unit8.raoh.examples.spring.membership;

/**
 * A many-to-many association between a user and a group, with a role.
 *
 * @param userId  the user's identifier
 * @param groupId the group's identifier
 * @param role    the role the user holds in the group
 */
public record Membership(UserId userId, GroupId groupId, MembershipRole role) {}
