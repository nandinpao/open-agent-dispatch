-- Stage 3 / A0-R3: Flow -> Capability Shadow Migration
--
-- Contract:
--   * Existing Dispatch Flow / Flow Agent Assignment path remains the ONLY runtime authority.
--   * Capability migration evaluation is SHADOW_ONLY and MUST NOT create TaskAssignment,
--     DispatchRequest, execution adapter invocation, or any business side effect.
--   * Concrete Agent/Pool/Binding identifiers are migration evidence only; they are not
--     accepted as new Capability request inputs.

-- -----------------------------------------------------------------------------
-- 1. Explicit legacy Skill -> Canonical Capability alias bridge.
--    No arbitrary legacy skill is auto-promoted to Canonical Capability authority.
-- -----------------------------------------------------------------------------
create table if not exists skill_capability_aliases (
  tenant_id varchar(64) not null,
  legacy_skill_code varchar(160) not null,
  capability_code varchar(160) not null,
  status varchar(24) not null default 'PROPOSED',
  mapping_source varchar(40) not null default 'MANUAL',
  reason varchar(512),
  created_by varchar(160) not null default 'SYSTEM',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, legacy_skill_code),
  constraint fk_skill_capability_alias_definition
    foreign key (tenant_id, capability_code)
    references capability_definitions(tenant_id, capability_code)
    on update cascade on delete restrict,
  constraint skill_capability_alias_status_check
    check (status in ('PROPOSED','ACTIVE','DISABLED')),
  constraint skill_capability_alias_source_check
    check (mapping_source in ('MANUAL','EXACT_CANONICAL_CODE','FLOW_MIGRATION_REVIEW'))
);
create index if not exists idx_skill_capability_alias_capability
  on skill_capability_aliases(tenant_id,capability_code,status,legacy_skill_code);

alter table skill_capability_aliases enable row level security;
drop policy if exists tenant_isolation on skill_capability_aliases;
create policy tenant_isolation on skill_capability_aliases
  using (tenant_id=iam_current_tenant_id())
  with check (tenant_id=iam_current_tenant_id());

comment on table skill_capability_aliases is
  'Stage 3 explicit compatibility bridge from legacy flow skill codes to provider-neutral Canonical Capability codes. Alias rows do not authorize providers or execution.';

-- Only exact semantic code matches are safe to bootstrap automatically.
insert into skill_capability_aliases(
  tenant_id,legacy_skill_code,capability_code,status,mapping_source,reason,created_by)
select distinct f.tenant_id, upper(btrim(f.skill_code)), c.capability_code,
       'ACTIVE','EXACT_CANONICAL_CODE','Legacy skill exactly equals an existing Canonical Capability code ignoring case.','V189'
  from flow_required_capabilities f
  join capability_definitions c
    on c.tenant_id=f.tenant_id
   and lower(btrim(f.skill_code))=lower(c.capability_code)
   and c.status='ACTIVE'
 where f.skill_code is not null and btrim(f.skill_code)<>''
on conflict(tenant_id,legacy_skill_code) do nothing;

-- -----------------------------------------------------------------------------
-- 2. Explicit Managed Agent provider identity link.
--    capability_providers.provider_ref remains opaque in the general model; Stage 3
--    uses this verified link rather than assuming provider_ref always equals Agent ID.
-- -----------------------------------------------------------------------------
create table if not exists managed_agent_provider_links (
  tenant_id varchar(64) not null,
  provider_id varchar(160) not null,
  agent_id varchar(128) not null,
  status varchar(24) not null default 'ACTIVE',
  link_source varchar(40) not null default 'MANUAL',
  verified_at timestamptz,
  created_by varchar(160) not null default 'SYSTEM',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,provider_id),
  unique (tenant_id,agent_id),
  constraint fk_managed_agent_provider_link_provider
    foreign key (tenant_id,provider_id)
    references capability_providers(tenant_id,provider_id)
    on update cascade on delete restrict,
  constraint managed_agent_provider_link_status_check
    check (status in ('ACTIVE','SUSPENDED','RETIRED')),
  constraint managed_agent_provider_link_source_check
    check (link_source in ('MANUAL','EXACT_AGENT_PROFILE_REF','FLOW_MIGRATION_REVIEW'))
);
create index if not exists idx_managed_agent_provider_links_agent
  on managed_agent_provider_links(tenant_id,agent_id,status);

alter table managed_agent_provider_links enable row level security;
drop policy if exists tenant_isolation on managed_agent_provider_links;
create policy tenant_isolation on managed_agent_provider_links
  using (tenant_id=iam_current_tenant_id())
  with check (tenant_id=iam_current_tenant_id());

comment on table managed_agent_provider_links is
  'Stage 3 explicit identity correlation between MANAGED_AGENT Capability Provider and existing OpenDispatch Agent. This link is identity evidence only, not Capability authorization.';

-- Safe bootstrap only where the provider_ref exactly matches an existing governed Agent profile.
insert into managed_agent_provider_links(
  tenant_id,provider_id,agent_id,status,link_source,verified_at,created_by)
