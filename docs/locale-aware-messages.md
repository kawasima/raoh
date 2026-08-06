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

## When one code covers several constraints

`out_of_range` is reported by `min()`, `max()`, `range()`, `positive()`, `before()` and half a dozen others. They do not carry the same metadata — `min()` supplies only `min`, `before()` supplies only `before` — so no single template describes all of them. A template written for `range()` applied to a `min()` constraint would name an upper bound that does not exist.

Each constraint therefore carries a message key alongside its code, available as `Issue.messageKey()`:

```properties
raoh.out_of_range.minimum=must be at least {min}
raoh.out_of_range.maximum=must be at most {max}
raoh.out_of_range.range=must be between {min} and {max}
raoh.out_of_range.positive=must be positive
raoh.out_of_range.before=must be before {before}
raoh.out_of_range=must be between {min} and {max}
```

`ResourceBundleMessageResolver` looks up `raoh.<messageKey>` first and `raoh.<code>` second, so a bundle that only defines code-level keys keeps working. The constants are in `MessageKeys`; a key is always its error code, a dot, and a qualifier.

`Issue.code()` is unchanged and stays the thing to branch on in code. Only the wording is selected by the key.

## Resolvers can decline

A resolver receives the whole `Issue`, not just its code and metadata, and returns `Issue.message()` when it has nothing better — the message stored at decode time already describes the constraint. `ResourceBundleMessageResolver` declines when no key matches and when the template asks for a placeholder the metadata does not supply.

Custom resolvers get the same check from `MessageResolver.interpolateFully`, which returns `null` rather than leaving a `{max}` in the output:

```java
@Override
public String resolve(Issue issue, Locale locale) {
    var template = lookUp(issue.messageKey(), locale);
    var filled = template == null ? null : MessageResolver.interpolateFully(template, issue.meta());
    return filled != null ? filled : issue.message();
}
```

Implementing `resolve(String code, Map<String, Object> meta)` alone still works; it just cannot tell those constraints apart or decline.

For Spring applications, see the `SpringMessageResolver` adapter in the `examples/spring` module, which delegates to Spring's `MessageSource` and integrates with `Accept-Language` header-based locale injection.

Existing code using `resolve(MessageResolver)` (without locale) continues to work unchanged.
