-- V40-7: Runtime Configuration governance, dedicated ReBAC permissions, separation-of-duty,
-- Permission Catalog identities use revision ...0025 / publication ...005025; ...0024 / ...005024 are owned by V203.
-- append-only rollback workflow and temporary key-level emergency overrides.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','v40-7-configuration-governance',true);

-- Dedicated configuration resource and permissions stay inside the existing IAM/ReBAC authority.
insert into resource_catalog(resource_type,category,descriptor_authority,ownership_supported,participants_supported,
  field_visibility_supported,runtime_lease_supported,default_sensitivity,description)
values ('RUNTIME_CONFIGURATION','CONFIGURATION','RUNTIME_CONFIGURATION',false,false,true,false,'RESTRICTED',
  'Instance-level governed Runtime Configuration revisions and emergency overlays.')
on conflict(resource_type) do update set category=excluded.category,descriptor_authority=excluded.descriptor_authority,
  ownership_supported=excluded.ownership_supported,participants_supported=excluded.participants_supported,
  field_visibility_supported=excluded.field_visibility_supported,runtime_lease_supported=excluded.runtime_lease_supported,
  default_sensitivity=excluded.default_sensitivity,description=excluded.description,status='ACTIVE',
  catalog_version=resource_catalog.catalog_version+1,updated_at=now();

insert into permission_catalog_revisions(
 revision_id,revision_code,revision_number,status,content_hash,description,supersedes_revision_id,
 created_at,created_by,published_at,published_by,version)
