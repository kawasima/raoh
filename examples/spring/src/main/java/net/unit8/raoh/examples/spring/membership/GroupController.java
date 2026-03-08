package net.unit8.raoh.examples.spring.membership;

import net.unit8.raoh.*;
import net.unit8.raoh.examples.spring.SpringMessageResolver;
import net.unit8.raoh.examples.spring.membership.MembershipDecoders.AddMemberCommand;
import net.unit8.raoh.examples.spring.membership.MembershipDecoders.CreateGroupCommand;
import tools.jackson.databind.JsonNode;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

/**
 * REST controller for group and membership operations.
 *
 * <p>Group creation uses {@link MembershipDecoders#CREATE_GROUP} and member
 * addition uses {@link MembershipDecoders#ADD_MEMBER} for request body
 * decoding with full error accumulation.
 */
@RestController
@RequestMapping("/groups")
public class GroupController {

    private final GroupRepository groups;
    private final UserRepository users;
    private final MessageResolver resolver;

    /**
     * Creates a new controller.
     *
     * @param groups        the group repository
     * @param users         the user repository (for membership existence checks)
     * @param messageSource the Spring message source for locale-aware error messages
     */
    public GroupController(GroupRepository groups, UserRepository users, MessageSource messageSource) {
        this.groups = groups;
        this.users = users;
        this.resolver = new SpringMessageResolver(messageSource);
    }

    /**
     * Creates a new group from a JSON request body.
     *
     * @param body   the raw JSON input
     * @param locale the client's locale from the {@code Accept-Language} header
     * @return 201 with the created group, or 400 with validation issues
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody JsonNode body, Locale locale) {
        // Same sealed-type pattern matching as UserController.create().
        return switch (MembershipDecoders.CREATE_GROUP.decode(body)) {
            case Ok<CreateGroupCommand>(var cmd) -> {
                GroupId id = groups.insert(cmd.name(), cmd.description());
                yield ResponseEntity.status(HttpStatus.CREATED)
                        .body(groups.findById(id.value()).orElseThrow());
            }
            case Err<CreateGroupCommand>(var issues) ->
                    ResponseEntity.badRequest().body(UserController.errorBody(issues, resolver, locale));
        };
    }

    /**
     * Lists all groups.
     *
     * @return 200 with the list of groups
     */
    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(groups.findAll());
    }

    /**
     * Shows a single group by ID.
     *
     * @param id the group ID
     * @return 200 with the group, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> show(@PathVariable long id) {
        return groups.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lists all members of a group.
     *
     * @param id the group ID
     * @return 200 with the member list, or 404 if the group does not exist
     */
    @GetMapping("/{id}/members")
    public ResponseEntity<?> members(@PathVariable long id) {
        if (groups.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(groups.findMembers(id));
    }

    /**
     * Adds a user to a group with a given role.
     *
     * <p>The request body is decoded by {@link MembershipDecoders#ADD_MEMBER}.
     * If the role is omitted, it defaults to {@link MembershipRole#MEMBER}.
     *
     * @param id   the group ID
     * @param body the raw JSON input
     * @return 201 with the updated member list, or 400/404 on failure
     */
    @PostMapping("/{id}/members")
    public ResponseEntity<?> addMember(@PathVariable long id, @RequestBody JsonNode body, Locale locale) {
        return switch (MembershipDecoders.ADD_MEMBER.decode(body)) {
            case Ok<AddMemberCommand>(var cmd) -> {
                if (groups.findById(id).isEmpty()) {
                    yield ResponseEntity.notFound().build();
                }
                // Business-level validation that cannot be expressed in the decoder itself.
                // Issue.of() creates a structured error with a JSON-pointer path ("/userId"),
                // a machine-readable code ("not_found"), and a human-readable message.
                if (users.findById(cmd.userId().value()).isEmpty()) {
                    yield ResponseEntity.badRequest().body(UserController.errorBody(
                            new Issues(java.util.List.of(
                                    Issue.of(Path.ROOT.append("userId"), "not_found", "user not found"))),
                            resolver, locale));
                }
                groups.addMember(new GroupId(id), cmd.userId(), cmd.role());
                yield ResponseEntity.status(HttpStatus.CREATED)
                        .body(groups.findMembers(id));
            }
            case Err<AddMemberCommand>(var issues) ->
                    ResponseEntity.badRequest().body(UserController.errorBody(issues, resolver, locale));
        };
    }

    /**
     * Removes a user from a group.
     *
     * @param groupId the group ID
     * @param userId  the user ID to remove
     * @return 204 No Content
     */
    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<?> removeMember(@PathVariable long groupId, @PathVariable long userId) {
        groups.removeMember(new GroupId(groupId), new UserId(userId));
        return ResponseEntity.noContent().build();
    }
}
