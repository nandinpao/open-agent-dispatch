-- MRS A0-R5 — Plan Admission + Binding Authorization Envelope.
-- Contract: Planner proposes WHAT only. Plan Admission decides WHO MAY ENTER ROUTING.
-- It does not rank/select a provider, choose HOW, create an Assignment, or send network I/O.

-- -----------------------------------------------------------------------------
-- 1. Freeze side-effect semantics with each immutable plan revision step.
-- Legacy steps are backfilled conservatively by the application layer; schema defaults
-- exist only for migration compatibility and are not the admission inference authority.
-- -----------------------------------------------------------------------------
alter table execution_plan_revision_steps
  add column if not exists side_effect varchar(16) not null default 'READ',
  add column if not exists write_semantics varchar(24),
  add column if not exists compensation_binding_id varchar(160),
  add column if not exists max_binding_fallback int not null default 0;

alter table execution_plan_revision_steps drop constraint if exists a0r5_plan_step_side_effect_check;
alter table execution_plan_revision_steps add constraint a0r5_plan_step_side_effect_check
  check (side_effect in ('NONE','READ','WRITE'));
alter table execution_plan_revision_steps drop constraint if exists a0r5_plan_step_write_semantics_check;
alter table execution_plan_revision_steps add constraint a0r5_plan_step_write_semantics_check check (
  (side_effect <> 'WRITE' and write_semantics is null)
  or (side_effect='WRITE' and write_semantics in ('IDEMPOTENT','COMPENSATABLE','NON_COMPENSATABLE'))
);
alter table execution_plan_revision_steps drop constraint if exists a0r5_plan_step_compensation_check;
alter table execution_plan_revision_steps add constraint a0r5_plan_step_compensation_check check (
  write_semantics <> 'COMPENSATABLE' or compensation_binding_id is not null
);
alter table execution_plan_revision_steps drop constraint if exists a0r5_plan_step_fallback_check;
alter table execution_plan_revision_steps add constraint a0r5_plan_step_fallback_check check (max_binding_fallback between 0 and 20);

alter table plan_execution_steps
  add column if not exists side_effect varchar(16) not null default 'READ',
  add column if not exists write_semantics varchar(24),
  add column if not exists compensation_binding_id varchar(160),
  add column if not exists max_binding_fallback int not null default 0,
  add column if not exists binding_authorization_envelope_id varchar(180);

alter table plan_execution_steps drop constraint if exists a0r5_runtime_step_side_effect_check;
alter table plan_execution_steps add constraint a0r5_runtime_step_side_effect_check check (side_effect in ('NONE','READ','WRITE'));
alter table plan_execution_steps drop constraint if exists a0r5_runtime_step_write_semantics_check;
alter table plan_execution_steps add constraint a0r5_runtime_step_write_semantics_check check (
  (side_effect <> 'WRITE' and write_semantics is null)
  or (side_effect='WRITE' and write_semantics in ('IDEMPOTENT','COMPENSATABLE','NON_COMPENSATABLE'))
);

-- Flow Candidate Constraint is a business narrowing input, never authorization by itself.
create table if not exists flow_candidate_constraints (
  tenant_id varchar(64) not null,
  constraint_id varchar(180) not null,
  flow_id varchar(128) not null,
  rule_id varchar(128),
  capability_code varchar(160) not null,
  allowed_agent_pool_ids_json jsonb not null default '[]'::jsonb,
  allowed_binding_ids_json jsonb not null default '[]'::jsonb,
  allowed_binding_classes_json jsonb not null default '[]'::jsonb,
  excluded_binding_ids_json jsonb not null default '[]'::jsonb,
  constraint_reason varchar(512) not null,
  status varchar(24) not null default 'ACTIVE',
  version int not null default 1,
  created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
  primary key(tenant_id,constraint_id),
  constraint a0r5_flow_candidate_constraint_status check(status in ('DRAFT','ACTIVE','DISABLED','RETIRED')),
  constraint a0r5_flow_candidate_pools_array check(jsonb_typeof(allowed_agent_pool_ids_json)='array'),
  constraint a0r5_flow_candidate_ids_array check(jsonb_typeof(allowed_binding_ids_json)='array'),
  constraint a0r5_flow_candidate_classes_array check(jsonb_typeof(allowed_binding_classes_json)='array'),
  constraint a0r5_flow_candidate_excluded_array check(jsonb_typeof(excluded_binding_ids_json)='array')
);
create index if not exists idx_a0r5_flow_candidate_constraint_lookup on flow_candidate_constraints(tenant_id,flow_id,rule_id,capability_code,status);
alter table flow_candidate_constraints enable row level security;
drop policy if exists tenant_isolation on flow_candidate_constraints;
create policy tenant_isolation on flow_candidate_constraints using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

