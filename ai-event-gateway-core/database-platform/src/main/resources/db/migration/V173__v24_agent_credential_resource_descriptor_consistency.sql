-- V24 credential/runtime authorization convergence.
--
-- Problem fixed:
-- Agent credential rotation increments agent_profiles.policy_version.  The Phase 10/RS3
-- security-epoch triggers then write AGENT / AGENT_SERVICE_SCOPE / AGENT_CREDENTIAL_METADATA
-- rows to resource_security_epochs.  That table has a FK to resource_descriptors, but older
-- Agent profiles are not guaranteed to have all three descriptor projections persisted yet.
-- The resulting FK violation was surfaced as HTTP 500 from /admin/agents/{agentId}/credentials/issue.
--
-- The canonical Agent profile remains the authority.  This migration only makes the Resource
-- Access projection/epoch dependency deterministic and backfills existing Agents tenant-by-tenant.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','v24-agent-credential-descriptor-consistency',true);

create or replace function v24_ensure_agent_machine_descriptors(
  p_tenant_id varchar,
  p_agent_id varchar,
  p_actor varchar default 'v24-agent-credential-descriptor-consistency'
) returns void
language plpgsql
security invoker
as $$
declare
  v_now timestamptz := now();
begin
  insert into resource_descriptors(
    tenant_id, resource_type, resource_id, resource_key,
    owner_department_id, owner_group_id,
    sensitivity_level, maximum_visibility, visibility_policy_id,
    security_state, ownership_version, participant_version, resource_version,
    descriptor_authority, descriptor_hash, source_updated_at,
    visibility_policy_version, source_resolved_at, last_projected_at,
    projection_status, updated_at
  )
  select
    a.tenant_id,
    rt.resource_type,
    a.agent_id,
    coalesce(nullif(a.agent_name,''),a.agent_id),
    nullif(nullif(a.owner_department_id,''),'UNASSIGNED'),
    nullif(a.owner_group_id,''),
    case when rt.resource_type='AGENT_CREDENTIAL_METADATA' then 'SECRET' else 'RESTRICTED' end,
    case when rt.resource_type='AGENT_CREDENTIAL_METADATA' then 'SECRET_METADATA' else 'SENSITIVE' end,
    rt.resource_type,
    case
      when a.risk_status in ('QUARANTINED','COMPROMISED') then 'QUARANTINED'
      when a.risk_status in ('SUSPENDED','REVOKED') then 'RESTRICTED'
      when a.approval_status in ('REVOKED','REJECTED') or not a.enabled then 'RESTRICTED'
      else 'NORMAL'
    end,
    greatest(coalesce(a.policy_version,1),0),
    0,
    greatest(coalesce(a.policy_version,1),0),
    'AGENT_CONTROL',
    md5(a.tenant_id||'|'||rt.resource_type||'|'||a.agent_id||'|'||coalesce(a.approval_status,'')||'|'||coalesce(a.risk_status,'')||'|'||a.enabled::text||'|'||coalesce(a.owner_department_id,'')||'|'||coalesce(a.owner_group_id,'')||'|'||coalesce(a.policy_version,1)::text)
      || md5(rt.resource_type||'|'||a.agent_id||'|'||coalesce(a.updated_at,v_now)::text),
    coalesce(a.updated_at,v_now),
    greatest(coalesce(a.policy_version,1),0),
    coalesce(a.updated_at,v_now),
    v_now,
    'CURRENT',
    v_now
  from agent_profiles a
  cross join (values ('AGENT'),('AGENT_SERVICE_SCOPE'),('AGENT_CREDENTIAL_METADATA')) rt(resource_type)
  where a.tenant_id=p_tenant_id and a.agent_id=p_agent_id
  on conflict(tenant_id,resource_type,resource_id) do update set
    resource_key=excluded.resource_key,
    owner_department_id=excluded.owner_department_id,
    owner_group_id=excluded.owner_group_id,
    sensitivity_level=excluded.sensitivity_level,
    maximum_visibility=excluded.maximum_visibility,
    visibility_policy_id=excluded.visibility_policy_id,
    security_state=excluded.security_state,
    ownership_version=greatest(resource_descriptors.ownership_version,excluded.ownership_version),
    resource_version=greatest(resource_descriptors.resource_version,excluded.resource_version),
    descriptor_authority='AGENT_CONTROL',
    descriptor_hash=excluded.descriptor_hash,
    source_updated_at=excluded.source_updated_at,
    visibility_policy_version=greatest(resource_descriptors.visibility_policy_version,excluded.visibility_policy_version),
    source_resolved_at=excluded.source_resolved_at,
    last_projected_at=excluded.last_projected_at,
    projection_status='CURRENT',
    updated_at=excluded.updated_at;
