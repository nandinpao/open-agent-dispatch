-- Phase 3: Delegation Governance / WHO MAY.
--
-- Authorization is a hard gate. These policies never encode Source->Target topology,
-- never select an Agent Pool/Agent/System/Domain, never rank providers and never choose
-- A2A/MCP/Netty transport.

create table if not exists delegation_policies (
  tenant_id varchar(64) not null,
  policy_id varchar(160) not null,
  display_name varchar(255) not null,
  description text,
  effect varchar(16) not null default 'ALLOW',
  status varchar(24) not null default 'DRAFT',
  requester_principal_types_json jsonb not null default '[]'::jsonb,
  requester_department_ids_json jsonb not null default '[]'::jsonb,
  requester_group_ids_json jsonb not null default '[]'::jsonb,
  requester_role_codes_json jsonb not null default '[]'::jsonb,
  capability_codes_json jsonb not null default '[]'::jsonb,
  operations_json jsonb not null default '[]'::jsonb,
  resource_constraints_json jsonb not null default '{}'::jsonb,
  allowed_data_classes_json jsonb not null default '[]'::jsonb,
  max_sensitivity_level varchar(24) not null default 'RESTRICTED',
  allowed_access_modes_json jsonb not null default '["READ"]'::jsonb,
  required_provider_types_json jsonb not null default '[]'::jsonb,
  required_provider_certifications_json jsonb not null default '[]'::jsonb,
  approval_mode varchar(16) not null default 'NONE',
  max_estimated_cost numeric(18,6),
  max_delegation_depth int,
  max_agent_calls int,
  max_execution_time_ms bigint,
  priority int not null default 100,
  version int not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,policy_id),
  constraint delegation_policy_effect_check check (effect in ('ALLOW','DENY')),
  constraint delegation_policy_status_check check (status in ('DRAFT','ACTIVE','SUSPENDED','RETIRED')),
  constraint delegation_policy_sensitivity_check check (max_sensitivity_level in ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED','CRITICAL')),
  constraint delegation_policy_approval_check check (approval_mode in ('NONE','SINGLE','DUAL')),
  constraint delegation_policy_priority_check check (priority between 0 and 1000000),
  constraint delegation_policy_version_check check (version >= 1),
  constraint delegation_policy_cost_check check (max_estimated_cost is null or max_estimated_cost >= 0),
  constraint delegation_policy_depth_check check (max_delegation_depth is null or max_delegation_depth >= 0),
  constraint delegation_policy_calls_check check (max_agent_calls is null or max_agent_calls >= 0),
  constraint delegation_policy_time_check check (max_execution_time_ms is null or max_execution_time_ms >= 0),
  constraint delegation_policy_principal_array check (jsonb_typeof(requester_principal_types_json)='array'),
  constraint delegation_policy_department_array check (jsonb_typeof(requester_department_ids_json)='array'),
  constraint delegation_policy_group_array check (jsonb_typeof(requester_group_ids_json)='array'),
  constraint delegation_policy_role_array check (jsonb_typeof(requester_role_codes_json)='array'),
  constraint delegation_policy_capability_array check (jsonb_typeof(capability_codes_json)='array'),
  constraint delegation_policy_operations_array check (jsonb_typeof(operations_json)='array'),
  constraint delegation_policy_resource_object check (jsonb_typeof(resource_constraints_json)='object'),
  constraint delegation_policy_data_class_array check (jsonb_typeof(allowed_data_classes_json)='array'),
  constraint delegation_policy_access_array check (jsonb_typeof(allowed_access_modes_json)='array'),
  constraint delegation_policy_provider_type_array check (jsonb_typeof(required_provider_types_json)='array'),
  constraint delegation_policy_certification_array check (jsonb_typeof(required_provider_certifications_json)='array'),
  constraint delegation_policy_deny_semantics_check check (
    effect <> 'DENY' or (
      approval_mode='NONE' and max_sensitivity_level='CRITICAL'
      and max_estimated_cost is null and max_delegation_depth is null
      and max_agent_calls is null and max_execution_time_ms is null
    )
  )
);

create index if not exists idx_delegation_policies_active
  on delegation_policies(tenant_id,status,effect,priority desc,policy_id);
create index if not exists idx_delegation_policies_capabilities_gin
  on delegation_policies using gin (capability_codes_json jsonb_path_ops);

comment on table delegation_policies is
  'Phase 3 WHO MAY authorization policies. No Source->Target topology, provider ranking or execution transport is permitted.';
comment on column delegation_policies.required_provider_types_json is
  'Authorization attribute filter only. It does not choose a provider or execution adapter.';
comment on column delegation_policies.priority is
  'Conflict resolution priority among otherwise matching policies. Ties at equal priority and specificity fail closed as ambiguous.';