-- -----------------------------------------------------------------------------
-- 2. Plan Admission policy. This is a security ceiling/whitelist policy, not a
-- Routing Profile and not a Provider ranking policy.
-- -----------------------------------------------------------------------------
create table if not exists plan_admission_policies (
  tenant_id varchar(64) not null,
  policy_id varchar(160) not null,
  display_name varchar(255) not null,
  max_candidate_bindings_per_step int not null default 200,
  envelope_ttl_seconds bigint not null default 900,
  allowed_binding_classes_json jsonb not null default '["LOCAL_DETERMINISTIC","LOCAL_AGENT"]'::jsonb,
  allowed_data_classes_json jsonb not null default '["PUBLIC","INTERNAL"]'::jsonb,
  max_sensitivity_level varchar(24) not null default 'INTERNAL',
  external_egress_allowed boolean not null default false,
  allowed_regions_json jsonb not null default '[]'::jsonb,
  max_side_effect varchar(16) not null default 'READ',
  security_ceiling_ref varchar(255) not null,
  data_policy_ref varchar(255) not null,
  egress_policy_ref varchar(255) not null,
  residency_constraint_ref varchar(255) not null,
  status varchar(24) not null default 'DRAFT',
  version int not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,policy_id),
  constraint a0r5_plan_admission_policy_status_check check (status in ('DRAFT','ACTIVE','DISABLED','RETIRED')),
  constraint a0r5_plan_admission_policy_candidates_check check (max_candidate_bindings_per_step between 1 and 5000),
  constraint a0r5_plan_admission_policy_ttl_check check (envelope_ttl_seconds between 30 and 86400),
  constraint a0r5_plan_admission_policy_classes_array check (jsonb_typeof(allowed_binding_classes_json)='array'),
  constraint a0r5_plan_admission_policy_data_array check (jsonb_typeof(allowed_data_classes_json)='array'),
  constraint a0r5_plan_admission_policy_regions_array check (jsonb_typeof(allowed_regions_json)='array'),
  constraint a0r5_plan_admission_policy_sensitivity_check check (max_sensitivity_level in ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED','CRITICAL')),
  constraint a0r5_plan_admission_policy_side_effect_check check (max_side_effect in ('NONE','READ','WRITE')),
  constraint a0r5_plan_admission_policy_version_check check (version >= 1)
);
create unique index if not exists uq_a0r5_active_policy_per_tenant on plan_admission_policies(tenant_id) where status='ACTIVE';

create table if not exists plan_admission_policy_versions (
  tenant_id varchar(64) not null,
  policy_id varchar(160) not null,
  version int not null,
  snapshot_json jsonb not null,
  change_reason varchar(512) not null,
  actor_ref varchar(255) not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id,policy_id,version),
  constraint fk_a0r5_plan_admission_policy_version foreign key(tenant_id,policy_id)
    references plan_admission_policies(tenant_id,policy_id) on delete cascade,
  constraint a0r5_plan_admission_policy_snapshot_object check (jsonb_typeof(snapshot_json)='object')
);

-- -----------------------------------------------------------------------------
-- 3. Append-only Plan Admission decision + per-binding evaluation evidence.
-- -----------------------------------------------------------------------------
create table if not exists plan_admission_decisions (
  tenant_id varchar(64) not null,
  decision_id varchar(180) not null,
  plan_id varchar(160) not null,
  plan_revision int not null,
  result varchar(32) not null,
  policy_id varchar(160),
  policy_version int,
  policy_snapshot_ref varchar(255),
  step_count int not null,
  admitted_step_count int not null,
  denied_step_count int not null,
  waiting_approval_step_count int not null,
  candidate_binding_count int not null,
  admitted_binding_count int not null,
  reason_codes_json jsonb not null default '[]'::jsonb,
  actor_ref varchar(255) not null,
  decided_at timestamptz not null default now(),
  primary key (tenant_id,decision_id),
  constraint fk_a0r5_plan_admission_plan foreign key(tenant_id,plan_id) references execution_plans(tenant_id,plan_id) on delete cascade,
  constraint a0r5_plan_admission_result_check check (result in ('ADMITTED','DENIED','WAITING_APPROVAL','POLICY_NOT_CONFIGURED')),
  constraint a0r5_plan_admission_reason_array check (jsonb_typeof(reason_codes_json)='array'),
  constraint a0r5_plan_admission_counts_check check (step_count>=0 and admitted_step_count>=0 and denied_step_count>=0 and waiting_approval_step_count>=0 and candidate_binding_count>=0 and admitted_binding_count>=0)
);
create index if not exists idx_a0r5_plan_admission_decisions on plan_admission_decisions(tenant_id,plan_id,plan_revision,decided_at desc);

