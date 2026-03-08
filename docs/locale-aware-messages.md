# Locale-Aware Message Resolution

`Issues.resolve(MessageResolver, Locale)` lets you produce localized error messages. The locale is passed at resolution time, not baked into the decoder — so a single decoder can serve multiple locales in a web server.

Raoh ships `ResourceBundleMessageResolver` for standard `java.util.ResourceBundle`-based i18n:

```java
var resolver = new ResourceBundleMessageResolver("com.example.messages");

switch (decoder.decode(input)) {
    case Ok(var value) -> handle(value);
    case Err(var issues) -> {
        var resolved = issues.resolve(resolver, Locale.JAPANESE);
        resolved.flatten();
        // → {"/name": ["必須です"], "/age": ["0から150の範囲で入力してください"]}
    }
}
```

Message templates use named placeholders matching the issue's `meta` keys:

```properties
# messages.properties (default English)
raoh.required=is required
raoh.too_short=must be at least {min} characters
raoh.out_of_range=must be between {min} and {max}

# messages_ja.properties (Japanese)
raoh.required=必須です
raoh.too_short={min}文字以上で入力してください
raoh.out_of_range={min}から{max}の範囲で入力してください
```

Raoh includes a default English message bundle at `net.unit8.raoh.messages` covering all built-in error codes. To use it:

```java
var resolver = new ResourceBundleMessageResolver("net.unit8.raoh.messages");
```

For Spring applications, see the `SpringMessageResolver` adapter in the `examples/spring` module, which delegates to Spring's `MessageSource` and integrates with `Accept-Language` header-based locale injection.

Existing code using `resolve(MessageResolver)` (without locale) continues to work unchanged.
