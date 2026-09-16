-- Phase 5G enforcement: fail-closed Entry Point inventory and immutable governance evidence.

alter table permission_entry_point_inventory
  add constraint chk_phase5g_entry_point_type check(entry_point_type in('REST','COMMAND','QUERY','JOB','EVENT_CONSUMER','WEBHOOK','EXPORT','INTERNAL_API')),
  add constraint chk_phase5g_authority_state check(authority_state in('LEGACY_ONLY','DUAL_SHADOW','TARGET_READY','TARGET_ONLY','EXEMPT')),
  add constraint chk_phase5g_legacy_authorities_array check(jsonb_typeof(legacy_authorities)='array'),
  add constraint chk_phase5g_source_hash check(source_hash~'^[0-9a-f]{64}$'),
  add constraint chk_phase5g_exempt_reason check(authority_state<>'EXEMPT' or length(trim(coalesce(exemption_reason,'')))>=12),
  add constraint chk_phase5g_legacy_state check(authority_state not in('LEGACY_ONLY','DUAL_SHADOW') or jsonb_array_length(legacy_authorities)>0),
  add constraint chk_phase5g_target_state check(authority_state not in('DUAL_SHADOW','TARGET_READY','TARGET_ONLY') or target_permission_code is not null),
  add constraint chk_phase5g_version check(version>0);

alter table permission_legacy_authority_mappings
  add constraint chk_phase5g_mapping_status check(status in('UNMAPPED','MAPPED','DEPRECATED','RETIRED')),
  add constraint chk_phase5g_mapped_target check(status<>'MAPPED' or target_permission_code is not null),
  add constraint chk_phase5g_mapping_version check(version>0);
alter table permission_entry_point_bypasses
  add constraint chk_phase5g_bypass_status check(status in('ACTIVE','REVOKED','EXPIRED')),
  add constraint chk_phase5g_bypass_expiration check(expires_at>created_at),
  add constraint chk_phase5g_bypass_revoke check((status='ACTIVE' and revoked_at is null and revoked_by is null) or (status<>'ACTIVE' and revoked_at is not null and revoked_by is not null)),
  add constraint chk_phase5g_bypass_version check(version>0);

create or replace function phase5g_validate_entry_point()
returns trigger language plpgsql as $$
declare lifecycle_value varchar(32);
begin
  if new.target_permission_code is not null then
    select lifecycle into lifecycle_value from permission_definitions
    where permission_code=new.target_permission_code and active=true and lifecycle in('ACTIVE','DEPRECATED');
    if not found then raise exception 'ENTRY_POINT_UNKNOWN_PERMISSION:%',new.target_permission_code using errcode='23503'; end if;
  end if;
  if new.authority_state='TARGET_ONLY' and exists(
    select 1 from permission_entry_point_bypasses b where b.entry_point_id=new.entry_point_id and b.status='ACTIVE' and b.expires_at>now()) then
    raise exception 'ENTRY_POINT_TARGET_ONLY_ACTIVE_BYPASS' using errcode='23514';
  end if;
  new.updated_at=now();
  new.version=case when tg_op='INSERT' then coalesce(new.version,1) else old.version+1 end;
  return new;
end $$;
drop trigger if exists trg_phase5g_validate_entry_point on permission_entry_point_inventory;
create trigger trg_phase5g_validate_entry_point before insert or update on permission_entry_point_inventory
for each row execute function phase5g_validate_entry_point();

create or replace function phase5g_validate_legacy_mapping()
returns trigger language plpgsql as $$
begin
  if new.target_permission_code is not null and not exists(
    select 1 from permission_definitions where permission_code=new.target_permission_code and active=true and lifecycle in('ACTIVE','DEPRECATED')) then
    raise exception 'LEGACY_MAPPING_UNKNOWN_PERMISSION:%',new.target_permission_code using errcode='23503';
  end if;
  new.updated_at=now();new.version=case when tg_op='INSERT' then coalesce(new.version,1) else old.version+1 end;return new;
end $$;
drop trigger if exists trg_phase5g_validate_legacy_mapping on permission_legacy_authority_mappings;
create trigger trg_phase5g_validate_legacy_mapping before insert or update on permission_legacy_authority_mappings
for each row execute function phase5g_validate_legacy_mapping();

