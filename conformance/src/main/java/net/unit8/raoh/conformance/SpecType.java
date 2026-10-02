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

    /** A scalar type: one with no type arguments. */
    enum Scalar implements SpecType {
        /** {@code bool}. */
        BOOL("bool"),
        /** {@code int32}. */
        INT32("int32"),
        /** {@code int64}. */
        INT64("int64"),
        /** {@code float32}. */
        FLOAT32("float32"),
        /** {@code float64}. */
        FLOAT64("float64"),
        /** {@code decimal}. */
        DECIMAL("decimal"),
        /** {@code string}. */
        STRING("string"),
        /** {@code uuid}. */
        UUID("uuid"),
        /** {@code uri}. */
        URI("uri"),
        /** {@code date}. */
        DATE("date"),
        /** {@code time}. */
        TIME("time"),
        /** {@code datetime}. */
        DATETIME("datetime"),
        /** {@code offset_datetime}. */
        OFFSET_DATETIME("offset_datetime"),
        /** {@code instant}. */
        INSTANT("instant");

        private final String kind;

        Scalar(String kind) {
            this.kind = kind;
        }

        @Override
        public String kind() {
            return kind;
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
