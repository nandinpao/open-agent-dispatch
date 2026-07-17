#!/usr/bin/env bash
set -euo pipefail
export PYTHONDONTWRITEBYTECODE=1
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
VERSION="1.0.0-p25.7.4-p5-callback-transition-governance-fix"
KERNEL="kernel/src/main/java/com/opensocket/aievent/core/kernel/persistence"
DISPATCH="execution-control/src/main/java/com/opensocket/aievent/core/dispatch"
DISPATCH_MODEL="data-model/src/main/java/com/opensocket/aievent/core/dispatch"
DB="database-platform"

for type in ClaimRequest ClaimOwnership LeaseRenewalRequest PersistenceWriteOutcome PersistenceWriteResult PersistenceWriteVerifier; do
  test -f "$KERNEL/$type.java"
done

# All claim-aware feature modules and the persistence platform must declare the kernel contract directly.
for module in \
  domain-events \
  integration-events \
  execution-control \
  adapter-action \
  database-platform; do
  grep -q '<artifactId>kernel</artifactId>' "$module/pom.xml"
done

# Dispatch must claim atomically, persist with ownership fencing, and never hold a Spring transaction across HTTP.
grep -q 'DISPATCHING' "$DISPATCH_MODEL/DispatchRequestStatus.java"
grep -q 'claimById(dispatchRequestId, claimRequest)' "$DISPATCH/DispatchExecutionService.java"
grep -q 'claimExecutable(claimRequest)' "$DISPATCH/DispatchExecutionService.java"
# Claim one row immediately before each synchronous gateway call so a batch cannot age out while waiting.
grep -q 'claimRequest(OffsetDateTime.now(ZoneOffset.UTC), 1)' "$DISPATCH/DispatchExecutionService.java"
grep -q 'effectiveClaimLease()' "$DISPATCH/DispatchExecutionService.java"
grep -q 'executeClaimed(request, ownership(request), startedAt)' "$DISPATCH/DispatchExecutionService.java"
# Synchronous workers must claim one row immediately before each external operation.
grep -q 'properties.getClaimLease(),[[:space:]]*$' \
  domain-events/src/main/java/com/opensocket/aievent/core/outbox/OutboxEventDispatcher.java
grep -q 'batch.getFirst()' \
  domain-events/src/main/java/com/opensocket/aievent/core/outbox/OutboxEventDispatcher.java
grep -q 'batch.getFirst()' \
  integration-events/src/main/java/com/opensocket/aievent/core/integration/IntegrationEventDeliveryService.java
grep -q 'nettyDispatchPort.dispatch(request)' "$DISPATCH/DispatchExecutionService.java"
grep -q 'dispatchRepository.saveClaimed(request, ownership)' "$DISPATCH/DispatchExecutionService.java"
if grep -q '@Transactional' "$DISPATCH/DispatchExecutionService.java"; then
  echo 'DispatchExecutionService must not hold a database transaction across the gateway call' >&2
  exit 1
fi

for field in claimedBy claimStartedAt claimUntil; do
  grep -q "$field" "$DISPATCH_MODEL/DispatchRequest.java"
done

# SQL claims must be multi-node safe and terminal writes must verify the ownership fence.
for xml in \
  "$DB/src/main/resources/mybatis/postgresql/domainevent/OutboxEventDao.xml" \
  "$DB/src/main/resources/mybatis/postgresql/integrationevent/IntegrationEventDao.xml" \
  "$DB/src/main/resources/mybatis/postgresql/execution/DispatchRequestDao.xml"; do
  grep -qi 'for update skip locked' "$xml"
  grep -q 'claimed_by' "$xml"
  grep -q 'claim_until' "$xml"
done

grep -q 'saveClaimed' "$DB/src/main/resources/mybatis/postgresql/adapter/AdapterActionDao.xml"
grep -q 'recoverExpiredClaim' "$DB/src/main/resources/mybatis/postgresql/adapter/AdapterActionDao.xml"
grep -q 'lease_expires_at' "$DB/src/main/resources/mybatis/postgresql/adapter/AdapterActionDao.xml"

# V20 and the concurrency suite are mandatory release gates.
test -f "$DB/src/main/resources/db/migration/V20__dispatch_claim_lease.sql"
grep -q 'V20__dispatch_claim_lease.sql' deploy/sql/postgresql/common/01_schema.sql
grep -q 'idx_dispatch_requests_claimable' deploy/sql/postgresql/common/01_schema.sql
grep -q 'ux_dispatch_requests_open_assignment' deploy/sql/postgresql/common/90_verify_schema.sql
test -f control-plane-app/src/test/java/com/opensocket/aievent/core/container/PostgresClaimLeaseConcurrencyContainerTest.java
grep -q 'concurrentOutboxWorkersMustNotClaimTheSameRows' \
  control-plane-app/src/test/java/com/opensocket/aievent/core/container/PostgresClaimLeaseConcurrencyContainerTest.java
grep -q 'concurrentDispatchWorkersMustClaimOnlyOnceAndFenceStaleCompletion' \
  control-plane-app/src/test/java/com/opensocket/aievent/core/container/PostgresClaimLeaseConcurrencyContainerTest.java

# Architecture baselines must include the reviewed kernel edges and persistence contracts.
for source in adapter-action database-platform domain-events execution-control integration-events; do
  grep -q "^${source},foundation-kernel," architecture/baseline/m8-context-edges.csv
done
for type in ClaimOwnership ClaimRequest LeaseRenewalRequest PersistenceWriteResult; do
  grep -q "com.opensocket.aievent.core.kernel.persistence.${type}" \
    architecture/baseline/p4-database-platform-contract-imports.csv
done

# Prevent generated Python bytecode and build products from entering release archives.
if find . -type d -name target -print -quit | grep -q .; then
  echo 'Generated target directories must not be present in the source release' >&2
  exit 1
fi

python3 scripts/architecture/verify-dependency-baseline.py
grep -q "$VERSION" README.md
grep -q "$VERSION" kernel/src/main/java/com/opensocket/aievent/core/kernel/CoreVersion.java

echo 'P5 transaction, claim, and lease hardening verification passed.'
