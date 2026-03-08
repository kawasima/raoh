package net.unit8.raoh.examples.spring.membership;

import net.unit8.raoh.*;
import net.unit8.raoh.examples.spring.SpringMessageResolver;
import net.unit8.raoh.examples.spring.membership.MembershipDecoders.CreateUserCommand;
import tools.jackson.databind.JsonNode;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Locale;
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
    private final MessageResolver resolver;

    /**
     * Creates a new controller.
     *
     * @param users         the user repository
     * @param messageSource the Spring message source for locale-aware error messages
     */
    public UserController(UserRepository users, MessageSource messageSource) {
        this.users = users;
        this.resolver = new SpringMessageResolver(messageSource);
    }

    /**
     * Creates a new user from a JSON request body.
     *
     * <p>The {@code locale} parameter is automatically injected by Spring from
     * the {@code Accept-Language} header, enabling locale-aware error messages.
     *
     * @param body   the raw JSON input
     * @param locale the client's locale from the {@code Accept-Language} header
     * @return 201 with the created user, or 400 with validation issues
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody JsonNode body, Locale locale) {
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
                    ResponseEntity.badRequest().body(errorBody(issues, resolver, locale));
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
     * @param issues   the validation issues
     * @param resolver the message resolver
     * @param locale   the target locale for error messages
     * @return a map containing both the flat and list representations
     */
    static Map<String, Object> errorBody(Issues issues, MessageResolver resolver, Locale locale) {
        // resolve() turns raw Issues into human-readable messages using the given resolver.
        // The locale is passed through to support Accept-Language–based message resolution.
        var resolved = issues.resolve(resolver, locale);
        var body = new LinkedHashMap<String, Object>();
        // toJsonList() produces a list of {path, message} objects for structured clients.
        body.put("issues", resolved.toJsonList());
        // flatten() produces a simple Map<path, message> for convenience.
        body.put("errors", resolved.flatten());
        return body;
    }
}
