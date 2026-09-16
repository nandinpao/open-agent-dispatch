-- Phase 0G: Issue Projection and Sync Reliability expand migration.
-- OpenDispatch remains the Task authority. External issues are asynchronous projections.

-- Upgrade the legacy one-link-per-task read model into a tenant-aware many-to-many link model.
drop index if exists ux_task_issue_links_task_id;

alter table task_issue_links add column if not exists connection_id varchar(128);
alter table task_issue_links add column if not exists project_mapping_id varchar(128);
alter table task_issue_links add column if not exists provider_type varchar(32);
alter table task_issue_links add column if not exists external_project_id varchar(255);
alter table task_issue_links add column if not exists external_project_key varchar(255);
alter table task_issue_links add column if not exists external_issue_id varchar(255);
alter table task_issue_links add column if not exists external_issue_key varchar(255);
alter table task_issue_links add column if not exists external_issue_url text;
alter table task_issue_links add column if not exists link_role varchar(32) not null default 'PRIMARY';
alter table task_issue_links add column if not exists projection_strategy varchar(48) not null default 'CREATE_NEW_ISSUE';
alter table task_issue_links add column if not exists payload_hash varchar(128);
alter table task_issue_links add column if not exists idempotency_key varchar(128);
alter table task_issue_links add column if not exists resource_version bigint not null default 1;
alter table task_issue_links add column if not exists last_provider_event_id varchar(255);
alter table task_issue_links add column if not exists conflict_status varchar(32);

update task_issue_links
   set provider_type = coalesce(provider_type, issue_vendor),
       external_issue_id = coalesce(external_issue_id, issue_id, issue_key),
       external_issue_key = coalesce(external_issue_key, issue_key, issue_id),
       external_issue_url = coalesce(external_issue_url, issue_url),
       sync_status = case upper(coalesce(sync_status, 'SYNC_PENDING'))
         when 'SYNC_PENDING' then 'PENDING'
         when 'PENDING' then 'PENDING'
         when 'SYNCED' then 'SYNCED'
         when 'SYNC_FAILED' then case when coalesce(issue_retryable,false) then 'FAILED_RETRYABLE' else 'FAILED_PERMANENT' end
         when 'NOT_LINKED' then 'NOT_REQUIRED'
         else sync_status
       end;

create unique index if not exists ux_task_issue_links_idempotency
  on task_issue_links(tenant_id, idempotency_key)
  where idempotency_key is not null;
create unique index if not exists ux_task_issue_links_external_task_role
  on task_issue_links(tenant_id, connection_id, external_project_id, external_issue_id, task_id, link_role)
  where connection_id is not null and external_project_id is not null and external_issue_id is not null;
create index if not exists idx_task_issue_links_task_many
  on task_issue_links(tenant_id, task_id, created_at desc);
create index if not exists idx_task_issue_links_external
  on task_issue_links(tenant_id, connection_id, external_project_id, external_issue_id);
create index if not exists idx_task_issue_links_sync
  on task_issue_links(tenant_id, sync_status, updated_at desc);

create table if not exists issue_relationships (
  tenant_id varchar(64) not null,
  relationship_id varchar(128) not null,
  from_task_issue_link_id varchar(128) not null,
  to_task_issue_link_id varchar(128) not null,
  relationship_type varchar(32) not null,
  provider_relation_id varchar(255),
  sync_status varchar(32) not null default 'PENDING',
  payload_hash varchar(128),
  idempotency_key varchar(128) not null,
  last_error_code varchar(128),
  last_error_message text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  resource_version bigint not null default 1,
  primary key (tenant_id, relationship_id),
  unique (tenant_id, idempotency_key),
  check (from_task_issue_link_id <> to_task_issue_link_id)
);

create table if not exists integration_outbox (
  tenant_id varchar(64) not null,
  outbox_id varchar(128) not null,
  aggregate_type varchar(64) not null,
  aggregate_id varchar(128) not null,
  task_id varchar(128),
  task_issue_link_id varchar(128),
  issue_relationship_id varchar(128),
  connection_id varchar(128),
  project_mapping_id varchar(128),
  operation_type varchar(48) not null,
  event_type varchar(128) not null,
  payload_json jsonb not null default '{}'::jsonb,
  payload_hash varchar(128) not null,
  idempotency_key varchar(128) not null,
  status varchar(32) not null default 'PENDING',
  priority integer not null default 100,
  attempt_count integer not null default 0,
  max_attempts integer not null default 8,
  next_attempt_at timestamptz not null default now(),
  claimed_by varchar(128),
  claim_until timestamptz,
  last_error_code varchar(128),
  last_error_message text,
  correlation_id varchar(128) not null,
  causation_id varchar(128),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  completed_at timestamptz,
  primary key (tenant_id, outbox_id),
  unique (tenant_id, idempotency_key)
);

