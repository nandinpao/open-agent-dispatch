-- V41 C3R3G-HF3: IAM / Cutover Governance Closure.
-- Adds dedicated Runtime Configuration cutover Atomic Permissions, canonical HTTP inventory,
-- and a durable append-only audit trail for high-risk wave mutations.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','v41-c3r3g-hf3-iam-cutover-governance',true);

create table if not exists runtime_config_cutover_wave_audit_events (
  event_id varchar(64) primary key,
  wave_id varchar(64) not null,
  action_code varchar(48) not null,
  permission_code varchar(160) not null,
  actor_id varchar(255) not null,
  audit_reason text not null,
  correlation_id varchar(255) not null default '',
  before_phase varchar(64),
  after_phase varchar(64),
  detail_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);
create index if not exists idx_runtime_config_cutover_wave_audit_wave_created
  on runtime_config_cutover_wave_audit_events(wave_id,created_at desc);
create index if not exists idx_runtime_config_cutover_wave_audit_correlation
  on runtime_config_cutover_wave_audit_events(correlation_id) where correlation_id<>'';

create or replace function deny_runtime_config_cutover_wave_audit_mutation() returns trigger language plpgsql as $$
begin
  raise exception 'RUNTIME_CONFIG_CUTOVER_WAVE_AUDIT_APPEND_ONLY';
end $$;
drop trigger if exists trg_runtime_config_cutover_wave_audit_append_only on runtime_config_cutover_wave_audit_events;
create trigger trg_runtime_config_cutover_wave_audit_append_only
  before update or delete on runtime_config_cutover_wave_audit_events
  for each row execute function deny_runtime_config_cutover_wave_audit_mutation();

-- Publish a new Permission Catalog revision by copying the active revision and adding six cutover permissions.
insert into permission_catalog_revisions(
 revision_id,revision_code,revision_number,status,content_hash,description,supersedes_revision_id,
 created_at,created_by,published_at,published_by,version)
