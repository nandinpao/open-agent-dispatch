-- P0C: cancellation fencing, runtime acknowledgement evidence and reconciliation.
create table if not exists a2a_cancellations (
 tenant_id varchar(64) not null, cancellation_id varchar(128) not null, a2a_request_id varchar(128) not null, child_task_id varchar(128) not null,
 assignment_id varchar(128), execution_attempt_id varchar(128), dispatch_request_id varchar(128), agent_id varchar(128), agent_session_id varchar(128), owner_gateway_node_id varchar(128),
 revoked_fencing_token_hash varchar(96), active_fencing_token_hash varchar(96), status varchar(32) not null, reason text, requested_by_type varchar(32), requested_by_id varchar(128),
 delivery_status varchar(64), last_error text, requested_at timestamptz not null default now(), delivery_at timestamptz, acknowledged_at timestamptz, deadline_at timestamptz not null, updated_at timestamptz not null default now(), version bigint not null default 1,
 primary key(tenant_id,cancellation_id), unique(tenant_id,a2a_request_id)
);
create table if not exists a2a_cancellation_evidence (
 tenant_id varchar(64) not null, evidence_id varchar(128) not null, cancellation_id varchar(128) not null, a2a_request_id varchar(128) not null,
 evidence_type varchar(64) not null, evidence_reference varchar(255), evidence_hash varchar(96), decision varchar(32) not null, reason_code varchar(128), details text, occurred_at timestamptz not null default now(), primary key(tenant_id,evidence_id)
);
create table if not exists a2a_reconciliation_cases (
 tenant_id varchar(64) not null, case_id varchar(128) not null, a2a_request_id varchar(128) not null, cancellation_id varchar(128), case_type varchar(64) not null,
 status varchar(32) not null default 'OPEN', reason_code varchar(128) not null, reason text, evidence_summary text, recommended_action text, attempt_count int not null default 0,
 next_attempt_at timestamptz, resolved_by varchar(128), resolution_reason text, created_at timestamptz not null default now(), updated_at timestamptz not null default now(), resolved_at timestamptz, version bigint not null default 1,
 primary key(tenant_id,case_id)
);
create index if not exists idx_a2a_cancellations_due on a2a_cancellations(tenant_id,status,deadline_at);
create index if not exists idx_a2a_cancellations_due_global on a2a_cancellations(status,deadline_at);
create index if not exists idx_a2a_cancel_evidence_timeline on a2a_cancellation_evidence(tenant_id,cancellation_id,occurred_at);
create index if not exists idx_a2a_reconcile_open on a2a_reconciliation_cases(tenant_id,status,created_at desc);