create table if not exists integration_inbox (
  tenant_id varchar(64) not null,
  inbox_id varchar(128) not null,
  connection_id varchar(128) not null,
  provider_type varchar(32) not null,
  provider_event_id varchar(255) not null,
  event_type varchar(128) not null,
  external_project_id varchar(255),
  external_issue_id varchar(255),
  signature_verified boolean not null default false,
  payload_json jsonb not null default '{}'::jsonb,
  payload_hash varchar(128) not null,
  status varchar(32) not null default 'RECEIVED',
  replay_count integer not null default 0,
  received_at timestamptz not null default now(),
  processed_at timestamptz,
  last_error_code varchar(128),
  last_error_message text,
  correlation_id varchar(128) not null,
  primary key (tenant_id, inbox_id),
  unique (tenant_id, connection_id, provider_event_id)
);

create table if not exists integration_sync_attempts (
  tenant_id varchar(64) not null,
  attempt_id varchar(128) not null,
  outbox_id varchar(128) not null,
  attempt_no integer not null,
  worker_id varchar(128),
  status varchar(32) not null,
  provider_status integer,
  request_payload_hash varchar(128) not null,
  response_summary text,
  retryable boolean not null default false,
  error_code varchar(128),
  error_message text,
  started_at timestamptz not null default now(),
  completed_at timestamptz,
  correlation_id varchar(128),
  primary key (tenant_id, attempt_id),
  unique (tenant_id, outbox_id, attempt_no)
);

create table if not exists integration_dead_letters (
  tenant_id varchar(64) not null,
  dead_letter_id varchar(128) not null,
  outbox_id varchar(128) not null,
  task_id varchar(128),
  task_issue_link_id varchar(128),
  operation_type varchar(48) not null,
  payload_json jsonb not null default '{}'::jsonb,
  payload_hash varchar(128) not null,
  failure_code varchar(128) not null,
  failure_message text,
  attempt_count integer not null,
  status varchar(32) not null default 'OPEN',
  opened_at timestamptz not null default now(),
  retried_at timestamptz,
  resolved_at timestamptz,
  resolved_by varchar(128),
  resolution_reason text,
  correlation_id varchar(128),
  primary key (tenant_id, dead_letter_id),
  unique (tenant_id, outbox_id)
);

create table if not exists integration_conflicts (
  tenant_id varchar(64) not null,
  conflict_id varchar(128) not null,
  task_id varchar(128) not null,
  task_issue_link_id varchar(128),
  inbox_id varchar(128),
  conflict_type varchar(64) not null,
  task_status varchar(32),
  external_issue_status varchar(64),
  detail_json jsonb not null default '{}'::jsonb,
  status varchar(32) not null default 'OPEN',
  detected_at timestamptz not null default now(),
  resolved_at timestamptz,
  resolved_by varchar(128),
  resolution_action varchar(64),
  resolution_reason text,
  correlation_id varchar(128),
  primary key (tenant_id, conflict_id)
);

create table if not exists integration_circuit_breakers (
  tenant_id varchar(64) not null,
  connection_id varchar(128) not null,
  project_mapping_id varchar(128),
  state varchar(16) not null default 'CLOSED',
  consecutive_failures integer not null default 0,
  failure_threshold integer not null default 5,
  opened_at timestamptz,
  next_probe_at timestamptz,
  last_success_at timestamptz,
  last_failure_at timestamptz,
  last_error_code varchar(128),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, connection_id)
);

create index if not exists idx_issue_relationships_link
  on issue_relationships(tenant_id, from_task_issue_link_id, to_task_issue_link_id);
create index if not exists idx_integration_outbox_due
  on integration_outbox(tenant_id, status, next_attempt_at, priority, created_at)
  where status in ('PENDING','FAILED_RETRYABLE');
create index if not exists idx_integration_inbox_issue
  on integration_inbox(tenant_id, connection_id, external_issue_id, received_at desc);
create index if not exists idx_integration_dead_letters_status
  on integration_dead_letters(tenant_id, status, opened_at desc);
create index if not exists idx_integration_conflicts_status
  on integration_conflicts(tenant_id, status, detected_at desc);
