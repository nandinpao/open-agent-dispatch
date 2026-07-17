#!/usr/bin/env bash
set -euo pipefail
# Superseded by I7.10.13. Keep this script as a compatibility shim for verify-all.
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
exec "$SCRIPT_DIR/verify-i7.10.13-flyway-existing-postgres-alignment.sh"