select p.tenant_id,p.provider_id,a.agent_id,'ACTIVE','EXACT_AGENT_PROFILE_REF',now(),'V189'
  from capability_providers p
  join agent_profiles a
    on a.tenant_id=p.tenant_id
   and a.agent_id=p.provider_ref
 where p.provider_type='MANAGED_AGENT'
   and p.catalog_status='REGISTERED'
   and a.approval_status='APPROVED'
   and a.enabled=true
on conflict(tenant_id,provider_id) do nothing;

-- -----------------------------------------------------------------------------
-- 3. Per-Flow migration state. Stage 3 deliberately has no CAPABILITY_ROUTING state.
-- -----------------------------------------------------------------------------
create table if not exists flow_capability_migration_states (
  tenant_id varchar(64) not null,
  flow_id varchar(128) not null,
  migration_state varchar(32) not null default 'LEGACY_DIRECT',
  authoritative_mode varchar(40) not null default 'LEGACY_FLOW_DIRECT',
  shadow_enabled boolean not null default false,
  shadow_started_at timestamptz,
  last_evaluated_at timestamptz,
  state_reason varchar(512),
  version int not null default 1,
  updated_by varchar(160) not null default 'SYSTEM',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,flow_id),
  constraint fk_flow_capability_migration_flow
    foreign key (tenant_id,flow_id)
    references dispatch_flows(tenant_id,flow_id)
    on update cascade on delete cascade,
  constraint flow_capability_migration_state_check
    check (migration_state in ('LEGACY_DIRECT','SHADOW_EVALUATION','CAPABILITY_READY','ROLLED_BACK')),
  constraint flow_capability_authority_check
    check (authoritative_mode='LEGACY_FLOW_DIRECT'),
  constraint flow_capability_migration_version_check check (version >= 1),
  constraint flow_capability_shadow_state_check
    check ((migration_state='SHADOW_EVALUATION' and shadow_enabled=true)
       or (migration_state<>'SHADOW_EVALUATION' and shadow_enabled=false))
);
create index if not exists idx_flow_capability_migration_state
  on flow_capability_migration_states(tenant_id,migration_state,updated_at desc);

alter table flow_capability_migration_states enable row level security;
drop policy if exists tenant_isolation on flow_capability_migration_states;
create policy tenant_isolation on flow_capability_migration_states
  using (tenant_id=iam_current_tenant_id())
  with check (tenant_id=iam_current_tenant_id());

insert into flow_capability_migration_states(
  tenant_id,flow_id,migration_state,authoritative_mode,shadow_enabled,state_reason,updated_by)
select f.tenant_id,f.flow_id,'LEGACY_DIRECT','LEGACY_FLOW_DIRECT',false,
       'V189 bootstrap preserves existing Flow/Agent assignment authority.','V189'
  from dispatch_flows f
on conflict(tenant_id,flow_id) do nothing;

comment on table flow_capability_migration_states is
  'Stage 3 per-Flow migration state. authoritative_mode is constrained to LEGACY_FLOW_DIRECT; Stage 3 cannot cut over runtime authority.';

-- -----------------------------------------------------------------------------
-- 4. Immutable shadow evaluation evidence.
-- -----------------------------------------------------------------------------
create table if not exists flow_capability_shadow_evaluations (
  tenant_id varchar(64) not null,
  evaluation_id varchar(160) not null,
  task_id varchar(128) not null,
  task_version bigint,
  flow_id varchar(128) not null,
  rule_id varchar(128),
  legacy_selected_agent_id varchar(128),
  legacy_candidate_agents_json jsonb not null default '[]'::jsonb,
  legacy_required_skills_json jsonb not null default '[]'::jsonb,
  resolved_capabilities_json jsonb not null default '[]'::jsonb,
  unresolved_skills_json jsonb not null default '[]'::jsonb,
  shadow_candidate_agents_json jsonb not null default '[]'::jsonb,
  shadow_candidate_bindings_json jsonb not null default '[]'::jsonb,
  comparison_result varchar(48) not null,
  reason_codes_json jsonb not null default '[]'::jsonb,
  authority_mode varchar(24) not null default 'SHADOW_ONLY',
  side_effect_allowed boolean not null default false,
  evaluator_version varchar(80) not null default 'STAGE3_A0_R3_V1',
  evaluated_by varchar(160) not null,
  evaluated_at timestamptz not null default now(),
  primary key (tenant_id,evaluation_id),
  constraint fk_flow_capability_shadow_task
    foreign key (tenant_id,task_id)
    references tasks(tenant_id,task_id) on delete cascade,
  constraint fk_flow_capability_shadow_flow
    foreign key (tenant_id,flow_id)
    references dispatch_flows(tenant_id,flow_id) on delete cascade,
  constraint flow_capability_shadow_comparison_check check (comparison_result in (
    'EQUIVALENT_CANDIDATE_SET','SHADOW_EXPANDS_CANDIDATES','SHADOW_REDUCES_CANDIDATES',
    'DIFFERENT_CANDIDATE_SET','LEGACY_SELECTED_NOT_ELIGIBLE','UNMAPPED_CAPABILITY',
    'NO_REQUIRED_CAPABILITY','NO_SHADOW_PROVIDER','NO_LEGACY_CANDIDATE')),
  constraint flow_capability_shadow_authority_check check (authority_mode='SHADOW_ONLY'),
  constraint flow_capability_shadow_side_effect_check check (side_effect_allowed=false),
  constraint flow_capability_shadow_legacy_agents_array check (jsonb_typeof(legacy_candidate_agents_json)='array'),
  constraint flow_capability_shadow_legacy_skills_array check (jsonb_typeof(legacy_required_skills_json)='array'),
  constraint flow_capability_shadow_capabilities_array check (jsonb_typeof(resolved_capabilities_json)='array'),
  constraint flow_capability_shadow_unresolved_array check (jsonb_typeof(unresolved_skills_json)='array'),
  constraint flow_capability_shadow_agents_array check (jsonb_typeof(shadow_candidate_agents_json)='array'),
  constraint flow_capability_shadow_bindings_array check (jsonb_typeof(shadow_candidate_bindings_json)='array'),
  constraint flow_capability_shadow_reasons_array check (jsonb_typeof(reason_codes_json)='array')
);
create index if not exists idx_flow_capability_shadow_flow
  on flow_capability_shadow_evaluations(tenant_id,flow_id,evaluated_at desc);
