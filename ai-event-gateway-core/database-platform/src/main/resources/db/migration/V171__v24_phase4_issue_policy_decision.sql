-- v24 Phase 4: durable Issue policy authority. Projection/Outbox/Link retain independent lifecycles.
create table if not exists issue_policy_decisions (
    tenant_id varchar(128) not null,
    decision_id varchar(160) not null,
    task_id varchar(160) not null,
    projection_purpose varchar(64) not null default 'PRIMARY_ISSUE',
    policy_id varchar(160) not null,
    policy_version integer not null default 1,
    task_issue_sync_policy varchar(32) not null,
    decision varchar(32) not null,
    reason_code varchar(160) not null,
    source_event_id varchar(160) not null,
    source_event_type varchar(128) not null,
    task_status varchar(64),
    binding_status varchar(64) not null default 'NOT_EVALUATED',
    connection_id varchar(160),
    project_mapping_id varchar(160),
    project_mapping_version integer,
    project_mapping_schema_hash varchar(160),
    projection_id varchar(160),
    task_issue_link_id varchar(160),
    outbox_id varchar(160),
    automation_status varchar(64) not null,
    last_error_code varchar(160),
    last_error_message text,
    correlation_id varchar(160),
    causation_id varchar(160),
    trace_id varchar(160),
    actor_type varchar(64),
    actor_id varchar(160),
    version bigint not null default 1,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (tenant_id, decision_id),
    unique (tenant_id, task_id, projection_purpose),
    constraint ck_issue_policy_decision check (decision in ('NOT_REQUIRED','REQUIRED','MANUAL_DECISION')),
    constraint ck_issue_policy_binding_status check (binding_status in ('NOT_REQUIRED','NOT_EVALUATED','RESOLVED','MAPPING_NOT_FOUND','MAPPING_AMBIGUOUS','MAPPING_NOT_ACTIVE','MAPPING_SCHEMA_UNAVAILABLE','CONNECTION_UNAVAILABLE')),
    constraint ck_issue_policy_automation_status check (automation_status in ('NOT_REQUIRED','WAITING_MANUAL_DECISION','BINDING_BLOCKED','INTENT_READY','MATERIALIZED','FAILED'))
);
create index if not exists idx_issue_policy_decisions_task on issue_policy_decisions(tenant_id,task_id,created_at desc);
create index if not exists idx_issue_policy_decisions_correlation on issue_policy_decisions(tenant_id,correlation_id,created_at desc);
alter table issue_policy_decisions enable row level security;
drop policy if exists tenant_isolation on issue_policy_decisions;
create policy tenant_isolation on issue_policy_decisions using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
comment on table issue_policy_decisions is 'Durable business decision explaining whether a Task requires an external Issue. Not Projection, Outbox, Provider or TaskIssueLink lifecycle authority.';
