package net.unit8.raoh.examples.versioning.order;

import net.unit8.raoh.Result;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static net.unit8.raoh.examples.versioning.order.OrderDecoders.ORDER_ROW;

/**
 * Data access for orders, using Spring's {@link JdbcClient} and Raoh's
 * {@link OrderDecoders#ORDER_ROW} to decode rows from different schema versions.
 *
 * <p>The repository fetches all columns from the orders table and delegates
 * version-specific decoding entirely to the Raoh decoder. This means the
 * repository does not need to know which columns exist in each version.
 *
 * <p>Methods return {@link Result} so that callers can handle decode errors
 * (e.g., an unknown schema version) explicitly via pattern matching.
 */
@Repository
public class OrderRepository {

    private final JdbcClient jdbc;

    /**
     * Creates a new repository.
     *
     * @param jdbc the JDBC client
     */
    public OrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Returns all orders, decoded from rows that may span multiple schema versions.
     *
     * @return a result containing the list of orders, or accumulated decode errors
     */
    public Result<List<Order>> findAll() {
        // SELECT * is intentional: each schema version uses a different subset of
        // columns, and the version-specific decoder reads only what it needs.
        List<Map<String, Object>> rows = jdbc.sql("SELECT * FROM orders ORDER BY id")
                .query().listOfRows();
        return ORDER_ROW.list().decode(rows);
    }

    /**
     * Finds an order by ID.
     *
     * @param id the order ID
     * @return empty if the row does not exist, otherwise a result with the decoded order
     */
    public Optional<Result<Order>> findById(long id) {
        List<Map<String, Object>> rows = jdbc.sql("SELECT * FROM orders WHERE id = ?")
                .param(id)
                .query().listOfRows();
        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(ORDER_ROW.decode(rows.getFirst()));
    }
}
