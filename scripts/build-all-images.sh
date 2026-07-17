#!/usr/bin/env bash
set -euo pipefail
cat >&2 <<'MSG'
Application image builds are intentionally disabled for local CI/CD.
Use:
  make build-core
  make build-netty
  make build-admin
  make up
  make ci-local

P13 local runtime mounts built Core/Netty jars into a shared Java 25 image and
runs Admin UI in a shared Node runtime container. This avoids producing separate Core/Netty/Admin
application images during local development.
MSG
exit 2
