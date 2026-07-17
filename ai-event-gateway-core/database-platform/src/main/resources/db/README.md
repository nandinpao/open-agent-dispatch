# Runtime database migrations

This directory is the runtime Flyway migration source of truth owned by
`database-platform`.

```text
db/migration/V1__incident_store.sql
...
db/migration/V20__dispatch_claim_lease.sql
```

The consolidated DBA/offline installation scripts remain under:

```text
deploy/sql/postgresql
```

Rules:

1. Add schema changes here first as a new immutable Flyway migration.
2. Never edit a migration already applied to a shared environment; add a new version.
3. Update `deploy/sql/postgresql/common/01_schema.sql` after migration changes.
4. Do not place environment-specific seed or destructive SQL in this runtime folder.
5. Environment-specific SQL belongs under `deploy/sql/postgresql/local`, `dev`, or `prod`.
