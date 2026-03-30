package net.unit8.raoh.examples.versioning.order;

import net.unit8.raoh.Err;
import net.unit8.raoh.Issues;
import net.unit8.raoh.MessageResolver;
import net.unit8.raoh.Ok;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * REST controller for order queries.
 *
 * <p>Demonstrates how a single endpoint serves rows from multiple schema
 * versions, all transparently decoded into the current {@link Order} model
 * by Raoh's {@code discriminate()}-based decoder.
 *
 * <p>Decode errors (e.g., a row with an unknown schema version) are handled
 * via pattern matching on the sealed {@link net.unit8.raoh.Result Result} type,
 * returning structured error responses instead of throwing exceptions.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderRepository orders;

    /**
     * Creates a new controller.
     *
     * @param orders the order repository
     */
    public OrderController(OrderRepository orders) {
        this.orders = orders;
    }

    /**
     * Lists all orders across all schema versions.
     *
     * @return 200 with the list of orders, or 500 if any row fails to decode
     */
    @GetMapping
    public ResponseEntity<?> list() {
        return switch (orders.findAll()) {
            case Ok<List<Order>>(var list) -> ResponseEntity.ok(list);
            case Err<List<Order>>(var issues) -> errorResponse(issues);
        };
    }

    /**
     * Shows a single order by ID.
     *
     * @param id the order ID
     * @return 200 with the order, 404 if not found, or 500 if decoding fails
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> show(@PathVariable long id) {
        return orders.findById(id)
                .<ResponseEntity<?>>map(result -> switch (result) {
                    case Ok<Order>(var order) -> ResponseEntity.ok(order);
                    case Err<Order>(var issues) -> errorResponse(issues);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Builds an error response from decode issues.
     * This typically indicates a data integrity problem such as an
     * unrecognized schema version in the database.
     *
     * @param issues the decode issues
     * @return 500 with resolved error messages
     */
    private static ResponseEntity<?> errorResponse(Issues issues) {
        var resolved = issues.resolve(MessageResolver.DEFAULT, Locale.ENGLISH);
        return ResponseEntity.internalServerError().body(resolved.toJsonList());
    }
}
