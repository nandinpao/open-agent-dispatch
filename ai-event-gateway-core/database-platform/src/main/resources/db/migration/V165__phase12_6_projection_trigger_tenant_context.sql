-- Phase 12.6 analytics is a rebuildable projection sidecar and must never become
-- the authority that blocks Task/Lineage/Incident lifecycle writes merely because
-- an internal execution path (callback, scheduler, replay) did not already expose
-- app.current_tenant_id on the PostgreSQL session.
--
-- Keep FORCE RLS intact. Each row trigger temporarily enters the authoritative
-- Tenant carried by NEW.tenant_id only for the projection write, then restores the
-- exact previous session value (including the empty/unset state).

create or replace function phase12_6_enqueue_task_projection() returns trigger language plpgsql as $$
declare
  event_id varchar(220);
  payload_hash varchar(64);
  at_time timestamptz := coalesce(new.updated_at,new.created_at,now());
  previous_tenant text := current_setting('app.current_tenant_id',true);
begin
  perform set_config('app.current_tenant_id',new.tenant_id,true);
  payload_hash:=md5(new.task_id||'|'||new.status||'|'||coalesce(new.updated_at::text,'')||'|'||coalesce(new.failure_domain,''));
  event_id:='task:'||new.task_id||':'||substr(payload_hash,1,24);
  insert into enterprise_analytics_projection_events(tenant_id,event_id,source_type,source_id,event_type,occurred_at,payload_hash,projection_status)
  values(new.tenant_id,event_id,'TASK',new.task_id,'TASK_UPSERT',at_time,payload_hash,'PENDING') on conflict do nothing;
  perform set_config('app.current_tenant_id',coalesce(previous_tenant,''),true);
  return new;
exception when others then
  perform set_config('app.current_tenant_id',coalesce(previous_tenant,''),true);
  raise;
end $$;

create or replace function phase12_6_enqueue_lineage_projection() returns trigger language plpgsql as $$
declare previous_tenant text := current_setting('app.current_tenant_id',true);
begin
  perform set_config('app.current_tenant_id',new.tenant_id,true);
  insert into enterprise_analytics_projection_events(tenant_id,event_id,source_type,source_id,event_type,occurred_at,payload_hash,projection_status)
  values(new.tenant_id,'lineage:'||new.evidence_id,'TASK_LINEAGE',new.evidence_id,new.event_type,new.occurred_at,md5(row_to_json(new)::text),'PENDING') on conflict do nothing;
  perform set_config('app.current_tenant_id',coalesce(previous_tenant,''),true);
  return new;
exception when others then
  perform set_config('app.current_tenant_id',coalesce(previous_tenant,''),true);
  raise;
end $$;

create or replace function phase12_6_enqueue_incident_projection() returns trigger language plpgsql as $$
declare
  at_time timestamptz:=coalesce(new.last_action_at,new.opened_at,now());
  previous_tenant text := current_setting('app.current_tenant_id',true);
begin
  perform set_config('app.current_tenant_id',new.tenant_id,true);
  insert into enterprise_analytics_projection_events(tenant_id,event_id,source_type,source_id,event_type,occurred_at,payload_hash,projection_status)
  values(new.tenant_id,'incident:'||new.case_id||':'||new.version::text,'SECURITY_INCIDENT',new.case_id,'INCIDENT_UPSERT',at_time,
         md5(new.case_id||'|'||new.status||'|'||new.version::text),'PENDING') on conflict do nothing;
  perform set_config('app.current_tenant_id',coalesce(previous_tenant,''),true);
  return new;
exception when others then
  perform set_config('app.current_tenant_id',coalesce(previous_tenant,''),true);
  raise;
end $$;

create or replace function phase12_6_enqueue_security_control_projection() returns trigger language plpgsql as $$
declare previous_tenant text := current_setting('app.current_tenant_id',true);
begin
  perform set_config('app.current_tenant_id',new.tenant_id,true);
  insert into enterprise_analytics_projection_events(tenant_id,event_id,source_type,source_id,event_type,occurred_at,payload_hash,projection_status)
  values(new.tenant_id,'control-action:'||new.action_id,'SECURITY_CONTROL',new.action_id,new.action_type,new.occurred_at,md5(row_to_json(new)::text),'PENDING') on conflict do nothing;
  perform set_config('app.current_tenant_id',coalesce(previous_tenant,''),true);
  return new;
exception when others then
  perform set_config('app.current_tenant_id',coalesce(previous_tenant,''),true);
  raise;
end $$;

comment on function phase12_6_enqueue_task_projection() is
'Phase 12.6 rebuildable analytics enqueue. Uses NEW.tenant_id only inside the trigger projection side-effect and restores the caller tenant context; it is not Task/IAM authority.';
comment on function phase12_6_enqueue_lineage_projection() is
'Phase 12.6 rebuildable lineage analytics enqueue with trigger-local Tenant RLS context.';
