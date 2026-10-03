-- V41 C3R3G-HF5.32B: Integration Recovery Authority Closure.
-- Publishes dedicated Recovery read/preflight/retry permissions and registers
-- the canonical AdapterAction recovery HTTP entry points. Existing grants are
-- projected from equivalent AdapterAction permissions to avoid privilege expansion.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','v41-c3r3g-hf5-32b-integration-recovery',true);

insert into permission_catalog_revisions(
 revision_id,revision_code,revision_number,status,content_hash,description,supersedes_revision_id,
 created_at,created_by,published_at,published_by,version)
select '00000000-0000-0000-0000-000000000027'::uuid,
       'V41-C3R3G-HF5-32B-INTEGRATION-RECOVERY',coalesce(max(revision_number),0)+1,
       'DRAFT','DRAFT:UNPUBLISHED','Dedicated Integration Recovery read, live-preflight and governed-retry authority.',
       (select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE'),
       now(),'v41-c3r3g-hf5-32b-integration-recovery',null,null,1
from permission_catalog_revisions
on conflict(revision_id) do nothing;

insert into permission_catalog_revision_entries(
 revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,
 allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
select '00000000-0000-0000-0000-000000000027'::uuid,e.permission_code,e.owner_module,e.resource_type,e.action_code,e.description,
 e.risk_level,e.risk_lane,e.lifecycle,e.allowed_scope_types,e.system_managed,e.replacement_permission_code,e.introduced_at,e.deprecated_at,
 e.retired_at,now(),'v41-c3r3g-hf5-32b-integration-recovery',1
from permission_catalog_revision_entries e
join permission_catalog_active_revision a on a.singleton_id='ACTIVE' and a.revision_id=e.revision_id
on conflict(revision_id,permission_code) do nothing;

insert into permission_catalog_revision_aliases(
 revision_id,alias_code,canonical_permission_code,alias_type,valid_from,valid_until,reason,created_by,created_at,version)
select '00000000-0000-0000-0000-000000000027'::uuid,a.alias_code,a.canonical_permission_code,a.alias_type,a.valid_from,a.valid_until,a.reason,
 'v41-c3r3g-hf5-32b-integration-recovery',now(),1
from permission_catalog_revision_aliases a
join permission_catalog_active_revision active on active.singleton_id='ACTIVE' and active.revision_id=a.revision_id
on conflict(revision_id,alias_code) do nothing;

insert into permission_catalog_revision_entries(
 revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,
 allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
values
 ('00000000-0000-0000-0000-000000000027','api.adapter.action.integration.recovery.read','adapter-action','ADAPTER_ACTION','RECOVERY_READ','Read canonical Integration Recovery summary and timestamp-ordered executor failure evidence.','MEDIUM','READ','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'v41-c3r3g-hf5-32b-integration-recovery',1),
 ('00000000-0000-0000-0000-000000000027','api.adapter.action.integration.recovery.preflight','adapter-action','ADAPTER_ACTION','RECOVERY_PREFLIGHT','Run live Integration Recovery preflight using current Integration Identity, mapping preview and provider metadata.','HIGH','WRITE','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'v41-c3r3g-hf5-32b-integration-recovery',1),
 ('00000000-0000-0000-0000-000000000027','api.adapter.action.integration.recovery.retry','adapter-action','ADAPTER_ACTION','RECOVERY_RETRY','Retry a recoverable Issue Tracking AdapterAction only after server-side Recovery Preflight passes.','CRITICAL','CRITICAL','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'v41-c3r3g-hf5-32b-integration-recovery',1)
on conflict(revision_id,permission_code) do update set description=excluded.description,risk_level=excluded.risk_level,
 risk_lane=excluded.risk_lane,lifecycle='ACTIVE',allowed_scope_types=excluded.allowed_scope_types,updated_at=now(),
 updated_by='v41-c3r3g-hf5-32b-integration-recovery',version=permission_catalog_revision_entries.version+1;

select set_config('app.permission_catalog_publish_revision_id','00000000-0000-0000-0000-000000000027',true);
insert into permission_definitions(
 permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,active,version,owner_module,risk_lane,lifecycle,
 catalog_revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by)
select permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,lifecycle<>'RETIRED',1,owner_module,risk_lane,lifecycle,
 revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by
from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000027'::uuid
on conflict(permission_code) do update set resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,
 risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,system_managed=excluded.system_managed,active=excluded.active,
 owner_module=excluded.owner_module,risk_lane=excluded.risk_lane,lifecycle=excluded.lifecycle,catalog_revision_id=excluded.catalog_revision_id,
 updated_at=excluded.updated_at,updated_by=excluded.updated_by,version=permission_definitions.version+1;

update permission_catalog_revisions set status='SUPERSEDED',version=version+1
where revision_id=(select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE')
 and revision_id<>'00000000-0000-0000-0000-000000000027'::uuid and status='PUBLISHED';

update permission_catalog_revisions set status='PUBLISHED',content_hash=(
 with catalog_lines as (
   select 'P|'||permission_code||'|'||owner_module||'|'||resource_type||'|'||action_code||'|'||description||'|'||risk_level||'|'||risk_lane||'|'||lifecycle||'|'||coalesce(array_to_string(allowed_scope_types,','),'')||'|'||system_managed::text||'|'||coalesce(replacement_permission_code,'') line
   from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000027'::uuid
   union all
   select 'A|'||alias_code||'|'||canonical_permission_code||'|'||alias_type||'|'||coalesce(to_char(valid_from at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||coalesce(to_char(valid_until at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||reason
   from permission_catalog_revision_aliases where revision_id='00000000-0000-0000-0000-000000000027'::uuid)
 select 'sha256:'||encode(sha256(convert_to(coalesce(string_agg(line,E'\n' order by line),''),'UTF8')),'hex') from catalog_lines),
 published_at=now(),published_by='v41-c3r3g-hf5-32b-integration-recovery',version=version+1
where revision_id='00000000-0000-0000-0000-000000000027'::uuid and status='DRAFT';

update permission_catalog_active_revision set revision_id='00000000-0000-0000-0000-000000000027'::uuid,
 activated_at=now(),activated_by='v41-c3r3g-hf5-32b-integration-recovery',version=version+1 where singleton_id='ACTIVE';

insert into permission_catalog_publication_events(publication_id,revision_id,previous_revision_id,content_hash,entry_count,alias_count,actor_id,audit_reason,correlation_id,published_at)
select '00000000-0000-0000-0000-000000005027'::uuid,r.revision_id,r.supersedes_revision_id,r.content_hash,
 (select count(*)::integer from permission_catalog_revision_entries e where e.revision_id=r.revision_id),
 (select count(*)::integer from permission_catalog_revision_aliases a where a.revision_id=r.revision_id),
 'v41-c3r3g-hf5-32b-integration-recovery','HF5.32B Integration Recovery authority permission publication','v41-c3r3g-hf5-32b-integration-recovery',coalesce(r.published_at,now())
from permission_catalog_revisions r where r.revision_id='00000000-0000-0000-0000-000000000027'::uuid
on conflict(publication_id) do nothing;

-- Preserve the existing privilege boundary instead of broadening access:
-- readers inherit from adapter-action recent; preflight/retry inherit from adapter-action retry.
insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'hf532b-read-'||substr(md5(existing.role_id||':'||p.permission_code),1,27),existing.tenant_id,existing.role_id,p.permission_code,now(),'v41-c3r3g-hf5-32b-integration-recovery',1
from rbac_role_permissions existing
join permission_definitions p on p.permission_code='api.adapter.action.integration.recovery.read'
where existing.permission_point='api.adapter.action.recent'
on conflict(role_id,permission_point) do nothing;

insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'hf532b-op-'||substr(md5(existing.role_id||':'||p.permission_code),1,29),existing.tenant_id,existing.role_id,p.permission_code,now(),'v41-c3r3g-hf5-32b-integration-recovery',1
from rbac_role_permissions existing
join permission_definitions p on p.permission_code in('api.adapter.action.integration.recovery.preflight','api.adapter.action.integration.recovery.retry')
where existing.permission_point='api.adapter.action.retry'
on conflict(role_id,permission_point) do nothing;

insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,target_permission_code,
 legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,migration_deadline,manifest_revision,source_ref,source_hash,
 last_verified_at,created_by,updated_by)
values
('REST:GET:/api/adapter-actions/integration-recovery','REST','control-plane-app','adapter-action','AdapterActionController.integrationRecovery','/api/adapter-actions/integration-recovery','GET','TARGET_ONLY','api.adapter.action.integration.recovery.read',null,'[]'::jsonb,'ADAPTER_ACTION','R3_ROUTE_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf5-32b-2026-10-02','ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/api/AdapterActionController.java#integrationRecovery','43e0514a4e27269dd3ee848601d639d9b880b6c732e8705476f293e7962114e3',now(),'v41-c3r3g-hf5-32b-integration-recovery','v41-c3r3g-hf5-32b-integration-recovery'),
('REST:POST:/api/adapter-actions/{actionId}/recovery-preflight','REST','control-plane-app','adapter-action','AdapterActionController.recoveryPreflight','/api/adapter-actions/{actionId}/recovery-preflight','POST','TARGET_ONLY','api.adapter.action.integration.recovery.preflight',null,'[]'::jsonb,'ADAPTER_ACTION','R3_PATH_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf5-32b-2026-10-02','ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/api/AdapterActionController.java#recoveryPreflight','43e0514a4e27269dd3ee848601d639d9b880b6c732e8705476f293e7962114e3',now(),'v41-c3r3g-hf5-32b-integration-recovery','v41-c3r3g-hf5-32b-integration-recovery'),
('REST:POST:/api/adapter-actions/{actionId}/recovery-retry','REST','control-plane-app','adapter-action','AdapterActionController.recoveryRetry','/api/adapter-actions/{actionId}/recovery-retry','POST','TARGET_ONLY','api.adapter.action.integration.recovery.retry',null,'[]'::jsonb,'ADAPTER_ACTION','R3_PATH_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf5-32b-2026-10-02','ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/api/AdapterActionController.java#recoveryRetry','43e0514a4e27269dd3ee848601d639d9b880b6c732e8705476f293e7962114e3',now(),'v41-c3r3g-hf5-32b-integration-recovery','v41-c3r3g-hf5-32b-integration-recovery')
on conflict(entry_point_id) do update set display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,
 authority_state=excluded.authority_state,target_permission_code=excluded.target_permission_code,legacy_authority_type=null,legacy_authorities='[]'::jsonb,
 resource_type=excluded.resource_type,resource_resolver_id=excluded.resource_resolver_id,exemption_reason=null,migration_deadline=null,
 manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,source_hash=excluded.source_hash,last_verified_at=now(),updated_at=now(),
 updated_by='v41-c3r3g-hf5-32b-integration-recovery',version=permission_entry_point_inventory.version+1;

update rbac_policy_versions set policy_version=policy_version+1,updated_at=now(),updated_by='v41-c3r3g-hf5-32b-integration-recovery';

do $$
declare v_routes integer; v_permissions integer;
begin
  select count(*) into v_routes from permission_entry_point_inventory
   where route_pattern in('/api/adapter-actions/integration-recovery','/api/adapter-actions/{actionId}/recovery-preflight','/api/adapter-actions/{actionId}/recovery-retry')
     and authority_state='TARGET_ONLY' and target_permission_code like 'api.adapter.action.integration.recovery.%';
  if v_routes<>3 then raise exception 'HF5_32B_RECOVERY_ROUTE_MAPPING_INCOMPLETE expected=3 actual=%',v_routes; end if;
  select count(*) into v_permissions from permission_definitions
   where permission_code in('api.adapter.action.integration.recovery.read','api.adapter.action.integration.recovery.preflight','api.adapter.action.integration.recovery.retry')
     and active=true and lifecycle='ACTIVE';
  if v_permissions<>3 then raise exception 'HF5_32B_RECOVERY_PERMISSION_CATALOG_INCOMPLETE expected=3 actual=%',v_permissions; end if;
end $$;
