-- PC-S6 Runtime Operational Resilience
-- These indexes match the canonical due-work ordering used by Dispatch and A2A reconciliation.
-- They do not change claim authority, lease semantics, retry semantics, or tenant isolation.

create index if not exists idx_dispatch_requests_claim_scan_pc_s6
    on dispatch_requests(updated_at asc nulls first, created_at asc)
    where status in ('APPROVED','RETRY_WAITING','DISPATCHING');

create index if not exists idx_a2a_reconciliation_due_pc_s6
    on a2a_reconciliation_cases((coalesce(next_attempt_at,created_at)), created_at)
    where status in ('OPEN','READY','RETRY_WAITING','CLAIMED','EXECUTING');

create index if not exists idx_a2a_reconciliation_executable_pc_s6
    on a2a_reconciliation_cases(updated_at asc, created_at asc)
    where status='READY';

create index if not exists idx_a2a_tracking_due_pc_s6
    on a2a_remote_tracking_leases((coalesce(next_poll_at,updated_at)), tracking_id)
    where status in ('PENDING','ACTIVE','RECONCILING','CANCELING');

comment on index idx_dispatch_requests_claim_scan_pc_s6 is
  'PC-S6 ordering support for canonical Dispatch claimExecutable scan; due predicates remain evaluated by canonical query.';
comment on index idx_a2a_reconciliation_due_pc_s6 is
  'PC-S6 ordering support for multi-replica reconciliation claimDue; FOR UPDATE SKIP LOCKED remains the concurrency authority.';
comment on index idx_a2a_reconciliation_executable_pc_s6 is
  'PC-S6 ordering support for canonical reconciliation repair claimExecutable.';
comment on index idx_a2a_tracking_due_pc_s6 is
  'PC-S6 ordering support for authoritative remote tracking claimDue; lease/token/epoch/stream fencing is unchanged.';
