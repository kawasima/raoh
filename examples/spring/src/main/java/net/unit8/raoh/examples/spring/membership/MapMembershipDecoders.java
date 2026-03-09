package net.unit8.raoh.examples.spring.membership;

import net.unit8.raoh.Decoder;
import net.unit8.raoh.Result;

import java.util.List;
import java.util.Map;

import static net.unit8.raoh.ObjectDecoders.*;
import static net.unit8.raoh.map.MapDecoders.*;

/**
 * Map boundary decoders for the membership domain.
 *
 * <p>Decodes {@code Map<String, Object>} rows — the format returned by Spring JDBC's
 * {@code JdbcClient.query().listOfRows()} — into domain objects.
 * Use {@code import static net.unit8.raoh.examples.spring.membership.MapMembershipDecoders.*;}
 * to bring all constants into scope.
 */
public final class MapMembershipDecoders {

    private MapMembershipDecoders() {}

    // MapDecoders.combine() works exactly like JsonDecoders.combine() but reads from
    // Map<String, Object> — the format returned by Spring JDBC's listOfRows().
    // This means no ORM or custom RowMapper is needed; Raoh decodes rows directly.

    /** Decodes a JDBC row into a {@link User}. */
    public static final Decoder<Map<String, Object>, User> USER_ROW = combine(
            // map() transforms the decoded long into a UserId value object.
            field("id", long_()).map(UserId::new),
            field("name", string()),
            field("email", string()).map(EmailAddress::new)
    ).apply(User::new);

    /** Decodes a JDBC row into a {@link Group}. */
    public static final Decoder<Map<String, Object>, Group> GROUP_ROW = combine(
            field("id", long_()).map(GroupId::new),
            field("name", string()),
            field("description", string())
    ).apply(Group::new);

    /** Decodes a JDBC join row into a {@link GroupMembership}. */
    public static final Decoder<Map<String, Object>, GroupMembership> GROUP_MEMBERSHIP_ROW =
            combine(
                    field("group_id", long_()).map(GroupId::new),
                    field("group_name", string()),
                    // map() after decoding applies a custom transformation; here it converts
                    // the raw string into the MembershipRole enum.
                    field("role", string())
                            .map(s -> MembershipRole.valueOf(s.toUpperCase()))
            ).apply(GroupMembership::new);

    /**
     * Decodes a user row plus a list of group-membership rows into {@link UserWithGroups},
     * demonstrating {@link Result#map2} for merging two independent decode results
     * and {@link Decoder#list()} for decoding a variable-length list.
     *
     * @param userRow   the JDBC row from the users table
     * @param groupRows the JDBC rows from the memberships + groups JOIN
     * @return a result combining user and group data, with all errors accumulated
     */
    public static Result<UserWithGroups> decodeUserWithGroups(
            Map<String, Object> userRow,
            List<Map<String, Object>> groupRows) {
        Result<User> userResult = USER_ROW.decode(userRow);
        // list() lifts a single-row decoder into a List decoder that applies the decoder
        // to every element, accumulating errors from all rows rather than failing fast.
        Result<List<GroupMembership>> groupsResult = GROUP_MEMBERSHIP_ROW.list().decode(groupRows);
        // map2 combines two independent Results: if both succeed the mapping function runs;
        // if either or both fail, all errors are merged into one Err.
        return Result.map2(userResult, groupsResult, UserWithGroups::new);
    }
}
