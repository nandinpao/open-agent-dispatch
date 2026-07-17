#!/usr/bin/env bash
set -euo pipefail

cat >&2 <<'MSG'
Local Docker Compose no longer creates PostgreSQL or runs a postgres-schema service.
It connects to an existing PostgreSQL server, and Flyway owns schema lifecycle.

Use:
  scripts/db/flyway-migrate-postgres.sh
  scripts/db/flyway-validate-postgres.sh
  scripts/db/verify-postgres-schema.sh
MSG
exit 2
