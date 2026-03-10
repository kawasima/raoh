# raoh-gsh — Domain Construction Guard

Detects accidental domain object construction without going through a Decoder, at test/CI time.

Within a `DomainConstructionScope`, any domain object construction is checked against the call stack. If a Decoder's `decode` method is on the stack, construction is allowed. If not (i.e., a direct `new`), a `DomainConstructionGuardException` is thrown. Outside of a scope, no checking occurs — so production code is never affected.

## Requirements

- Java 25+

## Three Weaving Modes

| Mode | When | Modifies .class files? | Best for |
|------|------|------------------------|----------|
| **Java Agent** | Class-load time | No | Test execution via `-javaagent` |
| **Maven Plugin** | Build time | Yes (test-classes) | CI pipeline |
| **CLI** | Any time | Yes | Gradle / other build tools |

All three modes are **test/CI only** — production code is never affected.

## Quick Start: Java Agent (Recommended)

Add `raoh-gsh` as a test dependency and pass it as a Java Agent to Surefire:

```xml
<dependency>
    <groupId>net.unit8.raoh</groupId>
    <artifactId>raoh-gsh</artifactId>
    <version>${raoh.version}</version>
    <scope>test</scope>
</dependency>
```

```xml
<plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <argLine>
            -javaagent:${settings.localRepository}/net/unit8/raoh/raoh-gsh/${raoh.version}/raoh-gsh-${raoh.version}.jar=packages=com.example.domain.**
        </argLine>
    </configuration>
</plugin>
```

Agent argument format: `key=value;key=value`

| Key | Description | Example |
|-----|-------------|---------|
| `packages` | Comma-separated package glob patterns | `com.example.domain.**,com.example.model.*` |
| `classes` | Comma-separated fully qualified class names | `com.example.Money` |
| `exclude` | Comma-separated exclusion glob patterns | `com.example.domain.dto.**` |

## Quick Start: CLI

```sh
java -jar raoh-gsh.jar --packages "com.example.domain.**" --target target/test-classes
```

Options:

```
--packages <patterns>   Comma-separated package glob patterns
--classes <names>       Comma-separated fully qualified class names
--exclude <patterns>    Comma-separated exclusion glob patterns
--config <file>         Path to raoh-gsh.properties file
--target <dir>          Target directory containing .class files (required)
```

## Configuration File

Instead of inline arguments, you can use a `raoh-gsh.properties` file:

```properties
guard.packages=com.example.domain.**,com.example.model.*
guard.classes=com.example.special.Money
guard.exclude=com.example.domain.internal.**
```

## Glob Patterns

- `*` — matches a single package segment (no dots): `com.example.*` matches `com.example.Foo` but not `com.example.sub.Bar`
- `**` — matches any number of segments (including zero): `com.example.domain.**` matches `com.example.domain.Email` and `com.example.domain.sub.UserId`

## Using DomainConstructionScope

Wrap your test code with `DomainConstructionScope.run()` to enable the guard:

```java
DomainConstructionScope.run(() -> {
    // Decoder-based construction — OK (decode is on the call stack)
    var result = decoder.decode(input);

    // Direct construction — throws DomainConstructionGuardException
    var email = new EmailAddress("test@example.com");
    // => DomainConstructionGuardException: EmailAddress was constructed without going through Decoder.decode()
});

// Outside scope — no checking, construction always succeeds
var email = new EmailAddress("test@example.com"); // OK (guard inactive)
```

## How It Works

raoh-gsh uses the Java ClassFile API (JEP 484) to inject a `DomainConstructionScope.checkActive()` call into every constructor of target classes, immediately after the super constructor invocation.

`checkActive()` uses `StackWalker` to inspect the call stack: if a `decode` method in a class implementing `net.unit8.raoh.Decoder` is found, construction is allowed; otherwise, `DomainConstructionGuardException` is thrown. This check only runs when a `DomainConstructionScope` is active (via `ScopedValue`, JEP 506).

## Performance

Weaving is test/CI only — production `.class` files are never modified (especially with the Java Agent approach). No runtime overhead in production.
