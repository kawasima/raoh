#!/usr/bin/env bash
# bump-version.sh — update the project version in all pom.xml files
#
# Usage:
#   scripts/bump-version.sh <new-version>
#
# Example:
#   scripts/bump-version.sh 0.3.0-SNAPSHOT
#   scripts/bump-version.sh 0.2.0

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <new-version>" >&2
    exit 1
fi

NEW_VERSION="$1"

# Detect current version from the parent pom
CURRENT_VERSION="$(grep -m1 '<version>' "$ROOT/pom.xml" | sed 's/.*<version>\(.*\)<\/version>.*/\1/' | tr -d '[:space:]')"

if [[ -z "$CURRENT_VERSION" ]]; then
    echo "ERROR: could not detect current version from pom.xml" >&2
    exit 1
fi

if [[ "$CURRENT_VERSION" == "$NEW_VERSION" ]]; then
    echo "Version is already $NEW_VERSION — nothing to do."
    exit 0
fi

echo "Bumping $CURRENT_VERSION → $NEW_VERSION"

# Files that contain the version string
FILES=(
    "$ROOT/pom.xml"
    "$ROOT/raoh/pom.xml"
    "$ROOT/raoh-json/pom.xml"
    "$ROOT/raoh-jooq/pom.xml"
    "$ROOT/raoh-gsh/pom.xml"
    "$ROOT/raoh-gsh-weaver/pom.xml"
    "$ROOT/raoh-gsh-maven-plugin/pom.xml"
    "$ROOT/examples/spring/pom.xml"
)

for f in "${FILES[@]}"; do
    if [[ -f "$f" ]]; then
        sed -i '' "s|${CURRENT_VERSION}|${NEW_VERSION}|g" "$f"
        echo "  updated: $f"
    else
        echo "  WARNING: not found: $f" >&2
    fi
done

# Verify no stale references remain
REMAINING="$(grep -rn "$CURRENT_VERSION" "${FILES[@]}" 2>/dev/null || true)"
if [[ -n "$REMAINING" ]]; then
    echo ""
    echo "WARNING: old version still found in:" >&2
    echo "$REMAINING" >&2
    exit 1
fi

echo ""
echo "Done. All files updated to $NEW_VERSION."
echo "Next: mvn clean test && git commit -am \"Release v${NEW_VERSION}\""
