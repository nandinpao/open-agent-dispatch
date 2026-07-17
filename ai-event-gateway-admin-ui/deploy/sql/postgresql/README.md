# PostgreSQL schema management

## Source of truth

Runtime schema lifecycle is managed by Flyway migrations:

```text
ai-event-gateway-database-platform/src/main/resources/db/migration
```

The `deploy/sql/postgresql/common/01_schema.sql` file is an offline DBA full-install artifact only. It must not be used by normal local/dev startup because it bypasses `flyway_schema_history`.

## Existing PostgreSQL server

For an existing PostgreSQL server, configure:

```bash
export PG_SINGLE_URL=jdbc:postgresql://127.0.0.1:5432/aeg_core
export PG_SINGLE_USERNAME=admin
export PG_SINGLE_PASSWORD=123456
```

Run Flyway:

```bash
scripts/db/flyway-info-postgres.sh
scripts/db/flyway-migrate-postgres.sh
scripts/db/flyway-validate-postgres.sh
scripts/db/verify-postgres-schema.sh
```

## Non-empty schema without Flyway history

Do not automatically baseline. Review with DBA / release owner and choose an explicit baseline version only when the existing schema is known to match that release.

Example for a legacy schema known to match V17:

```bash
FLYWAY_BASELINE_ON_MIGRATE=true \
FLYWAY_BASELINE_VERSION=17 \
scripts/db/flyway-migrate-postgres.sh
```

## Offline DBA install

Only use the generated full SQL with explicit opt-in:

```bash
ALLOW_OFFLINE_SQL_INSTALL=true SQL_ENV=local scripts/db/install-postgres-schema.sh
```

After an offline install, align Flyway history intentionally before using runtime migrations.
