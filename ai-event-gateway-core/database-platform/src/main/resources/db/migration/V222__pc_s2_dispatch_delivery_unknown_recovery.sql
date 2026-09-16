-- PC-S2 Dispatch Runtime Correctness.
-- SEND_STARTED + unknown transport outcome must never be treated as proof of non-delivery.
-- Hold the bridge request in RECOVERY_PENDING until callback/reconciliation produces evidence.

alter table dispatch_requests drop constraint if exists ck_dispatch_outbox_status_p2d;
alter table dispatch_requests add constraint ck_dispatch_outbox_status_p2d
  check(outbox_status in (
    'PENDING','CLAIMED','DISPATCHING','ACKNOWLEDGED','FAILED_RETRYABLE',
    'RECOVERY_PENDING','ABANDONED','BLOCKED','DEAD_LETTER'
  ));

create index if not exists idx_dispatch_requests_delivery_unknown_pc_s2
  on dispatch_requests(uncertain_since asc nulls last, updated_at asc)
  where recovery_classification='RESPONSE_LOST' or outbox_status='RECOVERY_PENDING';

comment on column dispatch_requests.outbox_status is
  'Durable worker lifecycle. PC-S2 RECOVERY_PENDING means SEND_STARTED may have reached the executor; automatic resend/reassignment is forbidden until evidence resolves the outcome.';
comment on column dispatch_requests.recovery_classification is
  'Typed recovery semantics. RESPONSE_LOST is an unknown delivery outcome and must not enter ordinary runtime-failure retry/reassignment.';
