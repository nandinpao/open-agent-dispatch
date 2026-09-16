-- Stage 6 — MCP READ Provider.
-- Provider-neutral ExecutionAssignment on existing task_assignments; MCP never creates Agent DispatchRequest.

alter table task_assignments alter column agent_id drop not null;
alter table task_assignments add column if not exists execution_target_type varchar(40);
alter table task_assignments add column if not exists provider_type varchar(40);
alter table task_assignments add column if not exists provider_id varchar(160);
alter table task_assignments add column if not exists selected_mcp_tool_id varchar(160);

alter table task_assignments drop constraint if exists chk_task_assignments_execution_target_type;
alter table task_assignments add constraint chk_task_assignments_execution_target_type
  check (execution_target_type is null or execution_target_type in ('MANAGED_AGENT','MCP_TOOL','REMOTE_A2A_AGENT'));
alter table task_assignments drop constraint if exists chk_task_assignments_provider_type;
alter table task_assignments add constraint chk_task_assignments_provider_type
  check (provider_type is null or provider_type in ('MANAGED_AGENT','MCP_TOOL','REMOTE_A2A_AGENT'));
alter table task_assignments drop constraint if exists chk_task_assignments_target_identity;
alter table task_assignments add constraint chk_task_assignments_target_identity check (
  execution_target_type is null
  or (execution_target_type='MANAGED_AGENT' and agent_id is not null)
  or (execution_target_type='MCP_TOOL' and agent_id is null and provider_type='MCP_TOOL' and provider_id is not null and selected_mcp_server_id is not null and selected_mcp_tool_id is not null)
  or (execution_target_type='REMOTE_A2A_AGENT' and agent_id is null and provider_type='REMOTE_A2A_AGENT' and provider_id is not null and selected_peer_interface_id is not null)
);
create index if not exists idx_task_assignments_provider_target
  on task_assignments(tenant_id,provider_type,provider_id,status,created_at desc) where provider_id is not null;

alter table capability_delegation_requests add column if not exists execution_kind varchar(40);
alter table capability_delegation_requests add column if not exists selected_provider_type varchar(40);
alter table capability_delegation_requests drop constraint if exists capability_delegation_execution_kind_check;
alter table capability_delegation_requests add constraint capability_delegation_execution_kind_check
  check (execution_kind is null or execution_kind in ('MANAGED_AGENT','MCP_READ','REMOTE_A2A_READ'));

create table if not exists mcp_server_registrations (
  tenant_id varchar(64) not null,
  mcp_server_id varchar(160) not null,
  display_name varchar(255) not null,
  base_url varchar(1024) not null,
  protocol varchar(48) not null default 'MCP_STREAMABLE_HTTP',
  protocol_version varchar(40) not null default '2026-07-28',
  credential_ref varchar(255),
  trust_status varchar(32) not null default 'PROPOSED',
  status varchar(24) not null default 'DRAFT',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,mcp_server_id),
  constraint mcp_server_protocol_check check (protocol in ('MCP_STREAMABLE_HTTP')),
  constraint mcp_server_protocol_version_check check (protocol_version='2026-07-28'),
  constraint mcp_server_trust_check check (trust_status in ('PROPOSED','APPROVED','REVOKED')),
  constraint mcp_server_status_check check (status in ('DRAFT','ACTIVE','SUSPENDED','RETIRED'))
);

create table if not exists mcp_tool_registrations (
  tenant_id varchar(64) not null,
  mcp_tool_id varchar(160) not null,
  mcp_server_id varchar(160) not null,
  tool_name varchar(255) not null,
  title varchar(255),
  input_schema_json jsonb not null default '{}'::jsonb,
  governed_side_effect_level varchar(24) not null default 'NONE',
  declared_read_only_hint boolean,
  declared_destructive_hint boolean,
  declared_idempotent_hint boolean,
  declared_open_world_hint boolean,
  status varchar(24) not null default 'PROPOSED',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,mcp_tool_id),
  unique (tenant_id,mcp_server_id,tool_name),
  constraint fk_mcp_tool_server foreign key (tenant_id,mcp_server_id) references mcp_server_registrations(tenant_id,mcp_server_id),
  constraint mcp_tool_side_effect_check check (governed_side_effect_level in ('NONE','READ','WRITE_REVERSIBLE','WRITE_IRREVERSIBLE')),
  constraint mcp_tool_status_check check (status in ('PROPOSED','APPROVED','SUSPENDED','REVOKED')),
  constraint mcp_tool_input_schema_object check (jsonb_typeof(input_schema_json)='object')
);