select '00000000-0000-0000-0000-000000000026'::uuid,
       'V41-C3R3G-HF3-CONFIG-CUTOVER-GOVERNANCE',coalesce(max(revision_number),0)+1,
       'DRAFT','DRAFT:UNPUBLISHED','Dedicated Runtime Configuration cutover-wave Atomic Permissions and governance closure.',
       (select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE'),
       now(),'v41-c3r3g-hf3-iam-cutover-governance',null,null,1
from permission_catalog_revisions
on conflict(revision_id) do nothing;

insert into permission_catalog_revision_entries(
 revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,
 allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
select '00000000-0000-0000-0000-000000000026'::uuid,e.permission_code,e.owner_module,e.resource_type,e.action_code,e.description,
 e.risk_level,e.risk_lane,e.lifecycle,e.allowed_scope_types,e.system_managed,e.replacement_permission_code,e.introduced_at,e.deprecated_at,
 e.retired_at,now(),'v41-c3r3g-hf3-iam-cutover-governance',1
from permission_catalog_revision_entries e
join permission_catalog_active_revision a on a.singleton_id='ACTIVE' and a.revision_id=e.revision_id
on conflict(revision_id,permission_code) do nothing;

insert into permission_catalog_revision_aliases(
 revision_id,alias_code,canonical_permission_code,alias_type,valid_from,valid_until,reason,created_by,created_at,version)
select '00000000-0000-0000-0000-000000000026'::uuid,a.alias_code,a.canonical_permission_code,a.alias_type,a.valid_from,a.valid_until,a.reason,
 'v41-c3r3g-hf3-iam-cutover-governance',now(),1
from permission_catalog_revision_aliases a
join permission_catalog_active_revision active on active.singleton_id='ACTIVE' and active.revision_id=a.revision_id
on conflict(revision_id,alias_code) do nothing;

insert into permission_catalog_revision_entries(
 revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,
 allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
values
 ('00000000-0000-0000-0000-000000000026','configuration.cutover.view','control-plane-app','RUNTIME_CONFIGURATION','READ_CUTOVER','View ordered Runtime Configuration cutover-wave state, safety evidence and certification state.','MEDIUM','READ','ACTIVE',array['INSTANCE']::varchar[],true,null,now(),null,null,now(),'v41-c3r3g-hf3-iam-cutover-governance',1),
 ('00000000-0000-0000-0000-000000000026','configuration.cutover.assess','control-plane-app','RUNTIME_CONFIGURATION','ASSESS_CUTOVER','Execute governed pre-cutover safety/readiness assessment without changing Runtime Authority.','HIGH','WRITE','ACTIVE',array['INSTANCE']::varchar[],true,null,now(),null,null,now(),'v41-c3r3g-hf3-iam-cutover-governance',1),
 ('00000000-0000-0000-0000-000000000026','configuration.cutover.prepare','control-plane-app','RUNTIME_CONFIGURATION','PREPARE_CUTOVER','Prepare an ordered Runtime Configuration wave and distribute its RUNTIME_ONLY authority contract.','CRITICAL','CRITICAL','ACTIVE',array['INSTANCE']::varchar[],true,null,now(),null,null,now(),'v41-c3r3g-hf3-iam-cutover-governance',1),
 ('00000000-0000-0000-0000-000000000026','configuration.cutover.finalize','control-plane-app','RUNTIME_CONFIGURATION','FINALIZE_CUTOVER','Finalize a converged cutover wave and commit its members to MIGRATED Runtime Authority.','CRITICAL','CRITICAL','ACTIVE',array['INSTANCE']::varchar[],true,null,now(),null,null,now(),'v41-c3r3g-hf3-iam-cutover-governance',1),
 ('00000000-0000-0000-0000-000000000026','configuration.cutover.cancel','control-plane-app','RUNTIME_CONFIGURATION','CANCEL_CUTOVER','Cancel a prepared cutover wave before finalization and restore governed pre-cutover distribution.','CRITICAL','CRITICAL','ACTIVE',array['INSTANCE']::varchar[],true,null,now(),null,null,now(),'v41-c3r3g-hf3-iam-cutover-governance',1),
 ('00000000-0000-0000-0000-000000000026','configuration.cutover.certify','control-plane-app','RUNTIME_CONFIGURATION','CERTIFY_CUTOVER','Record durable PASS certification for a finalized cutover wave after convergence evidence is revalidated.','CRITICAL','CRITICAL','ACTIVE',array['INSTANCE']::varchar[],true,null,now(),null,null,now(),'v41-c3r3g-hf3-iam-cutover-governance',1)
on conflict(revision_id,permission_code) do update set description=excluded.description,risk_level=excluded.risk_level,
 risk_lane=excluded.risk_lane,lifecycle='ACTIVE',allowed_scope_types=excluded.allowed_scope_types,updated_at=now(),
 updated_by='v41-c3r3g-hf3-iam-cutover-governance',version=permission_catalog_revision_entries.version+1;

select set_config('app.permission_catalog_publish_revision_id','00000000-0000-0000-0000-000000000026',true);
insert into permission_definitions(
 permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,active,version,owner_module,risk_lane,lifecycle,
 catalog_revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by)
select permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,lifecycle<>'RETIRED',1,owner_module,risk_lane,lifecycle,
 revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by
from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000026'::uuid
on conflict(permission_code) do update set resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,
 risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,system_managed=excluded.system_managed,active=excluded.active,
 owner_module=excluded.owner_module,risk_lane=excluded.risk_lane,lifecycle=excluded.lifecycle,catalog_revision_id=excluded.catalog_revision_id,
 updated_at=excluded.updated_at,updated_by=excluded.updated_by,version=permission_definitions.version+1;

update permission_catalog_revisions set status='SUPERSEDED',version=version+1
where revision_id=(select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE')
 and revision_id<>'00000000-0000-0000-0000-000000000026'::uuid and status='PUBLISHED';

update permission_catalog_revisions set status='PUBLISHED',content_hash=(
 with catalog_lines as (
   select 'P|'||permission_code||'|'||owner_module||'|'||resource_type||'|'||action_code||'|'||description||'|'||risk_level||'|'||risk_lane||'|'||lifecycle||'|'||coalesce(array_to_string(allowed_scope_types,','),'')||'|'||system_managed::text||'|'||coalesce(replacement_permission_code,'') line
   from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000026'::uuid
   union all
   select 'A|'||alias_code||'|'||canonical_permission_code||'|'||alias_type||'|'||coalesce(to_char(valid_from at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||coalesce(to_char(valid_until at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||reason
   from permission_catalog_revision_aliases where revision_id='00000000-0000-0000-0000-000000000026'::uuid)
 select 'sha256:'||encode(sha256(convert_to(coalesce(string_agg(line,E'\n' order by line),''),'UTF8')),'hex') from catalog_lines),
 published_at=now(),published_by='v41-c3r3g-hf3-iam-cutover-governance',version=version+1
where revision_id='00000000-0000-0000-0000-000000000026'::uuid and status='DRAFT';

update permission_catalog_active_revision set revision_id='00000000-0000-0000-0000-000000000026'::uuid,
 activated_at=now(),activated_by='v41-c3r3g-hf3-iam-cutover-governance',version=version+1 where singleton_id='ACTIVE';

insert into permission_catalog_publication_events(publication_id,revision_id,previous_revision_id,content_hash,entry_count,alias_count,actor_id,audit_reason,correlation_id,published_at)
select '00000000-0000-0000-0000-000000005026'::uuid,r.revision_id,r.supersedes_revision_id,r.content_hash,
 (select count(*)::integer from permission_catalog_revision_entries e where e.revision_id=r.revision_id),
 (select count(*)::integer from permission_catalog_revision_aliases a where a.revision_id=r.revision_id),
 'v41-c3r3g-hf3-iam-cutover-governance','C3R3G-HF3 Runtime Configuration cutover governance permission publication','v41-c3r3g-hf3-iam-cutover-governance',coalesce(r.published_at,now())
from permission_catalog_revisions r where r.revision_id='00000000-0000-0000-0000-000000000026'::uuid
on conflict(publication_id) do nothing;

-- Only instance-level platform administrators receive cutover mutation permissions by default.
-- Custom roles remain explicit through normal Role Binding administration.
insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'c3r3ghf3-'||substr(md5(r.role_id||':'||p.permission_code),1,30),null,r.role_id,p.permission_code,now(),'v41-c3r3g-hf3-iam-cutover-governance',1
from rbac_roles r join permission_definitions p on p.permission_code in
 ('configuration.cutover.view','configuration.cutover.assess','configuration.cutover.prepare','configuration.cutover.finalize','configuration.cutover.cancel','configuration.cutover.certify')
where r.tenant_id is null and r.role_code in('SYSTEM_ADMIN','PLATFORM_ADMIN') and r.status='ACTIVE'
on conflict(role_id,permission_point) do nothing;

-- Canonical Human Admin HTTP entry-point inventory. All routes are INSTANCE scoped.
insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,target_permission_code,
 legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,migration_deadline,manifest_revision,source_ref,source_hash,
 last_verified_at,created_by,updated_by)
values
('REST:GET:/api/platform/runtime-configuration/cutover-waves','REST','control-plane-app','control-plane-app','RuntimeConfigurationCutoverWaveController.list','/api/platform/runtime-configuration/cutover-waves','GET','TARGET_ONLY','configuration.cutover.view',null,'[]'::jsonb,'RUNTIME_CONFIGURATION','R3_ROUTE_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf3-2026-09-29','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/configuration/RuntimeConfigurationCutoverWaveController.java#list','319f9e81e8df3c52e17b89cee2482d1ea6f88a51db753e76fd1e0f2cc875717f',now(),'v41-c3r3g-hf3-iam-cutover-governance','v41-c3r3g-hf3-iam-cutover-governance'),
('REST:GET:/api/platform/runtime-configuration/cutover-waves/{waveId}','REST','control-plane-app','control-plane-app','RuntimeConfigurationCutoverWaveController.status','/api/platform/runtime-configuration/cutover-waves/{waveId}','GET','TARGET_ONLY','configuration.cutover.view',null,'[]'::jsonb,'RUNTIME_CONFIGURATION','R3_ROUTE_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf3-2026-09-29','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/configuration/RuntimeConfigurationCutoverWaveController.java#status','319f9e81e8df3c52e17b89cee2482d1ea6f88a51db753e76fd1e0f2cc875717f',now(),'v41-c3r3g-hf3-iam-cutover-governance','v41-c3r3g-hf3-iam-cutover-governance'),
('REST:GET:/api/platform/runtime-configuration/cutover-waves/{waveId}/safety-attestation','REST','control-plane-app','control-plane-app','RuntimeConfigurationCutoverWaveController.safetyAttestation','/api/platform/runtime-configuration/cutover-waves/{waveId}/safety-attestation','GET','TARGET_ONLY','configuration.cutover.view',null,'[]'::jsonb,'RUNTIME_CONFIGURATION','R3_ROUTE_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf3-2026-09-29','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/configuration/RuntimeConfigurationCutoverWaveController.java#safetyAttestation','319f9e81e8df3c52e17b89cee2482d1ea6f88a51db753e76fd1e0f2cc875717f',now(),'v41-c3r3g-hf3-iam-cutover-governance','v41-c3r3g-hf3-iam-cutover-governance'),
('REST:GET:/api/platform/runtime-configuration/cutover-waves/{waveId}/certification','REST','control-plane-app','control-plane-app','RuntimeConfigurationCutoverWaveController.certification','/api/platform/runtime-configuration/cutover-waves/{waveId}/certification','GET','TARGET_ONLY','configuration.cutover.view',null,'[]'::jsonb,'RUNTIME_CONFIGURATION','R3_ROUTE_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf3-2026-09-29','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/configuration/RuntimeConfigurationCutoverWaveController.java#certification','319f9e81e8df3c52e17b89cee2482d1ea6f88a51db753e76fd1e0f2cc875717f',now(),'v41-c3r3g-hf3-iam-cutover-governance','v41-c3r3g-hf3-iam-cutover-governance'),
('REST:POST:/api/platform/runtime-configuration/cutover-waves/{waveId}/safety-attestation','REST','control-plane-app','control-plane-app','RuntimeConfigurationCutoverWaveController.assessSafety','/api/platform/runtime-configuration/cutover-waves/{waveId}/safety-attestation','POST','TARGET_ONLY','configuration.cutover.assess',null,'[]'::jsonb,'RUNTIME_CONFIGURATION','R3_ROUTE_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf3-2026-09-29','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/configuration/RuntimeConfigurationCutoverWaveController.java#assessSafety','319f9e81e8df3c52e17b89cee2482d1ea6f88a51db753e76fd1e0f2cc875717f',now(),'v41-c3r3g-hf3-iam-cutover-governance','v41-c3r3g-hf3-iam-cutover-governance'),
('REST:POST:/api/platform/runtime-configuration/cutover-waves/{waveId}/prepare','REST','control-plane-app','control-plane-app','RuntimeConfigurationCutoverWaveController.prepare','/api/platform/runtime-configuration/cutover-waves/{waveId}/prepare','POST','TARGET_ONLY','configuration.cutover.prepare',null,'[]'::jsonb,'RUNTIME_CONFIGURATION','R3_ROUTE_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf3-2026-09-29','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/configuration/RuntimeConfigurationCutoverWaveController.java#prepare','319f9e81e8df3c52e17b89cee2482d1ea6f88a51db753e76fd1e0f2cc875717f',now(),'v41-c3r3g-hf3-iam-cutover-governance','v41-c3r3g-hf3-iam-cutover-governance'),
('REST:POST:/api/platform/runtime-configuration/cutover-waves/{waveId}/finalize','REST','control-plane-app','control-plane-app','RuntimeConfigurationCutoverWaveController.finalizeWave','/api/platform/runtime-configuration/cutover-waves/{waveId}/finalize','POST','TARGET_ONLY','configuration.cutover.finalize',null,'[]'::jsonb,'RUNTIME_CONFIGURATION','R3_ROUTE_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf3-2026-09-29','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/configuration/RuntimeConfigurationCutoverWaveController.java#finalizeWave','319f9e81e8df3c52e17b89cee2482d1ea6f88a51db753e76fd1e0f2cc875717f',now(),'v41-c3r3g-hf3-iam-cutover-governance','v41-c3r3g-hf3-iam-cutover-governance'),
('REST:POST:/api/platform/runtime-configuration/cutover-waves/{waveId}/cancel','REST','control-plane-app','control-plane-app','RuntimeConfigurationCutoverWaveController.cancel','/api/platform/runtime-configuration/cutover-waves/{waveId}/cancel','POST','TARGET_ONLY','configuration.cutover.cancel',null,'[]'::jsonb,'RUNTIME_CONFIGURATION','R3_ROUTE_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf3-2026-09-29','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/configuration/RuntimeConfigurationCutoverWaveController.java#cancel','319f9e81e8df3c52e17b89cee2482d1ea6f88a51db753e76fd1e0f2cc875717f',now(),'v41-c3r3g-hf3-iam-cutover-governance','v41-c3r3g-hf3-iam-cutover-governance'),
('REST:POST:/api/platform/runtime-configuration/cutover-waves/{waveId}/certify','REST','control-plane-app','control-plane-app','RuntimeConfigurationCutoverWaveController.certify','/api/platform/runtime-configuration/cutover-waves/{waveId}/certify','POST','TARGET_ONLY','configuration.cutover.certify',null,'[]'::jsonb,'RUNTIME_CONFIGURATION','R3_ROUTE_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf3-2026-09-29','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/configuration/RuntimeConfigurationCutoverWaveController.java#certify','319f9e81e8df3c52e17b89cee2482d1ea6f88a51db753e76fd1e0f2cc875717f',now(),'v41-c3r3g-hf3-iam-cutover-governance','v41-c3r3g-hf3-iam-cutover-governance')
on conflict(entry_point_id) do update set display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,
 authority_state=excluded.authority_state,target_permission_code=excluded.target_permission_code,legacy_authority_type=null,legacy_authorities='[]'::jsonb,
 resource_type=excluded.resource_type,resource_resolver_id=excluded.resource_resolver_id,exemption_reason=null,migration_deadline=null,
 manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,source_hash=excluded.source_hash,last_verified_at=now(),updated_at=now(),
 updated_by='v41-c3r3g-hf3-iam-cutover-governance',version=permission_entry_point_inventory.version+1;

update rbac_policy_versions set policy_version=policy_version+1,updated_at=now(),updated_by='v41-c3r3g-hf3-iam-cutover-governance';

-- Fail the migration if any Wave route or permission was not installed as canonical target-only authority.
do $$
declare v_routes integer; v_permissions integer;
begin
  select count(*) into v_routes from permission_entry_point_inventory
   where route_pattern like '/api/platform/runtime-configuration/cutover-waves%'
     and authority_state='TARGET_ONLY' and target_permission_code like 'configuration.cutover.%';
  if v_routes<>9 then raise exception 'C3R3G_HF3_CUTOVER_ROUTE_MAPPING_INCOMPLETE expected=9 actual=%',v_routes; end if;
  select count(*) into v_permissions from permission_definitions
   where permission_code in('configuration.cutover.view','configuration.cutover.assess','configuration.cutover.prepare','configuration.cutover.finalize','configuration.cutover.cancel','configuration.cutover.certify')
     and active=true and lifecycle='ACTIVE';
  if v_permissions<>6 then raise exception 'C3R3G_HF3_CUTOVER_PERMISSION_CATALOG_INCOMPLETE expected=6 actual=%',v_permissions; end if;
end $$;
