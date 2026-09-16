-- HF21 terminal Issue-side-effect convergence.
-- A missing governed Issue Provider is a terminal, non-error projection outcome. The Java/domain
-- model records it as SKIPPED; extend the existing Phase0G outbox status constraint accordingly.
alter table integration_outbox drop constraint if exists ck_integration_outbox_status;
alter table integration_outbox
  add constraint ck_integration_outbox_status
  check(status in (
    'PENDING','CLAIMED','IN_PROGRESS','COMPLETED','SKIPPED',
    'FAILED_RETRYABLE','FAILED_PERMANENT','DEAD_LETTER','CANCELLED'
  ));
