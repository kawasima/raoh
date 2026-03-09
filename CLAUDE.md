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

jetshell must be on PATH. Run in non-interactive (batch) mode by piping a script file:

```sh
cat input.jsh | jetshell -nostartup
```

### Input file template

Build the project first to populate `target/dependency/`:

```sh
mvn -f raoh/pom.xml dependency:copy-dependencies -q
```

Every verification script must start with these lines (adjust the path to the repo root as needed):

```text
/classpath raoh/target/raoh-*.jar
/classpath raoh/target/dependency/*.jar
import net.unit8.raoh.*;
import net.unit8.raoh.map.*;
import net.unit8.raoh.builtin.*;
import static net.unit8.raoh.map.MapDecoders.*;
import static net.unit8.raoh.Decoders.*;
import java.util.*;
```

Notes:

- `/classpath` takes one path per line — glob expansion does not work across `:` separators.
- Do **not** `import static net.unit8.raoh.ObjectDecoders.*` alongside `MapDecoders.*`; both define `string()` and the import becomes ambiguous. `MapDecoders` re-exports everything needed for `Map`-based decoding.

### Known gotchas

- **Root-path errors** print as `Err[/: message]` (root path is shown as `/`).
- **`nonBlank()`** currently returns error code `required` / message `is required` (not `must not be blank`). See issue #16.
- **`Presence` toString**: `Present[value=hello]`, `PresentNull[]`, `Absent[]`.
- **`optionalField` absent** → `Ok[Optional.empty]`, not `Ok[null]`.
- **`oneOf` failure** → `Err[/: no variant matched]`.
- **`field("x", subDec)`** where `subDec: Decoder<Map<String,Object>,T>` requires `nested(subDec)`. See issue #17.
- **`list(subDec)`** where `subDec: Decoder<Map<String,Object>,T>` requires `list(nested(subDec))`.
- **`Path.of(String)`** does not exist — use `Path.ROOT.append("x")`. See issue #18.
- **`Decoder.fail()`** does not exist — use `(in, path) -> Result.fail(path, code, message)`. See issue #19.
- **`flatMap` returning `Decoder`** does not work — `flatMap` expects `Function<T, Result<U>>`, not `Function<T, Decoder<I,U>>`. Use a full `Decoder<I,T>` lambda instead.
- **`sealed interface`** in JShell requires all permits classes declared in the same snippet; use plain `interface` as a workaround.

## Development Flow

Features progress through this lifecycle:

1. Check the relevant GitHub Issue for requirements.
2. Create a feature branch from `develop`: `git checkout develop && git checkout -b feature/<name>`.
3. Implement, then run `mvn test` to confirm all tests pass.
4. Commit and push, then open a PR with `gh pr create --base develop`.
5. After `/simplify` and `/review`, address any findings and push additional commits.
6. PR is merged into `develop` when approved.