create table if not exists plan_admission_binding_evaluations (
  tenant_id varchar(64) not null,
  evaluation_id varchar(180) not null,
  decision_id varchar(180) not null,
  plan_id varchar(160) not null,
  plan_revision int not null,
  step_id varchar(160) not null,
  capability_code varchar(160) not null,
  capability_version int not null,
  binding_id varchar(160) not null,
  provider_id varchar(160) not null,
  provider_type varchar(40) not null,
  binding_class varchar(32) not null,
  trust_domain_ref varchar(255),
  result varchar(32) not null,
  authorization_decision_id varchar(160),
  reason_codes_json jsonb not null default '[]'::jsonb,
  evaluated_at timestamptz not null default now(),
  primary key (tenant_id,evaluation_id),
  constraint fk_a0r5_binding_eval_decision foreign key(tenant_id,decision_id) references plan_admission_decisions(tenant_id,decision_id) on delete cascade,
  constraint a0r5_binding_eval_class_check check (binding_class in ('LOCAL_DETERMINISTIC','LOCAL_AGENT','EXTERNAL_TOOL','EXTERNAL_AGENT')),
  constraint a0r5_binding_eval_result_check check (result in ('ADMITTED','DENIED','WAITING_APPROVAL')),
  constraint a0r5_binding_eval_reasons_array check (jsonb_typeof(reason_codes_json)='array')
);
create index if not exists idx_a0r5_binding_eval_step on plan_admission_binding_evaluations(tenant_id,plan_id,plan_revision,step_id,result,binding_id);

-- -----------------------------------------------------------------------------
-- 4. Step-scoped Plan-time Binding Authorization Envelope.
-- Arrays are the compact routing hard-filter contract; full candidate reasoning remains
-- externalized in plan_admission_binding_evaluations.
-- -----------------------------------------------------------------------------
create table if not exists binding_authorization_envelopes (
  tenant_id varchar(64) not null,
  envelope_id varchar(180) not null,
  decision_id varchar(180) not null,
  plan_id varchar(160) not null,
  plan_revision int not null,
  step_id varchar(160) not null,
  capability_code varchar(160) not null,
  capability_version int not null,
  admitted_binding_ids_json jsonb not null default '[]'::jsonb,
  admitted_binding_classes_json jsonb not null default '[]'::jsonb,
  admitted_trust_domain_refs_json jsonb not null default '[]'::jsonb,
  max_side_effect varchar(16) not null,
  security_ceiling_ref varchar(255) not null,
  data_policy_ref varchar(255) not null,
  egress_policy_ref varchar(255) not null,
  residency_constraint_ref varchar(255) not null,
  policy_snapshot_ref varchar(255) not null,
  authorization_epoch bigint not null default 1,
  revocation_version bigint not null default 0,
  status varchar(16) not null default 'ACTIVE',
  issued_at timestamptz not null default now(),
  valid_until timestamptz not null,
  revoked_at timestamptz,
  revocation_reason varchar(512),
  primary key (tenant_id,envelope_id),
  unique (tenant_id,decision_id,step_id),
  constraint fk_a0r5_binding_envelope_decision foreign key(tenant_id,decision_id) references plan_admission_decisions(tenant_id,decision_id) on delete cascade,
  constraint a0r5_binding_envelope_ids_array check (jsonb_typeof(admitted_binding_ids_json)='array'),
  constraint a0r5_binding_envelope_classes_array check (jsonb_typeof(admitted_binding_classes_json)='array'),
  constraint a0r5_binding_envelope_trust_domains_array check (jsonb_typeof(admitted_trust_domain_refs_json)='array'),
  constraint a0r5_binding_envelope_side_effect_check check (max_side_effect in ('NONE','READ','WRITE')),
  constraint a0r5_binding_envelope_status_check check (status in ('ACTIVE','EXPIRED','REVOKED')),
  constraint a0r5_binding_envelope_expiry_check check (valid_until > issued_at)
);
create index if not exists idx_a0r5_binding_envelope_plan on binding_authorization_envelopes(tenant_id,plan_id,plan_revision,step_id,status,valid_until);

create table if not exists binding_authorization_envelope_events (
  tenant_id varchar(64) not null,
  event_id varchar(180) not null,
  envelope_id varchar(180) not null,
  event_type varchar(48) not null,
  from_status varchar(16),
  to_status varchar(16) not null,
  revocation_version bigint not null,
  reason_code varchar(160),
  actor_ref varchar(255) not null,
  evidence_json jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null default now(),
  primary key (tenant_id,event_id),
  constraint fk_a0r5_binding_envelope_event foreign key(tenant_id,envelope_id) references binding_authorization_envelopes(tenant_id,envelope_id) on delete cascade,
  constraint a0r5_binding_envelope_event_evidence_object check (jsonb_typeof(evidence_json)='object')
);

-- Runtime envelope is Assignment-time revalidation and MUST be a child of the Plan-time envelope for PLAN_STEP.
alter table capability_runtime_authorization_envelopes
  add column if not exists parent_binding_authorization_envelope_id varchar(180);
