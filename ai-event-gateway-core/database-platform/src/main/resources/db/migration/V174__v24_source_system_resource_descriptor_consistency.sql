-- V24 Source System Resource Access descriptor/epoch convergence.
--
-- Observed failure:
-- PUT /admin/source-systems/{sourceSystemId} changes owner Department/Group and fires
-- rs2_touch_source_scope_epoch(). resource_security_epochs has a FK to resource_descriptors,
-- but legacy/bootstrap Source Systems are not guaranteed to have a persisted SOURCE_SYSTEM
-- descriptor. The ownership update therefore fails with HTTP 500 / DataIntegrityViolation.
--
-- Keep source_systems as the canonical authority. Persist/update its Resource Access projection
-- before advancing the resource security epoch, and backfill existing Source Systems.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','v24-source-system-descriptor-consistency',true);

create or replace function v24_ensure_source_system_descriptor(
  p_tenant_id varchar,
  p_source_system_id varchar,
  p_actor varchar default 'v24-source-system-descriptor-consistency'
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
    parent_resource_type, parent_resource_id,
    root_resource_type, root_resource_id,
    sensitivity_level, maximum_visibility, visibility_policy_id,
    security_state, ownership_version, participant_version, resource_version,
    descriptor_authority, descriptor_hash, source_updated_at,
    visibility_policy_version, source_resolved_at, last_projected_at,
    projection_status, updated_at
  )
  select
    s.tenant_id,
    'SOURCE_SYSTEM',
    s.source_system_id,
    coalesce(nullif(s.display_name,''),s.source_system_id),
    nullif(s.owner_department_id,''),
    nullif(s.owner_group_id,''),
    null,null,null,null,
    'INTERNAL',
    'STANDARD',
    'P2_3B_BUSINESS_SCOPE',
    case when upper(coalesce(s.status,'ACTIVE'))='RETIRED' then 'ARCHIVED' else 'NORMAL' end,
    greatest(coalesce(s.version,1),1),
    0,
    greatest(coalesce(s.version,1),1),
    'DISPATCH_CONFIGURATION',
    md5(s.tenant_id||'|SOURCE_SYSTEM|'||s.source_system_id||'|'||coalesce(s.display_name,'')||'|'||coalesce(s.status,'')||'|'||coalesce(s.owner_department_id,'')||'|'||coalesce(s.owner_group_id,'')||'|'||coalesce(s.version,1)::text)
      || md5('SOURCE_SYSTEM|'||s.source_system_id||'|'||coalesce(s.updated_at,v_now)::text),
    coalesce(s.updated_at,v_now),
    0,
    coalesce(s.updated_at,v_now),
    v_now,
    'CURRENT',
    v_now
  from source_systems s
  where s.tenant_id=p_tenant_id and s.source_system_id=p_source_system_id
  on conflict(tenant_id,resource_type,resource_id) do update set
    resource_key=excluded.resource_key,
    owner_department_id=excluded.owner_department_id,
    owner_group_id=excluded.owner_group_id,
    parent_resource_type=null,
    parent_resource_id=null,
    root_resource_type=null,
    root_resource_id=null,
    sensitivity_level=excluded.sensitivity_level,
    maximum_visibility=excluded.maximum_visibility,
    visibility_policy_id=excluded.visibility_policy_id,
    security_state=excluded.security_state,
    ownership_version=greatest(resource_descriptors.ownership_version,excluded.ownership_version),
    resource_version=greatest(resource_descriptors.resource_version,excluded.resource_version),
    descriptor_authority='DISPATCH_CONFIGURATION',
    descriptor_hash=excluded.descriptor_hash,
    source_updated_at=excluded.source_updated_at,
    visibility_policy_version=excluded.visibility_policy_version,
    source_resolved_at=excluded.source_resolved_at,
    last_projected_at=excluded.last_projected_at,
    projection_status='CURRENT',
    updated_at=excluded.updated_at;
end $$;

create or replace function rs2_touch_source_scope_epoch()
returns trigger language plpgsql as $$
declare actor varchar;
begin
  if old.owner_department_id is not distinct from new.owner_department_id
     and old.owner_group_id is not distinct from new.owner_group_id then return new; end if;
  actor=coalesce(nullif(current_setting('app.current_actor_id',true),''),'rs2-source-owner-change');
  perform v24_ensure_source_system_descriptor(new.tenant_id,new.source_system_id,actor);
  perform p4ra_advance_policy_revision(new.tenant_id,actor);
  insert into resource_security_epochs(tenant_id,resource_type,resource_id,resource_security_epoch,updated_at,updated_by)
  values(new.tenant_id,'SOURCE_SYSTEM',new.source_system_id,1,now(),actor)
  on conflict(tenant_id,resource_type,resource_id) do update
    set resource_security_epoch=resource_security_epochs.resource_security_epoch+1,
        updated_at=excluded.updated_at,updated_by=excluded.updated_by;
  return new;
end $$;

-- Backfill all existing Source Systems under explicit Tenant RLS context.
do $$
declare t record; s record;
begin
  for t in select tenant_id from tenants loop
    perform set_config('app.current_tenant_id',t.tenant_id,true);
    perform set_config('app.current_actor_id','v24-source-system-descriptor-backfill',true);
    for s in select source_system_id from source_systems where tenant_id=t.tenant_id loop
      perform v24_ensure_source_system_descriptor(t.tenant_id,s.source_system_id,'v24-source-system-descriptor-backfill');
    end loop;
  end loop;
  perform set_config('app.current_tenant_id','INSTANCE',true);
  perform set_config('app.current_actor_id','v24-source-system-descriptor-consistency',true);
end $$;
