package net.unit8.raoh.examples.spring.membership;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data access for users, using Spring's {@link JdbcClient} and Raoh's
 * {@link net.unit8.raoh.map.MapDecoders MapDecoders} to decode JDBC rows.
 *
 * <p>Key Raoh patterns demonstrated here:
 * <ul>
 *   <li>{@code Decoder.list()} in {@link #findAll()} — decode all rows with error accumulation</li>
 *   <li>{@link net.unit8.raoh.Result#map2 Result.map2} + {@link net.unit8.raoh.Decoder#list() Decoder.list()}
 *       in {@link #findByIdWithGroups(long)} — merge results from two independent queries</li>
 * </ul>
 */
@Repository
public class UserRepository {

    private final JdbcClient jdbc;

    /**
     * Creates a new repository.
     *
     * @param jdbc the JDBC client
     */
    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts a new user row.
     *
     * @param name  the display name
     * @param email the email address
     * @return the generated user ID
     */
    public UserId insert(String name, EmailAddress email) {
        var keyHolder = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO users (name, email) VALUES (?, ?)")
                .param(name)
                .param(email.value())
                .update(keyHolder);
        return new UserId(keyHolder.getKey().longValue());
    }

    /**
     * Finds a user by ID, decoding the JDBC row via {@link MembershipDecoders#USER_ROW}.
     *
     * @param id the user ID
     * @return the decoded user, or empty if not found
     */
    public Optional<User> findById(long id) {
        List<Map<String, Object>> rows = jdbc.sql("SELECT id, name, email FROM users WHERE id = ?")
                .param(id)
                .query().listOfRows();
        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(MembershipDecoders.USER_ROW.decode(rows.getFirst()).getOrThrow());
    }

    /**
     * Returns all users, decoded using {@code Decoder.list()}.
     *
     * @return the list of all users
     */
    public List<User> findAll() {
        List<Map<String, Object>> rows = jdbc.sql("SELECT id, name, email FROM users ORDER BY id")
                .query().listOfRows();
        // Decoder.list() — decode all rows, accumulating any errors
        return MembershipDecoders.USER_ROW.list().decode(rows).getOrThrow();
    }

    /**
     * Finds a user and all their group memberships.
     *
     * <p>This method issues two independent queries (users table and memberships
     * JOIN groups) and combines the results using
     * {@link MembershipDecoders#decodeUserWithGroups}, which demonstrates
     * {@link net.unit8.raoh.Result#map2 Result.map2} and
     * {@link net.unit8.raoh.Decoder#list() Decoder.list()}.
     *
     * @param id the user ID
     * @return the user with groups, or empty if the user does not exist
     */
    public Optional<UserWithGroups> findByIdWithGroups(long id) {
        List<Map<String, Object>> userRows = jdbc.sql("SELECT id, name, email FROM users WHERE id = ?")
                .param(id)
                .query().listOfRows();
        if (userRows.isEmpty()) return Optional.empty();

        List<Map<String, Object>> groupRows = jdbc.sql("""
                SELECT g.id AS group_id, g.name AS group_name, m.role
                FROM memberships m JOIN groups g ON m.group_id = g.id
                WHERE m.user_id = ?
                ORDER BY g.name
                """)
                .param(id)
                .query().listOfRows();

        // Result.map2 — two independent decode results from different tables
        return Optional.of(
                MembershipDecoders.decodeUserWithGroups(userRows.getFirst(), groupRows).getOrThrow());
    }
}
