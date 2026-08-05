# Raoh Project Instructions

## Javadoc

Write Javadoc so that no warnings are produced. Do not rely on suppression flags like `-Xdoclint:none`.

Specifically:

- Always include `@param`, `@return`, and `@throws` tags when the corresponding element exists.
- Do not use heading tags such as `<h3>` — Java 25's javadoc enforces strict heading level rules. Use `<p><strong>...</strong></p>` instead.
- All public methods should have a Javadoc comment to avoid `no comment` warnings.

## Examples

Example code under `examples/` must also have Javadoc and inline comments written in English.
Examples serve as documentation — treat them with the same care as the library itself.

## Git Workflow

- The main integration branch is **`develop`**. All feature branches must be based on `develop` and PRs must target `develop`.
- **`main`** is the release branch only. Never open a PR targeting `main` for feature work.
- Branch naming: `feature/<short-description>` (e.g., `feature/bool-constraints`).
- **ALWAYS** pass `--base develop` when creating a PR — never omit it, as `gh pr create` defaults to `main`:

  ```sh
  gh pr create --base develop --title "..." --body "..."
  ```

- If a PR was accidentally opened against `main`, fix it immediately with `gh pr edit <number> --base develop`.

## Error Code Implementation Checklist

When adding a new error code and its associated decoder constraint, verify all of the following:

- **Meta values must never be null**: `Map.of()` and `List.copyOf()` reject null silently at runtime with `NullPointerException`. If the decoded value may be null (e.g., `nullable(...)` combinator), use `new ArrayList<>(collection)` instead of `List.copyOf()` for meta payloads.
- **Fallback message must be self-contained**: `Issue.message()` is the pre-resolved message stored at decode time. Users who never call `Issues.resolve()` rely on this string alone. Always include the key variable values (e.g., the missing elements, the duplicate values) in the fallback message — do not assume `resolve()` will be called.
- **`messages.properties` templates must reference the same meta keys**: The placeholders in `raoh.<code>=...` must match the keys actually put into the meta map by the decoder. Mismatches silently produce literal `{key}` output.
- **`MessageResolver.DEFAULT` and `messages.properties` must both be updated**: The `ErrorCodesDefaultCoverageTest` reflection test enforces this automatically — a new `ErrorCodes` constant without coverage in both will fail the build.

## Tutorial Verification with jetshell

Use jetshell to verify code snippets in `docs/tutorial.ja.md`.

### Setup

jetshell must be on PATH (use **1.0.2 or later**). Run in non-interactive (batch) mode by piping a script file:

```sh
cat input.jsh | jetshell
```

### Input file template

Build the project first to populate `target/dependency/`:

```sh
mvn -f raoh/pom.xml dependency:copy-dependencies -q
```

Every verification script must start with these lines (adjust the path to the repo root as needed).
`java.util.*`, `java.math.*`, `java.util.regex.*` etc. are already imported by jetshell's default startup.

```text
/classpath raoh/target/raoh-*.jar
/classpath raoh/target/dependency/*.jar
import net.unit8.raoh.*;
import net.unit8.raoh.decode.*;
import net.unit8.raoh.decode.map.*;
import net.unit8.raoh.decode.builtin.*;
import static net.unit8.raoh.decode.map.MapDecoders.*;
import static net.unit8.raoh.decode.Decoders.*;
import java.util.stream.*;
import java.time.*;
```

Notes:

- `/classpath` takes one path per line — glob expansion does not work across `:` separators.
- Do **not** `import static net.unit8.raoh.decode.ObjectDecoders.*` alongside `MapDecoders.*`; both define `string()` and the import becomes ambiguous. `MapDecoders` re-exports everything needed for `Map`-based decoding.

### Known gotchas