end $$;

-- Recreate the Phase 10 trigger function with descriptor synchronization before epoch mutation.
create or replace function phase10_touch_agent_machine_epoch()
returns trigger language plpgsql as $$
declare actor varchar;
begin
  if old.policy_version is not distinct from new.policy_version then return new; end if;
  actor=coalesce(nullif(current_setting('app.current_actor_id',true),''),'phase10-agent-machine-identity');
  perform v24_ensure_agent_machine_descriptors(new.tenant_id,new.agent_id,actor);
  perform p4ra_advance_policy_revision(new.tenant_id,actor);
  insert into resource_security_epochs(tenant_id,resource_type,resource_id,resource_security_epoch,updated_at,updated_by)
  select new.tenant_id,t,new.agent_id,1,now(),actor
    from unnest(array['AGENT','AGENT_SERVICE_SCOPE','AGENT_CREDENTIAL_METADATA']::varchar[]) t
  on conflict(tenant_id,resource_type,resource_id) do update
    set resource_security_epoch=resource_security_epochs.resource_security_epoch+1,
        updated_at=now(),updated_by=actor;
  return new;
end $$;

-- Ownership changes use a separate RS3 trigger; it has the same descriptor-before-epoch requirement.
create or replace function rs3_touch_agent_scope_epoch()
returns trigger language plpgsql as $$
declare actor varchar;
begin
  if old.owner_department_id is not distinct from new.owner_department_id
     and old.owner_group_id is not distinct from new.owner_group_id
     and old.tenant_id is not distinct from new.tenant_id then return new; end if;
  if old.tenant_id is distinct from new.tenant_id then
    raise exception 'RS3_AGENT_TENANT_IMMUTABLE: create a new Agent identity in the target Tenant' using errcode='23514';
  end if;
  actor=coalesce(nullif(current_setting('app.current_actor_id',true),''),'rs3-agent-owner-change');
  perform v24_ensure_agent_machine_descriptors(new.tenant_id,new.agent_id,actor);
  perform p4ra_advance_policy_revision(new.tenant_id,actor);
  insert into resource_security_epochs(tenant_id,resource_type,resource_id,resource_security_epoch,updated_at,updated_by)
  select new.tenant_id,t,new.agent_id,1,now(),actor from unnest(array['AGENT','AGENT_SERVICE_SCOPE','AGENT_CREDENTIAL_METADATA']::varchar[]) t
  on conflict(tenant_id,resource_type,resource_id) do update
    set resource_security_epoch=resource_security_epochs.resource_security_epoch+1,updated_at=now(),updated_by=actor;
  return new;
end $$;

-- Backfill existing Agents under each Tenant RLS context.  No epoch is incremented by the
-- backfill itself; it only establishes the descriptor rows required by later governed mutations.
do $$
declare
  t record;
  a record;
begin
  for t in select tenant_id from tenants order by tenant_id loop
    perform set_config('app.current_tenant_id',t.tenant_id,true);
    perform set_config('app.current_actor_id','v24-agent-credential-descriptor-backfill',true);
    for a in select agent_id from agent_profiles where tenant_id=t.tenant_id order by agent_id loop
      perform v24_ensure_agent_machine_descriptors(t.tenant_id,a.agent_id,'v24-agent-credential-descriptor-backfill');
    end loop;
  end loop;
  perform set_config('app.current_tenant_id','INSTANCE',true);
  perform set_config('app.current_actor_id','v24-agent-credential-descriptor-consistency',true);
end $$;
