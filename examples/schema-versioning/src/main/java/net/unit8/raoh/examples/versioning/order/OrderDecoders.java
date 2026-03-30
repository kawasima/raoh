package net.unit8.raoh.examples.versioning.order;

import net.unit8.raoh.Decoder;
import net.unit8.raoh.Decoders;

import java.math.BigDecimal;
import java.util.Map;

import static net.unit8.raoh.ObjectDecoders.*;
import static net.unit8.raoh.map.MapDecoders.*;

/**
 * Schema-versioned decoders for the orders table.
 *
 * <p>The orders table carries a {@code schema_version} column that indicates
 * which set of columns a given row uses. Each version has its own decoder
 * that normalizes the row into the current {@link Order} domain model.
 * This avoids expensive data migrations when the table schema evolves.
 *
 * <p><strong>Version history:</strong></p>
 * <ul>
 *   <li>V1 — single {@code customer_name} field, amount in JPY</li>
 *   <li>V2 — {@code customer_name} split into {@code first_name} + {@code last_name}</li>
 *   <li>V3 — explicit {@code currency} column added alongside {@code amount}</li>
 * </ul>
 *
 * <p>The top-level {@link #ORDER_ROW} decoder uses
 * {@link net.unit8.raoh.map.MapDecoders#discriminate(String, Map) discriminate()}
 * to dispatch to the correct version-specific decoder based on the
 * {@code schema_version} value.
 *
 * <p><strong>Composability:</strong> Decoders are immutable values and can be
 * shared across versions. {@link #SPLIT_NAME} and {@link #MONEY} demonstrate
 * how common building blocks are extracted and reused. In particular,
 * {@link Decoders#withDefault(Decoder, Object) withDefault()} absorbs the
 * V2-to-V3 difference (missing {@code currency} column defaults to "JPY"),
 * which allows V2 and V3 to share the same {@link #MONEY} decoder.
 */
public final class OrderDecoders {

    private OrderDecoders() {}

    // -- Shared building blocks --

    /**
     * Decodes split {@code first_name} / {@code last_name} columns into a
     * {@link CustomerName}. Shared by V2 and V3.
     */
    static final Decoder<Map<String, Object>, CustomerName> SPLIT_NAME =
            combine(
                    field("first_name", string()),
                    field("last_name", string())
            ).map(CustomerName::new);

    /**
     * Decodes {@code amount} and {@code currency} into a {@link Money}.
     * When the {@code currency} column is absent (V1/V2 rows), it defaults to "JPY"
     * via {@link Decoders#withDefault(Decoder, Object) withDefault()}.
     * This lets V2 and V3 share a single decoder for monetary values.
     */
    static final Decoder<Map<String, Object>, Money> MONEY =
            combine(
                    field("amount", long_()),
                    Decoders.withDefault(field("currency", string()), "JPY")
            ).map((a, c) -> new Money(BigDecimal.valueOf(a), c));

    // -- Version-specific decoders --

    /**
     * V1 decoder: reads {@code customer_name} as a single string and splits it
     * on the first space into first/last name. Currency is assumed to be JPY.
     */
    static final Decoder<Map<String, Object>, Order> ORDER_V1 = combine(
            field("id", long_()).map(OrderId::new),
            field("customer_name", string()).map(name -> {
                // Simplified split on the first space: "Taro Yamada" -> first="Taro", last="Yamada".
                // Real-world code would need more robust parsing for middle names,
                // multi-word family names, or cultures with different name ordering.
                var parts = name.split(" ", 2);
                return new CustomerName(parts[0], parts.length > 1 ? parts[1] : "");
            }),
            MONEY
    ).map(Order::new);

    /**
     * V2 decoder: reads separate {@code first_name} and {@code last_name} columns.
     * Currency defaults to "JPY" via the shared {@link #MONEY} decoder.
     */
    static final Decoder<Map<String, Object>, Order> ORDER_V2 = combine(
            field("id", long_()).map(OrderId::new),
            SPLIT_NAME,
            MONEY
    ).map(Order::new);

    /**
     * V3 decoder: reads {@code first_name}, {@code last_name}, {@code amount},
     * and the new {@code currency} column.
     *
     * <p>Note that V3 is structurally identical to V2 because
     * {@link #MONEY} absorbs the currency-column difference via
     * {@link Decoders#withDefault(Decoder, Object) withDefault()}.
     * They are kept as separate entries in the discriminate map to make
     * the version dispatch explicit and to allow future divergence.
     */
    static final Decoder<Map<String, Object>, Order> ORDER_V3 = combine(
            field("id", long_()).map(OrderId::new),
            SPLIT_NAME,
            MONEY
    ).map(Order::new);

    /**
     * Top-level order row decoder.
     *
     * <p>Uses {@code discriminate("schema_version", ...)} to select the
     * version-specific decoder. Rows with an unknown version will produce
     * a {@code not_allowed} error listing the supported versions.
     */
    public static final Decoder<Map<String, Object>, Order> ORDER_ROW =
            discriminate("schema_version", Map.of(
                    "1", ORDER_V1,
                    "2", ORDER_V2,
                    "3", ORDER_V3
            ));
}
