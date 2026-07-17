#!/usr/bin/env bash
set -euo pipefail
echo "Core image build is disabled for local CI/CD; use make build-core and shared Java 25 runtime." >&2
exit 2
