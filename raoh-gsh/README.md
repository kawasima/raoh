# raoh-gsh — Domain Construction Guard (Runtime)

Runtime library for the domain construction guard. Provides `DomainConstructionScope` and `DomainConstructionGuardException`.

Within a `DomainConstructionScope`, any woven domain object construction is checked against the call stack. If a `decode` method in a class implementing `net.unit8.raoh.Decoder` is on the stack, construction is allowed. If not (i.e., a direct `new`), a `DomainConstructionGuardException` is thrown. Outside of a scope, no checking occurs — so production code is never affected.

## Requirements

- Java 25+

## Setup

Add `raoh-gsh` as a test dependency:

```xml
<dependency>
    <groupId>net.unit8.raoh</groupId>
    <artifactId>raoh-gsh</artifactId>
    <version>${raoh.version}</version>
    <scope>test</scope>
</dependency>
```

To weave constructors, you also need [raoh-gsh-weaver](../raoh-gsh-weaver/) (Java Agent or CLI) or [raoh-gsh-maven-plugin](../raoh-gsh-maven-plugin/) (build-time weaving).

## Using DomainConstructionScope

Wrap your test code with `DomainConstructionScope.run()` to enable the guard:

```java
DomainConstructionScope.run(() -> {
    // Decoder-based construction — OK (decode is on the call stack)
    var result = decoder.decode(input);

    // Direct construction — throws DomainConstructionGuardException
    var email = new EmailAddress("test@example.com");
    // => DomainConstructionGuardException: EmailAddress was constructed outside of an allowed decode scope.
});

// Outside scope — no checking, construction always succeeds
var email = new EmailAddress("test@example.com"); // OK (guard inactive)
```

## Related Modules

| Module | Purpose |
|--------|---------|
| **raoh-gsh** (this) | Runtime: `DomainConstructionScope`, `DomainConstructionGuardException` |
| [raoh-gsh-weaver](../raoh-gsh-weaver/) | Bytecode weaver, Java Agent, CLI |
| [raoh-gsh-maven-plugin](../raoh-gsh-maven-plugin/) | Maven plugin for build-time weaving |
