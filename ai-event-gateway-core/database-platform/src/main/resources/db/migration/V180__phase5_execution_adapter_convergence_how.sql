-- Phase 5 — Execution Adapter Convergence / HOW.
-- HOW is resolved only from a persisted Phase 4 SELECTED binding/provider. This schema contains
-- no source->target topology and cannot rank or authorize providers.

create table if not exists execution_adapter_registrations (
  tenant_id varchar(64) not null,
  adapter_id varchar(160) not null,
  provider_id varchar(160) not null,
  provider_type varchar(40) not null,
  adapter_type varchar(40) not null,
  protocol varchar(40) not null,
  protocol_version varchar(80),
  endpoint_ref varchar(255),
  credential_ref varchar(255),
  runtime_ref varchar(255),
  status varchar(24) not null default 'DRAFT',
  selection_priority int not null default 100,
  configuration_json jsonb not null default '{}'::jsonb,
  version int not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,adapter_id),
  constraint fk_execution_adapter_provider foreign key (tenant_id,provider_id) references capability_providers(tenant_id,provider_id),
  constraint execution_adapter_provider_type_check check (provider_type in ('MANAGED_AGENT','REMOTE_A2A_AGENT','MCP_TOOL','INTERNAL_SERVICE')),
  constraint execution_adapter_type_check check (adapter_type in ('MANAGED_AGENT_NETTY','REMOTE_A2A','MCP_TOOL','INTERNAL_SERVICE')),
  constraint execution_adapter_status_check check (status in ('DRAFT','ACTIVE','SUSPENDED','RETIRED')),
  constraint execution_adapter_priority_check check (selection_priority between 0 and 100000),
  constraint execution_adapter_config_object check (jsonb_typeof(configuration_json)='object'),
  constraint execution_adapter_version_check check (version >= 1)
);
create index if not exists idx_execution_adapter_provider on execution_adapter_registrations(tenant_id,provider_id,status,selection_priority,adapter_id);
comment on table execution_adapter_registrations is 'Phase 5 HOW registry. endpoint_ref and credential_ref are opaque registry references, never raw URL/token/secret values.';

create table if not exists execution_adapter_versions (
  tenant_id varchar(64) not null,
  adapter_id varchar(160) not null,
  version int not null,
  snapshot_json jsonb not null,
  change_reason varchar(512) not null,
  actor_ref varchar(255) not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id,adapter_id,version),
  constraint fk_execution_adapter_version foreign key (tenant_id,adapter_id) references execution_adapter_registrations(tenant_id,adapter_id) on delete cascade,
  constraint execution_adapter_version_snapshot_object check (jsonb_typeof(snapshot_json)='object')
);

create table if not exists execution_adapter_resolutions (
  tenant_id varchar(64) not null,
  resolution_id varchar(160) not null,
  resolution_mode varchar(16) not null default 'PREVIEW',
  result varchar(40) not null,
  routing_decision_id varchar(160) not null,
  capability_code varchar(160) not null,
  operation varchar(80) not null,
  binding_id varchar(160),
  provider_id varchar(160),
  provider_type varchar(40),
  adapter_id varchar(160),
  adapter_version int,
  adapter_type varchar(40),
  protocol varchar(40),
  protocol_version varchar(80),
  endpoint_ref varchar(255),
  credential_ref varchar(255),
  runtime_ref varchar(255),
  reason_codes_json jsonb not null default '[]'::jsonb,
  resolved_at timestamptz not null default now(),
  primary key (tenant_id,resolution_id),
  constraint execution_adapter_resolution_mode_check check (resolution_mode in ('PREVIEW','RUNTIME')),
  constraint execution_adapter_resolution_result_check check (result in ('SELECTED','NO_ACTIVE_ADAPTER','ADAPTER_AMBIGUOUS','ROUTING_DECISION_STALE')),
  constraint execution_adapter_resolution_reasons_array check (jsonb_typeof(reason_codes_json)='array'),
  constraint fk_execution_resolution_routing_decision foreign key (tenant_id,routing_decision_id) references provider_routing_decisions(tenant_id,decision_id)
);
create index if not exists idx_execution_adapter_resolutions on execution_adapter_resolutions(tenant_id,routing_decision_id,resolved_at desc);
comment on table execution_adapter_resolutions is 'Append-only Phase 5 HOW evidence. A selected adapter never authorizes or re-ranks a provider.';

alter table execution_adapter_registrations enable row level security;
drop policy if exists tenant_isolation on execution_adapter_registrations;
create policy tenant_isolation on execution_adapter_registrations using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());
alter table execution_adapter_versions enable row level security;
drop policy if exists tenant_isolation on execution_adapter_versions;
create policy tenant_isolation on execution_adapter_versions using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());
alter table execution_adapter_resolutions enable row level security;
drop policy if exists tenant_isolation on execution_adapter_resolutions;
create policy tenant_isolation on execution_adapter_resolutions using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());

create or replace function prevent_execution_adapter_version_mutation() returns trigger language plpgsql as $$ begin raise exception 'EXECUTION_ADAPTER_VERSION_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_execution_adapter_version_immutable on execution_adapter_versions;
create trigger trg_execution_adapter_version_immutable before update or delete on execution_adapter_versions for each row execute function prevent_execution_adapter_version_mutation();
create or replace function prevent_execution_adapter_resolution_mutation() returns trigger language plpgsql as $$ begin raise exception 'EXECUTION_ADAPTER_RESOLUTION_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_execution_adapter_resolution_immutable on execution_adapter_resolutions;
create trigger trg_execution_adapter_resolution_immutable before update or delete on execution_adapter_resolutions for each row execute function prevent_execution_adapter_resolution_mutation();

insert into capability_architecture_evidence(evidence_id,architecture_version,principle,effective_at,created_by)
values('phase5-execution-adapter-authority','PHASE5_EXECUTION_ADAPTER_CONVERGENCE_HOW','HOW_RESOLVES_ONLY_FROM_PERSISTED_PHASE4_SELECTION_AND_MUST_NOT_AUTHORIZE_RANK_OR_ENCODE_ENTERPRISE_TOPOLOGY',now(),'V180')
on conflict(evidence_id) do nothing;