alter table capability_runtime_authorization_envelopes drop constraint if exists fk_a0r5_runtime_parent_binding_envelope;
alter table capability_runtime_authorization_envelopes add constraint fk_a0r5_runtime_parent_binding_envelope
  foreign key(tenant_id,parent_binding_authorization_envelope_id)
  references binding_authorization_envelopes(tenant_id,envelope_id) on delete restrict;
alter table capability_runtime_authorization_envelopes drop constraint if exists a0r5_plan_runtime_parent_required;
alter table capability_runtime_authorization_envelopes add constraint a0r5_plan_runtime_parent_required check (
  execution_context_type <> 'PLAN_STEP' or parent_binding_authorization_envelope_id is not null
);

-- -----------------------------------------------------------------------------
-- 5. DB guard: a PLAN_STEP RuntimeAuthorizationEnvelope cannot widen its parent.
-- -----------------------------------------------------------------------------
create or replace function enforce_a0r5_runtime_envelope_parent() returns trigger language plpgsql as $$
declare p binding_authorization_envelopes%rowtype;
begin
  if new.execution_context_type <> 'PLAN_STEP' then return new; end if;
  select * into p from binding_authorization_envelopes
   where tenant_id=new.tenant_id and envelope_id=new.parent_binding_authorization_envelope_id;
  if p.envelope_id is null then raise exception 'A0_R5_PARENT_BINDING_AUTHORIZATION_ENVELOPE_REQUIRED'; end if;
  if p.status <> 'ACTIVE' or p.valid_until <= now() then raise exception 'A0_R5_PARENT_BINDING_AUTHORIZATION_ENVELOPE_NOT_ACTIVE'; end if;
  if not (p.admitted_binding_ids_json ? new.binding_id) then raise exception 'A0_R5_RUNTIME_BINDING_OUTSIDE_PARENT_ENVELOPE'; end if;
  return new;
end $$;
drop trigger if exists trg_a0r5_runtime_envelope_parent on capability_runtime_authorization_envelopes;
create trigger trg_a0r5_runtime_envelope_parent before insert or update of binding_id,parent_binding_authorization_envelope_id,status
on capability_runtime_authorization_envelopes for each row execute function enforce_a0r5_runtime_envelope_parent();

-- -----------------------------------------------------------------------------
-- 6. Tenant isolation and immutable evidence.
-- -----------------------------------------------------------------------------
alter table plan_admission_policies enable row level security;
drop policy if exists tenant_isolation on plan_admission_policies;
create policy tenant_isolation on plan_admission_policies using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table plan_admission_policy_versions enable row level security;
drop policy if exists tenant_isolation on plan_admission_policy_versions;
create policy tenant_isolation on plan_admission_policy_versions using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table plan_admission_decisions enable row level security;
drop policy if exists tenant_isolation on plan_admission_decisions;
create policy tenant_isolation on plan_admission_decisions using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table plan_admission_binding_evaluations enable row level security;
drop policy if exists tenant_isolation on plan_admission_binding_evaluations;
create policy tenant_isolation on plan_admission_binding_evaluations using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table binding_authorization_envelopes enable row level security;
drop policy if exists tenant_isolation on binding_authorization_envelopes;
create policy tenant_isolation on binding_authorization_envelopes using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table binding_authorization_envelope_events enable row level security;
drop policy if exists tenant_isolation on binding_authorization_envelope_events;
create policy tenant_isolation on binding_authorization_envelope_events using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

create or replace function prevent_a0r5_plan_admission_decision_mutation() returns trigger language plpgsql as $$ begin raise exception 'A0_R5_PLAN_ADMISSION_DECISION_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_a0r5_plan_admission_decision_immutable on plan_admission_decisions;
create trigger trg_a0r5_plan_admission_decision_immutable before update or delete on plan_admission_decisions for each row execute function prevent_a0r5_plan_admission_decision_mutation();
create or replace function prevent_a0r5_binding_evaluation_mutation() returns trigger language plpgsql as $$ begin raise exception 'A0_R5_PLAN_ADMISSION_BINDING_EVALUATION_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_a0r5_binding_evaluation_immutable on plan_admission_binding_evaluations;
create trigger trg_a0r5_binding_evaluation_immutable before update or delete on plan_admission_binding_evaluations for each row execute function prevent_a0r5_binding_evaluation_mutation();
create or replace function prevent_a0r5_envelope_event_mutation() returns trigger language plpgsql as $$ begin raise exception 'A0_R5_BINDING_AUTHORIZATION_ENVELOPE_EVENT_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_a0r5_envelope_event_immutable on binding_authorization_envelope_events;
create trigger trg_a0r5_envelope_event_immutable before update or delete on binding_authorization_envelope_events for each row execute function prevent_a0r5_envelope_event_mutation();
create or replace function prevent_a0r5_policy_version_mutation() returns trigger language plpgsql as $$ begin raise exception 'A0_R5_PLAN_ADMISSION_POLICY_VERSION_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_a0r5_policy_version_immutable on plan_admission_policy_versions;
create trigger trg_a0r5_policy_version_immutable before update or delete on plan_admission_policy_versions for each row execute function prevent_a0r5_policy_version_mutation();

