# ai-event-gateway-core scripts

Scripts are grouped by purpose:

```text
scripts/api/      curl helpers for runtime APIs
scripts/build/    Maven package and Docker helpers
scripts/db/       PostgreSQL install / verify / drop helpers
scripts/smoke/    smoke test flows
scripts/verify/   static consolidation checks
```

## Common build commands

```bash
./scripts/build/build.sh
./scripts/build/docker-build.sh
./scripts/build/local-docker-up.sh
./scripts/build/local-docker-down.sh
```

`local-docker-up.sh` performs the local developer path in one command:

1. Maven package the app module and required reactor modules.
2. Build the Docker image from `ai-event-gateway-core-app/Dockerfile`.
3. Start PostgreSQL, Redis, and ai-event-gateway-core with `docker-compose.core-local.yml`.

Options:

```bash
SKIP_TESTS=false ./scripts/build/local-docker-up.sh
FOLLOW_LOGS=true ./scripts/build/local-docker-up.sh
IMAGE_TAG=ai-event-gateway-core:local-test ./scripts/build/local-docker-up.sh
REMOVE_VOLUMES=true ./scripts/build/local-docker-down.sh
```

## Verification

```bash
./scripts/verify/verify-all.sh
```


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

## M0 modularization baseline

```bash
./scripts/architecture/verify-dependency-baseline.py
./scripts/verify/verify-m0-modularization-baseline.sh
```

`generate-dependency-baseline.py` is an architecture-review command, not a normal CI command. Run it only when an approved refactor intentionally changes the checked-in M0 dependency baseline.

## M1 modular-monolith foundation checks

```bash
./scripts/verify/verify-m1-foundation-modules.sh
./scripts/architecture/verify-dependency-baseline.py
```
