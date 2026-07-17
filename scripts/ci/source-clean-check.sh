#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

CI_OUTPUT_DIR="${CI_OUTPUT_DIR:-${ROOT_DIR}/.ci-output}"
REPORT_FILE="${CI_OUTPUT_DIR}/reports/source-clean-check.txt"
mkdir -p "$(dirname "$REPORT_FILE")"

# This is a release/source-package cleanliness gate, not a developer cache cleanup.
# It deliberately fails when generated build outputs or archive metadata are
# present in the source tree so CI cannot package polluted artifacts.
find . \
  \( -path './.git' -o -path './.ci-output' \) -prune -o \
  \( \
    -name target -type d -o \
    -name node_modules -type d -o \
    -name .next -type d -o \
    -name .next-ci -type d -o \
    -name .next-local -type d -o \
    -name .next-release -type d -o \
    -name dist -type d -o \
    -name coverage -type d -o \
    -name .turbo -type d -o \
    -name __pycache__ -type d -o \
    -name .pytest_cache -type d -o \
    -name .mypy_cache -type d -o \
    -name .ruff_cache -type d -o \
    -name __MACOSX -type d -o \
    -name .AppleDouble -type d -o \
    -name tsconfig.tsbuildinfo -type f -o \
    -name '*.pyc' -type f -o \
    -name '*.pyo' -type f -o \
    -name '._*' -type f -o \
    -name '.DS_Store' -type f \
  \) -print | sort > "$REPORT_FILE"

if [[ -s "$REPORT_FILE" ]]; then
  echo "[FAIL] Source tree contains generated artifacts or archive metadata:" >&2
  cat "$REPORT_FILE" >&2
  echo "Run 'make clean-artifacts' before packaging, then re-run 'make test-source-clean'." >&2
  exit 1
fi

echo "Source cleanliness check passed."
