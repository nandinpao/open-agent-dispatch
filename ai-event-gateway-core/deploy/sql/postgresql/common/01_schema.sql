-- ai-event-gateway-core p14.0 PostgreSQL schema
-- Generated from src/main/resources/db/migration/V1..V14 for DBA/offline installation. Runtime Flyway migrations remain under classpath:db/migration.
-- Flyway remains the runtime migration source of truth.


-- ======================================================================
-- V1__incident_store.sql
-- ======================================================================
create table if not exists incidents (
    incident_id text primary key,
    fingerprint text not null,
    tenant_id text not null,
    source_system text not null,
    site_id text,
    plant_id text,
    object_type text,
    object_id text,
    event_type text,
    error_code text,
    severity text not null,
    status text not null,
    first_seen_at timestamptz not null,
    last_seen_at timestamptz not null,
    occurrence_count bigint not null default 0,
    last_message text,
    linked_task_id text,
    linked_issue_id text,
    resolved_at timestamptz,
    reopened_at timestamptz,
    reopen_count integer not null default 0,
    lifecycle_reason text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_incidents_fingerprint on incidents (fingerprint);
create index if not exists idx_incidents_active_fingerprint on incidents (fingerprint, last_seen_at desc) where status in ('ACTIVE', 'ESCALATED');
create index if not exists idx_incidents_site_status on incidents (site_id, status, last_seen_at desc);
create index if not exists idx_incidents_plant_object on incidents (plant_id, object_type, object_id, last_seen_at desc);
create index if not exists idx_incidents_severity_status on incidents (severity, status, last_seen_at desc);
create index if not exists idx_incidents_lifecycle_stale_active on incidents (last_seen_at asc) where status in ('ACTIVE', 'ESCALATED');
create index if not exists idx_incidents_fingerprint_latest on incidents (fingerprint, last_seen_at desc);

create table if not exists incident_occurrence_summary (
    summary_id text primary key,
    incident_id text not null references incidents(incident_id) on delete cascade,
    fingerprint text not null,
    tenant_id text not null,
    source_system text not null,
    site_id text,
    plant_id text,
    object_type text,
    object_id text,
    event_type text,
    error_code text,
    window_start timestamptz not null,
    window_end timestamptz not null,
    occurrence_count bigint not null default 0,
    max_severity text not null,
    latest_event_id text,
    latest_message text,
    latest_payload_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_incident_occurrence_window unique (incident_id, window_start, window_end)
);

create index if not exists idx_incident_summary_incident_window on incident_occurrence_summary (incident_id, window_start desc);
create index if not exists idx_incident_summary_site_window on incident_occurrence_summary (site_id, window_start desc);

create table if not exists event_decisions (
    event_id text primary key,
    fingerprint text not null,
    incident_id text not null references incidents(incident_id) on delete cascade,
    decision_type text not null,
    duplicate boolean not null,
    occurrence_count bigint not null,
    actions_json jsonb not null default '[]'::jsonb,
    reason text,
    decided_at timestamptz not null
);

create index if not exists idx_event_decisions_incident on event_decisions (incident_id, decided_at desc);
create index if not exists idx_event_decisions_fingerprint on event_decisions (fingerprint, decided_at desc);
create index if not exists idx_event_decisions_decided_at on event_decisions (decided_at desc);

create table if not exists event_dedup_state (
    fingerprint text primary key,
    active_incident_id text,
    tenant_id text not null,
    source_system text not null,
    site_id text,
    plant_id text,
    object_type text,
    object_id text,
    event_type text,
    error_code text,
    first_seen_at timestamptz not null,
    last_seen_at timestamptz not null,
    occurrence_count bigint not null default 0,
    max_severity text,
    last_event_id text,
    last_message text,
    expires_at timestamptz,
    updated_at timestamptz not null default now()
);

create index if not exists idx_event_dedup_state_incident on event_dedup_state (active_incident_id);
create index if not exists idx_event_dedup_state_expires_at on event_dedup_state (expires_at);

create or replace function severity_rank(severity text)
returns integer
language sql
immutable
as $$
    select case upper(coalesce(severity, 'MEDIUM'))
        when 'LOW' then 1
        when 'MEDIUM' then 2
        when 'HIGH' then 3
        when 'CRITICAL' then 4
        else 2
    end
$$;


-- ======================================================================
-- V2__task_decision_store.sql
-- ======================================================================
create table if not exists tasks (
    task_id text primary key,
    incident_id text not null references incidents(incident_id) on delete cascade,
    source_event_id text,
    task_type text not null,
    status text not null,
    priority text not null,
    tenant_id text not null,
    site_id text,
    plant_id text,
    object_type text,
    object_id text,
    event_type text,
    error_code text,
    assigned_pool_id text,
    target_pool_id text,
    classification_status text not null default 'CLASSIFIED',
    classification_result_json jsonb not null default '{}'::jsonb,
    routing_policy text not null,
    required_capabilities_json jsonb not null default '[]'::jsonb,
    created_reason text,
    occurrence_count_at_creation bigint not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    timeout_at timestamptz,
    terminal_at timestamptz,
    reassignment_count integer not null default 0,
    lifecycle_reason text
);

create index if not exists idx_tasks_incident_created on tasks (incident_id, created_at desc);
create index if not exists idx_tasks_incident_type_status on tasks (incident_id, task_type, status, created_at desc);
create index if not exists idx_tasks_site_status on tasks (site_id, status, created_at desc);
create index if not exists idx_tasks_tenant_status on tasks (tenant_id, status, created_at desc);
create index if not exists idx_tasks_classification_status on tasks (tenant_id, classification_status, updated_at desc);
create index if not exists idx_tasks_status_priority on tasks (status, priority, created_at desc);
create index if not exists idx_tasks_lifecycle_open_updated on tasks (updated_at asc) where status not in ('COMPLETED', 'FAILED', 'TIMED_OUT', 'CANCELLED');
create index if not exists idx_tasks_lifecycle_status_updated on tasks (status, updated_at asc);


-- ======================================================================
-- V3__agent_assignment_routing.sql
-- ======================================================================
create table if not exists agents (
    agent_id text primary key,
    agent_type text,
    owner_gateway_node_id text not null,
    site_id text,
    site_name text,
    region text,
    zone text,
    status text not null,
    capabilities_json jsonb not null default '[]'::jsonb,
    current_task_count integer not null default 0,
    max_concurrent_tasks integer not null default 1,
    health_score integer not null default 100,
    last_heartbeat_at timestamptz,
    lease_expires_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_agents_site_status on agents (site_id, status, last_heartbeat_at desc);
create index if not exists idx_agents_gateway_status on agents (owner_gateway_node_id, status, last_heartbeat_at desc);
create index if not exists idx_agents_capabilities on agents using gin (capabilities_json);

create table if not exists routing_decisions (
    decision_id text primary key,
    task_id text not null references tasks(task_id) on delete cascade,
    incident_id text not null references incidents(incident_id) on delete cascade,
    routing_policy text not null,
    status text not null,
    selected_agent_id text,
    selected_gateway_node_id text,
    selected_site_id text,
    selected_score integer not null default 0,
    decision_reason text,
    user_facing_error_json jsonb,
    candidates_json jsonb not null default '[]'::jsonb,
    created_at timestamptz not null
);

create index if not exists idx_routing_decisions_task on routing_decisions (task_id, created_at desc);
create index if not exists idx_routing_decisions_incident on routing_decisions (incident_id, created_at desc);
create index if not exists idx_routing_decisions_selected_agent on routing_decisions (selected_agent_id, created_at desc);

create table if not exists task_assignments (
    assignment_id text primary key,
    task_id text not null references tasks(task_id) on delete cascade,
    incident_id text not null references incidents(incident_id) on delete cascade,
    agent_id text,
    agent_type text,
    owner_gateway_node_id text,
    site_id text,
    status text not null,
    routing_policy text not null,
    routing_decision_id text references routing_decisions(decision_id) on delete set null,
    score integer not null default 0,
    reason text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists idx_task_assignments_task on task_assignments (task_id, created_at desc);
create index if not exists idx_task_assignments_agent on task_assignments (agent_id, created_at desc);
create index if not exists idx_task_assignments_site_status on task_assignments (site_id, status, created_at desc);
create index if not exists idx_task_assignments_status on task_assignments (status, created_at desc);


-- ======================================================================
-- V4__dispatch_request_contract.sql
-- ======================================================================
create table if not exists dispatch_requests (
    dispatch_request_id text primary key,
    assignment_id text not null references task_assignments(assignment_id) on delete cascade,
    task_id text not null references tasks(task_id) on delete cascade,
    incident_id text not null references incidents(incident_id) on delete cascade,
    agent_id text,
    owner_gateway_node_id text,
    site_id text,
    status text not null,
    review_mode text not null,
    eligibility_status text not null,
    dispatch_method text not null,
    gateway_dispatch_path text not null,
    dispatch_token text,
    reason text,
    command_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    approved_at timestamptz
);

create index if not exists idx_dispatch_requests_assignment on dispatch_requests (assignment_id, created_at desc);
create index if not exists idx_dispatch_requests_task on dispatch_requests (task_id, created_at desc);
create index if not exists idx_dispatch_requests_status on dispatch_requests (status, created_at desc);
create index if not exists idx_dispatch_requests_gateway on dispatch_requests (owner_gateway_node_id, status, created_at desc);
create index if not exists idx_dispatch_requests_agent on dispatch_requests (agent_id, created_at desc);


-- ======================================================================
-- V5__netty_dispatch_client_integration.sql
-- ======================================================================
alter table dispatch_requests
    add column if not exists dispatched_at timestamptz,
    add column if not exists failed_at timestamptz,
    add column if not exists attempt_count integer not null default 0,
    add column if not exists last_error text;

create index if not exists idx_dispatch_requests_status_updated on dispatch_requests (status, updated_at desc);
create index if not exists idx_dispatch_requests_approved on dispatch_requests (status, approved_at desc) where status = 'APPROVED';


-- ======================================================================
-- V6__task_result_relay_and_dispatch_recovery.sql
-- ======================================================================
create table if not exists task_callbacks (
    callback_id varchar(128) primary key,
    callback_type varchar(32) not null,
    task_id varchar(80) not null,
    dispatch_request_id varchar(80),
    assignment_id varchar(80),
    agent_id varchar(160),
    owner_gateway_node_id varchar(160),
    message text,
    progress_percent integer,
    error_code varchar(120),
    error_message text,
    payload_json jsonb not null default '{}'::jsonb,
    occurred_at timestamptz,
    processed_at timestamptz not null default now(),
    duplicate boolean not null default false,
    previous_task_status varchar(40),
    new_task_status varchar(40),
    previous_dispatch_status varchar(40),
    new_dispatch_status varchar(40)
);

create index if not exists idx_task_callbacks_task on task_callbacks(task_id, processed_at desc);
create index if not exists idx_task_callbacks_dispatch on task_callbacks(dispatch_request_id, processed_at desc);
create index if not exists idx_task_callbacks_type_time on task_callbacks(callback_type, processed_at desc);
create index if not exists idx_task_callbacks_agent_time on task_callbacks(agent_id, processed_at desc);

alter table dispatch_requests
    add column if not exists last_callback_id varchar(128),
    add column if not exists completed_at timestamptz,
    add column if not exists timed_out_at timestamptz;

create index if not exists idx_dispatch_requests_timeout_scan
    on dispatch_requests(status, dispatched_at, updated_at)
    where status in ('DISPATCHED', 'ACKED', 'RUNNING');


-- ======================================================================
-- V7__adapter_action_orchestration.sql
-- ======================================================================
create table if not exists adapter_actions (
    action_id varchar(80) primary key,
    idempotency_key varchar(256) not null,
    incident_id varchar(80),
    task_id varchar(80),
    dispatch_request_id varchar(80),
    assignment_id varchar(80),
    agent_id varchar(160),
    adapter_name varchar(160) not null,
    adapter_type varchar(40) not null,
    action_type varchar(80) not null,
    status varchar(40) not null,
    reason text,
    request_hash varchar(128),
    response_ref text,
    payload_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    completed_at timestamptz,
    failed_at timestamptz,
    last_error text
);

create unique index if not exists ux_adapter_actions_idempotency_key on adapter_actions(idempotency_key);
create index if not exists idx_adapter_actions_incident on adapter_actions(incident_id, created_at desc);
create index if not exists idx_adapter_actions_task on adapter_actions(task_id, created_at desc);
create index if not exists idx_adapter_actions_status on adapter_actions(status, created_at desc);
create index if not exists idx_adapter_actions_adapter_type on adapter_actions(adapter_type, action_type, created_at desc);
create index if not exists idx_adapter_actions_dispatch on adapter_actions(dispatch_request_id, created_at desc);


-- ======================================================================
-- V8__adapter_executor_contract.sql
-- ======================================================================
alter table adapter_actions
    add column if not exists executing_at timestamptz,
    add column if not exists next_attempt_at timestamptz,
    add column if not exists attempt_count integer not null default 0,
    add column if not exists max_attempts integer not null default 0,
    add column if not exists executor_name varchar(160);

create index if not exists idx_adapter_actions_executable_pending
    on adapter_actions(status, next_attempt_at, created_at)
    where status = 'PENDING';

create index if not exists idx_adapter_actions_executor
    on adapter_actions(executor_name, status, updated_at desc);


-- ======================================================================
-- V9__real_adapter_executor_modules.sql
-- ======================================================================
alter table adapter_actions
    add column if not exists retry_waiting_at timestamptz,
    add column if not exists executor_unavailable_at timestamptz;

create table if not exists adapter_executor_audit (
    audit_id varchar(80) primary key,
    action_id varchar(80) not null,
    task_id varchar(80),
    incident_id varchar(80),
    adapter_type varchar(80),
    action_type varchar(80),
    executor_name varchar(160),
    before_status varchar(80),
    after_status varchar(80),
    outcome varchar(80),
    message text,
    attempt_count integer not null default 0,
    created_at timestamptz not null,
    payload_snapshot_json jsonb not null default '{}'::jsonb
);

create index if not exists idx_adapter_actions_executable_retry_waiting
    on adapter_actions(status, next_attempt_at, created_at)
    where status in ('PENDING', 'RETRY_WAITING', 'EXECUTOR_UNAVAILABLE');

create index if not exists idx_adapter_executor_audit_action
    on adapter_executor_audit(action_id, created_at desc);

create index if not exists idx_adapter_executor_audit_task
    on adapter_executor_audit(task_id, created_at desc);

create index if not exists idx_adapter_executor_audit_executor
    on adapter_executor_audit(executor_name, created_at desc);


-- ======================================================================
-- V10__p0_p1_hardening_indexes.sql
-- ======================================================================
-- P0/P1 hardening: prevent duplicate active incidents and duplicate open tasks under concurrent core instances.
-- These are PostgreSQL partial unique indexes and are intentionally placed in the PostgreSQL migration path only.

create unique index if not exists ux_incidents_active_fingerprint
    on incidents (fingerprint)
    where status in ('ACTIVE', 'ESCALATED');

create unique index if not exists ux_tasks_open_incident_type
    on tasks (incident_id, task_type)
    where status not in ('COMPLETED', 'FAILED', 'TIMED_OUT', 'CANCELLED');


-- ======================================================================
-- V11__gateway_agent_directory.sql
-- ======================================================================
create table if not exists gateway_nodes (
    gateway_node_id text primary key,
    node_name text,
    host_name text,
    advertise_host text,
    http_port integer,
    ws_port integer,
    region text,
    zone text,
    site_id text,
    status text not null,
    version text,
    metadata_json jsonb not null default '{}'::jsonb,
    registered_at timestamptz,
    last_heartbeat_at timestamptz,
    lease_expires_at timestamptz,
    updated_at timestamptz
);

create index if not exists idx_gateway_nodes_status_heartbeat
    on gateway_nodes(status, last_heartbeat_at desc);
create index if not exists idx_gateway_nodes_site_status
    on gateway_nodes(site_id, status, last_heartbeat_at desc);
create index if not exists idx_gateway_nodes_region_zone
    on gateway_nodes(region, zone, status, last_heartbeat_at desc);

alter table agents
    add column if not exists agent_session_id text,
    add column if not exists connected_at timestamptz,
    add column if not exists disconnected_at timestamptz;

create index if not exists idx_agents_session on agents(agent_session_id);
create index if not exists idx_agents_gateway_session_status
    on agents(owner_gateway_node_id, agent_session_id, status, last_heartbeat_at desc);

alter table routing_decisions
    add column if not exists selected_agent_session_id text,
    add column if not exists user_facing_error_json jsonb;

create index if not exists idx_routing_decisions_user_facing_error_code
    on routing_decisions ((user_facing_error_json ->> 'code'))
    where user_facing_error_json is not null;

alter table task_assignments
    add column if not exists agent_session_id text;

create index if not exists idx_task_assignments_gateway_session
    on task_assignments(owner_gateway_node_id, agent_session_id, status, created_at desc);

alter table dispatch_requests
    add column if not exists agent_session_id text;

create index if not exists idx_dispatch_requests_gateway_session
    on dispatch_requests(owner_gateway_node_id, agent_session_id, status, created_at desc);


-- ======================================================================
-- V12__dispatch_callback_reliability.sql
-- ======================================================================
-- P11.1 dispatch/callback reliability hardening.

alter table dispatch_requests
    add column if not exists retry_waiting_at timestamptz,
    add column if not exists next_retry_at timestamptz,
    add column if not exists dead_letter_at timestamptz;

create index if not exists idx_dispatch_requests_retry_due
    on dispatch_requests(status, next_retry_at, updated_at)
    where status = 'RETRY_WAITING';

create index if not exists idx_dispatch_requests_dead_letter
    on dispatch_requests(status, dead_letter_at desc)
    where status = 'DEAD_LETTER';

alter table task_callbacks
    add column if not exists agent_session_id text,
    add column if not exists attempt_no integer,
    add column if not exists accepted boolean not null default true,
    add column if not exists ignored_reason text;

create index if not exists idx_task_callbacks_acceptance
    on task_callbacks(accepted, ignored_reason, processed_at desc);

create index if not exists idx_task_callbacks_dispatch_attempt
    on task_callbacks(dispatch_request_id, attempt_no, processed_at desc);


-- ======================================================================
-- V13__adapter_external_worker.sql
-- ======================================================================
-- P12.0 external adapter worker lease/claim support.

alter table adapter_actions
    add column if not exists claimed_by text,
    add column if not exists claimed_at timestamptz,
    add column if not exists lease_expires_at timestamptz,
    add column if not exists worker_heartbeat_at timestamptz;

create index if not exists idx_adapter_actions_external_claimable
    on adapter_actions(adapter_type, status, next_attempt_at, lease_expires_at, created_at)
    where status in ('PENDING', 'RETRY_WAITING', 'EXECUTOR_UNAVAILABLE', 'CLAIMED');

create index if not exists idx_adapter_actions_claimed_by
    on adapter_actions(claimed_by, lease_expires_at, updated_at)
    where status = 'CLAIMED';



-- ======================================================================
-- V14__incident_task_lifecycle.sql
-- ======================================================================
alter table incidents add column if not exists resolved_at timestamptz;
alter table incidents add column if not exists reopened_at timestamptz;
alter table incidents add column if not exists reopen_count integer not null default 0;
alter table incidents add column if not exists lifecycle_reason text;
create index if not exists idx_incidents_lifecycle_stale_active on incidents (last_seen_at asc) where status in ('ACTIVE', 'ESCALATED');
create index if not exists idx_incidents_fingerprint_latest on incidents (fingerprint, last_seen_at desc);
alter table tasks add column if not exists timeout_at timestamptz;
alter table tasks add column if not exists terminal_at timestamptz;
alter table tasks add column if not exists reassignment_count integer not null default 0;
alter table tasks add column if not exists lifecycle_reason text;
create index if not exists idx_tasks_lifecycle_open_updated on tasks (updated_at asc) where status not in ('COMPLETED', 'FAILED', 'TIMED_OUT', 'CANCELLED');
create index if not exists idx_tasks_lifecycle_status_updated on tasks (status, updated_at asc);

-- ======================================================================
-- V15__dispatch_callback_adapter_reliability.sql
-- ======================================================================
create index if not exists idx_dispatch_requests_reliability_review
    on dispatch_requests(status, updated_at desc, next_retry_at, dead_letter_at)
    where status in ('FAILED', 'TIMED_OUT', 'RETRY_WAITING', 'DEAD_LETTER');

create index if not exists idx_adapter_actions_reliability_review
    on adapter_actions(status, updated_at desc, next_attempt_at, lease_expires_at)
    where status in ('FAILED', 'CANCELLED', 'CLAIMED', 'RETRY_WAITING', 'EXECUTOR_UNAVAILABLE');

create index if not exists idx_adapter_actions_expired_claim_scan
    on adapter_actions(status, lease_expires_at, updated_at)
    where status = 'CLAIMED';

create index if not exists idx_task_callbacks_ignored_reason_review
    on task_callbacks(ignored_reason, processed_at desc)
    where accepted = false;

-- Phase 5 observability / operations indexes.
CREATE INDEX IF NOT EXISTS idx_incidents_ops_status_updated
    ON incidents (status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_incidents_ops_severity_status
    ON incidents (severity, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_tasks_ops_status_updated
    ON tasks (status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_tasks_ops_priority_status
    ON tasks (priority, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_dispatch_requests_ops_status_updated
    ON dispatch_requests (status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_adapter_actions_ops_status_updated
    ON adapter_actions (status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_agents_ops_status_gateway
    ON agents (status, owner_gateway_node_id);
CREATE INDEX IF NOT EXISTS idx_gateway_nodes_ops_status_site
    ON gateway_nodes (status, site_id);

-- ======================================================================
-- V17__agent_capacity_reservation.sql
-- ======================================================================
alter table agents
    add column if not exists reserved_task_count integer not null default 0;

alter table task_assignments
    add column if not exists capacity_reserved boolean not null default false,
    add column if not exists capacity_reserved_at timestamptz,
    add column if not exists capacity_released_at timestamptz;

create index if not exists idx_agents_assignable_capacity
    on agents(status, site_id, current_task_count, reserved_task_count, max_concurrent_tasks, last_heartbeat_at desc);

create index if not exists idx_task_assignments_capacity_reserved
    on task_assignments(agent_id, capacity_reserved, created_at desc)
    where capacity_reserved = true;

-- M7 module domain events / transactional outbox
create table if not exists module_outbox_events (
    outbox_id text primary key,
    event_id text not null unique,
    event_type text not null,
    aggregate_type text not null,
    aggregate_id text not null,
    payload_json jsonb not null,
    status text not null,
    attempt_count integer not null default 0,
    next_attempt_at timestamptz,
    last_error text,
    claimed_by text,
    claim_until timestamptz,
    created_at timestamptz not null,
    published_at timestamptz,
    updated_at timestamptz not null
);

create index if not exists idx_module_outbox_dispatch
    on module_outbox_events (status, next_attempt_at, claim_until, created_at);
create index if not exists idx_module_outbox_aggregate
    on module_outbox_events (aggregate_type, aggregate_id, created_at desc);
create index if not exists idx_module_outbox_event_type
    on module_outbox_events (event_type, created_at desc);
create table if not exists integration_event_outbox (
    integration_event_id varchar(96) primary key,
    event_id varchar(160) not null unique,
    event_type varchar(160) not null,
    aggregate_type varchar(120),
    aggregate_id varchar(160),
    envelope_json jsonb not null,
    status varchar(32) not null,
    attempt_count integer not null default 0,
    next_attempt_at timestamptz,
    last_error text,
    claimed_by varchar(160),
    claim_until timestamptz,
    created_at timestamptz not null,
    delivered_at timestamptz,
    updated_at timestamptz not null
);
create index if not exists idx_integration_event_outbox_dispatch on integration_event_outbox(status,next_attempt_at,created_at);
create index if not exists idx_integration_event_outbox_claim on integration_event_outbox(status,claim_until);

-- ======================================================================
-- V20__dispatch_claim_lease.sql
-- ======================================================================
alter table dispatch_requests
    add column if not exists claimed_by text,
    add column if not exists claim_started_at timestamptz,
    add column if not exists claim_until timestamptz;

create index if not exists idx_dispatch_requests_claimable
    on dispatch_requests(status, next_retry_at, claim_until, updated_at, created_at)
    where status in ('APPROVED', 'RETRY_WAITING', 'DISPATCHING');

create index if not exists idx_dispatch_requests_claim_owner
    on dispatch_requests(claimed_by, claim_until)
    where status = 'DISPATCHING';

create unique index if not exists ux_dispatch_requests_open_assignment
    on dispatch_requests(assignment_id)
    where status in (
        'PENDING_REVIEW',
        'APPROVED',
        'RETRY_WAITING',
        'DISPATCHING',
        'DISPATCHED',
        'ACKED',
        'RUNNING'
    );


create table if not exists agent_assignment_profile_policy_bindings (
    binding_id text primary key,
    tenant_id text not null default 'default',
    profile_code text not null,
    policy_code text not null,
    policy_name text,
    binding_mode text not null default 'REQUIRED',
    is_required boolean not null default true,
    is_active boolean not null default true,
    priority integer not null default 100,
    condition_expr text,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_agent_assignment_profile_policy_binding_mode check (binding_mode in ('REQUIRED','ALLOW','DENY','CONDITIONAL')),
    constraint ux_agent_assignment_profile_policy_binding unique (tenant_id, profile_code, policy_code)
);

create index if not exists idx_agent_assignment_profile_policy_bindings_profile
    on agent_assignment_profile_policy_bindings(tenant_id, profile_code, is_active, priority desc);
