# raoh-gsh-maven-plugin

Maven plugin for weaving domain construction guards into compiled classes at build time.

This plugin uses [raoh-gsh-weaver](../raoh-gsh-weaver/) to transform `.class` files, injecting `DomainConstructionScope.checkActive()` into constructors of target domain classes.

## Requirements

- Java 25+
- Maven 3.9+

## Usage

```xml
<build>
    <plugins>
        <plugin>
            <groupId>net.unit8.raoh</groupId>
            <artifactId>raoh-gsh-maven-plugin</artifactId>
            <version>${raoh.version}</version>
            <configuration>
                <packages>com.example.domain.**,com.example.model.*</packages>
                <!-- optional -->
                <exclude>com.example.domain.internal.**</exclude>
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>weave</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

You also need `raoh-gsh` as a runtime dependency so that the woven `checkActive()` calls can resolve at test time:

```xml
<dependency>
    <groupId>net.unit8.raoh</groupId>
    <artifactId>raoh-gsh</artifactId>
    <version>${raoh.version}</version>
    <scope>test</scope>
</dependency>
```

## Goal: `weave`

Default phase: `process-test-classes`

Weaves both `target/classes` and `target/test-classes`.

### Configuration Parameters

| Parameter | Property | Description |
|-----------|----------|-------------|
| `packages` | `raoh.gsh.packages` | Comma-separated package glob patterns |
| `classes` | `raoh.gsh.classes` | Comma-separated fully qualified class names |
| `exclude` | `raoh.gsh.exclude` | Comma-separated exclusion glob patterns |
| `config` | `raoh.gsh.config` | Path to a `raoh-gsh.properties` file |
| `target` | `raoh.gsh.target` | Classes directory (default: `${project.build.outputDirectory}`) |
| `testTarget` | `raoh.gsh.testTarget` | Test classes directory (default: `${project.build.testOutputDirectory}`) |

If no explicit configuration is provided, the plugin looks for `raoh-gsh.properties` on the classpath.

## Properties File Format

```properties
guard.packages=com.example.domain.**
guard.classes=com.example.special.Money
guard.exclude=com.example.domain.internal.**
```

See [raoh-gsh-weaver README](../raoh-gsh-weaver/README.md) for glob pattern syntax, and [raoh-gsh README](../raoh-gsh/README.md) for `DomainConstructionScope` usage.
