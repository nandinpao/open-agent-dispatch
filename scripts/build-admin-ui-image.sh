#!/usr/bin/env bash
set -euo pipefail
echo "Admin UI image build is disabled for local CI/CD; use make build-admin and shared Node runtime container." >&2
exit 2
