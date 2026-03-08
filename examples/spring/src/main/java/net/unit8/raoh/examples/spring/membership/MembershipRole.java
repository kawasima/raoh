package net.unit8.raoh.examples.spring.membership;

/** The role a user holds within a group. */
public enum MembershipRole {
    /** A regular member of the group. */
    MEMBER,
    /** A group administrator with management privileges. */
    ADMIN,
    /** The group owner with full control. */
    OWNER
}
