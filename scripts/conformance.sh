#!/usr/bin/env bash
# conformance.sh — check raoh-java against the Raoh Specification
#
# Usage:
#   scripts/conformance.sh
#
# Checks out the specification at the revision conformance/spec.lock pins, builds that revision's
# own raoh-verify, runs the conformance runner and verifies its result against
# conformance/conformance.json. Writes, under conformance/target/:
#
#   runner-result.json       what raoh-java gave for each case it can run
#   conformance-report.json  what raoh-verify made of it
#
# Exits with raoh-verify's status: 0 when no profile is non-conformant, 1 when one is, 2 when the
# input cannot be trusted. Any other failure exits non-zero too.
#
# Requires git, jq, Go and a JDK 25 with Maven. To use a checkout of raoh-specification you
# already have, set RAOH_SPECIFICATION_DIR; it has to be at the pinned revision, with no change to
# the files the specification consists of.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT="$ROOT/conformance/target"
LOCK="$ROOT/conformance/spec.lock"

REPOSITORY="$(jq -er .repository "$LOCK")"
REVISION="$(jq -er .revision "$LOCK")"

mkdir -p "$OUT"
# What this run writes, removed first, so that a run that fails before writing them never leaves
# an earlier run's result or report to be read as this one's.
rm -f "$OUT/runner-result.json" "$OUT/conformance-report.json"

if [[ -n "${RAOH_SPECIFICATION_DIR:-}" ]]; then
    SPEC="$(cd "$RAOH_SPECIFICATION_DIR" && pwd)"
else
    SPEC="$OUT/raoh-specification"
    if [[ ! -d "$SPEC/.git" ]]; then
        git clone --quiet "https://github.com/$REPOSITORY.git" "$SPEC"
    fi
    if ! git -C "$SPEC" cat-file -e "$REVISION^{commit}" 2>/dev/null; then
        git -C "$SPEC" fetch --quiet origin
    fi
    git -C "$SPEC" -c advice.detachedHead=false checkout --quiet --detach "$REVISION"
fi

# The suite and the verifier are both read from this checkout, so it has to be the pinned revision
# exactly: a changed or added file under the specification's paths would make the result describe
# something no revision is.
HEAD="$(git -C "$SPEC" rev-parse HEAD)"
if [[ "$HEAD" != "$REVISION" ]]; then
    echo "ERROR: $SPEC is at $HEAD, but conformance/spec.lock pins $REVISION" >&2
    exit 1
fi
DIRTY="$(git -C "$SPEC" status --porcelain --untracked-files=all -- \
    specification.json spec catalog schema suite cmd internal go.mod go.sum)"
if [[ -n "$DIRTY" ]]; then
    echo "ERROR: $SPEC has changes the pinned revision does not:" >&2
    echo "$DIRTY" >&2
    exit 1
fi

# The verifier of the pinned revision, not whichever raoh-verify is on PATH.
(cd "$SPEC" && go build -o "$OUT/raoh-verify" ./cmd/raoh-verify)
DIGEST="$("$OUT/raoh-verify" manifest "$SPEC")"

# The runner's own tests run; the modules it is built with are tested by the default build.
mvn -B --no-transfer-progress -q -f "$ROOT/pom.xml" -Pconformance -pl conformance -am package \
    -Dtest='net.unit8.raoh.conformance.**' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true

IMPLEMENTATION_REVISION="$(git -C "$ROOT" rev-parse HEAD)"
if [[ -n "$(git -C "$ROOT" status --porcelain --untracked-files=no)" ]]; then
    IMPLEMENTATION_REVISION="$IMPLEMENTATION_REVISION-dirty"
fi

java -cp "$ROOT/conformance/target/classes:$(cat "$ROOT/conformance/target/classpath.txt")" \
    net.unit8.raoh.conformance.RunnerMain \
    --spec "$SPEC" \
    --revision "$REVISION" \
    --manifest-digest "$DIGEST" \
    --implementation-revision "$IMPLEMENTATION_REVISION" \
    --out "$OUT/runner-result.json"

"$OUT/raoh-verify" verify \
    --spec "$SPEC" \
    --result "$OUT/runner-result.json" \
    --conformance "$ROOT/conformance/conformance.json" \
    -o "$OUT/conformance-report.json"
