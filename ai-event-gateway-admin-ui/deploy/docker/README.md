# ai-event-gateway-core Docker Compose files

Runtime Docker Compose files are environment-based:

```text
docker-compose.core-local.yml  PostgreSQL + Redis + core, local profile
docker-compose.core-dev.yml    PostgreSQL + Redis + core, dev profile
docker-compose.core-prod.yml   core only, prod profile, external PostgreSQL/Redis/Netty
```

Each compose file loads:

```text
../env/.env.core-common.example
../env/.env.core-<env>.example
```

Local and dev compose are runnable development stacks. Production compose expects external PostgreSQL, Redis, and Netty gateway endpoints.

## Local package + Docker up

From the project root:

```bash
./scripts/build/local-docker-up.sh
```

Useful options:

```bash
SKIP_TESTS=false ./scripts/build/local-docker-up.sh
FOLLOW_LOGS=true ./scripts/build/local-docker-up.sh
IMAGE_TAG=ai-event-gateway-core:local-test ./scripts/build/local-docker-up.sh
./scripts/build/local-docker-down.sh
REMOVE_VOLUMES=true ./scripts/build/local-docker-down.sh
```

Copy the env examples to non-example files and replace secrets before real deployment. Legacy phase compose files were moved to `docs/legacy-docker-compose/`.


### P12.12 stale database jar prevention

`local-docker-up.sh` defaults to `CLEAN_BUILD=true` and `DOCKER_NO_CACHE=true` so Docker does not reuse an image that still contains an old `database-1.0-SNAPSHOT.jar`. After packaging, you can inspect the bundled database jar with:

```bash
./scripts/build/inspect-core-jar-shared-utility.sh
```


## P12.22 schema initialization before core startup

Local and dev Docker Compose target an existing PostgreSQL server. They do not create PostgreSQL and do not run direct SQL installers. Runtime schema lifecycle is managed by Flyway; run `scripts/db/flyway-migrate-postgres.sh` before startup or use `scripts/dev/run-core-local.sh`.

Useful commands:

```bash
./scripts/db/install-local-docker-schema.sh
./scripts/db/verify-local-docker-schema.sh
RESET_DB=true ./scripts/build/local-docker-up.sh
```

Use `RESET_DB=true` when a local PostgreSQL volume was created by an older broken build or contains an incomplete schema.

## M8 hybrid Adapter Worker deployment

M8 adds an optional second executable process without changing the default Core deployment:

```text
ai-event-gateway-core-app            control plane and database owner
ai-event-gateway-adapter-worker-app  stateless MCP / issue action worker
```

The example compose file is:

```text
docker-compose.core-hybrid-worker.yml
```

It is an overlay/reference for environments that already provide the Core PostgreSQL and Redis settings. It intentionally does not create another database for the worker. The worker must not receive PostgreSQL, Redis, Flyway, MyBatis, or Core datasource credentials.

Build both deployables from the repository root:

```bash
./scripts/build/package-m8-deployables.sh -DskipTests

docker build \
  -f ai-event-gateway-core-app/Dockerfile \
  -t ai-event-gateway-core:local .

docker build \
  -f ai-event-gateway-adapter-worker-app/Dockerfile \
  -t ai-event-gateway-adapter-worker:local \
  ai-event-gateway-adapter-worker-app
```

Start the hybrid example:

```bash
docker compose \
  -f deploy/docker/docker-compose.core-hybrid-worker.yml \
  up -d
```

Required Core mode:

```text
SPRING_PROFILES_ACTIVE=prod,hybrid-worker
CORE_DEPLOYMENT_MODE=HYBRID_ADAPTER_WORKER
ADAPTER_EXECUTOR_MODE=external
ADAPTER_EXECUTOR_ENABLED=false
```

The worker only claims an adapter type when the corresponding endpoint is configured. `ADAPTER_WORKER_MOCK_SUCCESS_ENABLED=true` is intended only for explicit smoke testing; leave it `false` in production.

External integration-event delivery is independent of the Adapter Worker. Enable it only when an HTTP consumer is ready:

```text
CORE_INTEGRATION_EVENTS_PROJECTION_ENABLED=true
CORE_INTEGRATION_EVENTS_DELIVERY_ENABLED=true
CORE_INTEGRATION_EVENTS_STORE=MYBATIS
CORE_INTEGRATION_EVENTS_SINK=HTTP
CORE_INTEGRATION_EVENTS_ENDPOINT_URL=https://consumer.example/internal/events
```

Delivery is at-least-once. The consumer must deduplicate on `eventId` / `Idempotency-Key`.

## I7.10.9 local PostgreSQL credentials

The local/dev compose files default PostgreSQL to:

```text
POSTGRES_DB=aeg_core
POSTGRES_USER=admin
POSTGRES_PASSWORD=123456
```

Override these values with `POSTGRES_USER` / `POSTGRES_PASSWORD` only when your local database uses a different account. Keep `PG_SINGLE_USERNAME` / `PG_SINGLE_PASSWORD` aligned with the PostgreSQL container or external PostgreSQL server.

