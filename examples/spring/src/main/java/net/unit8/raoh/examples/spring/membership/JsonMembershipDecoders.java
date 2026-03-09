package net.unit8.raoh.examples.spring.membership;

import net.unit8.raoh.Decoder;
import net.unit8.raoh.Decoders;
import net.unit8.raoh.json.JsonDecoder;
import net.unit8.raoh.json.JsonDecoders;
import tools.jackson.databind.JsonNode;

import static net.unit8.raoh.json.JsonDecoders.*;

/**
 * JSON boundary decoders for the membership domain.
 *
 * <p>Decodes HTTP request bodies ({@link JsonNode}) into command objects.
 * Use {@code import static net.unit8.raoh.examples.spring.membership.JsonMembershipDecoders.*;}
 * to bring all constants into scope.
 */
public final class JsonMembershipDecoders {

    private JsonMembershipDecoders() {}

    /**
     * Decodes a JSON request body into a user-creation command.
     * <pre>{@code { "name": "Alice", "email": "alice@example.com" } }</pre>
     */
    // combine() merges two field decoders so that validation errors from both
    // "name" and "email" are accumulated rather than short-circuiting on the first failure.
    // Each field() call scopes errors under its JSON path (e.g. "/name", "/email").
    public static final JsonDecoder<CreateUserCommand> CREATE_USER = wrapJson(
            combine(
                    // Chained constraints: trim whitespace, reject blank, enforce max length.
                    field("name", string().trim().nonBlank().maxLength(100)),
                    // trim → toLowerCase → email validates format; map wraps the raw string
                    // in the EmailAddress value object after all validations pass.
                    field("email", string().trim().toLowerCase().email()
                            .maxLength(200).map(EmailAddress::new))
            ).map(CreateUserCommand::new));

    /**
     * Decodes a JSON request body into a group-creation command.
     * <pre>{@code { "name": "Engineering", "description": "..." } }</pre>
     */
    // withDefault() makes the "description" field optional: if the field is missing from
    // the JSON, the decoder produces "" instead of failing. This is different from
    // nullable — the result is always a non-null String.
    public static final JsonDecoder<CreateGroupCommand> CREATE_GROUP = wrapJson(
            combine(
                    field("name", string().trim().nonBlank().maxLength(100)),
                    Decoders.withDefault(
                            field("description", string().maxLength(500)), "")
            ).map(CreateGroupCommand::new));

    /**
     * Decodes a JSON request body into a membership-addition command.
     * <pre>{@code { "userId": 1, "role": "ADMIN" } }</pre>
     */
    // enumOf() decodes a string into an enum constant (case-insensitive).
    // withDefault() supplies MEMBER when the "role" field is absent, making it optional.
    public static final JsonDecoder<AddMemberCommand> ADD_MEMBER = wrapJson(
            combine(
                    // long_() parses a JSON number as long; map wraps it in UserId.
                    field("userId", long_().map(UserId::new)),
                    Decoders.withDefault(
                            field("role", enumOf(MembershipRole.class)),
                            MembershipRole.MEMBER)
            ).map(AddMemberCommand::new));

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
