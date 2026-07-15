# Contributing to Raoh

Thanks for taking an interest. This page covers what you need to build the project, the conventions
a change is expected to follow, and how work reaches `develop`.

## Requirements

- **Java 25.** Raoh targets a modern Java LTS baseline: the API relies on records, sealed types,
  pattern matching, and JSpecify type-use nullness, so older baselines are not supported.
- **Maven.**

## Build

```bash
mvn clean test        # compile and run the test suite
mvn verify            # additionally runs JaCoCo (reporting only — no thresholds)
mvn -Pnullcheck compile   # the NullAway null-analysis gate (requires JDK 25)
```

CI runs three jobs — the build and test suite, the `nullcheck` gate, and a build of each module under
`examples/` against the freshly installed snapshot — both on pushes to `develop` / `main` and on
pull requests targeting them, so your PR gets the same checks. Running them locally first still
saves a round trip.

The `examples/` modules are **not** part of the reactor — they are built separately against the
installed snapshot:

```bash
mvn -pl raoh,raoh-json -am install -DskipTests
mvn -f examples/spring/pom.xml test
```

## Branching and pull requests

- **`develop` is the integration branch.** Base your work on it, and target your PR at it.
- **`main` is the release branch only.** Never open a feature PR against `main`.
- Branch naming: `feature/<short-description>` (e.g. `feature/bool-constraints`).

```bash
git checkout develop
git checkout -b feature/<topic>
# ...
gh pr create --base develop      # --base develop is required; gh defaults to main
```

If a PR is accidentally opened against `main`, retarget it with `gh pr edit <number> --base develop`.

## Conventions

### Javadoc

Javadoc is treated as part of the build, not an afterthought. **Write it so that no warnings are
produced** — do not reach for suppression flags like `-Xdoclint:none`.

- Include `@param`, `@return`, and `@throws` whenever the corresponding element exists.
- Every public method needs a comment (a missing one is a `no comment` warning).
- Do not use heading tags (`<h3>` and friends) — Java 25's javadoc enforces strict heading level
  rules. Use `<p><strong>...</strong></p>` instead.

### Examples

Code under `examples/` is documentation, and is held to the same standard as the library: English
Javadoc and inline comments throughout.

### Adding an error code

Adding an `ErrorCodes` constant is not a one-line change. `ErrorCodesDefaultCoverageTest` reflects
over every constant and fails the build unless it is covered, so:

- Update **both** `MessageResolver.DEFAULT` and `messages.properties` (and the localized bundles).
- Make the placeholders in `raoh.<code>=...` match the meta keys the decoder actually puts in the
  map — a mismatch silently renders a literal `{key}`.
- Keep the fallback message self-contained. `Issue.message()` is what a caller who never calls
  `Issues.resolve()` sees, so include the key values (the offending element, the duplicate, …) in it.
- Never put a `null` into a meta map: `Map.of` / `List.copyOf` reject it at runtime. When a decoded
  value may be `null`, build the payload with `new ArrayList<>(collection)` instead.

### Tests

Assert the documented contract, not the current implementation shape. Error **codes**, paths, and
metadata are contract; fallback message wording generally is not. If you find yourself asserting
something the Javadoc does not promise, either document the behaviour deliberately or drop the
assertion.

## Design direction

Raoh is a decoder library built on parse-don't-validate, and a few decisions follow from that. They
come up often enough to be worth stating:

- **A decoder may fail; an encoder may not.** `Encoder<T, O>` is a total function with no failure
  channel. Anything on the decode side that exists to handle failure — `combine`'s error
  accumulation, `withDefault`, `recover`, `oneOf`, `strict` — therefore has no encode dual, and the
  resulting asymmetry is deliberate rather than a gap. When proposing an encode-side mirror of a
  decoder feature, the first question is "is this a failure-handling feature?"
- **Encoding targets the `Map<String, Object>` boundary by design.** JSON is reached by encoding to
  a map and bridging with `objectMapper.valueToTree(map)`; there is intentionally no separate JSON or
  jOOQ encoder. See the README's *Encoders → Scope* section and `docs/comparisons.md`.
- **`raoh-gsh*` is experimental.** Those modules build on the evolving JDK ClassFile API and sit
  outside the core library's stability promise.

## Upgrading

Breaking changes, and the migration for each, are recorded in the **Compatibility** section of
[CHANGELOG.md](CHANGELOG.md) for the release that introduces them.

## Reporting bugs and requesting features

Open an issue. For a decoding problem, the most useful report includes the input shape, the decoder
definition, and the `Issues` you got (`issues.toJsonList()` is a good paste). For security reports,
see [SECURITY.md](SECURITY.md) instead — please do not open a public issue.

## License

By contributing, you agree that your contributions are licensed under the
[Apache License 2.0](LICENSE).
