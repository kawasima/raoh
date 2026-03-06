package net.unit8.raoh.examples.spring.user;

import com.fasterxml.jackson.databind.JsonNode;
import net.unit8.raoh.Decoder;
import net.unit8.raoh.Issues;
import net.unit8.raoh.MessageResolver;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Err;
import net.unit8.raoh.json.JsonDecoder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static net.unit8.raoh.Decoders.withDefault;
import static net.unit8.raoh.json.JsonDecoders.*;
import static net.unit8.raoh.json.JsonDecoders.enumOf;
import static net.unit8.raoh.json.JsonDecoders.field;
import static net.unit8.raoh.json.JsonDecoders.int_;
import static net.unit8.raoh.json.JsonDecoders.list;
import static net.unit8.raoh.json.JsonDecoders.string;

@RestController
@RequestMapping("/users")
public class RegisterUserController {

    private static final Pattern POSTAL_CODE = Pattern.compile("^\\d{3}-\\d{4}$");

    record EmailAddress(String value) {}
    record Age(int value) {}
    record Address(String city, String postalCode) {}
    record RegisterUserCommand(EmailAddress email, Age age, UserRole role, Address address, List<String> tags) {
        static Result<RegisterUserCommand> parse(
                EmailAddress email,
                Age age,
                UserRole role,
                Address address,
                List<String> tags) {
            if (role == UserRole.ADMIN && age.value() < 20) {
                return Result.fail(Path.ROOT.append("role"),
                        "out_of_range",
                        "admin users must be at least 20 years old");
            }
            return Result.ok(new RegisterUserCommand(email, age, role, address, tags));
        }
    }

    record UserResponse(String id, String email, int age, String role, Address address, List<String> tags) {}

    private static JsonDecoder<EmailAddress> email() {
        Decoder<JsonNode, EmailAddress> dec = string().trim().toLowerCase().email().map(EmailAddress::new);
        return dec::decode;
    }

    private static JsonDecoder<Age> age() {
        Decoder<JsonNode, Age> dec = int_().range(0, 150).map(Age::new);
        return dec::decode;
    }

    private static JsonDecoder<Address> address() {
        Decoder<JsonNode, Address> dec = combine(
                field("city", string().trim().nonBlank().maxLength(100)),
                field("postalCode", string().pattern(POSTAL_CODE))
        ).apply(Address::new);
        return dec::decode;
    }

    private static JsonDecoder<RegisterUserCommand> command() {
        Decoder<JsonNode, RegisterUserCommand> dec = combine(
                field("email", email()),
                field("age", age()),
                field("role", withDefault(enumOf(UserRole.class), UserRole.MEMBER)),
                field("address", address()),
                field("tags", withDefault(list(string().trim().nonBlank()).maxSize(5), List.of()))
        ).flatMap(RegisterUserCommand::parse);
        return dec::decode;
    }

    @PostMapping
    public ResponseEntity<?> register(@RequestBody JsonNode body) {
        return switch (command().decode(body, Path.ROOT)) {
            case Ok<RegisterUserCommand>(var command) ->
                    ResponseEntity.status(HttpStatus.CREATED).body(new UserResponse(
                            UUID.randomUUID().toString(),
                            command.email().value(),
                            command.age().value(),
                            command.role().name(),
                            command.address(),
                            command.tags()
                    ));
            case Err<RegisterUserCommand>(var issues) ->
                    ResponseEntity.badRequest().body(errorBody(issues.resolve(MessageResolver.DEFAULT)));
        };
    }

    private static Map<String, Object> errorBody(Issues issues) {
        var body = new LinkedHashMap<String, Object>();
        body.put("issues", issues.toJsonList());
        body.put("errors", issues.flatten());
        return body;
    }
}
