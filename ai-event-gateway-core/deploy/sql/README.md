# SQL / Flyway operations

Runtime schema lifecycle is managed by Flyway migrations located at:

```text
database-platform/src/main/resources/db/migration
```

For an existing PostgreSQL server, use:

```bash
scripts/db/flyway-info-postgres.sh
scripts/db/flyway-migrate-postgres.sh
scripts/db/flyway-validate-postgres.sh
scripts/db/verify-postgres-schema.sh
```

The generated full SQL under `deploy/sql/postgresql/common/01_schema.sql` is only for DBA/offline installation and requires:

```bash
ALLOW_OFFLINE_SQL_INSTALL=true scripts/db/install-postgres-schema.sh
```

Normal local/dev startup must not use direct SQL installers.
