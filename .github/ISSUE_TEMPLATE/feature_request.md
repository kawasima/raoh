---
name: Feature request
about: A decoder, encoder, or combinator you want to see
labels: enhancement
---

## Problem

<!-- What you are trying to express today, and why the current API makes it awkward.
     A concrete snippet of the workaround you are writing is worth more than a description. -->

## Proposed

<!-- The API you have in mind. -->

## Notes

- If this mirrors something from another library (Zod, Elm, …), say which — `docs/comparisons.md`
  has the current mapping tables.
- **If this is an encode-side mirror of a decoder feature:** is it a failure-handling feature? An
  `Encoder` is a total function that cannot fail, so the encode side has no dual for error
  accumulation (`combine`), `withDefault`, `recover`, `oneOf`, or `strict`. That asymmetry is
  deliberate — see the README's *Encoders → Scope* section.
