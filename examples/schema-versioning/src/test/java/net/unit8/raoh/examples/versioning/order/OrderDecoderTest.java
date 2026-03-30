package net.unit8.raoh.examples.versioning.order;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static net.unit8.raoh.examples.versioning.order.OrderDecoders.ORDER_ROW;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for schema-versioned order decoding.
 *
 * <p>The unit tests verify that each schema version is correctly decoded
 * into the current {@link Order} domain model. The integration tests
 * verify that the REST API serves mixed-version rows transparently.
 */
@SpringBootTest
class OrderDecoderTest {

    /**
     * Unit tests for individual version decoders via direct Map input.
     * These do not require the Spring context.
     */
    @Nested
    class DecoderUnitTests {

        @Test
        void decodeV1SingleName() {
            var row = mapOf(
                    "id", 1L,
                    "schema_version", "1",
                    "customer_name", "Taro Yamada",
                    "amount", 1500L
            );
            switch (ORDER_ROW.decode(row)) {
                case Ok<Order>(var order) -> {
                    assertEquals(new OrderId(1L), order.id());
                    assertEquals("Taro", order.customer().firstName());
                    assertEquals("Yamada", order.customer().lastName());
                    assertEquals(BigDecimal.valueOf(1500L), order.total().amount());
                    assertEquals("JPY", order.total().currency());
                }
                case Err<Order>(var issues) -> fail("Expected Ok but got: " + issues);
            }
        }

        @Test
        void decodeV1SingleWordName() {
            var row = mapOf(
                    "id", 2L,
                    "schema_version", "1",
                    "customer_name", "Madonna",
                    "amount", 999L
            );
            // When customer_name has no space, last name defaults to empty.
            switch (ORDER_ROW.decode(row)) {
                case Ok<Order>(var order) -> {
                    assertEquals("Madonna", order.customer().firstName());
                    assertEquals("", order.customer().lastName());
                }
                case Err<Order>(var issues) -> fail("Expected Ok but got: " + issues);
            }
        }

        @Test
        void decodeV2SplitName() {
            var row = mapOf(
                    "id", 3L,
                    "schema_version", "2",
                    "first_name", "Hanako",
                    "last_name", "Suzuki",
                    "amount", 3200L
            );
            switch (ORDER_ROW.decode(row)) {
                case Ok<Order>(var order) -> {
                    assertEquals(new CustomerName("Hanako", "Suzuki"), order.customer());
                    assertEquals(BigDecimal.valueOf(3200L), order.total().amount());
                    assertEquals("JPY", order.total().currency());
                }
                case Err<Order>(var issues) -> fail("Expected Ok but got: " + issues);
            }
        }

        @Test
        void decodeV3WithCurrency() {
            var row = mapOf(
                    "id", 4L,
                    "schema_version", "3",
                    "first_name", "John",
                    "last_name", "Smith",
                    "amount", 4999L,
                    "currency", "USD"
            );
            switch (ORDER_ROW.decode(row)) {
                case Ok<Order>(var order) -> {
                    assertEquals(new CustomerName("John", "Smith"), order.customer());
                    assertEquals(new Money(BigDecimal.valueOf(4999L), "USD"), order.total());
                }
                case Err<Order>(var issues) -> fail("Expected Ok but got: " + issues);
            }
        }

        @Test
        void unknownVersionReturnsError() {
            var row = mapOf(
                    "id", 5L,
                    "schema_version", "99",
                    "amount", 100L
            );
            switch (ORDER_ROW.decode(row)) {
                case Ok<Order> _ -> fail("Expected Err for unknown version");
                case Err<Order>(var issues) ->
                        assertFalse(issues.asList().isEmpty());
            }
        }

        @Test
        void missingVersionReturnsError() {
            var row = mapOf(
                    "id", 6L,
                    "amount", 100L
            );
            switch (ORDER_ROW.decode(row)) {
                case Ok<Order> _ -> fail("Expected Err for missing version");
                case Err<Order>(var issues) ->
                        assertFalse(issues.asList().isEmpty());
            }
        }

        /**
         * Helper to build a mutable map from key-value pairs.
         * Uses a mutable map because Spring JDBC's listOfRows() may include null values
         * for nullable columns, and Map.of() does not allow null values.
         */
        private Map<String, Object> mapOf(Object... kvs) {
            var map = new HashMap<String, Object>();
            for (int i = 0; i < kvs.length; i += 2) {
                map.put((String) kvs[i], kvs[i + 1]);
            }
            return map;
        }
    }

    /**
     * Integration tests verifying that the REST API decodes mixed-version
     * seed data from the H2 database. Test assertions depend on the
     * insertion order in schema.sql (row IDs are auto-incremented).
     */
    @Nested
    class IntegrationTests {

        @Autowired
        private WebApplicationContext context;

        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
            mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        }

        @Test
        void listOrdersReturnsAllVersions() throws Exception {
            // The seed data contains 5 orders: 2 x V1, 1 x V2, 2 x V3.
            mockMvc.perform(get("/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(5));
        }

        @Test
        void v1OrderIsNormalized() throws Exception {
            // First seed row: V1, "Taro Yamada", 1500 JPY
            mockMvc.perform(get("/orders/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customer.firstName").value("Taro"))
                    .andExpect(jsonPath("$.customer.lastName").value("Yamada"))
                    .andExpect(jsonPath("$.total.currency").value("JPY"));
        }

        @Test
        void v2OrderIsNormalized() throws Exception {
            // Third seed row: V2, "Hanako" "Suzuki", 3200 JPY
            mockMvc.perform(get("/orders/3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customer.firstName").value("Hanako"))
                    .andExpect(jsonPath("$.customer.lastName").value("Suzuki"))
                    .andExpect(jsonPath("$.total.currency").value("JPY"));
        }

        @Test
        void v3OrderIsNormalized() throws Exception {
            // Fourth seed row: V3, "John" "Smith", 4999 USD
            mockMvc.perform(get("/orders/4"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customer.firstName").value("John"))
                    .andExpect(jsonPath("$.customer.lastName").value("Smith"))
                    .andExpect(jsonPath("$.total.currency").value("USD"));
        }

        @Test
        void nonExistentOrderReturns404() throws Exception {
            mockMvc.perform(get("/orders/9999"))
                    .andExpect(status().isNotFound());
        }
    }
}
