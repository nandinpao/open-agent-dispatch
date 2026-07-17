#!/usr/bin/env bash
set -euo pipefail
echo "Netty image build is disabled for local CI/CD; use make build-netty and shared Java 25 runtime." >&2
exit 2
