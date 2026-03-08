package net.unit8.raoh.examples.spring.membership;

import net.unit8.raoh.*;
import net.unit8.raoh.examples.spring.membership.MembershipDecoders.CreateUserCommand;
import tools.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller for user operations.
 *
 * <p>Demonstrates Raoh's JSON decoder at the HTTP boundary:
 * the request body is decoded into a typed command using
 * {@link MembershipDecoders#CREATE_USER}, and any validation
 * errors are returned as structured issues.
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository users;

    /**
     * Creates a new controller.
     *
     * @param users the user repository
     */
    public UserController(UserRepository users) {
        this.users = users;
    }

    /**
     * Creates a new user from a JSON request body.
     *
     * @param body the raw JSON input
     * @return 201 with the created user, or 400 with validation issues
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody JsonNode body) {
        // Decode returns a sealed Result: Ok (valid command) or Err (accumulated issues).
        // Pattern matching on the sealed type ensures both cases are handled at compile time.
        return switch (MembershipDecoders.CREATE_USER.decode(body)) {
            case Ok<CreateUserCommand>(var cmd) -> {
                UserId id = users.insert(cmd.name(), cmd.email());
                yield ResponseEntity.status(HttpStatus.CREATED)
                        .body(users.findById(id.value()).orElseThrow());
            }
            // Err carries all accumulated validation Issues (e.g. both name and email errors).
            case Err<CreateUserCommand>(var issues) ->
                    ResponseEntity.badRequest().body(errorBody(issues));
        };
    }

    /**
     * Lists all users.
     *
     * @return 200 with the list of users
     */
    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(users.findAll());
    }

    /**
     * Shows a single user together with their group memberships.
     *
     * <p>The response is built by {@link MembershipDecoders#decodeUserWithGroups},
     * which uses {@link Result#map2} to combine independently decoded user and
     * group-membership data, and {@link net.unit8.raoh.Decoder#list() Decoder.list()} to decode the
     * variable-length list of memberships.
     *
     * @param id the user ID
     * @return 200 with the user and groups, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> show(@PathVariable long id) {
        return users.findByIdWithGroups(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Builds a structured error response body from Raoh issues.
     *
     * @param issues the validation issues
     * @return a map containing both the flat and list representations
     */
    static Map<String, Object> errorBody(Issues issues) {
        // resolve() turns raw Issues into human-readable messages using the given resolver.
        // MessageResolver.DEFAULT provides built-in English messages for standard constraints.
        var resolved = issues.resolve(MessageResolver.DEFAULT);
        var body = new LinkedHashMap<String, Object>();
        // toJsonList() produces a list of {path, message} objects for structured clients.
        body.put("issues", resolved.toJsonList());
        // flatten() produces a simple Map<path, message> for convenience.
        body.put("errors", resolved.flatten());
        return body;
    }
}
