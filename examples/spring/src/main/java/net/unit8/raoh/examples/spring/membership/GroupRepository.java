package net.unit8.raoh.examples.spring.membership;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data access for groups and memberships, using Spring's {@link JdbcClient}
 * and Raoh's {@link net.unit8.raoh.map.MapDecoders MapDecoders} to decode JDBC rows.
 */
@Repository
public class GroupRepository {

    private final JdbcClient jdbc;

    /**
     * Creates a new repository.
     *
     * @param jdbc the JDBC client
     */
    public GroupRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts a new group row.
     *
     * @param name        the group name
     * @param description the group description
     * @return the generated group ID
     */
    public GroupId insert(String name, String description) {
        var keyHolder = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO groups (name, description) VALUES (?, ?)")
                .param(name)
                .param(description)
                .update(keyHolder);
        return new GroupId(keyHolder.getKey().longValue());
    }

    /**
     * Finds a group by ID.
     *
     * @param id the group ID
     * @return the decoded group, or empty if not found
     */
    public Optional<Group> findById(long id) {
        List<Map<String, Object>> rows = jdbc.sql("SELECT id, name, description FROM groups WHERE id = ?")
                .param(id)
                .query().listOfRows();
        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(MembershipDecoders.GROUP_ROW.decode(rows.getFirst()).getOrThrow());
    }

    /**
     * Returns all groups, decoded using {@code Decoder.list()}.
     *
     * @return the list of all groups
     */
    public List<Group> findAll() {
        List<Map<String, Object>> rows = jdbc.sql("SELECT id, name, description FROM groups ORDER BY id")
                .query().listOfRows();
        // Decoder.list() — decode all rows with error accumulation
        return MembershipDecoders.GROUP_ROW.list().decode(rows).getOrThrow();
    }

    /**
     * Adds a user to a group with the given role (upserts on conflict).
     *
     * @param groupId the group to add the member to
     * @param userId  the user to add
     * @param role    the membership role
     */
    public void addMember(GroupId groupId, UserId userId, MembershipRole role) {
        jdbc.sql("MERGE INTO memberships (user_id, group_id, role) VALUES (?, ?, ?)")
                .param(userId.value())
                .param(groupId.value())
                .param(role.name())
                .update();
    }

    /**
     * Removes a user from a group.
     *
     * @param groupId the group
     * @param userId  the user to remove
     */
    public void removeMember(GroupId groupId, UserId userId) {
        jdbc.sql("DELETE FROM memberships WHERE user_id = ? AND group_id = ?")
                .param(userId.value())
                .param(groupId.value())
                .update();
    }

    /**
     * Returns the users that belong to a group.
     *
     * <p>Reuses the same {@link MembershipDecoders#USER_ROW} decoder that is
     * used for the users table — the join result has the same column shape.
     *
     * @param groupId the group ID
     * @return the list of member users
     */
    public List<User> findMembers(long groupId) {
        List<Map<String, Object>> rows = jdbc.sql("""
                SELECT u.id, u.name, u.email
                FROM memberships m JOIN users u ON m.user_id = u.id
                WHERE m.group_id = ?
                ORDER BY u.name
                """)
                .param(groupId)
                .query().listOfRows();
        // Reuse the same USER_ROW decoder for join results
        return MembershipDecoders.USER_ROW.list().decode(rows).getOrThrow();
    }
}
