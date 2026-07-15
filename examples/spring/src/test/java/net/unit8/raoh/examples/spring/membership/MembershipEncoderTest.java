package net.unit8.raoh.examples.spring.membership;

import net.unit8.raoh.examples.spring.membership.JsonMembershipDecoders.CreateUserCommand;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit tests for the write-side encoders, demonstrating that {@link MapMembershipEncoders} and
 * {@link MapMembershipDecoders} are inverses at the flat {@code Map<String, Object>} (JDBC row)
 * boundary — the decode+encode symmetry the library is built around.
 */
class MembershipEncoderTest {

    @Test
    void newUserRowEncodesOnlyTheInsertableColumns() {
        var cmd = new CreateUserCommand("Alice", new EmailAddress("alice@example.com"));

        Map<String, Object> row = MapMembershipEncoders.NEW_USER_ROW.encode(cmd);

        assertEquals("Alice", row.get("name"));
        assertEquals("alice@example.com", row.get("email"));
        // The id is database-generated, so it must not appear in the INSERT shape.
        assertFalse(row.containsKey("id"));
    }

    @Test
    void userRowEncodesToFlatColumns() {
        var user = new User(new UserId(42L), "Alice", new EmailAddress("alice@example.com"));

        Map<String, Object> row = MapMembershipEncoders.USER_ROW.encode(user);

        // Value objects are unwrapped to their column types, matching the decoder's expectations.
        assertEquals(42L, row.get("id"));
        assertEquals("Alice", row.get("name"));
        assertEquals("alice@example.com", row.get("email"));
    }

    @Test
    void encodeThenDecodeRoundTripsToTheSameUser() {
        var user = new User(new UserId(7L), "Bob", new EmailAddress("bob@example.com"));

        // Encode the domain object to a row, then decode that row back with the mirror decoder.
        Map<String, Object> row = MapMembershipEncoders.USER_ROW.encode(user);
        User decoded = MapMembershipDecoders.USER_ROW.decode(row).getOrThrow();

        assertEquals(user, decoded);
    }
}
