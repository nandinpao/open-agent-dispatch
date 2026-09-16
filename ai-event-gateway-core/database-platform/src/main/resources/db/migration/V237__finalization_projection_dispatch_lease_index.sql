-- HF18: finalization projection outbox dispatch-lease recovery.
-- DISPATCHED rows are reclaimable after next_attempt_at, so the due index must cover them.
drop index if exists idx_a0r2_finalization_outbox_due;
create index if not exists idx_a0r2_finalization_outbox_due
  on task_finalization_projection_outbox(tenant_id,status,next_attempt_at,outbox_id)
  where status in ('PENDING','FAILED','DISPATCHED');