create table if not exists mcp_tool_provider_links (
  tenant_id varchar(64) not null,
  provider_id varchar(160) not null,
  mcp_server_id varchar(160) not null,
  mcp_tool_id varchar(160) not null,
  status varchar(24) not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,provider_id),
  unique (tenant_id,mcp_server_id,mcp_tool_id),
  constraint fk_mcp_provider_link_provider foreign key (tenant_id,provider_id) references capability_providers(tenant_id,provider_id),
  constraint fk_mcp_provider_link_server foreign key (tenant_id,mcp_server_id) references mcp_server_registrations(tenant_id,mcp_server_id),
  constraint fk_mcp_provider_link_tool foreign key (tenant_id,mcp_tool_id) references mcp_tool_registrations(tenant_id,mcp_tool_id),
  constraint mcp_provider_link_status_check check (status in ('ACTIVE','SUSPENDED','RETIRED'))
);

create table if not exists mcp_read_executions (
  tenant_id varchar(64) not null,
  execution_id varchar(180) not null,
  delegation_id varchar(160) not null,
  task_id varchar(128) not null,
  assignment_id varchar(128) not null,
  provider_id varchar(160) not null,
  mcp_server_id varchar(160) not null,
  mcp_tool_id varchar(160) not null,
  endpoint_url varchar(1024) not null,
  request_json jsonb not null,
  status varchar(32) not null default 'QUEUED',
  attempts int not null default 0,
  max_attempts int not null default 4,
  next_attempt_at timestamptz,
  claimed_by varchar(160),
  claim_until timestamptz,
  http_status int,
  response_json jsonb,
  error_code varchar(128),
  error_message text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  completed_at timestamptz,
  primary key (tenant_id,execution_id),
  unique (tenant_id,delegation_id),
  constraint fk_mcp_execution_delegation foreign key (tenant_id,delegation_id) references capability_delegation_requests(tenant_id,delegation_id) on delete cascade,
  constraint mcp_execution_status_check check (status in ('QUEUED','PROCESSING','SUCCEEDED','FAILED','DEAD_LETTER')),
  constraint mcp_execution_attempts_check check (attempts>=0 and max_attempts between 1 and 20),
  constraint mcp_execution_request_object check (jsonb_typeof(request_json)='object')
);
create index if not exists idx_mcp_read_executions_due on mcp_read_executions(tenant_id,status,next_attempt_at,created_at)
 where status in ('QUEUED','FAILED');

create table if not exists capability_execution_artifacts (
  tenant_id varchar(64) not null,
  artifact_id varchar(180) not null,
  delegation_id varchar(160) not null,
  task_id varchar(128) not null,
  provider_type varchar(40) not null,
  provider_id varchar(160),
  external_execution_ref varchar(255),
  artifact_type varchar(64) not null default 'RESULT',
  media_type varchar(160) not null default 'application/json',
  content_json jsonb,
  content_text text,
  content_hash varchar(128),
  created_at timestamptz not null default now(),
  primary key (tenant_id,artifact_id),
  constraint capability_execution_artifact_provider_type_check check (provider_type in ('MANAGED_AGENT','MCP_TOOL','REMOTE_A2A_AGENT'))
);
create index if not exists idx_capability_execution_artifacts_delegation
  on capability_execution_artifacts(tenant_id,delegation_id,created_at,artifact_id);

alter table mcp_server_registrations enable row level security;
alter table mcp_tool_registrations enable row level security;
alter table mcp_tool_provider_links enable row level security;
alter table mcp_read_executions enable row level security;
alter table capability_execution_artifacts enable row level security;
do $$ declare t text; begin foreach t in array array['mcp_server_registrations','mcp_tool_registrations','mcp_tool_provider_links','mcp_read_executions','capability_execution_artifacts'] loop execute format('drop policy if exists tenant_isolation on %I',t); execute format('create policy tenant_isolation on %I using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id())',t); end loop; end $$;

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('stage6-mcp-read-provider-v1','STAGE6_MCP_READ_PROVIDER','MCP_READ_USES_PROVIDER_NEUTRAL_TASK_ASSIGNMENT; AGENT_ID_NULL_FOR_MCP; MCP_EXECUTION_IS_DURABLE; NO_AGENT_DISPATCH_REQUEST; GOVERNED_SIDE_EFFECT_LEVEL_NONE_REQUIRED','now'::timestamptz,'V192')
on conflict(contract_id) do nothing;
