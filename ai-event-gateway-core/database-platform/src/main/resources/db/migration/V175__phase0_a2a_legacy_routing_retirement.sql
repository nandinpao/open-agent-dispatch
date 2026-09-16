-- Phase 0: hard-retire legacy directional A2A routing.
-- Historical A2A policy/request rows remain for audit, reconciliation and evidence only.
-- New capability-first delegation is introduced in a later phase; this migration does not invent that model.

update a2a_cutover_state
set stage='LEGACY_WRITE_DISABLED',
    legacy_write_enabled=false,
    shadow_read_enabled=false,
    updated_at=now(),
    updated_by='phase0-a2a-architecture-reset',
    version=version+1
where scope_id='INSTANCE' and stage <> 'CONTRACT';

update a2a_policies
set enabled=false,
    updated_at=now(),
    version=version+1
where enabled=true;


-- Retired A2A policies must no longer block Department / Group lifecycle changes.
-- These functions preserve every non-A2A resource guard from RS3 and only ignore disabled historical A2A rows.
create or replace function p23b_guard_department_resource_scope_status()
returns trigger language plpgsql security invoker as $$
begin
  if new.status in ('DISABLED','DELETED') and old.status is distinct from new.status then
    if exists (select 1 from source_systems s where s.tenant_id=new.tenant_id and s.owner_department_id=new.department_id and s.status<>'RETIRED')
       or exists (select 1 from dispatch_flows f where f.tenant_id=new.tenant_id and f.owner_department_id=new.department_id and f.status<>'RETIRED')
       or exists (select 1 from agent_pools p where p.tenant_id=new.tenant_id and p.owner_department_id=new.department_id and p.status<>'RETIRED')
       or exists (select 1 from agent_profiles a where a.tenant_id=new.tenant_id and a.owner_department_id=new.department_id
                    and a.owner_department_id<>'UNASSIGNED' and a.approval_status not in ('REVOKED','REJECTED')) then
      if new.status='DELETED' then
        raise exception 'DEPARTMENT_DELETE_BLOCKED: re-scope active Source Systems, Dispatch Flows, Agent Pools, and Agents first' using errcode='23514';
      else
        raise exception 'DEPARTMENT_DISABLE_BLOCKED_BY_RESOURCES' using errcode='23514';
      end if;
    end if;
    if exists (select 1 from a2a_policies a where a.tenant_id=new.tenant_id and a.enabled=true
               and (a.source_department_id=new.department_id or a.target_department_id=new.department_id)) then
      if new.status='DELETED' then raise exception 'DEPARTMENT_DELETE_BLOCKED: re-scope active A2A Policies first' using errcode='23514';
      else raise exception 'DEPARTMENT_DISABLE_BLOCKED_BY_A2A_POLICIES' using errcode='23514'; end if;
    end if;
  end if;
  return new;
end $$;

create or replace function p23b_guard_group_resource_scope_status()
returns trigger language plpgsql security invoker as $$
begin
  if new.status in ('DISABLED','DELETED') and old.status is distinct from new.status then
    if exists (select 1 from source_systems s where s.tenant_id=new.tenant_id and s.owner_group_id=new.group_id and s.status<>'RETIRED')
       or exists (select 1 from dispatch_flows f where f.tenant_id=new.tenant_id and f.owner_group_id=new.group_id and f.status<>'RETIRED')
       or exists (select 1 from agent_pools p where p.tenant_id=new.tenant_id and p.owner_group_id=new.group_id and p.status<>'RETIRED')
       or exists (select 1 from agent_profiles a where a.tenant_id=new.tenant_id and a.owner_group_id=new.group_id
                    and a.approval_status not in ('REVOKED','REJECTED')) then
      if new.status='DELETED' then
        raise exception 'GROUP_DELETE_BLOCKED: re-scope active Source Systems, Dispatch Flows, Agent Pools, and Agents first' using errcode='23514';
      else
        raise exception 'GROUP_DISABLE_BLOCKED_BY_RESOURCES' using errcode='23514';
      end if;
    end if;
    if exists (select 1 from a2a_policies a where a.tenant_id=new.tenant_id and a.enabled=true
               and (a.source_group_id=new.group_id or a.target_group_id=new.group_id)) then
      if new.status='DELETED' then raise exception 'GROUP_DELETE_BLOCKED: re-scope active A2A Policies first' using errcode='23514';
      else raise exception 'GROUP_DISABLE_BLOCKED_BY_A2A_POLICIES' using errcode='23514'; end if;
    end if;
  end if;
  return new;
end $$;

create table if not exists a2a_architecture_retirement_evidence (
  evidence_id varchar(160) primary key,
  architecture_version varchar(80) not null,
  retired_model varchar(160) not null,
  replacement_principle varchar(512) not null,
  effective_at timestamptz not null,
  created_by varchar(160) not null
);

insert into a2a_architecture_retirement_evidence(
  evidence_id,architecture_version,retired_model,replacement_principle,effective_at,created_by)
values(
  'phase0-directional-a2a-routing-retired',
  'PHASE0_CAPABILITY_DRIVEN_RESET',
  'SOURCE_DOMAIN_TO_TARGET_DOMAIN_AND_POLICY_TO_AGENT_POOL',
  'DELEGATION_MUST_BE_CAPABILITY_FIRST_PROVIDER_NEUTRAL_AND_PROTOCOL_NEUTRAL',
  now(),
  'V175')
on conflict(evidence_id) do nothing;

create or replace function prevent_a2a_architecture_retirement_evidence_mutation() returns trigger language plpgsql as $$
begin raise exception 'A2A_ARCHITECTURE_RETIREMENT_EVIDENCE_IS_APPEND_ONLY'; end $$;

drop trigger if exists trg_a2a_architecture_retirement_evidence_immutable on a2a_architecture_retirement_evidence;
create trigger trg_a2a_architecture_retirement_evidence_immutable
before update or delete on a2a_architecture_retirement_evidence
for each row execute function prevent_a2a_architecture_retirement_evidence_mutation();
