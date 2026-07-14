package net.unit8.raoh.gsh.maven.fixture;

/**
 * A weavable domain type used only by {@code GuardWeaveMojoTest}: its compiled {@code .class}
 * file is copied into temp directories and fed to the mojo, which should inject a construction
 * guard into its constructor.
 *
 * @param value an arbitrary value so the record has a constructor to weave
 */
public record SampleDomain(String value) {}
