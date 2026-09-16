-- P1B A2A Operations Workspace read-model support.
-- These indexes do not create a second authority or change mutation semantics.

create index if not exists idx_a2a_result_quarantine_request_open
    on a2a_result_quarantine(tenant_id, a2a_request_id, status, quarantined_at desc);

create index if not exists idx_a2a_reconciliation_request_open
    on a2a_reconciliation_cases(tenant_id, a2a_request_id, status, created_at desc);

create index if not exists idx_a2a_cancellations_request_recent
    on a2a_cancellations(tenant_id, a2a_request_id, requested_at desc);
