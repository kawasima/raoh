package net.unit8.raoh.examples.versioning.order;

import java.math.BigDecimal;

/**
 * A monetary amount with its currency code.
 *
 * @param amount   the raw amount as stored in the database
 * @param currency the ISO 4217 currency code (e.g. "JPY", "USD")
 */
public record Money(BigDecimal amount, String currency) {}
