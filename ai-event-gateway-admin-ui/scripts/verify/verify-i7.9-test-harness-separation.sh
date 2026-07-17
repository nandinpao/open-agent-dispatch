#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DOC="$ROOT_DIR/docs/i7.9-test-harness-separation.md"
EXCLUDES="$ROOT_DIR/release/i7.9/production-runtime-excludes.txt"
INCLUDES="$ROOT_DIR/release/i7.9/test-harness-includes.txt"

fail() {
  echo "[I7.9][FAIL] $1" >&2
  exit 1
}

[[ -f "$DOC" ]] || fail "Missing I7.9 test harness separation document"
[[ -f "$EXCLUDES" ]] || fail "Missing production-runtime-excludes.txt"
[[ -f "$INCLUDES" ]] || fail "Missing test-harness-includes.txt"

grep -q 'scripts/e2e/\*\*' "$EXCLUDES" || fail "Core production excludes must remove scripts/e2e"
grep -q 'scripts/smoke/\*\*' "$EXCLUDES" || fail "Core production excludes must remove scripts/smoke"
grep -q 'docker-compose.i6-e2e.yml' "$EXCLUDES" || fail "Core production excludes must remove i6 e2e compose"
grep -q 'docker-compose.i7-local-integrated.yml' "$EXCLUDES" || fail "Core production excludes must remove i7 local integrated compose"
grep -q 'docker-compose.core-local.yml' "$EXCLUDES" || fail "Core production excludes must remove local compose"
grep -q 'scripts/e2e/\*\*' "$INCLUDES" || fail "Core test harness includes must include scripts/e2e"
grep -q 'scripts/smoke/\*\*' "$INCLUDES" || fail "Core test harness includes must include scripts/smoke"

grep -q 'production runtime artifact must not contain' "$DOC" || fail "I7.9 document must define production exclusion policy"
grep -q 'opensocket-i7.9-production-runtime.zip' "$DOC" || fail "I7.9 document must describe production runtime artifact"
grep -q 'opensocket-i7.9-test-harness.zip' "$DOC" || fail "I7.9 document must describe test harness artifact"

echo "I7.9 Core test harness separation static verification passed."
