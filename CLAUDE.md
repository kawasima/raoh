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
- When creating a PR with `gh pr create`, always pass `--base develop` explicitly to avoid defaulting to `main`.

## Development Flow

Features progress through this lifecycle:

1. Check the relevant GitHub Issue for requirements.
2. Create a feature branch from `develop`: `git checkout develop && git checkout -b feature/<name>`.
3. Implement, then run `mvn test` to confirm all tests pass.
4. Commit and push, then open a PR with `gh pr create --base develop`.
5. After `/simplify` and `/review`, address any findings and push additional commits.
6. PR is merged into `develop` when approved.