create index if not exists idx_flow_capability_shadow_task
  on flow_capability_shadow_evaluations(tenant_id,task_id,evaluated_at desc);
create index if not exists idx_flow_capability_shadow_result
  on flow_capability_shadow_evaluations(tenant_id,flow_id,comparison_result,evaluated_at desc);

alter table flow_capability_shadow_evaluations enable row level security;
drop policy if exists tenant_isolation on flow_capability_shadow_evaluations;
create policy tenant_isolation on flow_capability_shadow_evaluations
  using (tenant_id=iam_current_tenant_id())
  with check (tenant_id=iam_current_tenant_id());

create or replace function prevent_flow_capability_shadow_evaluation_mutation()
returns trigger language plpgsql as $$
begin raise exception 'FLOW_CAPABILITY_SHADOW_EVALUATION_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_flow_capability_shadow_evaluation_immutable on flow_capability_shadow_evaluations;
create trigger trg_flow_capability_shadow_evaluation_immutable
before update or delete on flow_capability_shadow_evaluations
for each row execute function prevent_flow_capability_shadow_evaluation_mutation();

comment on table flow_capability_shadow_evaluations is
  'Stage 3 immutable Flow->Capability migration comparison evidence. Rows are SHADOW_ONLY, side_effect_allowed=false, and can never create runtime Assignment/Dispatch.';

-- Read model for operators. This is evidence/readiness only.
create or replace view flow_capability_migration_readiness_v53 as
with latest_per_task as (
  select distinct on (tenant_id,flow_id,task_id)
         tenant_id,flow_id,task_id,comparison_result,legacy_selected_agent_id,
         unresolved_skills_json,shadow_candidate_agents_json,evaluated_at
    from flow_capability_shadow_evaluations
   order by tenant_id,flow_id,task_id,evaluated_at desc,evaluation_id desc
)
select s.tenant_id,s.flow_id,s.migration_state,s.authoritative_mode,s.shadow_enabled,
       count(l.task_id)::bigint as sample_size,
       count(*) filter (where l.comparison_result='UNMAPPED_CAPABILITY')::bigint as unmapped_capability_count,
       count(*) filter (where l.comparison_result='NO_SHADOW_PROVIDER')::bigint as no_shadow_provider_count,
       count(*) filter (where l.comparison_result='LEGACY_SELECTED_NOT_ELIGIBLE')::bigint as selected_not_eligible_count,
       count(*) filter (where l.comparison_result in ('EQUIVALENT_CANDIDATE_SET','SHADOW_EXPANDS_CANDIDATES'))::bigint as legacy_preserved_count,
       max(l.evaluated_at) as last_evaluated_at
  from flow_capability_migration_states s
  left join latest_per_task l on l.tenant_id=s.tenant_id and l.flow_id=s.flow_id
 group by s.tenant_id,s.flow_id,s.migration_state,s.authoritative_mode,s.shadow_enabled;

comment on view flow_capability_migration_readiness_v53 is
  'Stage 3 readiness aggregate. It never authorizes capability routing; authoritative_mode remains LEGACY_FLOW_DIRECT.';

-- -----------------------------------------------------------------------------
-- 5. Architecture evidence.
-- -----------------------------------------------------------------------------
insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values(
  'stage3-a0-r3-flow-capability-shadow-migration-v1',
  'A0_R3_FLOW_CAPABILITY_SHADOW_MIGRATION',
  'SHADOW_ONLY: LEGACY_FLOW_DIRECT_REMAINS_RUNTIME_AUTHORITY; CAPABILITY_ALIAS_PROVIDER_LINK_AND_CANDIDATE_COMPARISON_MUST_NOT_CREATE_ASSIGNMENT_DISPATCH_OR_SIDE_EFFECT',
  now(),
  'V189')
on conflict(contract_id) do nothing;
