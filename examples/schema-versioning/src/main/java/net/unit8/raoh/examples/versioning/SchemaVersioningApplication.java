package net.unit8.raoh.examples.versioning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the schema versioning example application.
 *
 * <p>This example demonstrates how to use Raoh's
 * {@link net.unit8.raoh.decode.map.MapDecoders#discriminate(String, java.util.Map) discriminate()}
 * to decode database rows whose schema varies by a version column. Different
 * schema versions coexist in the same table and are transparently normalized
 * into a single domain model without data migration.
 */
@SpringBootApplication
public class SchemaVersioningApplication {

    /**
     * Starts the application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(SchemaVersioningApplication.class, args);
    }
}