create table if not exists delegation_policy_versions (
  tenant_id varchar(64) not null,
  policy_id varchar(160) not null,
  version int not null,
  snapshot_json jsonb not null,
  change_reason varchar(512) not null,
  actor_ref varchar(255) not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id,policy_id,version),
  constraint delegation_policy_version_snapshot_object check (jsonb_typeof(snapshot_json)='object'),
  constraint fk_delegation_policy_version
    foreign key (tenant_id,policy_id) references delegation_policies(tenant_id,policy_id) on delete cascade
);

comment on table delegation_policy_versions is
  'Append-only full policy snapshots. Authorization decisions retain selected policy version so the exact WHO MAY authority can be reconstructed.';

create table if not exists delegation_policy_audit_events (
  tenant_id varchar(64) not null,
  event_id varchar(160) not null,
  policy_id varchar(160) not null,
  policy_version int not null,
  action varchar(40) not null,
  reason varchar(512) not null,
  actor_ref varchar(255) not null,
  occurred_at timestamptz not null default now(),
  primary key (tenant_id,event_id),
  constraint fk_delegation_policy_audit
    foreign key (tenant_id,policy_id) references delegation_policies(tenant_id,policy_id) on delete cascade
);

create index if not exists idx_delegation_policy_audit
  on delegation_policy_audit_events(tenant_id,policy_id,occurred_at desc);

create table if not exists delegation_authorization_decisions (
  tenant_id varchar(64) not null,
  decision_id varchar(160) not null,
  decision_mode varchar(16) not null default 'PREVIEW',
  result varchar(32) not null,
  capability_code varchar(160) not null,
  operation varchar(80) not null,
  binding_id varchar(160) not null,
  provider_id varchar(160) not null,
  provider_type varchar(40) not null,
  selected_policy_id varchar(160),
  selected_policy_version int,
  approval_mode varchar(16),
  reason_codes_json jsonb not null default '[]'::jsonb,
  considered_policy_ids_json jsonb not null default '[]'::jsonb,
  evaluated_at timestamptz not null default now(),
  primary key (tenant_id,decision_id),
  constraint delegation_authorization_result_check check (result in ('PASS','FAIL','WAITING_APPROVAL')),
  constraint delegation_authorization_mode_check check (decision_mode in ('PREVIEW','RUNTIME')),
  constraint delegation_authorization_reason_array check (jsonb_typeof(reason_codes_json)='array'),
  constraint delegation_authorization_considered_array check (jsonb_typeof(considered_policy_ids_json)='array')
);

create index if not exists idx_delegation_authorization_decisions
  on delegation_authorization_decisions(tenant_id,capability_code,evaluated_at desc);

comment on table delegation_authorization_decisions is
  'Append-only WHO MAY decision evidence. Phase 3 admin evaluations are PREVIEW and are not dispatch/routing authority.';

alter table delegation_policies enable row level security;
drop policy if exists tenant_isolation on delegation_policies;
create policy tenant_isolation on delegation_policies
  using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());

alter table delegation_policy_versions enable row level security;
drop policy if exists tenant_isolation on delegation_policy_versions;
create policy tenant_isolation on delegation_policy_versions
  using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());

alter table delegation_policy_audit_events enable row level security;
drop policy if exists tenant_isolation on delegation_policy_audit_events;
create policy tenant_isolation on delegation_policy_audit_events
  using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());

alter table delegation_authorization_decisions enable row level security;
drop policy if exists tenant_isolation on delegation_authorization_decisions;
create policy tenant_isolation on delegation_authorization_decisions
  using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());

create or replace function prevent_delegation_policy_version_mutation() returns trigger language plpgsql as $$
begin raise exception 'DELEGATION_POLICY_VERSION_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_delegation_policy_version_immutable on delegation_policy_versions;
create trigger trg_delegation_policy_version_immutable before update or delete on delegation_policy_versions
for each row execute function prevent_delegation_policy_version_mutation();

create or replace function prevent_delegation_policy_audit_mutation() returns trigger language plpgsql as $$
begin raise exception 'DELEGATION_POLICY_AUDIT_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_delegation_policy_audit_immutable on delegation_policy_audit_events;
create trigger trg_delegation_policy_audit_immutable before update or delete on delegation_policy_audit_events
for each row execute function prevent_delegation_policy_audit_mutation();

create or replace function prevent_delegation_authorization_decision_mutation() returns trigger language plpgsql as $$
begin raise exception 'DELEGATION_AUTHORIZATION_DECISION_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_delegation_authorization_decision_immutable on delegation_authorization_decisions;
create trigger trg_delegation_authorization_decision_immutable before update or delete on delegation_authorization_decisions
for each row execute function prevent_delegation_authorization_decision_mutation();

insert into capability_architecture_evidence(evidence_id,architecture_version,principle,effective_at,created_by)
values(
  'phase3-delegation-governance-authority',
  'PHASE3_DELEGATION_GOVERNANCE_WHO_MAY',
  'AUTHORIZATION_IS_A_HARD_GATE_AND_MUST_NOT_ENCODE_SOURCE_TARGET_TOPOLOGY_RANK_PROVIDERS_OR_SELECT_EXECUTION_PROTOCOL',
  now(),
  'V178')
on conflict(evidence_id) do nothing;