-- Current read model only; decisions remain append-only.
create or replace view plan_admission_current_v203 as
select distinct on (d.tenant_id,d.plan_id,d.plan_revision)
       d.tenant_id,d.plan_id,d.plan_revision,d.decision_id,d.result,d.policy_id,d.policy_version,
       d.policy_snapshot_ref,d.step_count,d.admitted_step_count,d.denied_step_count,
       d.waiting_approval_step_count,d.candidate_binding_count,d.admitted_binding_count,
       d.reason_codes_json,d.actor_ref,d.decided_at
  from plan_admission_decisions d
 order by d.tenant_id,d.plan_id,d.plan_revision,d.decided_at desc,d.decision_id desc;

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values(
 'mrs-a0-r5-plan-admission-binding-envelope-v1',
 'A0_R5_PLAN_ADMISSION_BINDING_AUTHORIZATION',
 'PLAN_ADMISSION_IS_DEFAULT_DENY_AND_ONLY_AUTHORIZES_THE_BINDING_BOUNDARY_FOR_ROUTING; IT_NEVER_RANKS_SELECTS_ASSIGNMENTS_OR_SENDS_IO; RUNTIME_AUTHORIZATION_ENVELOPE_MUST_BE_CHILD_OF_AND_NOT_WIDEN_PLAN_BINDING_AUTHORIZATION_ENVELOPE',
 now(),'V203')
on conflict(contract_id) do nothing;

-- -----------------------------------------------------------------------------
-- 7. Revocation propagation. Binding trust degradation invalidates every active
-- Plan-time envelope that admitted that named binding. This prevents stale
-- admission cache semantics.
-- -----------------------------------------------------------------------------
create or replace function a0r5_revoke_envelopes_on_binding_trust_change() returns trigger language plpgsql as $$
begin
  if old.trust_status='APPROVED' and new.trust_status<>'APPROVED' then
    with revoked as (
      update binding_authorization_envelopes
         set status='REVOKED',revocation_version=revocation_version+1,authorization_epoch=authorization_epoch+1,
             revoked_at=now(),revocation_reason='BINDING_TRUST_'||new.trust_status
       where tenant_id=new.tenant_id and status='ACTIVE' and admitted_binding_ids_json ? new.binding_id
       returning *
    )
    insert into binding_authorization_envelope_events(
      tenant_id,event_id,envelope_id,event_type,from_status,to_status,revocation_version,reason_code,actor_ref,evidence_json,occurred_at)
    select tenant_id,'a0r5-auto-revoke-'||substr(md5(envelope_id||':'||clock_timestamp()::text||':'||random()::text),1,24),
           envelope_id,'REVOKED','ACTIVE','REVOKED',revocation_version,revocation_reason,
           'DB_TRIGGER:capability_bindings',jsonb_build_object('bindingId',new.binding_id,'oldTrustStatus',old.trust_status,'newTrustStatus',new.trust_status),now()
      from revoked;
  end if;
  return new;
end $$;
drop trigger if exists trg_a0r5_binding_trust_revokes_plan_envelopes on capability_bindings;
create trigger trg_a0r5_binding_trust_revokes_plan_envelopes after update of trust_status on capability_bindings
for each row execute function a0r5_revoke_envelopes_on_binding_trust_change();

-- Adapter availability/credential changes are also authorization invalidation signals.
-- An envelope is revoked only when the provider no longer has the required ACTIVE adapter
-- capability after the change, so a healthy alternate adapter does not cause false revocation.
create or replace function a0r5_revoke_envelopes_on_adapter_security_change() returns trigger language plpgsql as $$
declare reason text;
begin
  reason := null;
  if old.status='ACTIVE' and new.status<>'ACTIVE'
     and not exists (
       select 1 from execution_adapter_registrations a
        where a.tenant_id=new.tenant_id and a.provider_id=new.provider_id and a.status='ACTIVE'
     ) then
    reason := 'EXECUTION_ADAPTER_'||new.status;
  elsif coalesce(btrim(old.credential_ref),'')<>'' and coalesce(btrim(new.credential_ref),'')=''
     and not exists (
       select 1 from execution_adapter_registrations a
        where a.tenant_id=new.tenant_id and a.provider_id=new.provider_id and a.status='ACTIVE'
          and a.credential_ref is not null and btrim(a.credential_ref)<>''
     ) then
    reason := 'EXECUTION_CREDENTIAL_UNAVAILABLE';
  end if;

  if reason is not null then
    with provider_bindings as (
      select binding_id from capability_bindings
       where tenant_id=new.tenant_id and provider_id=new.provider_id
    ), revoked as (
      update binding_authorization_envelopes e
         set status='REVOKED',revocation_version=revocation_version+1,authorization_epoch=authorization_epoch+1,
             revoked_at=now(),revocation_reason=reason
       where e.tenant_id=new.tenant_id and e.status='ACTIVE'
         and exists (select 1 from provider_bindings b where e.admitted_binding_ids_json ? b.binding_id)
       returning e.*
    )
    insert into binding_authorization_envelope_events(
      tenant_id,event_id,envelope_id,event_type,from_status,to_status,revocation_version,reason_code,actor_ref,evidence_json,occurred_at)
    select tenant_id,'a0r5-auto-revoke-'||substr(md5(envelope_id||':'||clock_timestamp()::text||':'||random()::text),1,24),
           envelope_id,'REVOKED','ACTIVE','REVOKED',revocation_version,revocation_reason,
           'DB_TRIGGER:execution_adapter_registrations',jsonb_build_object('providerId',new.provider_id,'adapterId',new.adapter_id,'oldStatus',old.status,'newStatus',new.status),now()
      from revoked;
  end if;
  return new;
