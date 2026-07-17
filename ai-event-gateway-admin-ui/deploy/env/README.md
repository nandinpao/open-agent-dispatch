# ai-event-gateway-core env layout

Runtime env files are organized by environment, not by historical phase.

```text
.env.core-common.example  shared non-secret defaults
.env.core-local.example   local Docker / local integration; PostgreSQL + Redis through SharedUtility
.env.core-dev.example     developer integration / SIT; PostgreSQL + Redis through SharedUtility
.env.core-prod.example    production baseline; replace every <...> value
```

Recommended usage:

```bash
cp deploy/env/.env.core-common.example deploy/env/.env.core-common
cp deploy/env/.env.core-local.example deploy/env/.env.core-local
# edit deploy/env/.env.core-local before running if ports or passwords differ
```

Docker compose currently loads the `.example` files directly for local/dev examples so the project can run immediately after checkout. For real deployment, copy the examples to non-example files, update the compose `env_file` entries, and do not commit secrets.

Legacy phase-based examples were moved to:

```text
docs/legacy-env/
docs/legacy-docker-compose/
```

Do not use the legacy phase files for new deployment. They are retained only as migration references.

## SharedUtility alignment

`local`, `dev`, and `prod` env examples now use the property names consumed by the bundled SharedUtility packages:

- `PG_SINGLE_*` maps to `pg.single` for the SharedUtility single PostgreSQL datasource mode.
- `PG_MYBATIS_ENABLED` and `PG_MYBATIS_STATEMENT_TIMEOUT` map to `pg.mybatis.*`; mapper package names stay fixed in YAML.
- `REDIS_SINGLE_ADDRESS`, `REDIS_POOL_*`, and `REDIS_CLUSTER_*` map to `redis.*` for `shared-utility/redisson-client`.
- `EVENT_DEDUP_STORE=REDISSON` is the default for local/dev/prod, so dedup uses `RedissonAccess` instead of Spring Data Redis.

Do not reintroduce `pg.write[]`, `pg.read[]`, `PG_WRITE_0_*`, `PG_READ_0_*`, `spring.data.redis.*`, `REDIS_HOST`, `REDIS_PORT`, `POSTGRES_URL`, or `POSTGRES_HIKARI_*` into the active runtime env files.

## IDE local run note

`.env.core-local.example` is optimized for Docker compose and uses service names:

```text
PG_SINGLE_URL=jdbc:postgresql://postgres:5432/aeg_core
REDIS_SINGLE_ADDRESS=redis:6379
```

When running the app directly from an IDE, either rely on `application-local.yml` defaults or override these two values to:

```text
PG_SINGLE_URL=jdbc:postgresql://127.0.0.1:5432/aeg_core
REDIS_SINGLE_ADDRESS=127.0.0.1:6379
```

## Phase 2 fingerprint env knobs

The common env file now exposes the safe global switches for the configurable fingerprint policy:

```text
CORE_FINGERPRINT_ENABLED=true
CORE_FINGERPRINT_POLICY_VERSION=v2
CORE_FINGERPRINT_MASKING_ENABLED=true
```

Per-source field templates and masking regex rules are intentionally kept in `application.yml` because
list/object binding is safer and more readable in YAML than in flat env variables. For customer-specific
ERP/MES/BPM policies, override `core.fingerprint.policies[]` in an environment YAML profile or a mounted
Spring configuration file.

## Phase 5 observability env knobs

The common env file exposes the standard operations switches:

```text
MANAGEMENT_ENDPOINTS=health,info,metrics,prometheus
CORE_OBSERVABILITY_ENABLED=true
CORE_OBSERVABILITY_BUSINESS_METRICS_ENABLED=true
CORE_OBSERVABILITY_REPOSITORY_SUMMARY_ENABLED=true
CORE_OBSERVABILITY_HEALTH_INDICATOR_ENABLED=true
CORE_OBSERVABILITY_SUMMARY_SAMPLE_LIMIT=500
CORE_OBSERVABILITY_SLOW_INTAKE_THRESHOLD=3s
```

Keep `CORE_OBSERVABILITY_INCLUDE_TENANT_TAG=false` unless tenant count is small and controlled. Enabling high-cardinality tags in Prometheus can create excessive time-series cardinality.

## M8 deployment and service-extraction settings

The Core common env example now contains the deployment-mode and integration-event switches. The supported modes are:

```text
MODULAR_MONOLITH          default; all Core feature modules execute in one process
HYBRID_ADAPTER_WORKER     Core owns state; an external worker executes Adapter Actions
EXTERNALIZED_CONTROL_PLANE reserved migration target; guarded from accidental use
```

Adapter Worker settings are kept separately in:

```text
deploy/env/.env.adapter-worker.example
```

The worker must not be given Core database or Redis credentials. Its required connection is the Core internal Adapter Action HTTP API:

```text
ADAPTER_WORKER_CORE_BASE_URL
ADAPTER_WORKER_ID
ADAPTER_WORKER_TYPES
ADAPTER_WORKER_TOKEN_HEADER
ADAPTER_WORKER_TOKEN
```

Execution endpoints are opt-in:

```text
ADAPTER_WORKER_MCP_ENDPOINT_URL
ADAPTER_WORKER_ISSUE_ENDPOINT_URL
```

When an endpoint is blank, the worker skips claiming that adapter type unless `ADAPTER_WORKER_MOCK_SUCCESS_ENABLED=true`. Keep mock success disabled outside test environments.

Integration-event projection and delivery settings belong to Core because Core owns `integration_event_outbox`:

```text
CORE_INTEGRATION_EVENTS_PROJECTION_ENABLED
CORE_INTEGRATION_EVENTS_DELIVERY_ENABLED
CORE_INTEGRATION_EVENTS_STORE
CORE_INTEGRATION_EVENTS_SINK
CORE_INTEGRATION_EVENTS_ENDPOINT_URL
CORE_INTEGRATION_EVENTS_TOKEN_HEADER
CORE_INTEGRATION_EVENTS_TOKEN
```

Projection can be enabled while delivery remains disabled. This permits an expand-first migration: persist the external event envelope, validate volume and payload compatibility, then enable HTTP delivery later.

## I7.10.9 local test database defaults

For local developer runs, the Core local/dev profiles now default to the local test PostgreSQL account below unless overridden by environment variables:

```properties
PG_SINGLE_URL=jdbc:postgresql://127.0.0.1:5432/aeg_core
PG_SINGLE_USERNAME=admin
PG_SINGLE_PASSWORD=123456
```

Docker Compose local/dev also creates PostgreSQL with `POSTGRES_USER=admin` and `POSTGRES_PASSWORD=123456` by default. Production profiles still require explicit database credentials and do not provide unsafe defaults.



## Host-local Redis / PostgreSQL defaults

For host `mvn spring-boot:run` usage, use `deploy/env/.env.core-host-local.example`.
It aligns local PostgreSQL and Redis defaults to:

- PostgreSQL: `admin / 123456`
- Redis single: `baofire.com:6379`
- Redis password: `123456`

For docker compose local usage, `.env.core-local.example` keeps `REDIS_SINGLE_ADDRESS=redis:6379` and starts the bundled Redis with `--requirepass 123456`.
