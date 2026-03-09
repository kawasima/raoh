package net.unit8.raoh.jooq;

import net.unit8.raoh.*;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static net.unit8.raoh.jooq.JooqRecordDecoders.*;
import static net.unit8.raoh.ObjectDecoders.*;
import static org.junit.jupiter.api.Assertions.*;

class JooqDecoderTest {

    /** Build a jOOQ Record from alternating name/value pairs. */
    @SuppressWarnings("unchecked")
    private static org.jooq.Record record(Object... nameValuePairs) {
        var fields = new org.jooq.Field[nameValuePairs.length / 2];
        var values = new Object[nameValuePairs.length / 2];
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            String name = (String) nameValuePairs[i];
            Object value = nameValuePairs[i + 1];
            fields[i / 2] = DSL.field(name, value == null ? Object.class : value.getClass());
            values[i / 2] = value;
        }
        var r = DSL.using(SQLDialect.DEFAULT).newRecord(fields);
        for (int i = 0; i < values.length; i++) {
            r.set(fields[i], values[i]);
        }
        return r;
    }

    // -------------------------------------------------------------------------
    // Domain types used across tests
    // -------------------------------------------------------------------------

    record User(String name, int age, String email) {}
    record Address(String city, String zip) {}
    record UserWithAddress(User user, Address address) {}

    enum Role { ADMIN, MEMBER }

    record Employee(String name, String email, Role role) {}
    record Department(String deptName, String location) {}
    record EmployeeWithDepartment(Employee employee, Optional<Department> department) {}

    record Money(BigDecimal amount, String currency) {}
    record OrderLine(String productCode, String productName, int quantity, Money unitPrice) {}

    // -------------------------------------------------------------------------
    // Basic field decoding
    // -------------------------------------------------------------------------

    @Test
    void decodeValidRecord() {
        var rec = record("name", "Alice", "age", 30, "email", "alice@example.com");

        Decoder<org.jooq.Record, User> dec = combine(
                field("name",  string()),
                field("age",   int_()),
                field("email", string())
        ).map(User::new);

        var result = dec.decode(rec);
        assertInstanceOf(Ok.class, result);
        var user = ((Ok<User>) result).value();
        assertEquals("Alice", user.name());
        assertEquals(30, user.age());
        assertEquals("alice@example.com", user.email());
    }

    @Test
    void missingFieldReturnsError() {
        var rec = record("name", "Bob");

        var result = field("email", string()).decode(rec);
        assertInstanceOf(Err.class, result);
        var issue = ((Err<String>) result).issues().asList().get(0);
        assertEquals("/email", issue.path().toString());
        assertEquals("missing_field", issue.code());
    }

    @Test
    void nullFieldReturnsError() {
        var rec = record("name", (Object) null);

        var result = field("name", string()).decode(rec);
        assertInstanceOf(Err.class, result);
        assertEquals("required", ((Err<?>) result).issues().asList().get(0).code());
    }

    @Test
    void typeMismatchReturnsError() {
        var rec = record("age", "not-a-number");

        var result = field("age", int_()).decode(rec);
        assertInstanceOf(Err.class, result);
        assertEquals("type_mismatch", ((Err<?>) result).issues().asList().get(0).code());
    }

    // -------------------------------------------------------------------------
    // Flat JOIN result → nested domain type
    // -------------------------------------------------------------------------

    private static final JooqRecordDecoder<User> USER_DECODER = combine(
            field("name",  string()),
            field("age",   int_()),
            field("email", string())
    ).map(User::new)::decode;

    private static final JooqRecordDecoder<Address> ADDRESS_DECODER = combine(
            field("city", string()),
            field("zip",  string())
    ).map(Address::new)::decode;

    @Test
    void flatJoinResultToNestedDomainType() {
        var rec = record(
                "name",  "Alice",
                "age",   28,
                "email", "alice@example.com",
                "city",  "Tokyo",
                "zip",   "100-0001"
        );

        Decoder<org.jooq.Record, UserWithAddress> dec = combine(
                nested(USER_DECODER),
                nested(ADDRESS_DECODER)
        ).map(UserWithAddress::new);

        var result = dec.decode(rec);
        assertInstanceOf(Ok.class, result);
        var uwa = ((Ok<UserWithAddress>) result).value();
        assertEquals("Alice",    uwa.user().name());
        assertEquals("Tokyo",    uwa.address().city());
        assertEquals("100-0001", uwa.address().zip());
    }

    @Test
    void flatJoinMissingColumnAccumulatesErrors() {
        // zip column is absent — missing_field error should surface
        var rec = record(
                "name",  "Alice",
                "age",   28,
                "email", "alice@example.com",
                "city",  "Tokyo"
                // "zip" intentionally omitted
        );

        Decoder<org.jooq.Record, UserWithAddress> dec = combine(
                nested(USER_DECODER),
                nested(ADDRESS_DECODER)
        ).map(UserWithAddress::new);

        var result = dec.decode(rec);
        assertInstanceOf(Err.class, result);
        var paths = ((Err<UserWithAddress>) result).issues().asList()
                .stream().map(i -> i.path().toString()).toList();
        assertTrue(paths.contains("/zip"), "expected /zip error");
    }

    // -------------------------------------------------------------------------
    // LEFT JOIN — nullable foreign-key target → Optional<Department>
    // -------------------------------------------------------------------------

    private static final JooqRecordDecoder<Employee> EMPLOYEE_DECODER = combine(
            field("name",  string()),
            field("email", string()),
            field("role",  enumOf(Role.class))
    ).map(Employee::new)::decode;

    private static JooqRecordDecoder<Optional<Department>> optDeptDecoder() {
        return (rec, path) -> {
            var presence = optionalNullableField("dept_name", string())
                    .decode(rec, path);
            return switch (presence) {
                case Err<Presence<String>> e -> e.coerce();
                case Ok<Presence<String>>(Presence.Absent<String> _) ->
                        Result.ok(Optional.empty());
                case Ok<Presence<String>>(Presence.PresentNull<String> _) ->
                        Result.ok(Optional.empty());
                case Ok<Presence<String>>(Presence.Present<String>(var deptName)) ->
                        field("location", string())
                                .decode(rec, path)
                                .map(loc -> Optional.of(new Department(deptName, loc)));
            };
        };
    }

    @Test
    void leftJoinWithDepartmentPresent() {
        var rec = record(
                "name",      "Bob",
                "email",     "bob@example.com",
                "role",      "MEMBER",
                "dept_name", "Engineering",
                "location",  "Shibuya"
        );

        Decoder<org.jooq.Record, EmployeeWithDepartment> dec = combine(
                nested(EMPLOYEE_DECODER),
                optDeptDecoder()
        ).map(EmployeeWithDepartment::new);

        var result = dec.decode(rec);
        assertInstanceOf(Ok.class, result);
        var ewd = ((Ok<EmployeeWithDepartment>) result).value();
        assertEquals("Bob",         ewd.employee().name());
        assertEquals(Role.MEMBER,   ewd.employee().role());
        assertTrue(ewd.department().isPresent());
        assertEquals("Engineering", ewd.department().get().deptName());
        assertEquals("Shibuya",     ewd.department().get().location());
    }

    @Test
    void leftJoinWithNoDepartment() {
        var rec = record(
                "name",      "Carol",
                "email",     "carol@example.com",
                "role",      "ADMIN",
                "dept_name", (Object) null
        );

        Decoder<org.jooq.Record, EmployeeWithDepartment> dec = combine(
                nested(EMPLOYEE_DECODER),
                optDeptDecoder()
        ).map(EmployeeWithDepartment::new);

        var result = dec.decode(rec);
        assertInstanceOf(Ok.class, result);
        var ewd = ((Ok<EmployeeWithDepartment>) result).value();
        assertEquals("Carol", ewd.employee().name());
        assertTrue(ewd.department().isEmpty());
    }

    // -------------------------------------------------------------------------
    // Value object composition — Money from two columns
    // -------------------------------------------------------------------------

    private static final JooqRecordDecoder<Money> MONEY_DECODER = combine(
            field("unit_price", decimal()),
            field("currency",   string())
    ).map(Money::new)::decode;

    @Test
    void valueObjectComposedFromTwoColumns() {
        var rec = record(
                "code",       "SKU-001",
                "name",       "Raoh T-Shirt",
                "quantity",   2,
                "unit_price", new BigDecimal("2500.00"),
                "currency",   "JPY"
        );

        Decoder<org.jooq.Record, OrderLine> dec = combine(
                field("code",     string()),
                field("name",     string()),
                field("quantity", int_()),
                nested(MONEY_DECODER)
        ).map(OrderLine::new);

        var result = dec.decode(rec);
        assertInstanceOf(Ok.class, result);
        var line = ((Ok<OrderLine>) result).value();
        assertEquals("SKU-001",                 line.productCode());
        assertEquals(2,                         line.quantity());
        assertEquals(new BigDecimal("2500.00"), line.unitPrice().amount());
        assertEquals("JPY",                     line.unitPrice().currency());
    }

    // -------------------------------------------------------------------------
    // optionalField / optionalNullableField primitives
    // -------------------------------------------------------------------------

    @Test
    void optionalFieldAbsent() {
        var rec = record("name", "Carol");

        var result = JooqRecordDecoders.<String>optionalField("email", string()).decode(rec);
        assertInstanceOf(Ok.class, result);
        assertEquals(Optional.empty(), ((Ok<?>) result).value());
    }

    @Test
    void optionalFieldPresent() {
        var rec = record("email", "carol@example.com");

        var result = JooqRecordDecoders.<String>optionalField("email", string()).decode(rec);
        assertInstanceOf(Ok.class, result);
        assertEquals(Optional.of("carol@example.com"), ((Ok<?>) result).value());
    }

    @Test
    void optionalNullableFieldAbsent() {
        var rec = record("name", "Dave");

        var result = JooqRecordDecoders.<String>optionalNullableField("email", string()).decode(rec);
        assertInstanceOf(Ok.class, result);
        assertInstanceOf(Presence.Absent.class, ((Ok<?>) result).value());
    }

    @Test
    void optionalNullableFieldNull() {
        var rec = record("email", (Object) null);

        var result = JooqRecordDecoders.<String>optionalNullableField("email", string()).decode(rec);
        assertInstanceOf(Ok.class, result);
        assertInstanceOf(Presence.PresentNull.class, ((Ok<?>) result).value());
    }
}