end $$;
drop trigger if exists trg_a0r5_adapter_security_revokes_plan_envelopes on execution_adapter_registrations;
create trigger trg_a0r5_adapter_security_revokes_plan_envelopes
  after update of status,credential_ref on execution_adapter_registrations
  for each row execute function a0r5_revoke_envelopes_on_adapter_security_change();

alter table plan_execution_steps drop constraint if exists fk_a0r5_runtime_step_binding_envelope;
alter table plan_execution_steps add constraint fk_a0r5_runtime_step_binding_envelope
  foreign key(tenant_id,binding_authorization_envelope_id)
  references binding_authorization_envelopes(tenant_id,envelope_id) on delete restrict;

-- -----------------------------------------------------------------------------
-- Flyway INSTANCE governance context for FORCE-RLS IAM/catalog DML below.
-- set_config(..., true) is transaction-local; earlier tenant-data backfills remain context-neutral.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','a0-r5-plan-admission-migration',true);

-- 8. Dedicated RBAC vocabulary. Plan Admission is not Agent Assignment.
-- -----------------------------------------------------------------------------
insert into resource_catalog(resource_type,category,descriptor_authority,ownership_supported,participants_supported,
  field_visibility_supported,runtime_lease_supported,default_sensitivity,description)
values('PLAN_ADMISSION','GOVERNANCE','PLAN_ADMISSION',true,true,true,false,'CONFIDENTIAL','Plan Admission policy, decision and Binding Authorization Envelope governance')
on conflict(resource_type) do update set category=excluded.category,descriptor_authority=excluded.descriptor_authority,
 default_sensitivity=excluded.default_sensitivity,description=excluded.description,status='ACTIVE',catalog_version=resource_catalog.catalog_version+1,updated_at=now();

insert into permission_catalog_revisions(
 revision_id,revision_code,revision_number,status,content_hash,description,supersedes_revision_id,
 created_at,created_by,published_at,published_by,version)
