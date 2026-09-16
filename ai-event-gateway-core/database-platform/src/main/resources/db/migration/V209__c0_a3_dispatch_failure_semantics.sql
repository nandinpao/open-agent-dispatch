-- C0-A3: typed dispatch failure semantics.
-- Infrastructure retries, reassignment, policy/security blocks and terminal failures are durable
-- outcomes; they must not all collapse into DEAD_LETTER.

alter table dispatch_requests drop constraint if exists ck_dispatch_outbox_status_p2d;
alter table dispatch_requests add constraint ck_dispatch_outbox_status_p2d
  check(outbox_status in ('PENDING','CLAIMED','DISPATCHING','ACKNOWLEDGED','FAILED_RETRYABLE','ABANDONED','BLOCKED','DEAD_LETTER'));

alter table dispatch_requests drop constraint if exists ck_dispatch_recovery_classification_p2d;
alter table dispatch_requests add constraint ck_dispatch_recovery_classification_p2d
  check(recovery_classification in (
    'NONE','RESPONSE_LOST','ASSIGNMENT_STATE_UNCERTAIN','ACK_PERSISTENCE_UNCERTAIN',
    'LEASE_EXPIRED','RETRY_EXHAUSTED','POLICY_OR_PERMISSION_BLOCKED',
    'INFRASTRUCTURE_RETRY','REASSIGN_REQUIRED','POLICY_BLOCKED','SECURITY_BLOCKED','TERMINAL_FAILURE'
  ));

comment on column dispatch_requests.outbox_status is
  'Durable worker lifecycle. ABANDONED means stale assignment/lease must not be retried; BLOCKED means policy/security authority intentionally prevents dispatch.';
comment on column dispatch_requests.recovery_classification is
  'Typed recovery semantics. C0-A3 forbids inferring retry/reassign/block behavior from last_error text.';
