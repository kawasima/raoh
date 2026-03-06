# Raoh Project Instructions

## Javadoc

Write Javadoc so that no warnings are produced. Do not rely on suppression flags like `-Xdoclint:none`.

Specifically:

- Always include `@param`, `@return`, and `@throws` tags when the corresponding element exists.
- Do not use heading tags such as `<h3>` — Java 25's javadoc enforces strict heading level rules. Use `<p><strong>...</strong></p>` instead.
- All public methods should have a Javadoc comment to avoid `no comment` warnings.