select '00000000-0000-0000-0000-000000000024'::uuid,'A0-R5-PLAN-ADMISSION-0.8.2',coalesce(max(revision_number),0)+1,
 'DRAFT','DRAFT:UNPUBLISHED','A0-R5 Plan Admission and Binding Authorization Envelope governance.',
 (select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE'),now(),'a0-r5-plan-admission',null,null,1
from permission_catalog_revisions on conflict(revision_id) do nothing;

insert into permission_catalog_revision_entries(
 revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,
 allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
select '00000000-0000-0000-0000-000000000024'::uuid,e.permission_code,e.owner_module,e.resource_type,e.action_code,e.description,
 e.risk_level,e.risk_lane,e.lifecycle,e.allowed_scope_types,e.system_managed,e.replacement_permission_code,e.introduced_at,e.deprecated_at,
 e.retired_at,now(),'a0-r5-plan-admission',1
from permission_catalog_revision_entries e join permission_catalog_active_revision a on a.singleton_id='ACTIVE' and a.revision_id=e.revision_id
on conflict(revision_id,permission_code) do nothing;

insert into permission_catalog_revision_aliases(
 revision_id,alias_code,canonical_permission_code,alias_type,valid_from,valid_until,reason,created_by,created_at,version)
select '00000000-0000-0000-0000-000000000024'::uuid,a.alias_code,a.canonical_permission_code,a.alias_type,a.valid_from,a.valid_until,a.reason,
 'a0-r5-plan-admission',now(),1
from permission_catalog_revision_aliases a join permission_catalog_active_revision active on active.singleton_id='ACTIVE' and active.revision_id=a.revision_id
on conflict(revision_id,alias_code) do nothing;

insert into permission_catalog_revision_entries(
 revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,
 allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
values
 ('00000000-0000-0000-0000-000000000024','plan.admission.read','control-plane-app','PLAN_ADMISSION','READ','Read Plan Admission policies, decisions, evaluations and envelopes.','HIGH','READ','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'a0-r5-plan-admission',1),
 ('00000000-0000-0000-0000-000000000024','plan.admission.manage','control-plane-app','PLAN_ADMISSION','MANAGE','Manage Plan Admission policies, execute formal admission and revoke envelopes.','CRITICAL','CRITICAL','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'a0-r5-plan-admission',1)
on conflict(revision_id,permission_code) do update set description=excluded.description,risk_level=excluded.risk_level,risk_lane=excluded.risk_lane,lifecycle='ACTIVE',updated_at=now(),updated_by='a0-r5-plan-admission',version=permission_catalog_revision_entries.version+1;

select set_config('app.permission_catalog_publish_revision_id','00000000-0000-0000-0000-000000000024',true);
insert into permission_definitions(
 permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,active,version,owner_module,risk_lane,lifecycle,
 catalog_revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by)
select permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,lifecycle<>'RETIRED',1,owner_module,risk_lane,lifecycle,
 revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by
from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000024'::uuid
on conflict(permission_code) do update set resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,
 risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,system_managed=excluded.system_managed,active=excluded.active,
 owner_module=excluded.owner_module,risk_lane=excluded.risk_lane,lifecycle=excluded.lifecycle,catalog_revision_id=excluded.catalog_revision_id,
 replacement_permission_code=excluded.replacement_permission_code,deprecated_at=excluded.deprecated_at,retired_at=excluded.retired_at,
 updated_at=excluded.updated_at,updated_by=excluded.updated_by,version=permission_definitions.version+1;

update permission_catalog_revisions set status='SUPERSEDED',version=version+1
where revision_id=(select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE')
 and revision_id<>'00000000-0000-0000-0000-000000000024'::uuid and status='PUBLISHED';
update permission_catalog_revisions set status='PUBLISHED',content_hash=(
 with catalog_lines as (
   select 'P|'||permission_code||'|'||owner_module||'|'||resource_type||'|'||action_code||'|'||description||'|'||risk_level||'|'||risk_lane||'|'||lifecycle||'|'||coalesce(array_to_string(allowed_scope_types,','),'')||'|'||system_managed::text||'|'||coalesce(replacement_permission_code,'') line
   from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000024'::uuid
   union all
   select 'A|'||alias_code||'|'||canonical_permission_code||'|'||alias_type||'|'||coalesce(to_char(valid_from at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||coalesce(to_char(valid_until at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||reason
   from permission_catalog_revision_aliases where revision_id='00000000-0000-0000-0000-000000000024'::uuid)
 select 'sha256:'||encode(sha256(convert_to(coalesce(string_agg(line,E'\n' order by line),''),'UTF8')),'hex') from catalog_lines),
 published_at=now(),published_by='a0-r5-plan-admission',version=version+1
where revision_id='00000000-0000-0000-0000-000000000024'::uuid and status='DRAFT';
update permission_catalog_active_revision set revision_id='00000000-0000-0000-0000-000000000024'::uuid,
 activated_at=now(),activated_by='a0-r5-plan-admission',version=version+1 where singleton_id='ACTIVE';
insert into permission_catalog_publication_events(publication_id,revision_id,previous_revision_id,content_hash,entry_count,alias_count,actor_id,audit_reason,correlation_id,published_at)
select '00000000-0000-0000-0000-000000005024'::uuid,r.revision_id,r.supersedes_revision_id,r.content_hash,
 (select count(*)::integer from permission_catalog_revision_entries e where e.revision_id=r.revision_id),
 (select count(*)::integer from permission_catalog_revision_aliases a where a.revision_id=r.revision_id),
 'a0-r5-plan-admission','A0-R5 Plan Admission publication','a0-r5-plan-admission',coalesce(r.published_at,now())
from permission_catalog_revisions r where r.revision_id='00000000-0000-0000-0000-000000000024'::uuid
on conflict(publication_id) do nothing;

insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'a0r5-'||substr(md5(r.role_id||':'||p.permission_code),1,32),null,r.role_id,p.permission_code,now(),'a0-r5-plan-admission',1
from rbac_roles r join permission_definitions p on p.permission_code in('plan.admission.read','plan.admission.manage') and p.active=true
where r.tenant_id is null and r.role_code in('DISPATCH_ADMIN','TENANT_ADMIN') and r.status='ACTIVE'
on conflict(role_id,permission_point) do nothing;

-- Human Admin fail-closed route inventory for A0-R5.
insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,
 target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,
 migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values
('REST:GET:/admin/plan-admission/policies','REST','control-plane-app','control-plane-app','PlanAdmissionController.policies','/admin/plan-admission/policies','GET','TARGET_ONLY','plan.admission.read',null,'[]'::jsonb,'PLAN_ADMISSION','R3_TENANT_RESOURCE_RESOLVER',null,null,'a0-r5-plan-admission-2026-08-24','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/PlanAdmissionController.java#policies','f3a5cd2f582f9d7b9b8db32682056b0a4dfc776c921180714fbc10251dd9236a',now(),'a0-r5-plan-admission','a0-r5-plan-admission'),
('REST:PUT:/admin/plan-admission/policies/{policyId}','REST','control-plane-app','control-plane-app','PlanAdmissionController.upsertPolicy','/admin/plan-admission/policies/{policyId}','PUT','TARGET_ONLY','plan.admission.manage',null,'[]'::jsonb,'PLAN_ADMISSION','R3_TENANT_RESOURCE_RESOLVER',null,null,'a0-r5-plan-admission-2026-08-24','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/PlanAdmissionController.java#upsertPolicy','f3a5cd2f582f9d7b9b8db32682056b0a4dfc776c921180714fbc10251dd9236a',now(),'a0-r5-plan-admission','a0-r5-plan-admission'),
('REST:POST:/admin/plan-admission/plans/{planId}/admit','REST','control-plane-app','control-plane-app','PlanAdmissionController.admit','/admin/plan-admission/plans/{planId}/admit','POST','TARGET_ONLY','plan.admission.manage',null,'[]'::jsonb,'PLAN_ADMISSION','R3_TENANT_RESOURCE_RESOLVER',null,null,'a0-r5-plan-admission-2026-08-24','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/PlanAdmissionController.java#admit','f3a5cd2f582f9d7b9b8db32682056b0a4dfc776c921180714fbc10251dd9236a',now(),'a0-r5-plan-admission','a0-r5-plan-admission'),
('REST:GET:/admin/plan-admission/plans/{planId}/decisions','REST','control-plane-app','control-plane-app','PlanAdmissionController.decisions','/admin/plan-admission/plans/{planId}/decisions','GET','TARGET_ONLY','plan.admission.read',null,'[]'::jsonb,'PLAN_ADMISSION','R3_TENANT_RESOURCE_RESOLVER',null,null,'a0-r5-plan-admission-2026-08-24','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/PlanAdmissionController.java#decisions','f3a5cd2f582f9d7b9b8db32682056b0a4dfc776c921180714fbc10251dd9236a',now(),'a0-r5-plan-admission','a0-r5-plan-admission'),
('REST:GET:/admin/plan-admission/plans/{planId}/envelopes','REST','control-plane-app','control-plane-app','PlanAdmissionController.envelopes','/admin/plan-admission/plans/{planId}/envelopes','GET','TARGET_ONLY','plan.admission.read',null,'[]'::jsonb,'PLAN_ADMISSION','R3_TENANT_RESOURCE_RESOLVER',null,null,'a0-r5-plan-admission-2026-08-24','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/PlanAdmissionController.java#envelopes','f3a5cd2f582f9d7b9b8db32682056b0a4dfc776c921180714fbc10251dd9236a',now(),'a0-r5-plan-admission','a0-r5-plan-admission'),
('REST:GET:/admin/plan-admission/decisions/{decisionId}/evaluations','REST','control-plane-app','control-plane-app','PlanAdmissionController.evaluations','/admin/plan-admission/decisions/{decisionId}/evaluations','GET','TARGET_ONLY','plan.admission.read',null,'[]'::jsonb,'PLAN_ADMISSION','R3_TENANT_RESOURCE_RESOLVER',null,null,'a0-r5-plan-admission-2026-08-24','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/PlanAdmissionController.java#evaluations','f3a5cd2f582f9d7b9b8db32682056b0a4dfc776c921180714fbc10251dd9236a',now(),'a0-r5-plan-admission','a0-r5-plan-admission'),
('REST:POST:/admin/plan-admission/envelopes/{envelopeId}/revoke','REST','control-plane-app','control-plane-app','PlanAdmissionController.revoke','/admin/plan-admission/envelopes/{envelopeId}/revoke','POST','TARGET_ONLY','plan.admission.manage',null,'[]'::jsonb,'PLAN_ADMISSION','R3_TENANT_RESOURCE_RESOLVER',null,null,'a0-r5-plan-admission-2026-08-24','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/PlanAdmissionController.java#revoke','f3a5cd2f582f9d7b9b8db32682056b0a4dfc776c921180714fbc10251dd9236a',now(),'a0-r5-plan-admission','a0-r5-plan-admission')
on conflict(entry_point_id) do update set display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,
 authority_state=excluded.authority_state,target_permission_code=excluded.target_permission_code,legacy_authority_type=null,legacy_authorities='[]'::jsonb,
 resource_type=excluded.resource_type,resource_resolver_id=excluded.resource_resolver_id,manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,
 source_hash=excluded.source_hash,last_verified_at=now(),updated_at=now(),updated_by='a0-r5-plan-admission',version=permission_entry_point_inventory.version+1;

update rbac_policy_versions set policy_version=policy_version+1,updated_at=now(),updated_by='a0-r5-plan-admission';
