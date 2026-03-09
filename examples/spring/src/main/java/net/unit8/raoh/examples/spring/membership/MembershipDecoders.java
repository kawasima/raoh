package net.unit8.raoh.examples.spring.membership;

import net.unit8.raoh.Decoder;
import net.unit8.raoh.Decoders;
import net.unit8.raoh.ObjectDecoders;
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
    // Utility class — all members are static decoder constants and factory methods.
    private MembershipDecoders() {}

    // ── JSON decoders (HTTP boundary) ───────────────────────────────

    /**
     * Decodes a JSON request body into a user-creation command.
     * <pre>{@code { "name": "Alice", "email": "alice@example.com" } }</pre>
     */
    // combine() merges two field decoders so that validation errors from both
    // "name" and "email" are accumulated rather than short-circuiting on the first failure.
    // Each field() call scopes errors under its JSON path (e.g. "/name", "/email").
    public static final JsonDecoder<CreateUserCommand> CREATE_USER = wrapJson(
            JsonDecoders.combine(
                    // Chained constraints: trim whitespace, reject blank, enforce max length.
                    JsonDecoders.field("name", JsonDecoders.string().trim().nonBlank().maxLength(100)),
                    // trim → toLowerCase → email validates format; map wraps the raw string
                    // in the EmailAddress value object after all validations pass.
                    JsonDecoders.field("email", JsonDecoders.string().trim().toLowerCase().email()
                            .maxLength(200).map(EmailAddress::new))
            ).apply(CreateUserCommand::new));

    /**
     * Decodes a JSON request body into a group-creation command.
     * <pre>{@code { "name": "Engineering", "description": "..." } }</pre>
     */
    // withDefault() makes the "description" field optional: if the field is missing from
    // the JSON, the decoder produces "" instead of failing. This is different from
    // nullable — the result is always a non-null String.
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
    // enumOf() decodes a string into an enum constant (case-sensitive).
    // withDefault() supplies MEMBER when the "role" field is absent, making it optional.
    public static final JsonDecoder<AddMemberCommand> ADD_MEMBER = wrapJson(
            JsonDecoders.combine(
                    // long_() parses a JSON number as long; map wraps it in UserId.
                    JsonDecoders.field("userId", JsonDecoders.long_().map(UserId::new)),
                    Decoders.withDefault(
                            JsonDecoders.field("role", JsonDecoders.enumOf(MembershipRole.class)),
                            MembershipRole.MEMBER)
            ).apply(AddMemberCommand::new));

    // ── Row decoders (JDBC boundary) ────────────────────────────────
    //
    // Spring JDBC's queryForList() returns List<Map<String, Object>>.
    // MapDecoders work directly with these rows — no adapter module needed.

    // MapDecoders.combine() works exactly like JsonDecoders.combine() but reads from
    // Map<String, Object> — the format returned by Spring JDBC's listOfRows().
    // This means no ORM or custom RowMapper is needed; Raoh decodes rows directly.

    /** Decodes a JDBC row into a {@link User}. */
    public static final Decoder<Map<String, Object>, User> USER_ROW = MapDecoders.combine(
            // map() transforms the decoded long into a UserId value object.
            MapDecoders.field("id", ObjectDecoders.long_()).map(UserId::new),
            MapDecoders.field("name", ObjectDecoders.string()),
            MapDecoders.field("email", ObjectDecoders.string()).map(EmailAddress::new)
    ).apply(User::new);

    /** Decodes a JDBC row into a {@link Group}. */
    public static final Decoder<Map<String, Object>, Group> GROUP_ROW = MapDecoders.combine(
            MapDecoders.field("id", ObjectDecoders.long_()).map(GroupId::new),
            MapDecoders.field("name", ObjectDecoders.string()),
            MapDecoders.field("description", ObjectDecoders.string())
    ).apply(Group::new);

    /** Decodes a JDBC join row into a {@link GroupMembership}. */
    public static final Decoder<Map<String, Object>, GroupMembership> GROUP_MEMBERSHIP_ROW =
            MapDecoders.combine(
                    MapDecoders.field("group_id", ObjectDecoders.long_()).map(GroupId::new),
                    MapDecoders.field("group_name", ObjectDecoders.string()),
                    // map() after decoding applies a custom transformation; here it converts
                    // the raw string into the MembershipRole enum.
                    MapDecoders.field("role", ObjectDecoders.string())
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

    /**
     * Adapts a generic {@code Decoder<JsonNode, T>} into a {@link JsonDecoder}.
     *
     * <p>{@code JsonDecoder<T>} is a functional interface whose single method
     * matches {@code Decoder.decode}, so a method reference suffices.
     *
     * @param dec the decoder to wrap
     * @param <T> the decoded type
     * @return the same decoder viewed as a {@link JsonDecoder}
     */
    private static <T> JsonDecoder<T> wrapJson(Decoder<JsonNode, T> dec) {
        return dec::decode;
    }
}