select '00000000-0000-0000-0000-000000000025'::uuid,'V40-7-RUNTIME-CONFIG-GOVERNANCE',coalesce(max(revision_number),0)+1,
 'DRAFT','DRAFT:UNPUBLISHED','V40-7 dedicated Runtime Configuration governance permissions.',
 (select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE'),now(),'v40-7-configuration-governance',null,null,1
from permission_catalog_revisions on conflict(revision_id) do nothing;

insert into permission_catalog_revision_entries(
 revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,
 allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
select '00000000-0000-0000-0000-000000000025'::uuid,e.permission_code,e.owner_module,e.resource_type,e.action_code,e.description,
 e.risk_level,e.risk_lane,e.lifecycle,e.allowed_scope_types,e.system_managed,e.replacement_permission_code,e.introduced_at,e.deprecated_at,
 e.retired_at,now(),'v40-7-configuration-governance',1
from permission_catalog_revision_entries e join permission_catalog_active_revision a on a.singleton_id='ACTIVE' and a.revision_id=e.revision_id
on conflict(revision_id,permission_code) do nothing;

insert into permission_catalog_revision_aliases(
 revision_id,alias_code,canonical_permission_code,alias_type,valid_from,valid_until,reason,created_by,created_at,version)
select '00000000-0000-0000-0000-000000000025'::uuid,a.alias_code,a.canonical_permission_code,a.alias_type,a.valid_from,a.valid_until,a.reason,
 'v40-7-configuration-governance',now(),1
from permission_catalog_revision_aliases a join permission_catalog_active_revision active on active.singleton_id='ACTIVE' and active.revision_id=a.revision_id
on conflict(revision_id,alias_code) do nothing;

insert into permission_catalog_revision_entries(
 revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,
 allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
values
 ('00000000-0000-0000-0000-000000000025','configuration.view','control-plane-app','RUNTIME_CONFIGURATION','READ','View Runtime Configuration desired/applied state, revision history and governance status.','MEDIUM','READ','ACTIVE',array['INSTANCE']::varchar[],true,null,now(),null,null,now(),'v40-7-configuration-governance',1),
 ('00000000-0000-0000-0000-000000000025','configuration.edit','control-plane-app','RUNTIME_CONFIGURATION','UPDATE','Create and validate Runtime Configuration change requests.','HIGH','WRITE','ACTIVE',array['INSTANCE']::varchar[],true,null,now(),null,null,now(),'v40-7-configuration-governance',1),
 ('00000000-0000-0000-0000-000000000025','configuration.publish','control-plane-app','RUNTIME_CONFIGURATION','PUBLISH','Publish an approved Runtime Configuration revision and create time-limited emergency overlays.','CRITICAL','CRITICAL','ACTIVE',array['INSTANCE']::varchar[],true,null,now(),null,null,now(),'v40-7-configuration-governance',1),
 ('00000000-0000-0000-0000-000000000025','configuration.approve','control-plane-app','RUNTIME_CONFIGURATION','APPROVE','Approve or reject Runtime Configuration change requests under separation-of-duty.','CRITICAL','CRITICAL','ACTIVE',array['INSTANCE']::varchar[],true,null,now(),null,null,now(),'v40-7-configuration-governance',1),
 ('00000000-0000-0000-0000-000000000025','configuration.rollback','control-plane-app','RUNTIME_CONFIGURATION','ROLLBACK','Create governed rollback change requests from prior published revisions.','CRITICAL','CRITICAL','ACTIVE',array['INSTANCE']::varchar[],true,null,now(),null,null,now(),'v40-7-configuration-governance',1),
 ('00000000-0000-0000-0000-000000000025','configuration.secret-rotate','control-plane-app','RUNTIME_CONFIGURATION','ROTATE','Rotate referenced Runtime Configuration secret material through the typed secret authority; raw secret values remain forbidden.','CRITICAL','CRITICAL','ACTIVE',array['INSTANCE']::varchar[],true,null,now(),null,null,now(),'v40-7-configuration-governance',1)
on conflict(revision_id,permission_code) do update set description=excluded.description,risk_level=excluded.risk_level,
 risk_lane=excluded.risk_lane,lifecycle='ACTIVE',allowed_scope_types=excluded.allowed_scope_types,updated_at=now(),
 updated_by='v40-7-configuration-governance',version=permission_catalog_revision_entries.version+1;

select set_config('app.permission_catalog_publish_revision_id','00000000-0000-0000-0000-000000000025',true);
insert into permission_definitions(
 permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,active,version,owner_module,risk_lane,lifecycle,
 catalog_revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by)
select permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,lifecycle<>'RETIRED',1,owner_module,risk_lane,lifecycle,
 revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by
from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000025'::uuid
on conflict(permission_code) do update set resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,
 risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,system_managed=excluded.system_managed,active=excluded.active,
 owner_module=excluded.owner_module,risk_lane=excluded.risk_lane,lifecycle=excluded.lifecycle,catalog_revision_id=excluded.catalog_revision_id,
 updated_at=excluded.updated_at,updated_by=excluded.updated_by,version=permission_definitions.version+1;

update permission_catalog_revisions set status='SUPERSEDED',version=version+1
where revision_id=(select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE')
 and revision_id<>'00000000-0000-0000-0000-000000000025'::uuid and status='PUBLISHED';
update permission_catalog_revisions set status='PUBLISHED',content_hash=(
 with catalog_lines as (
   select 'P|'||permission_code||'|'||owner_module||'|'||resource_type||'|'||action_code||'|'||description||'|'||risk_level||'|'||risk_lane||'|'||lifecycle||'|'||coalesce(array_to_string(allowed_scope_types,','),'')||'|'||system_managed::text||'|'||coalesce(replacement_permission_code,'') line
   from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000025'::uuid
   union all
   select 'A|'||alias_code||'|'||canonical_permission_code||'|'||alias_type||'|'||coalesce(to_char(valid_from at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||coalesce(to_char(valid_until at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||reason
   from permission_catalog_revision_aliases where revision_id='00000000-0000-0000-0000-000000000025'::uuid)
 select 'sha256:'||encode(sha256(convert_to(coalesce(string_agg(line,E'\n' order by line),''),'UTF8')),'hex') from catalog_lines),
 published_at=now(),published_by='v40-7-configuration-governance',version=version+1
where revision_id='00000000-0000-0000-0000-000000000025'::uuid and status='DRAFT';
update permission_catalog_active_revision set revision_id='00000000-0000-0000-0000-000000000025'::uuid,
 activated_at=now(),activated_by='v40-7-configuration-governance',version=version+1 where singleton_id='ACTIVE';
insert into permission_catalog_publication_events(publication_id,revision_id,previous_revision_id,content_hash,entry_count,alias_count,actor_id,audit_reason,correlation_id,published_at)
select '00000000-0000-0000-0000-000000005025'::uuid,r.revision_id,r.supersedes_revision_id,r.content_hash,
 (select count(*)::integer from permission_catalog_revision_entries e where e.revision_id=r.revision_id),
 (select count(*)::integer from permission_catalog_revision_aliases a where a.revision_id=r.revision_id),
 'v40-7-configuration-governance','V40-7 Runtime Configuration governance permission publication','v40-7-configuration-governance',coalesce(r.published_at,now())
from permission_catalog_revisions r where r.revision_id='00000000-0000-0000-0000-000000000025'::uuid
on conflict(publication_id) do nothing;

-- Built-in platform operators receive dedicated permissions. Custom roles remain explicit and are not broadened.
insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'v407-'||substr(md5(r.role_id||':'||p.permission_code),1,32),null,r.role_id,p.permission_code,now(),'v40-7-configuration-governance',1
from rbac_roles r join permission_definitions p on p.permission_code in
 ('configuration.view','configuration.edit','configuration.publish','configuration.approve','configuration.rollback','configuration.secret-rotate')
where r.tenant_id is null and r.role_code='SYSTEM_ADMIN' and r.status='ACTIVE'
on conflict(role_id,permission_point) do nothing;
insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'v407-'||substr(md5(r.role_id||':'||p.permission_code),1,32),null,r.role_id,p.permission_code,now(),'v40-7-configuration-governance',1
from rbac_roles r join permission_definitions p on p.permission_code in
 ('configuration.view','configuration.edit','configuration.publish','configuration.approve','configuration.rollback')
where r.tenant_id is null and r.role_code='PLATFORM_ADMIN' and r.status='ACTIVE'
on conflict(role_id,permission_point) do nothing;

-- Revision workflow is approval-mandatory from V40-7 onward; self-approval is denied at DB authority.
create or replace function guard_runtime_config_revision_transition() returns trigger language plpgsql as $$
declare v_item_count integer; v_invalid_count integer;
begin
  if new.revision_id is distinct from old.revision_id or new.config_set_id is distinct from old.config_set_id
     or new.sequence_no is distinct from old.sequence_no or new.base_revision_id is distinct from old.base_revision_id
     or new.rollback_of_revision_id is distinct from old.rollback_of_revision_id or new.restore_source_revision_id is distinct from old.restore_source_revision_id
     or new.definition_schema_version is distinct from old.definition_schema_version or new.reason is distinct from old.reason
     or new.created_at is distinct from old.created_at or new.created_by is distinct from old.created_by then
    raise exception 'RUNTIME_CONFIG_REVISION_IDENTITY_IMMUTABLE';
  end if;
  if new.state is distinct from old.state and not (
       (old.state='DRAFT' and new.state in ('VALIDATED','CANCELLED'))
    or (old.state='VALIDATED' and new.state in ('DRAFT','PENDING_APPROVAL','CANCELLED'))
    or (old.state='PENDING_APPROVAL' and new.state in ('APPROVED','REJECTED','CANCELLED'))
    or (old.state='APPROVED' and new.state in ('PUBLISHED','CANCELLED'))
    or (old.state='PUBLISHED' and new.state='SUPERSEDED')) then
    raise exception 'RUNTIME_CONFIG_REVISION_TRANSITION_DENIED: % -> %',old.state,new.state;
  end if;
  if new.state='APPROVED' and new.state is distinct from old.state then
    if nullif(trim(coalesce(new.approved_by,'')),'') is null then raise exception 'RUNTIME_CONFIG_APPROVER_REQUIRED'; end if;
    if new.approved_by=new.created_by or new.approved_by=coalesce(new.submitted_by,'') then
      raise exception 'RUNTIME_CONFIG_SELF_APPROVAL_DENIED';
    end if;
  end if;
  if new.state='PUBLISHED' and (old.state<>'APPROVED' or old.approved_by is null) then
    raise exception 'RUNTIME_CONFIG_APPROVAL_REQUIRED_BEFORE_PUBLISH';
  end if;
  if new.state in ('VALIDATED','PENDING_APPROVAL','APPROVED','PUBLISHED') and new.state is distinct from old.state then
    select count(*) into v_item_count from runtime_config_revision_items where revision_id=new.revision_id;
    if v_item_count<1 then raise exception 'RUNTIME_CONFIG_REVISION_EMPTY: %',new.revision_id; end if;
    select count(*) into v_invalid_count from runtime_config_revision_items i join runtime_config_definitions d on d.definition_key=i.definition_key
      where i.revision_id=new.revision_id and (d.migration_authorized is not true or d.authority_class<>'RUNTIME_TUNABLE');
    if v_invalid_count>0 then raise exception 'RUNTIME_CONFIG_REVISION_DEFINITION_AUTHORITY_CHANGED: % invalid items %',new.revision_id,v_invalid_count; end if;
  end if;
  if old.state in ('SUPERSEDED','REJECTED','CANCELLED') then raise exception 'RUNTIME_CONFIG_REVISION_TERMINAL_IMMUTABLE'; end if;
  new.updated_at=now(); return new;
end $$;

-- Temporary key-level emergency overlay; normal revision history remains untouched.
create table if not exists runtime_config_emergency_overrides(
  override_id varchar(128) primary key,
  config_set_id varchar(128) not null references runtime_config_sets(config_set_id),
  definition_key varchar(255) not null references runtime_config_definitions(definition_key),
  base_revision_id varchar(128) not null,
  value_json jsonb not null,
  value_fingerprint varchar(128) not null,
  status varchar(32) not null default 'ACTIVE',
  reason text not null,
  created_by varchar(128) not null,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  revoked_by varchar(128),
  revoked_at timestamptz,
  revoke_reason text,
  foreign key(config_set_id,base_revision_id) references runtime_config_revisions(config_set_id,revision_id),
  check(status in ('ACTIVE','EXPIRED','REVOKED')),
  check(value_fingerprint ~ '^[0-9a-fA-F]{64}$'),
  check(expires_at>created_at)
);
create index if not exists idx_runtime_config_emergency_due on runtime_config_emergency_overrides(status,expires_at);
create unique index if not exists uq_runtime_config_emergency_active_key on runtime_config_emergency_overrides(config_set_id,definition_key) where status='ACTIVE';
comment on table runtime_config_emergency_overrides is 'V40-7 key-level TTL overlay. Expiry/revocation reveals the latest normal revision value; it never rolls back the whole revision.';

create or replace function guard_runtime_config_emergency_override() returns trigger language plpgsql as $$
declare v_active varchar(128); v_authorized boolean; v_class varchar(64); v_state varchar(32);
begin
  if tg_op='INSERT' then
    select a.revision_id into v_active from runtime_config_active_revisions a where a.config_set_id=new.config_set_id;
    if v_active is distinct from new.base_revision_id then raise exception 'RUNTIME_CONFIG_EMERGENCY_BASE_REVISION_CONFLICT'; end if;
    select migration_authorized,authority_class into v_authorized,v_class from runtime_config_definitions where definition_key=new.definition_key;
    if v_authorized is not true or v_class<>'RUNTIME_TUNABLE' then raise exception 'RUNTIME_CONFIG_EMERGENCY_DEFINITION_NOT_AUTHORIZED'; end if;
    select state into v_state from runtime_config_revisions where config_set_id=new.config_set_id and revision_id=new.base_revision_id;
    if v_state is distinct from 'PUBLISHED' then raise exception 'RUNTIME_CONFIG_EMERGENCY_BASE_NOT_PUBLISHED'; end if;
    if new.expires_at>now()+interval '4 hours' then raise exception 'RUNTIME_CONFIG_EMERGENCY_TTL_TOO_LONG'; end if;
  else
    if new.override_id is distinct from old.override_id or new.config_set_id is distinct from old.config_set_id
       or new.definition_key is distinct from old.definition_key or new.base_revision_id is distinct from old.base_revision_id
       or new.value_json is distinct from old.value_json or new.value_fingerprint is distinct from old.value_fingerprint
       or new.reason is distinct from old.reason or new.created_by is distinct from old.created_by
       or new.created_at is distinct from old.created_at or new.expires_at is distinct from old.expires_at then
      raise exception 'RUNTIME_CONFIG_EMERGENCY_OVERRIDE_IDENTITY_IMMUTABLE';
    end if;
    if old.status<>'ACTIVE' or new.status not in ('EXPIRED','REVOKED') then raise exception 'RUNTIME_CONFIG_EMERGENCY_OVERRIDE_TRANSITION_DENIED'; end if;
  end if;
  return new;
end $$;
drop trigger if exists trg_runtime_config_emergency_override on runtime_config_emergency_overrides;
create trigger trg_runtime_config_emergency_override before insert or update on runtime_config_emergency_overrides
for each row execute function guard_runtime_config_emergency_override();

-- Update entry-point authority from the temporary V40-6 bridge to dedicated permissions.
update permission_entry_point_inventory set target_permission_code='configuration.view',manifest_revision='v40-7-configuration-governance-2026-09-18',updated_at=now(),updated_by='v40-7-configuration-governance',version=version+1
 where entry_point_id in ('REST:GET:/api/platform/runtime-configuration/overview','REST:GET:/api/platform/runtime-configuration/settings/{key}');
update permission_entry_point_inventory set authority_state='EXEMPT',target_permission_code=null,exemption_reason='Retired by V40-7 governed change-request workflow',manifest_revision='v40-7-configuration-governance-2026-09-18',updated_at=now(),updated_by='v40-7-configuration-governance',version=version+1
 where entry_point_id='REST:POST:/api/platform/runtime-configuration/settings/{key}/publish';

-- Governed V40-7 operator entry points. Source hash binds all mappings to the current controller source.
insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,target_permission_code,
 legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,migration_deadline,manifest_revision,source_ref,source_hash,
 last_verified_at,created_by,updated_by)
values
('REST:GET:/api/platform/runtime-configuration/governance','REST','control-plane-app','control-plane-app','RuntimeConfigurationAdminController.governance','/api/platform/runtime-configuration/governance','GET','TARGET_ONLY','configuration.view',null,'[]'::jsonb,'RUNTIME_CONFIGURATION','R3_ROUTE_RESOURCE_RESOLVER',null,null,'v40-7-configuration-governance-2026-09-18','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/configuration/RuntimeConfigurationAdminController.java#governance','e1b21958be75bdf31c8494bf03c0bc5d192a44f80e96f03b6078e0105f266cc4',now(),'v40-7-configuration-governance','v40-7-configuration-governance'),
('REST:GET:/api/platform/runtime-configuration/settings/{key}/revisions','REST','control-plane-app','control-plane-app','RuntimeConfigurationAdminController.revisions','/api/platform/runtime-configuration/settings/{key}/revisions','GET','TARGET_ONLY','configuration.view',null,'[]'::jsonb,'RUNTIME_CONFIGURATION','R3_ROUTE_RESOURCE_RESOLVER',null,null,'v40-7-configuration-governance-2026-09-18','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/configuration/RuntimeConfigurationAdminController.java#revisions','e1b21958be75bdf31c8494bf03c0bc5d192a44f80e96f03b6078e0105f266cc4',now(),'v40-7-configuration-governance','v40-7-configuration-governance'),
('REST:POST:/api/platform/runtime-configuration/settings/{key}/change-requests','REST','control-plane-app','control-plane-app','RuntimeConfigurationAdminController.requestChange','/api/platform/runtime-configuration/settings/{key}/change-requests','POST','TARGET_ONLY','configuration.edit',null,'[]'::jsonb,'RUNTIME_CONFIGURATION','R3_ROUTE_RESOURCE_RESOLVER',null,null,'v40-7-configuration-governance-2026-09-18','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/configuration/RuntimeConfigurationAdminController.java#requestChange','e1b21958be75bdf31c8494bf03c0bc5d192a44f80e96f03b6078e0105f266cc4',now(),'v40-7-configuration-governance','v40-7-configuration-governance'),
('REST:POST:/api/platform/runtime-configuration/revisions/{revisionId}/approve','REST','control-plane-app','control-plane-app','RuntimeConfigurationAdminController.approve','/api/platform/runtime-configuration/revisions/{revisionId}/approve','POST','TARGET_ONLY','configuration.approve',null,'[]'::jsonb,'RUNTIME_CONFIGURATION','R3_ROUTE_RESOURCE_RESOLVER',null,null,'v40-7-configuration-governance-2026-09-18','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/configuration/RuntimeConfigurationAdminController.java#approve','e1b21958be75bdf31c8494bf03c0bc5d192a44f80e96f03b6078e0105f266cc4',now(),'v40-7-configuration-governance','v40-7-configuration-governance'),
('REST:POST:/api/platform/runtime-configuration/revisions/{revisionId}/reject','REST','control-plane-app','control-plane-app','RuntimeConfigurationAdminController.reject','/api/platform/runtime-configuration/revisions/{revisionId}/reject','POST','TARGET_ONLY','configuration.approve',null,'[]'::jsonb,'RUNTIME_CONFIGURATION','R3_ROUTE_RESOURCE_RESOLVER',null,null,'v40-7-configuration-governance-2026-09-18','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/configuration/RuntimeConfigurationAdminController.java#reject','e1b21958be75bdf31c8494bf03c0bc5d192a44f80e96f03b6078e0105f266cc4',now(),'v40-7-configuration-governance','v40-7-configuration-governance'),
('REST:POST:/api/platform/runtime-configuration/revisions/{revisionId}/publish','REST','control-plane-app','control-plane-app','RuntimeConfigurationAdminController.publish','/api/platform/runtime-configuration/revisions/{revisionId}/publish','POST','TARGET_ONLY','configuration.publish',null,'[]'::jsonb,'RUNTIME_CONFIGURATION','R3_ROUTE_RESOURCE_RESOLVER',null,null,'v40-7-configuration-governance-2026-09-18','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/configuration/RuntimeConfigurationAdminController.java#publish','e1b21958be75bdf31c8494bf03c0bc5d192a44f80e96f03b6078e0105f266cc4',now(),'v40-7-configuration-governance','v40-7-configuration-governance'),
('REST:POST:/api/platform/runtime-configuration/settings/{key}/rollback-requests','REST','control-plane-app','control-plane-app','RuntimeConfigurationAdminController.rollback','/api/platform/runtime-configuration/settings/{key}/rollback-requests','POST','TARGET_ONLY','configuration.rollback',null,'[]'::jsonb,'RUNTIME_CONFIGURATION','R3_ROUTE_RESOURCE_RESOLVER',null,null,'v40-7-configuration-governance-2026-09-18','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/configuration/RuntimeConfigurationAdminController.java#rollback','e1b21958be75bdf31c8494bf03c0bc5d192a44f80e96f03b6078e0105f266cc4',now(),'v40-7-configuration-governance','v40-7-configuration-governance'),
('REST:POST:/api/platform/runtime-configuration/settings/{key}/emergency-overrides','REST','control-plane-app','control-plane-app','RuntimeConfigurationAdminController.emergencyOverride','/api/platform/runtime-configuration/settings/{key}/emergency-overrides','POST','TARGET_ONLY','configuration.publish',null,'[]'::jsonb,'RUNTIME_CONFIGURATION','R3_ROUTE_RESOURCE_RESOLVER',null,null,'v40-7-configuration-governance-2026-09-18','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/configuration/RuntimeConfigurationAdminController.java#emergencyOverride','e1b21958be75bdf31c8494bf03c0bc5d192a44f80e96f03b6078e0105f266cc4',now(),'v40-7-configuration-governance','v40-7-configuration-governance'),
('REST:POST:/api/platform/runtime-configuration/emergency-overrides/{overrideId}/revoke','REST','control-plane-app','control-plane-app','RuntimeConfigurationAdminController.revokeEmergencyOverride','/api/platform/runtime-configuration/emergency-overrides/{overrideId}/revoke','POST','TARGET_ONLY','configuration.publish',null,'[]'::jsonb,'RUNTIME_CONFIGURATION','R3_ROUTE_RESOURCE_RESOLVER',null,null,'v40-7-configuration-governance-2026-09-18','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/configuration/RuntimeConfigurationAdminController.java#revokeEmergencyOverride','e1b21958be75bdf31c8494bf03c0bc5d192a44f80e96f03b6078e0105f266cc4',now(),'v40-7-configuration-governance','v40-7-configuration-governance')
on conflict(entry_point_id) do update set display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,
 authority_state=excluded.authority_state,target_permission_code=excluded.target_permission_code,resource_type=excluded.resource_type,
 resource_resolver_id=excluded.resource_resolver_id,exemption_reason=null,manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,
 source_hash=excluded.source_hash,last_verified_at=now(),updated_at=now(),updated_by='v40-7-configuration-governance',version=permission_entry_point_inventory.version+1;

update permission_entry_point_inventory set source_hash='e1b21958be75bdf31c8494bf03c0bc5d192a44f80e96f03b6078e0105f266cc4',manifest_revision='v40-7-configuration-governance-2026-09-18',last_verified_at=now(),updated_at=now(),updated_by='v40-7-configuration-governance',version=version+1
 where entry_point_id in ('REST:GET:/api/platform/runtime-configuration/overview','REST:GET:/api/platform/runtime-configuration/settings/{key}');