create or replace function phase5g_validate_bypass()
returns trigger language plpgsql as $$
begin
  if length(trim(new.owner_id))<2 or length(trim(new.reason))<12 or length(trim(new.replacement))<3 then
    raise exception 'ENTRY_POINT_BYPASS_GOVERNANCE_INCOMPLETE' using errcode='23514';
  end if;
  if new.status='ACTIVE' and new.expires_at<=now() then raise exception 'ENTRY_POINT_BYPASS_ALREADY_EXPIRED' using errcode='23514'; end if;
  if new.status='ACTIVE' and new.expires_at>now()+interval '180 days' then raise exception 'ENTRY_POINT_BYPASS_EXPIRY_TOO_LONG' using errcode='23514'; end if;
  if new.status='ACTIVE' and exists(select 1 from permission_entry_point_inventory i where i.entry_point_id=new.entry_point_id and i.authority_state='TARGET_ONLY') then
    raise exception 'ENTRY_POINT_TARGET_ONLY_BYPASS_FORBIDDEN' using errcode='23514';
  end if;
  return new;
end $$;
drop trigger if exists trg_phase5g_validate_bypass on permission_entry_point_bypasses;
create trigger trg_phase5g_validate_bypass before insert or update on permission_entry_point_bypasses
for each row execute function phase5g_validate_bypass();

create or replace function phase5g_inventory_evidence()
returns trigger language plpgsql as $$
declare old_json jsonb:='{}'::jsonb;new_json jsonb:='{}'::jsonb;agg varchar(240);event_name varchar(64);
begin
 if tg_op<>'INSERT' then old_json=to_jsonb(old);end if;if tg_op<>'DELETE' then new_json=to_jsonb(new);end if;
 agg=coalesce((case when tg_op='DELETE' then old_json else new_json end)->>'entry_point_id',
              (case when tg_op='DELETE' then old_json else new_json end)->>'mapping_id',
              (case when tg_op='DELETE' then old_json else new_json end)->>'bypass_id');
 event_name=upper(tg_table_name)||'_'||tg_op;
 insert into permission_entry_point_events(event_id,event_type,aggregate_type,aggregate_id,previous_json,current_json,audit_reason,actor_id,correlation_id,occurred_at)
 values(gen_random_uuid(),event_name,tg_table_name,agg,old_json,new_json,
  coalesce(nullif(current_setting('app.current_audit_reason',true),''),'Database-enforced Permission Readiness mutation'),
  coalesce(nullif(current_setting('app.current_actor_id',true),''),'phase5g-system'),nullif(current_setting('app.current_correlation_id',true),''),now());
 if tg_op='DELETE' then return old;end if;return new;
end $$;
drop trigger if exists trg_phase5g_entry_point_evidence on permission_entry_point_inventory;
create trigger trg_phase5g_entry_point_evidence after insert or update or delete on permission_entry_point_inventory for each row execute function phase5g_inventory_evidence();
drop trigger if exists trg_phase5g_mapping_evidence on permission_legacy_authority_mappings;
create trigger trg_phase5g_mapping_evidence after insert or update or delete on permission_legacy_authority_mappings for each row execute function phase5g_inventory_evidence();
drop trigger if exists trg_phase5g_bypass_evidence on permission_entry_point_bypasses;
create trigger trg_phase5g_bypass_evidence after insert or update or delete on permission_entry_point_bypasses for each row execute function phase5g_inventory_evidence();

create or replace function phase5g_reject_evidence_mutation() returns trigger language plpgsql as $$
begin raise exception 'PERMISSION_ENTRY_POINT_EVENT_APPEND_ONLY' using errcode='55000';end $$;
drop trigger if exists trg_phase5g_evidence_immutable on permission_entry_point_events;
create trigger trg_phase5g_evidence_immutable before update or delete on permission_entry_point_events for each row execute function phase5g_reject_evidence_mutation();

alter table permission_entry_point_inventory enable row level security;alter table permission_entry_point_inventory force row level security;
create policy phase5g_entry_point_instance_scope on permission_entry_point_inventory using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
alter table permission_legacy_authority_mappings enable row level security;alter table permission_legacy_authority_mappings force row level security;
create policy phase5g_mapping_instance_scope on permission_legacy_authority_mappings using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
alter table permission_entry_point_bypasses enable row level security;alter table permission_entry_point_bypasses force row level security;
create policy phase5g_bypass_instance_scope on permission_entry_point_bypasses using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
alter table permission_entry_point_events enable row level security;alter table permission_entry_point_events force row level security;
create policy phase5g_event_instance_scope on permission_entry_point_events using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
