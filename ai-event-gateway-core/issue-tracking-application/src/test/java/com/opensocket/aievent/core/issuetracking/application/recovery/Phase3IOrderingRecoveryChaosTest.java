package com.opensocket.aievent.core.issuetracking.application.recovery;

import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.opensocket.aievent.core.issuetracking.core.ProviderCircuitBreakerPolicy;
import com.opensocket.aievent.core.issuetracking.recovery.*;

/** Runtime test source for the Phase 3I Maven/PostgreSQL chaos gate. */
class Phase3IOrderingRecoveryChaosTest {
 @Test void closeAndCommentRemainBehindCreateUntilAcknowledged() {
  var repo=new InMemoryProjectionRecoveryRepository();var ledger=new ProjectionRecoveryEventLedger(repo);var lanes=new OrderedProjectionLaneService(repo,ledger);var worker=new OrderedProjectionWorkerService(repo,ledger);
  var create=lanes.enqueue("t","a",ProjectionLaneType.EXTERNAL_ISSUE,"c","m",ProjectionOperationType.CREATE,"a","","p","h1","","",20,5,"actor","corr");
  var comment=lanes.enqueue("t","a",ProjectionLaneType.EXTERNAL_ISSUE,"c","m",ProjectionOperationType.COMMENT,"a","","p","h2","",create.workId(),20,5,"actor","corr");
  assertEquals(ProjectionWorkStatus.BLOCKED,comment.status());
  var first=worker.claim("t","w",60,10,"corr").getFirst();assertEquals(create.workId(),first.work().workId());
  worker.acknowledge("t",create.workId(),first.work().version(),first.claimToken(),"w","corr");
  assertEquals(comment.workId(),worker.claim("t","w",60,10,"corr").getFirst().work().workId());
 }
 @Test void lostResponseUsesReadbackAndIndexDelayNeverBlindlyCreates() {
  var repo=new InMemoryProjectionRecoveryRepository();var ledger=new ProjectionRecoveryEventLedger(repo);var lanes=new OrderedProjectionLaneService(repo,ledger);
  var work=lanes.enqueue("t","a",ProjectionLaneType.EXTERNAL_ISSUE,"c","m",ProjectionOperationType.CREATE,"a","","p","h","","",20,5,"actor","corr");
  var gateway=new ProjectionProviderRecoveryGateway(){public ProviderReadbackResult readback(ProviderReadbackCommand command){return new ProviderReadbackResult(ProviderReadbackStatus.INDEX_DELAY,null,null,null,"marker",null,"index lag");}public ReconciliationRepairResult repair(ReconciliationRepairCommand command){throw new AssertionError("repair not expected");}};
  var result=new ProjectionRecoveryService(repo,Optional.of(gateway),ledger).recover("t",work.workId(),"actor","response lost","corr");
  assertEquals(ProjectionRecoveryCaseType.PROVIDER_INDEX_DELAY,result.caseType());assertEquals(ProjectionRecoveryCaseStatus.FAILED_RETRYABLE,result.status());
 }
 @Test void expiredWorkerLeaseReturnsToRetryAndSaturationOpensCircuit() {
  var policy=new ProviderCircuitBreakerPolicy();assertEquals(ProviderHealthStatus.OPEN_CIRCUIT,policy.next(ProviderHealthStatus.HEALTHY,5,100,100,true,false));
  var repo=new InMemoryProjectionRecoveryRepository();var ledger=new ProjectionRecoveryEventLedger(repo);var lanes=new OrderedProjectionLaneService(repo,ledger);var worker=new OrderedProjectionWorkerService(repo,ledger);
  var work=lanes.enqueue("t","a",ProjectionLaneType.TASK,"c","m",ProjectionOperationType.UPDATE,"a","","p","h","k","",20,2,"actor","corr");
  var claim=worker.claim("t","w",60,1,"corr").getFirst();var c=claim.work();var stale=new OrderedProjectionWork(c.tenantId(),c.workId(),c.laneId(),c.laneSequence(),c.generation(),c.operationType(),c.status(),c.aggregateId(),c.outboxId(),c.projectionId(),c.payloadHash(),c.coalesceKey(),c.dependsOnWorkId(),c.supersededByWorkId(),c.externalIdempotencyMarker(),c.attemptCount(),c.maxAttempts(),c.claimOwner(),c.claimTokenHash(),OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1),c.nextAttemptAt(),c.lastErrorCode(),c.lastErrorMessage(),c.version()+1,c.createdAt(),OffsetDateTime.now(ZoneOffset.UTC),c.correlationId());
  assertTrue(repo.saveWorkExpectedVersion(stale,c.version()));assertEquals(1,worker.recoverExpiredClaims("t",10,"recovery","worker lease expired","corr"));assertEquals(ProjectionWorkStatus.RETRY_WAITING,repo.findWork("t",work.workId()).orElseThrow().status());
 }
}
