package net.unit8.raoh.examples.versioning.order;

/**
 * The current domain model for an order.
 *
 * <p>All schema versions (V1, V2, V3) are normalized into this single record
 * by their respective decoders.
 *
 * @param id       the order identifier
 * @param customer the customer name
 * @param total    the order amount with currency
 */
public record Order(OrderId id, CustomerName customer, Money total) {}
