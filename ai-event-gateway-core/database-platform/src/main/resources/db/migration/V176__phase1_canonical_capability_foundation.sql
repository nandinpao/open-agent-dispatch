-- Phase 1: Canonical Capability Foundation.
--
-- Capability is the provider-neutral semantic WHAT contract. This schema intentionally
-- does not contain source_system, target_system, source_domain, target_domain,
-- agent_pool_id, agent_id, endpoint, credential, A2A protocol or MCP transport fields.
-- Those concerns belong to later Provider/Governance/Execution phases.

create table if not exists capability_definitions (
  tenant_id varchar(64) not null,
  capability_id varchar(128) not null,
  capability_code varchar(160) not null,
  display_name varchar(255) not null,
  description text,
  semantic_domain varchar(128),
  category varchar(128),
  capability_type varchar(64) not null default 'SERVICE',
  operations_json jsonb not null default '[]'::jsonb,
  input_schema_json jsonb not null default '{}'::jsonb,
  output_schema_json jsonb not null default '{}'::jsonb,
  resource_types_json jsonb not null default '[]'::jsonb,
  data_classes_json jsonb not null default '[]'::jsonb,
  version int not null default 1,
  status varchar(32) not null default 'DRAFT',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, capability_code),
  unique (tenant_id, capability_id),
  constraint capability_definition_code_semantic_check
    check (capability_code ~ '^[a-z][a-z0-9-]*(\.[a-z0-9][a-z0-9-]*)+$'),
  constraint capability_definition_status_check
    check (status in ('DRAFT','ACTIVE','DISABLED','RETIRED')),
  constraint capability_definition_version_check
    check (version >= 1),
  constraint capability_definition_operations_array_check
    check (jsonb_typeof(operations_json)='array'),
  constraint capability_definition_input_object_check
    check (jsonb_typeof(input_schema_json)='object'),
  constraint capability_definition_output_object_check
    check (jsonb_typeof(output_schema_json)='object'),
  constraint capability_definition_resource_types_array_check
    check (jsonb_typeof(resource_types_json)='array'),
  constraint capability_definition_data_classes_array_check
    check (jsonb_typeof(data_classes_json)='array')
);

create index if not exists idx_capability_definitions_tenant_status_code
  on capability_definitions(tenant_id,status,capability_code);
create index if not exists idx_capability_definitions_semantic_domain
  on capability_definitions(tenant_id,semantic_domain,status);
create index if not exists idx_capability_definitions_category
  on capability_definitions(tenant_id,category,status);

comment on table capability_definitions is
  'Phase 1 canonical enterprise semantic WHAT catalog. Provider-neutral and protocol-neutral by architecture contract.';
comment on column capability_definitions.semantic_domain is
  'Taxonomy/search/ownership metadata only. It is never a mandatory routing destination.';
comment on column capability_definitions.capability_code is
  'System-neutral semantic identity such as inventory.availability.read. It must not encode an execution target.';

create table if not exists service_code_capability_mappings (
  tenant_id varchar(64) not null,
  service_code varchar(160) not null,
  capability_code varchar(160) not null,
  requirement_defaults_json jsonb not null default '{}'::jsonb,
  status varchar(32) not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,service_code,capability_code),
  constraint fk_service_code_capability_definition
    foreign key (tenant_id,capability_code)
    references capability_definitions(tenant_id,capability_code)
    on update cascade on delete restrict,
  constraint service_code_capability_mapping_status_check
    check (status in ('ACTIVE','DISABLED')),
  constraint service_code_capability_mapping_defaults_object_check
    check (jsonb_typeof(requirement_defaults_json)='object')
);

create index if not exists idx_service_code_capability_mapping_lookup
  on service_code_capability_mappings(tenant_id,service_code,status,capability_code);

comment on table service_code_capability_mappings is
  'Maps known enterprise Service Code classifications to required semantic Capabilities. This is WHAT resolution, not provider routing.';

-- Tenant isolation follows the existing IAM transaction-local tenant authority.
alter table capability_definitions enable row level security;
drop policy if exists tenant_isolation on capability_definitions;
create policy tenant_isolation on capability_definitions
  using (tenant_id=iam_current_tenant_id())
  with check (tenant_id=iam_current_tenant_id());

alter table service_code_capability_mappings enable row level security;
drop policy if exists tenant_isolation on service_code_capability_mappings;
create policy tenant_isolation on service_code_capability_mappings
  using (tenant_id=iam_current_tenant_id())
  with check (tenant_id=iam_current_tenant_id());

-- Existing Agent Capability Catalog is retained only as compatibility/diagnostic supply metadata.
-- It is not migrated automatically because legacy records may carry system-specific or Task-coupled semantics.
comment on table agent_capability_catalog is
  'Legacy Agent supply/reference capability metadata retained for compatibility. Phase 1 canonical WHAT authority is capability_definitions.';

create table if not exists capability_architecture_evidence (
  evidence_id varchar(160) primary key,
  architecture_version varchar(80) not null,
  principle varchar(512) not null,
  effective_at timestamptz not null,
  created_by varchar(160) not null
);

insert into capability_architecture_evidence(evidence_id,architecture_version,principle,effective_at,created_by)
values(
  'phase1-canonical-capability-authority',
  'PHASE1_CANONICAL_CAPABILITY_FOUNDATION',
  'CAPABILITY_DEFINES_WHAT_AND_MUST_NOT_REQUIRE_PROVIDER_SYSTEM_DOMAIN_POOL_AGENT_OR_PROTOCOL',
  now(),
  'V176')
on conflict(evidence_id) do nothing;

create or replace function prevent_capability_architecture_evidence_mutation() returns trigger language plpgsql as $$
begin raise exception 'CAPABILITY_ARCHITECTURE_EVIDENCE_IS_APPEND_ONLY'; end $$;

drop trigger if exists trg_capability_architecture_evidence_immutable on capability_architecture_evidence;
create trigger trg_capability_architecture_evidence_immutable
before update or delete on capability_architecture_evidence
for each row execute function prevent_capability_architecture_evidence_mutation();
