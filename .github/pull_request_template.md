<!-- Target this PR at `develop`. `main` is the release branch only. -->

## What

<!-- What changes, and why. Link the issue: Closes #123 -->

## Notes for the reviewer

<!-- Anything non-obvious: a design decision and its alternative, a trade-off you took,
     a behaviour you deliberately did not change. -->

## Checklist

- [ ] `mvn clean test` passes
- [ ] `mvn -Pnullcheck compile` passes
- [ ] Javadoc produces no warnings (`@param` / `@return` / `@throws` present; no heading tags)
- [ ] New public API is documented; new behaviour is tested against the documented contract
- [ ] If an `ErrorCodes` constant was added: `MessageResolver.DEFAULT` **and** `messages.properties`
      (plus localized bundles) updated, and the message placeholders match the decoder's meta keys
- [ ] If a breaking change: CHANGELOG's **Compatibility** section says how to migrate