- **Root-path errors** print as `Err[/: message]` (root path is shown as `/`).
- **`nonBlank()`** currently returns error code `required` / message `is required` (not `must not be blank`). See issue #16.
- **`Presence` toString**: `Present[value=hello]`, `PresentNull[]`, `Absent[]`.
- **`optionalField` absent** → `Ok[Optional.empty]`, not `Ok[null]`.
- **`oneOf` failure** → `Err[/: no variant matched]`.
- **`field("x", subDec)`** where `subDec: Decoder<Map<String,Object>,T>` requires `nested(subDec)`. See issue #17.
- **`list(subDec)`** where `subDec: Decoder<Map<String,Object>,T>` requires `list(nested(subDec))`.
- **`Decoder.fail()`** does not exist — use `(in, path) -> Result.fail(path, code, message)` (one-line lambda; closed as low-value convenience).
- **`flatMap` returning `Decoder`** does not work — `flatMap` expects `Function<T, Result<U>>`, not `Function<T, Decoder<I,U>>`. Use a full `Decoder<I,T>` lambda instead.
- **`sealed interface`** works as-is on jetshell 1.0.2+ (a contiguous `sealed` type plus its permitted subtypes in one script compiles like normal Java source). On 1.0.1 and earlier it failed unless you used a plain `interface`; that workaround is no longer needed. See [jetshell#17](https://github.com/kawasima/jetshell/issues/17).

## Development Flow

Features progress through this lifecycle:

1. Check the relevant GitHub Issue for requirements.
2. Create a feature branch from `develop`: `git checkout develop && git checkout -b feature/<name>`.
3. Implement, then run `mvn test` to confirm all tests pass.
4. Commit and push, then open a PR with `gh pr create --base develop`.
5. After `/simplify` and `/review`, address any findings and push additional commits.
6. PR is merged into `develop` when approved.

## Release Flow

Releases are done locally (no CI). `deploy` publishes straight to Maven Central
(`autoPublish=true`, `waitUntil=published`) and **cannot be undone** — a published version is
permanent. Everything before step 5 is reversible; treat step 5 as the point of no return.

1. Close any issue the CHANGELOG lists as done. The release notes are generated from the issues
   and PRs merged since the last tag, so an implemented-but-open issue silently misses them.

2. On `develop`, rename the CHANGELOG's `## [Unreleased]` section to `## [X.Y.Z] - <date>`, leave a
   fresh empty `## [Unreleased]` above it, and add the compare links at the bottom:

   ```
   [Unreleased]: https://github.com/kawasima/raoh/compare/vX.Y.Z...HEAD
   [X.Y.Z]: https://github.com/kawasima/raoh/compare/v<prev>...vX.Y.Z
   ```

   Cross-check the breaking-change list against the japicmp report — the `japicmp-api-diff`
   artifact from the last CI run, or `*/target/japicmp/api-diff.md` after a local `mvn verify`.
   It lists every removed, modified and added type in `raoh`, `raoh-json` and `raoh-jooq`
   against the baseline, which is what the CHANGELOG is supposed to be describing.

3. Set the release version in all POMs:

   ```sh
   ./scripts/bump-version.sh X.Y.Z
   mvn clean test
   ```

   Commit: `release: set version X.Y.Z`

   `bump-version.sh` seds the version string across every POM. Check `git diff` before committing —
   it should touch nothing but `<version>` / `<raoh.version>`. Maven also rewrites the checked-in
   `*/.settings/org.eclipse.jdt.core.prefs` files as a side effect of any build; revert those rather
   than committing them.

4. Merge `develop` into `main` (fast-forward):

   ```sh
   git checkout main && git merge --ff-only develop
   ```

5. On `main`, dry-run the release build, then deploy:

   ```sh
   mvn clean verify -Prelease   # source + javadoc + GPG signing, no publish
   mvn clean deploy -Prelease   # irreversible
   ```

   Run the dry run first — it exercises GPG signing, which is the step most likely to fail, while
   nothing has been published yet.

   **The deploy needs a real terminal.** `~/.gnupg/gpg.conf` uses `pinentry-mode loopback` with no
   `pinentry-mac` installed, so GPG reads the passphrase from `/dev/tty` and fails with
   `cannot open '/dev/tty'` in any non-interactive shell (including Claude Code's). Run this command
   yourself in a terminal. Passing `-Dgpg.passphrase=` is not an option: the gpg plugin sets
   `bestPractices=true`, which rejects it by design.

6. Push `main`, **then** create the GitHub release:

   ```sh
   git push origin main
   gh release create vX.Y.Z --target main --title "vX.Y.Z" --generate-notes
   ```

   Order matters. `--target main` resolves against the *remote* branch, so creating the release
   before pushing tags the previous commit.

7. Back on `develop`, bump to the next SNAPSHOT, move the API-diff baseline to the release just
   made, and push:

   ```sh
   git checkout develop
   ./scripts/bump-version.sh X.Y.(Z+1)-SNAPSHOT
   # set <japicmp.baseline> in the root pom.xml to X.Y.Z
   git push origin develop
   ```

   Commit: `chore: bump version to X.Y.(Z+1)-SNAPSHOT`

   Set the baseline *after* running the script, never before: `bump-version.sh` replaces the
   current version string anywhere it appears in a POM, so a baseline already reading `X.Y.Z`
   would be rewritten to the SNAPSHOT along with everything else. Left behind, the next cycle's
   diff covers two releases at once.

8. Verify the release landed:

   ```sh
   curl -s -o /dev/null -w "%{http_code}" \
     https://repo1.maven.org/maven2/net/unit8/raoh/raoh/X.Y.Z/raoh-X.Y.Z.pom
   git ls-remote origin refs/heads/main refs/heads/develop refs/tags/vX.Y.Z
   ```

   Confirm the artifact returns `200` and that the tag points at the same commit as `main`.
