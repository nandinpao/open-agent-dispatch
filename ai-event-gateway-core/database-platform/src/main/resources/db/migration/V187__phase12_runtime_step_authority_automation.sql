-- Phase 12 — Runtime Step Authority Automation / Provider Evidence Orchestration.
-- READY is dependency readiness only. RUNTIME authority is rebuilt server-side for every Step and Retry:
-- WHO CAN -> WHO MAY -> WHO SHOULD -> HOW. No Source/Target Domain, Agent Pool or target Agent topology is stored.

create table if not exists runtime_step_authority_policies (
  tenant_id varchar(64) not null,
  policy_id varchar(160) not null,
  display_name varchar(255) not null,
  routing_profile_id varchar(160) not null,
  default_access_mode varchar(16) not null default 'EXECUTE',
  operation_access_modes_json jsonb not null default '{}'::jsonb,
  max_candidate_bindings int not null default 200,
  automatic_attachment_enabled boolean not null default true,
  status varchar(24) not null default 'DRAFT',
  version int not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(tenant_id,policy_id),
  constraint runtime_step_authority_policy_status_check check(status in('DRAFT','ACTIVE','DISABLED','RETIRED')),
  constraint runtime_step_authority_access_mode_check check(default_access_mode in('READ','WRITE','EXECUTE')),
  constraint runtime_step_authority_operation_modes_object check(jsonb_typeof(operation_access_modes_json)='object'),
  constraint runtime_step_authority_candidate_limit_check check(max_candidate_bindings between 1 and 5000),
  constraint runtime_step_authority_policy_version_check check(version>=1),
  constraint fk_runtime_step_authority_routing_profile foreign key(tenant_id,routing_profile_id) references routing_profiles(tenant_id,profile_id)
);
create unique index if not exists uq_runtime_step_authority_active_policy on runtime_step_authority_policies(tenant_id) where status='ACTIVE';
comment on table runtime_step_authority_policies is 'Phase 12 runtime orchestration policy. It selects a Routing Profile and access-mode mapping only; it never selects a Provider, Agent, Pool, Domain, A2A/MCP/Netty transport or endpoint.';

create table if not exists runtime_step_authority_policy_versions (
  tenant_id varchar(64) not null,
  policy_id varchar(160) not null,
  version int not null,
  snapshot_json jsonb not null,
  change_reason varchar(512) not null,
  actor_ref varchar(255) not null,
  created_at timestamptz not null default now(),
  primary key(tenant_id,policy_id,version),
  constraint runtime_step_authority_policy_snapshot_object check(jsonb_typeof(snapshot_json)='object')
);

create table if not exists runtime_step_authority_decisions (
  tenant_id varchar(64) not null,
  decision_id varchar(160) not null,
  run_id varchar(160) not null,
  step_id varchar(160) not null,
  attempt_generation int not null,
  result varchar(48) not null,
  capability_code varchar(160) not null,
  operation varchar(80) not null,
  requester_principal_type varchar(40) not null,
  requester_principal_id varchar(160) not null,
  runtime_policy_id varchar(160),
  runtime_policy_version int,
  routing_profile_id varchar(160),
  candidate_count int not null,
  pass_count int not null,
  waiting_approval_count int not null,
  authorization_decision_ids_json jsonb not null default '[]'::jsonb,
  routing_decision_id varchar(160),
  adapter_resolution_id varchar(160),
  reason_codes_json jsonb not null default '[]'::jsonb,
  decided_at timestamptz not null default now(),
  primary key(tenant_id,decision_id),
  constraint runtime_step_authority_result_check check(result in('AUTHORIZED','WAITING_APPROVAL','NO_CANDIDATE','ROUTING_UNAVAILABLE','HOW_UNAVAILABLE','REQUESTER_CONTEXT_INVALID','POLICY_NOT_CONFIGURED','CANDIDATE_SET_TOO_LARGE','NOT_READY')),
  constraint runtime_step_authority_attempt_generation_check check(attempt_generation>=0),
  constraint runtime_step_authority_counts_check check(candidate_count>=0 and pass_count>=0 and waiting_approval_count>=0),
  constraint runtime_step_authority_auth_ids_array check(jsonb_typeof(authorization_decision_ids_json)='array'),
  constraint runtime_step_authority_reasons_array check(jsonb_typeof(reason_codes_json)='array'),
  constraint fk_runtime_step_authority_run foreign key(tenant_id,run_id) references plan_execution_runs(tenant_id,run_id),
  constraint fk_runtime_step_authority_policy_version foreign key(tenant_id,runtime_policy_id,runtime_policy_version) references runtime_step_authority_policy_versions(tenant_id,policy_id,version),
  constraint fk_runtime_step_authority_routing_decision foreign key(tenant_id,routing_decision_id) references provider_routing_decisions(tenant_id,decision_id),
  constraint fk_runtime_step_authority_adapter_resolution foreign key(tenant_id,adapter_resolution_id) references execution_adapter_resolutions(tenant_id,resolution_id)
);
create index if not exists idx_runtime_step_authority_decisions on runtime_step_authority_decisions(tenant_id,run_id,step_id,decided_at desc);
comment on table runtime_step_authority_decisions is 'Append-only Phase 12 evidence. Every RUNTIME Step/Retry rebuilds WHO CAN -> WHO MAY -> WHO SHOULD -> HOW from current server-side state.';

alter table runtime_step_authority_policies enable row level security;
drop policy if exists tenant_isolation on runtime_step_authority_policies;
create policy tenant_isolation on runtime_step_authority_policies using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table runtime_step_authority_policy_versions enable row level security;
drop policy if exists tenant_isolation on runtime_step_authority_policy_versions;
create policy tenant_isolation on runtime_step_authority_policy_versions using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table runtime_step_authority_decisions enable row level security;
drop policy if exists tenant_isolation on runtime_step_authority_decisions;
create policy tenant_isolation on runtime_step_authority_decisions using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

create or replace function prevent_phase12_runtime_authority_evidence_mutation() returns trigger language plpgsql as $$ begin raise exception 'PHASE12_RUNTIME_AUTHORITY_EVIDENCE_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_runtime_step_authority_policy_version_immutable on runtime_step_authority_policy_versions;
create trigger trg_runtime_step_authority_policy_version_immutable before update or delete on runtime_step_authority_policy_versions for each row execute function prevent_phase12_runtime_authority_evidence_mutation();
drop trigger if exists trg_runtime_step_authority_decision_immutable on runtime_step_authority_decisions;
create trigger trg_runtime_step_authority_decision_immutable before update or delete on runtime_step_authority_decisions for each row execute function prevent_phase12_runtime_authority_evidence_mutation();

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('phase12-runtime-step-authority','PHASE12_RUNTIME_STEP_AUTHORITY_AUTOMATION','READY_MEANS_DEPENDENCY_ONLY_RUNTIME_REBUILDS_WHO_CAN_WHO_MAY_WHO_SHOULD_HOW_SERVER_SIDE_FOR_EVERY_STEP_AND_RETRY_NO_TARGET_DOMAIN_POOL_AGENT_OR_PROTOCOL_INPUT',now(),'V187')
on conflict(contract_id) do update set contract_family=excluded.contract_family,authority_note=excluded.authority_note,created_at=excluded.created_at,schema_version=excluded.schema_version;
