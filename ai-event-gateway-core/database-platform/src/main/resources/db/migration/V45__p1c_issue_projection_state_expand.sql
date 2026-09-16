-- P1C: external Issue Projection desired/observed state.
-- OpenDispatch Task/A2A/Assignment/Result remain canonical authorities.
create table if not exists issue_projection_states (
    tenant_id varchar(128) not null,
    projection_id varchar(160) not null,
    source_event_id varchar(160) not null,
    source_event_type varchar(128) not null,
    source_aggregate_type varchar(128) not null,
    source_aggregate_id varchar(160) not null,
    task_id varchar(160),
    a2a_request_id varchar(160),
    handoff_snapshot_id varchar(160),
    task_issue_link_id varchar(160),
    connection_id varchar(160),
    project_mapping_id varchar(160),
    desired_state varchar(32) not null,
    observed_state varchar(32) not null,
    lifecycle_state varchar(32) not null,
    conflict_policy varchar(48) not null,
    desired_payload_hash varchar(128),
    observed_payload_hash varchar(128),
    retry_count integer not null default 0,
    max_attempts integer not null default 8,
    next_retry_at timestamptz,
    last_error_code varchar(128),
    last_error_message text,
    version bigint not null default 1,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    completed_at timestamptz,
    correlation_id varchar(160),
    primary key (tenant_id, projection_id),
    unique (tenant_id, source_event_id)
);

create index if not exists idx_issue_projection_due
    on issue_projection_states(lifecycle_state, next_retry_at)
    where lifecycle_state in ('PENDING','UPDATE_PENDING','FAILED');
create index if not exists idx_issue_projection_task
    on issue_projection_states(tenant_id, task_id, updated_at desc);
create index if not exists idx_issue_projection_a2a
    on issue_projection_states(tenant_id, a2a_request_id, updated_at desc);
create index if not exists idx_issue_projection_handoff
    on issue_projection_states(tenant_id, handoff_snapshot_id, updated_at desc);
create index if not exists idx_issue_projection_link
    on issue_projection_states(tenant_id, task_issue_link_id, updated_at desc);

create table if not exists issue_projection_reconciliation_cases (
    tenant_id varchar(128) not null,
    case_id varchar(160) not null,
    projection_id varchar(160) not null,
    reason_code varchar(128) not null,
    evidence_reference varchar(512),
    status varchar(32) not null,
    next_attempt_at timestamptz,
    attempt_count integer not null default 0,
    last_error text,
    resolved_by varchar(160),
    resolution_reason text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    resolved_at timestamptz,
    correlation_id varchar(160),
    primary key (tenant_id, case_id),
    foreign key (tenant_id, projection_id)
        references issue_projection_states(tenant_id, projection_id)
);

create index if not exists idx_issue_projection_case_open
    on issue_projection_reconciliation_cases(tenant_id, projection_id, status, created_at desc)
    where status in ('OPEN','RETRY_SCHEDULED','WAIT_HUMAN');

comment on table issue_projection_states is
'External Issue desired/observed state. This table is not Task, A2A, Assignment or Result authority.';
comment on column handoff_context_snapshots.source_issue_link_id is
'Legacy compatibility only. P1C Handoff domain no longer reads or writes this provider-specific column.';
comment on column handoff_context_snapshots.target_issue_link_id is
'Legacy compatibility only. P1C Handoff domain no longer reads or writes this provider-specific column.';
