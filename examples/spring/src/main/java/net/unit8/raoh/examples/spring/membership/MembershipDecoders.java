package net.unit8.raoh.examples.spring.membership;

import net.unit8.raoh.Decoder;
import net.unit8.raoh.Decoders;
import net.unit8.raoh.Result;
import net.unit8.raoh.json.JsonDecoder;
import net.unit8.raoh.json.JsonDecoders;
import net.unit8.raoh.map.MapDecoders;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Decoders for the membership domain.
 *
 * <p>This class contains two groups of decoders:
 * <ul>
 *   <li>JSON decoders — validate HTTP request bodies via {@link JsonDecoders}</li>
 *   <li>Row decoders — map {@code Map<String, Object>} JDBC rows to domain types via {@link MapDecoders}</li>
 * </ul>
 *
 * <p>The row decoders demonstrate that {@link MapDecoders} works directly with
 * Spring JDBC's {@code JdbcClient.query().listOfRows()} without any adapter module.
 */
public final class MembershipDecoders {
    private MembershipDecoders() {}

    // ── JSON decoders (HTTP boundary) ───────────────────────────────

    /**
     * Decodes a JSON request body into a user-creation command.
     * <pre>{@code { "name": "Alice", "email": "alice@example.com" } }</pre>
     */
    public static final JsonDecoder<CreateUserCommand> CREATE_USER = wrapJson(
            JsonDecoders.combine(
                    JsonDecoders.field("name", JsonDecoders.string().trim().nonBlank().maxLength(100)),
                    JsonDecoders.field("email", JsonDecoders.string().trim().toLowerCase().email()
                            .maxLength(200).map(EmailAddress::new))
            ).apply(CreateUserCommand::new));

    /**
     * Decodes a JSON request body into a group-creation command.
     * <pre>{@code { "name": "Engineering", "description": "..." } }</pre>
     */
    public static final JsonDecoder<CreateGroupCommand> CREATE_GROUP = wrapJson(
            JsonDecoders.combine(
                    JsonDecoders.field("name", JsonDecoders.string().trim().nonBlank().maxLength(100)),
                    Decoders.withDefault(
                            JsonDecoders.field("description", JsonDecoders.string().maxLength(500)), "")
            ).apply(CreateGroupCommand::new));

    /**
     * Decodes a JSON request body into a membership-addition command.
     * <pre>{@code { "userId": 1, "role": "ADMIN" } }</pre>
     */
    public static final JsonDecoder<AddMemberCommand> ADD_MEMBER = wrapJson(
            JsonDecoders.combine(
                    JsonDecoders.field("userId", JsonDecoders.long_().map(UserId::new)),
                    Decoders.withDefault(
                            JsonDecoders.field("role", JsonDecoders.enumOf(MembershipRole.class)),
                            MembershipRole.MEMBER)
            ).apply(AddMemberCommand::new));

    // ── Row decoders (JDBC boundary) ────────────────────────────────
    //
    // Spring JDBC's queryForList() returns List<Map<String, Object>>.
    // MapDecoders work directly with these rows — no adapter module needed.

    /** Decodes a JDBC row into a {@link User}. */
    public static final Decoder<Map<String, Object>, User> USER_ROW = MapDecoders.combine(
            MapDecoders.field("id", MapDecoders.long_()).map(UserId::new),
            MapDecoders.field("name", MapDecoders.string()),
            MapDecoders.field("email", MapDecoders.string()).map(EmailAddress::new)
    ).apply(User::new);

    /** Decodes a JDBC row into a {@link Group}. */
    public static final Decoder<Map<String, Object>, Group> GROUP_ROW = MapDecoders.combine(
            MapDecoders.field("id", MapDecoders.long_()).map(GroupId::new),
            MapDecoders.field("name", MapDecoders.string()),
            MapDecoders.field("description", MapDecoders.string())
    ).apply(Group::new);

    /** Decodes a JDBC join row into a {@link GroupMembership}. */
    public static final Decoder<Map<String, Object>, GroupMembership> GROUP_MEMBERSHIP_ROW =
            MapDecoders.combine(
                    MapDecoders.field("group_id", MapDecoders.long_()).map(GroupId::new),
                    MapDecoders.field("group_name", MapDecoders.string()),
                    MapDecoders.field("role", MapDecoders.string())
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
        Result<List<GroupMembership>> groupsResult = GROUP_MEMBERSHIP_ROW.list().decode(groupRows);
        return Result.map2(userResult, groupsResult, UserWithGroups::new);
    }

    // ── Command records ─────────────────────────────────────────────

    /**
     * Decoded command for creating a new user.
     *
     * @param name  the validated display name
     * @param email the validated and normalized email address
     */
    public record CreateUserCommand(String name, EmailAddress email) {}

    /**
     * Decoded command for creating a new group.
     *
     * @param name        the validated group name
     * @param description the group description (defaults to empty string)
     */
    public record CreateGroupCommand(String name, String description) {}

    /**
     * Decoded command for adding a user to a group.
     *
     * @param userId the target user's ID
     * @param role   the membership role (defaults to {@link MembershipRole#MEMBER})
     */
    public record AddMemberCommand(UserId userId, MembershipRole role) {}

    // ── Helpers ─────────────────────────────────────────────────────

    private static <T> JsonDecoder<T> wrapJson(Decoder<JsonNode, T> dec) {
        return dec::decode;
    }
}
