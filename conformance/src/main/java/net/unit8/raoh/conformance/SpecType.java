package net.unit8.raoh.conformance;

import java.util.List;

/**
 * The type of a value of the specification's value model, as far as the runner needs one.
 *
 * <p>The runner does not check types; the verifier does. A type here is what a form gives once it
 * is instantiated on raoh-java's API: each binding says what its result is. It decides two things,
 * the receiver kind that names an operation's feature ({@link #kind()}) and how a value is observed
 * and materialized ({@link ValueCodec}).
 */
sealed interface SpecType {

    /**
     * The outer kind, as a feature ID writes the receiver of an operation:
     * {@code operation.<kind>.<name>}.
     *
     * @return the kind
     */
    String kind();

    /**
     * A scalar type: one with no type arguments. Each is represented in raoh-java by one Java
     * class, which this table is the only place to say.
     */
    enum Scalar implements SpecType {
        /** {@code bool}. */
        BOOL("bool", Boolean.class),
        /** {@code int32}. */
        INT32("int32", Integer.class),
        /** {@code int64}. */
        INT64("int64", Long.class),
        /** {@code float32}. */
        FLOAT32("float32", Float.class),
        /** {@code float64}. */
        FLOAT64("float64", Double.class),
        /** {@code decimal}. */
        DECIMAL("decimal", java.math.BigDecimal.class),
        /** {@code string}. */
        STRING("string", String.class),
        /** {@code uuid}. */
        UUID("uuid", java.util.UUID.class),
        /** {@code uri}. */
        URI("uri", java.net.URI.class),
        /** {@code date}. */
        DATE("date", java.time.LocalDate.class),
        /** {@code time}. */
        TIME("time", java.time.LocalTime.class),
        /** {@code datetime}. */
        DATETIME("datetime", java.time.LocalDateTime.class),
        /** {@code offset_datetime}. */
        OFFSET_DATETIME("offset_datetime", java.time.OffsetDateTime.class),
        /** {@code instant}. */
        INSTANT("instant", java.time.Instant.class);

        private final String kind;
        private final Class<?> javaType;

        Scalar(String kind, Class<?> javaType) {
            this.kind = kind;
            this.javaType = javaType;
        }

        @Override
        public String kind() {
            return kind;
        }

        /**
         * The class of raoh-java's values of this type.
         *
         * @return the class
         */
        Class<?> javaType() {
            return javaType;
        }

        /**
         * The scalar type a Java value represents, by its class.
         *
         * @param value the value
         * @return the type, or empty when the value is of no scalar type's class
         */
        static java.util.Optional<Scalar> of(Object value) {
            for (Scalar s : values()) {
                if (s.javaType.isInstance(value)) {
                    return java.util.Optional.of(s);
                }
            }
            return java.util.Optional.empty();
        }
    }

    /**
     * {@code symbol<...>}: one of the alternatives an {@code enum} lists.
     *
     * @param alternatives the alternatives, as the form lists them
     */
    record Symbol(List<String> alternatives) implements SpecType {
        /**
         * Copies the alternatives.
         *
         * @param alternatives the alternatives, as the form lists them
         */
        public Symbol {
            alternatives = List.copyOf(alternatives);
        }

        @Override
        public String kind() {
            return "symbol";
        }
    }

    /**
     * {@code list<T>}.
     *
     * @param element the element type
     */
    record ListOf(SpecType element) implements SpecType {
        @Override
        public String kind() {
            return "list";
        }
    }

    /**
     * {@code set<T>}.
     *
     * @param element the element type
     */
    record SetOf(SpecType element) implements SpecType {
        @Override
        public String kind() {
            return "set";
        }
    }

    /**
     * {@code map<T>}: a map from strings.
     *
     * @param value the value type
     */
    record MapOf(SpecType value) implements SpecType {
        @Override
        public String kind() {
            return "map";
        }
    }

    /**
     * {@code product<T1,...,Tn>}.
     *
     * @param elements the element types, in order
     */
    record Product(List<SpecType> elements) implements SpecType {
        /**
         * Copies the element types.
         *
         * @param elements the element types, in order
         */
        public Product {
            elements = List.copyOf(elements);
        }

        @Override
        public String kind() {
            return "product";
        }
    }

    /**
     * {@code optional<T>}.
     *
     * @param value the type of the value when there is one
     */
    record OptionalOf(SpecType value) implements SpecType {
        @Override
        public String kind() {
            return "optional";
        }
    }

    /**
     * {@code nullable<T>}.
     *
     * @param value the type of the value when it is not null
     */
    record NullableOf(SpecType value) implements SpecType {
        @Override
        public String kind() {
            return "nullable";
        }
    }

    /**
     * {@code presence<T>}.
     *
     * @param value the type of the value when it is present
     */
    record PresenceOf(SpecType value) implements SpecType {
        @Override
        public String kind() {
            return "presence";
        }
    }
}
