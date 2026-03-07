package net.unit8.raoh.examples.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Spring Boot example application.
 *
 * <p>This example demonstrates Raoh's decoder patterns with Spring JDBC
 * and an H2 in-memory database, using a User–Group–Membership domain.
 */
@SpringBootApplication
public class ExampleSpringApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExampleSpringApplication.class, args);
    }
}
