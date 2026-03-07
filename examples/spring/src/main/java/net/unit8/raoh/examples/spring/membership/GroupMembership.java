package net.unit8.raoh.examples.spring.membership;

/**
 * A projection of a membership that includes the group name,
 * used when listing the groups a user belongs to.
 *
 * @param groupId   the group's identifier
 * @param groupName the group's display name
 * @param role      the role the user holds in the group
 */
public record GroupMembership(GroupId groupId, String groupName, MembershipRole role) {}
