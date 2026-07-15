# Security Policy

## Reporting a vulnerability

**Please do not open a public issue for a security problem.**

Report it privately through GitHub's
[private vulnerability reporting](https://github.com/kawasima/raoh/security/advisories/new) — the
*Report a vulnerability* button under the repository's **Security** tab. That opens a draft advisory
visible only to the maintainers.

A useful report includes the affected version, the input that triggers the problem, and what an
attacker gains. If you have a proof of concept, a failing test against `develop` is ideal.

Raoh is a small, single-maintainer project. Reports are acknowledged and fixed on a best-effort
basis — please allow reasonable time before disclosing publicly.

## Supported versions

Raoh is pre-1.0. Fixes land on `develop` and ship in the next release; only the **latest released
version** receives security fixes. There are no backports to earlier 0.x releases.

## Scope

Raoh decodes untrusted input at a boundary, so the interesting reports are ones where a decoder
mishandles hostile input — for example an input that escapes the `Result` contract by throwing out of
`decode()` instead of returning `Err`, unbounded resource consumption from a crafted input, or a
decoder accepting a value it documents as invalid.

Two things that are **not** vulnerabilities, because they are the documented contract:

- **`Issue.meta` may contain raw user-supplied values** (e.g. `actual` on a type mismatch). This is
  by design so that messages can be templated. If you return issues in an HTTP response, filter or
  omit `meta` first — see the security note on `Issues.toJsonList()`. Echoing raw input back to a
  client is the caller's decision, not the library's.
- **`raoh-gsh*`** is a test/CI-time tool built on bytecode weaving, and is experimental. It is not
  intended to run in production, and is outside the core library's stability and support promise.
