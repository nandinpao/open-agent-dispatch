-- RS2: Source Systems + Business Events scoped authorization.
-- Canonical rule: Source System ownership is snapshotted into Event / Incident evidence at intake time.
-- Historical Event visibility must not drift silently when a Source System is later re-owned.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','rs2-source-event-scope',true);

-- ---------------------------------------------------------------------------
-- Event / Incident scope snapshot.
-- UNRESOLVED is deliberately fail-closed in Resource Access and business-event list queries.
-- ---------------------------------------------------------------------------
alter table event_decisions add column if not exists event_type varchar(128);
alter table event_decisions add column if not exists event_stage varchar(32);
alter table event_decisions add column if not exists correlation_id varchar(128);
alter table event_decisions add column if not exists normalized_message text;
alter table event_decisions add column if not exists payload_json jsonb not null default '{}'::jsonb;
alter table event_decisions add column if not exists occurred_at timestamptz;
alter table event_decisions add column if not exists owner_department_id varchar(128);
alter table event_decisions add column if not exists owner_group_id varchar(128);
alter table event_decisions add column if not exists scope_status varchar(24) not null default 'UNRESOLVED';
alter table event_decisions add column if not exists scope_source_version bigint;
alter table event_decisions add column if not exists scope_inherited_at timestamptz;

alter table incidents add column if not exists owner_department_id varchar(128);
alter table incidents add column if not exists owner_group_id varchar(128);
alter table incidents add column if not exists scope_status varchar(24) not null default 'UNRESOLVED';
alter table incidents add column if not exists scope_source_version bigint;
alter table incidents add column if not exists scope_inherited_at timestamptz;

update event_decisions e
set owner_department_id=s.owner_department_id,
    owner_group_id=s.owner_group_id,
    scope_status=case when s.owner_department_id is null and s.owner_group_id is null then 'TENANT_OWNED' else 'INHERITED' end,
    scope_source_version=s.version,
    scope_inherited_at=coalesce(e.decided_at,e.created_at,now())
from source_systems s
where e.tenant_id=s.tenant_id and e.source_system=s.source_system_id and e.scope_status='UNRESOLVED';

update incidents i
set owner_department_id=s.owner_department_id,
    owner_group_id=s.owner_group_id,
    scope_status=case when s.owner_department_id is null and s.owner_group_id is null then 'TENANT_OWNED' else 'INHERITED' end,
    scope_source_version=s.version,
    scope_inherited_at=coalesce(i.occurred_at,i.first_seen_at,i.created_at,now())
from source_systems s
where i.tenant_id=s.tenant_id and i.source_system=s.source_system_id and i.scope_status='UNRESOLVED';

create index if not exists idx_event_decisions_scope_department on event_decisions(tenant_id,owner_department_id,scope_status,decided_at desc);
create index if not exists idx_event_decisions_scope_group on event_decisions(tenant_id,owner_group_id,scope_status,decided_at desc);
create index if not exists idx_event_decisions_tenant_source on event_decisions(tenant_id,source_system,decided_at desc);
create index if not exists idx_incidents_scope_department on incidents(tenant_id,owner_department_id,scope_status,last_seen_at desc);
create index if not exists idx_incidents_scope_group on incidents(tenant_id,owner_group_id,scope_status,last_seen_at desc);

alter table event_decisions drop constraint if exists ck_event_decisions_scope_status;
alter table event_decisions add constraint ck_event_decisions_scope_status check(scope_status in ('TENANT_OWNED','INHERITED','UNRESOLVED')) not valid;
alter table event_decisions validate constraint ck_event_decisions_scope_status;
alter table incidents drop constraint if exists ck_incidents_scope_status;
alter table incidents add constraint ck_incidents_scope_status check(scope_status in ('TENANT_OWNED','INHERITED','UNRESOLVED')) not valid;
alter table incidents validate constraint ck_incidents_scope_status;

do $$
begin
  if not exists(select 1 from pg_constraint where conname='fk_event_decision_owner_department') then
    alter table event_decisions add constraint fk_event_decision_owner_department
      foreign key(tenant_id,owner_department_id) references departments(tenant_id,department_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_event_decision_owner_group') then
    alter table event_decisions add constraint fk_event_decision_owner_group
      foreign key(tenant_id,owner_group_id) references organization_groups(tenant_id,group_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_incident_owner_department') then
    alter table incidents add constraint fk_incident_owner_department
      foreign key(tenant_id,owner_department_id) references departments(tenant_id,department_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_incident_owner_group') then
    alter table incidents add constraint fk_incident_owner_group
      foreign key(tenant_id,owner_group_id) references organization_groups(tenant_id,group_id) not valid;
  end if;
end $$;
alter table event_decisions validate constraint fk_event_decision_owner_department;
alter table event_decisions validate constraint fk_event_decision_owner_group;
alter table incidents validate constraint fk_incident_owner_department;
alter table incidents validate constraint fk_incident_owner_group;

create or replace function rs2_snapshot_source_scope()
returns trigger language plpgsql security invoker as $$
declare dep varchar; grp varchar; src_version bigint;
begin
  -- Event/Incident tenant + source identity is immutable once scope evidence exists.
  if tg_op='UPDATE' and old.scope_status in ('TENANT_OWNED','INHERITED') then
    if old.tenant_id is distinct from new.tenant_id or old.source_system is distinct from new.source_system then
      raise exception 'RS2_SCOPE_IDENTITY_IMMUTABLE: tenant/source cannot change after scope snapshot';
    end if;
    new.owner_department_id=old.owner_department_id;
    new.owner_group_id=old.owner_group_id;
    new.scope_status=old.scope_status;
    new.scope_source_version=old.scope_source_version;
    new.scope_inherited_at=old.scope_inherited_at;
    return new;
  end if;
  if new.tenant_id is null or btrim(new.tenant_id)='' or new.source_system is null or btrim(new.source_system)='' then
    new.scope_status='UNRESOLVED';
    return new;
  end if;
  select s.owner_department_id,s.owner_group_id,s.version into dep,grp,src_version
  from source_systems s
  where s.tenant_id=new.tenant_id and s.source_system_id=new.source_system and s.status<>'RETIRED';
  if not found then
    new.owner_department_id=null;
    new.owner_group_id=null;
    new.scope_status='UNRESOLVED';
    new.scope_source_version=null;
    new.scope_inherited_at=null;
    return new;
  end if;
  new.owner_department_id=dep;
  new.owner_group_id=grp;
  new.scope_status=case when dep is null and grp is null then 'TENANT_OWNED' else 'INHERITED' end;
  new.scope_source_version=src_version;
  new.scope_inherited_at=coalesce(new.scope_inherited_at,now());
  return new;
end $$;

drop trigger if exists trg_rs2_event_scope_snapshot on event_decisions;
create trigger trg_rs2_event_scope_snapshot
before insert or update on event_decisions
for each row execute function rs2_snapshot_source_scope();

drop trigger if exists trg_rs2_incident_scope_snapshot on incidents;
create trigger trg_rs2_incident_scope_snapshot
before insert or update on incidents
for each row execute function rs2_snapshot_source_scope();

-- When an administrator later registers or re-enables a previously unknown Source, resolve only
-- rows that were never visible (UNRESOLVED). Already inherited historical scope is immutable.
create or replace function rs2_remediate_unresolved_source_scope()
returns trigger language plpgsql as $$
declare event_count integer:=0; incident_count integer:=0; actor varchar;
begin
  if new.status='RETIRED' then return new; end if;
  update event_decisions set updated_at=now()
   where tenant_id=new.tenant_id and source_system=new.source_system_id and scope_status='UNRESOLVED';
  get diagnostics event_count=row_count;
  update incidents set updated_at=now()
   where tenant_id=new.tenant_id and source_system=new.source_system_id and scope_status='UNRESOLVED';
  get diagnostics incident_count=row_count;
  if event_count>0 or incident_count>0 then
    actor=coalesce(nullif(current_setting('app.current_actor_id',true),''),'rs2-source-scope-remediation');
    perform p4ra_advance_policy_revision(new.tenant_id,actor);
  end if;
  return new;
end $$;

drop trigger if exists trg_rs2_source_scope_remediation on source_systems;
create trigger trg_rs2_source_scope_remediation
after insert or update of status,owner_department_id,owner_group_id on source_systems
for each row execute function rs2_remediate_unresolved_source_scope();

-- Ownership changes are security-state changes for the Source System object itself.
create or replace function rs2_touch_source_scope_epoch()
returns trigger language plpgsql as $$
declare actor varchar;
begin
  if old.owner_department_id is not distinct from new.owner_department_id
     and old.owner_group_id is not distinct from new.owner_group_id then return new; end if;
  actor=coalesce(nullif(current_setting('app.current_actor_id',true),''),'rs2-source-owner-change');
  perform p4ra_advance_policy_revision(new.tenant_id,actor);
  insert into resource_security_epochs(tenant_id,resource_type,resource_id,resource_security_epoch,updated_at,updated_by)
  values(new.tenant_id,'SOURCE_SYSTEM',new.source_system_id,1,now(),actor)
  on conflict(tenant_id,resource_type,resource_id) do update
    set resource_security_epoch=resource_security_epochs.resource_security_epoch+1,
        updated_at=excluded.updated_at,updated_by=excluded.updated_by;
  return new;
end $$;

drop trigger if exists trg_rs2_source_scope_epoch on source_systems;
create trigger trg_rs2_source_scope_epoch
after update of owner_department_id,owner_group_id on source_systems
for each row execute function rs2_touch_source_scope_epoch();

comment on column event_decisions.scope_status is 'RS2 Source ownership snapshot status. UNRESOLVED is fail-closed and requires remediation.';
comment on column event_decisions.scope_source_version is 'Source System optimistic-lock version used when the Event scope snapshot was taken.';
comment on column incidents.scope_status is 'RS2 Source ownership snapshot status for the Event-derived Incident aggregate.';

-- ---------------------------------------------------------------------------
-- Resource Access catalog adds first-class Business Event and Incident resources.
-- ---------------------------------------------------------------------------
insert into resource_catalog(resource_type,category,descriptor_authority,ownership_supported,participants_supported,
  field_visibility_supported,runtime_lease_supported,default_sensitivity,description)
values
 ('EVENT','EVENT','EVENT_PROCESSING',true,true,true,false,'RESTRICTED','Source-scoped business event metadata/payload evidence'),
 ('INCIDENT','EVENT','EVENT_PROCESSING',true,true,true,false,'CONFIDENTIAL','Source-scoped event incident aggregate')
on conflict(resource_type) do update set
 category=excluded.category,descriptor_authority=excluded.descriptor_authority,
 ownership_supported=excluded.ownership_supported,participants_supported=excluded.participants_supported,
 field_visibility_supported=excluded.field_visibility_supported,runtime_lease_supported=excluded.runtime_lease_supported,
 default_sensitivity=excluded.default_sensitivity,description=excluded.description,status='ACTIVE',
 catalog_version=resource_catalog.catalog_version+1,updated_at=now();

-- ---------------------------------------------------------------------------
-- Permission Catalog revision 019: Source/Event organizational scopes + payload separation.
-- Existing Incident auto-resolve remains TENANT-only because it is a collection mutation.
-- ---------------------------------------------------------------------------
insert into permission_catalog_revisions(
  revision_id,revision_code,revision_number,status,content_hash,description,supersedes_revision_id,
  created_at,created_by,published_at,published_by,version)
select '00000000-0000-0000-0000-000000000019'::uuid,'RS2-SOURCE-EVENT-SCOPE-0.8.2',coalesce(max(revision_number),0)+1,
  'DRAFT','DRAFT:UNPUBLISHED','RS2 Source System and Business Event organizational authorization.',
  (select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE'),
  now(),'rs2-source-event-scope',null,null,1
from permission_catalog_revisions
on conflict(revision_id) do nothing;

insert into permission_catalog_revision_entries(
  revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,
  lifecycle,allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,
  retired_at,updated_at,updated_by,version)
select '00000000-0000-0000-0000-000000000019'::uuid,e.permission_code,e.owner_module,e.resource_type,
  e.action_code,e.description,e.risk_level,e.risk_lane,e.lifecycle,e.allowed_scope_types,e.system_managed,
  e.replacement_permission_code,e.introduced_at,e.deprecated_at,e.retired_at,now(),'rs2-source-event-scope',1
from permission_catalog_revision_entries e
join permission_catalog_active_revision a on a.singleton_id='ACTIVE' and a.revision_id=e.revision_id
on conflict(revision_id,permission_code) do nothing;

insert into permission_catalog_revision_aliases(
  revision_id,alias_code,canonical_permission_code,alias_type,valid_from,valid_until,reason,created_by,created_at,version)
select '00000000-0000-0000-0000-000000000019'::uuid,a.alias_code,a.canonical_permission_code,a.alias_type,
  a.valid_from,a.valid_until,a.reason,'rs2-source-event-scope',now(),1
from permission_catalog_revision_aliases a
join permission_catalog_active_revision active on active.singleton_id='ACTIVE' and active.revision_id=a.revision_id
on conflict(revision_id,alias_code) do nothing;

update permission_catalog_revision_entries
set allowed_scope_types=array['TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP']::varchar[],
    updated_at=now(),updated_by='rs2-source-event-scope',version=version+1
where revision_id='00000000-0000-0000-0000-000000000019'::uuid
  and (permission_code like 'admin.source.system.%'
       or permission_code in ('api.incident.incidents','api.incident.incident','api.incident.occurrence.summary','api.incident.reopen','api.incident.resolve','api.incident.suppress'));

insert into permission_catalog_revision_entries(
 revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,
 allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
values
 ('00000000-0000-0000-0000-000000000019'::uuid,'admin.source.system.test','control-plane-app','SOURCE_SYSTEM','TEST','Test Source System connectivity without exposing stored credential material.','HIGH','WRITE','ACTIVE',array['TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP']::varchar[],true,null,now(),null,null,now(),'rs2-source-event-scope',1),
 ('00000000-0000-0000-0000-000000000019'::uuid,'admin.source.system.mapping.manage','control-plane-app','SOURCE_SYSTEM','MAPPING_MANAGE','Manage Source System event/mapping configuration in the authorized scope.','HIGH','WRITE','ACTIVE',array['TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP']::varchar[],true,null,now(),null,null,now(),'rs2-source-event-scope',1),
 ('00000000-0000-0000-0000-000000000019'::uuid,'admin.source.system.credentials.read','control-plane-app','SOURCE_SYSTEM','CREDENTIAL_READ','Read credential metadata through a separately governed endpoint; never implied by Source read.','HIGH','READ','ACTIVE',array['TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP']::varchar[],true,null,now(),null,null,now(),'rs2-source-event-scope',1),
 ('00000000-0000-0000-0000-000000000019'::uuid,'admin.source.system.credentials.rotate','control-plane-app','SOURCE_SYSTEM','CREDENTIAL_ROTATE','Rotate Source System credentials through a separately governed endpoint.','CRITICAL','CRITICAL','ACTIVE',array['TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP']::varchar[],true,null,now(),null,null,now(),'rs2-source-event-scope',1),
 ('00000000-0000-0000-0000-000000000019'::uuid,'admin.business.event.list','control-plane-app','EVENT','LIST','List Business Event metadata in the current authorized scope.','MEDIUM','READ','ACTIVE',array['TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP']::varchar[],true,null,now(),null,null,now(),'rs2-source-event-scope',1),
 ('00000000-0000-0000-0000-000000000019'::uuid,'admin.business.event.detail','control-plane-app','EVENT','READ','Read Business Event metadata in the current authorized scope.','MEDIUM','READ','ACTIVE',array['TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP']::varchar[],true,null,now(),null,null,now(),'rs2-source-event-scope',1),
 ('00000000-0000-0000-0000-000000000019'::uuid,'admin.business.event.payload.read','control-plane-app','EVENT','READ_PAYLOAD','Read the normalized Event payload after independent scope authorization.','HIGH','READ','ACTIVE',array['TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP']::varchar[],true,null,now(),null,null,now(),'rs2-source-event-scope',1),
 ('00000000-0000-0000-0000-000000000019'::uuid,'admin.business.event.export','control-plane-app','EVENT','EXPORT','Export authorized Business Event evidence only.','HIGH','WRITE','ACTIVE',array['TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP']::varchar[],true,null,now(),null,null,now(),'rs2-source-event-scope',1),
 ('00000000-0000-0000-0000-000000000019'::uuid,'admin.business.event.replay','control-plane-app','EVENT','REPLAY','Replay an authorized Business Event through the governed replay path.','CRITICAL','CRITICAL','ACTIVE',array['TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP']::varchar[],true,null,now(),null,null,now(),'rs2-source-event-scope',1)
on conflict(revision_id,permission_code) do update set
 description=excluded.description,risk_level=excluded.risk_level,risk_lane=excluded.risk_lane,
 allowed_scope_types=excluded.allowed_scope_types,updated_at=excluded.updated_at,updated_by=excluded.updated_by,version=permission_catalog_revision_entries.version+1;

select set_config('app.permission_catalog_publish_revision_id','00000000-0000-0000-0000-000000000019',true);

insert into permission_definitions(
  permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,
  active,version,owner_module,risk_lane,lifecycle,catalog_revision_id,replacement_permission_code,
  introduced_at,deprecated_at,retired_at,updated_at,updated_by)
select permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,
  lifecycle<>'RETIRED',1,owner_module,risk_lane,lifecycle,revision_id,replacement_permission_code,
  introduced_at,deprecated_at,retired_at,updated_at,updated_by
from permission_catalog_revision_entries
where revision_id='00000000-0000-0000-0000-000000000019'::uuid
on conflict(permission_code) do update set
  resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,
  risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,
  system_managed=excluded.system_managed,active=excluded.active,owner_module=excluded.owner_module,
  risk_lane=excluded.risk_lane,lifecycle=excluded.lifecycle,catalog_revision_id=excluded.catalog_revision_id,
  replacement_permission_code=excluded.replacement_permission_code,deprecated_at=excluded.deprecated_at,
  retired_at=excluded.retired_at,updated_at=excluded.updated_at,updated_by=excluded.updated_by,
  version=permission_definitions.version+1;

update permission_catalog_revisions set status='SUPERSEDED',version=version+1
where revision_id=(select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE')
  and revision_id<>'00000000-0000-0000-0000-000000000019'::uuid and status='PUBLISHED';

update permission_catalog_revisions set status='PUBLISHED',content_hash=(
  with catalog_lines as (
    select 'P|'||permission_code||'|'||owner_module||'|'||resource_type||'|'||action_code||'|'||description||'|'||
      risk_level||'|'||risk_lane||'|'||lifecycle||'|'||coalesce(array_to_string(allowed_scope_types,','),'')||'|'||
      system_managed::text||'|'||coalesce(replacement_permission_code,'') as line
    from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000019'::uuid
    union all
    select 'A|'||alias_code||'|'||canonical_permission_code||'|'||alias_type||'|'||
      coalesce(to_char(valid_from at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||
      coalesce(to_char(valid_until at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||reason
    from permission_catalog_revision_aliases where revision_id='00000000-0000-0000-0000-000000000019'::uuid)
  select 'sha256:'||encode(sha256(convert_to(coalesce(string_agg(line,E'\n' order by line),''),'UTF8')),'hex') from catalog_lines),
  published_at=now(),published_by='rs2-source-event-scope',version=version+1
where revision_id='00000000-0000-0000-0000-000000000019'::uuid and status='DRAFT';

update permission_catalog_active_revision
set revision_id='00000000-0000-0000-0000-000000000019'::uuid,
    activated_at=now(),activated_by='rs2-source-event-scope',version=version+1
where singleton_id='ACTIVE';

insert into permission_catalog_publication_events(
  publication_id,revision_id,previous_revision_id,content_hash,entry_count,alias_count,
  actor_id,audit_reason,correlation_id,published_at)
select '00000000-0000-0000-0000-000000005019'::uuid,r.revision_id,r.supersedes_revision_id,r.content_hash,
  (select count(*)::integer from permission_catalog_revision_entries e where e.revision_id=r.revision_id),
  (select count(*)::integer from permission_catalog_revision_aliases a where a.revision_id=r.revision_id),
  'rs2-source-event-scope','RS2 Source Systems + Business Events scoped authorization publication',
  'rs2-source-event-scope',coalesce(r.published_at,now())
from permission_catalog_revisions r
where r.revision_id='00000000-0000-0000-0000-000000000019'::uuid
on conflict(publication_id) do nothing;

-- Preserve the Phase-2 responsibility intent after this later catalog publication.
-- Viewer receives Event metadata only; Operator receives payload evidence; Dispatch Admin receives Source System administration.
insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'rs2-'||substr(md5(r.role_id||':'||p.permission_code),1,32),null,r.role_id,p.permission_code,now(),'rs2-source-event-scope',1
from rbac_roles r
join permission_definitions p on p.permission_code in ('admin.business.event.list','admin.business.event.detail') and p.active=true
where r.tenant_id is null and r.role_code in('VIEWER','OPERATOR','AUDITOR','DISPATCH_ADMIN') and r.status='ACTIVE'
on conflict(role_id,permission_point) do nothing;

insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'rs2-'||substr(md5(r.role_id||':'||p.permission_code),1,32),null,r.role_id,p.permission_code,now(),'rs2-source-event-scope',1
from rbac_roles r
join permission_definitions p on p.permission_code='admin.business.event.payload.read' and p.active=true
where r.tenant_id is null and r.role_code in('OPERATOR','DISPATCH_ADMIN') and r.status='ACTIVE'
on conflict(role_id,permission_point) do nothing;

insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'rs2-'||substr(md5(r.role_id||':'||p.permission_code),1,32),null,r.role_id,p.permission_code,now(),'rs2-source-event-scope',1
from rbac_roles r
join permission_definitions p on p.permission_code like 'admin.source.system.%' and p.active=true
where r.tenant_id is null and r.role_code='DISPATCH_ADMIN' and r.status='ACTIVE'
on conflict(role_id,permission_point) do nothing;


-- ---------------------------------------------------------------------------
-- R3 human API entry-point convergence. Controller source hashes are updated because
-- Source/Incident handlers changed; Business Event routes are registered as TARGET_ONLY.
-- ---------------------------------------------------------------------------
update permission_entry_point_inventory
set source_hash='41b1f52f7738e6206d0bbe397c4212477f305af4932e3bafe7af442668a3661b',
    manifest_revision='rs2-source-event-entrypoints-2026-08-12',last_verified_at=now(),updated_at=now(),updated_by='rs2-source-event-scope',version=version+1
where source_ref like 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/SourceSystemController.java#%';

update permission_entry_point_inventory
set source_hash='85e49a3bb60347633705ee9b3d85d3a303a9a3ea9d4883233a645910f78029fb',
    manifest_revision='rs2-source-event-entrypoints-2026-08-12',last_verified_at=now(),updated_at=now(),updated_by='rs2-source-event-scope',version=version+1
where source_ref like 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/IncidentController.java#%';

insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,
 target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,
 migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values
('REST:GET:/admin/business-events/','REST','control-plane-app','control-plane-app','BusinessEventController.list','/admin/business-events/','GET','TARGET_ONLY','admin.business.event.list',null,'[]'::jsonb,'EVENT','R3_ROUTE_RESOURCE_RESOLVER',null,null,'rs2-business-event-entrypoints-2026-08-12','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/BusinessEventController.java#list','97daf1f57d4efdc7d975042ef5a441119dae49114fff4e2989e003f54654d318',now(),'rs2-source-event-scope','rs2-source-event-scope'),
('REST:GET:/admin/business-events/{eventId}','REST','control-plane-app','control-plane-app','BusinessEventController.detail','/admin/business-events/{eventId}','GET','TARGET_ONLY','admin.business.event.detail',null,'[]'::jsonb,'EVENT','R3_PATH_RESOURCE_RESOLVER',null,null,'rs2-business-event-entrypoints-2026-08-12','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/BusinessEventController.java#detail','97daf1f57d4efdc7d975042ef5a441119dae49114fff4e2989e003f54654d318',now(),'rs2-source-event-scope','rs2-source-event-scope'),
('REST:GET:/admin/business-events/{eventId}/payload','REST','control-plane-app','control-plane-app','BusinessEventController.payload','/admin/business-events/{eventId}/payload','GET','TARGET_ONLY','admin.business.event.payload.read',null,'[]'::jsonb,'EVENT','R3_PATH_RESOURCE_RESOLVER',null,null,'rs2-business-event-entrypoints-2026-08-12','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/BusinessEventController.java#payload','97daf1f57d4efdc7d975042ef5a441119dae49114fff4e2989e003f54654d318',now(),'rs2-source-event-scope','rs2-source-event-scope')
on conflict(entry_point_id) do update set display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,
 authority_state=excluded.authority_state,target_permission_code=excluded.target_permission_code,legacy_authority_type=null,legacy_authorities='[]'::jsonb,
 resource_type=excluded.resource_type,resource_resolver_id=excluded.resource_resolver_id,exemption_reason=null,migration_deadline=null,
 manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,source_hash=excluded.source_hash,last_verified_at=now(),updated_at=now(),
 updated_by='rs2-source-event-scope',version=permission_entry_point_inventory.version+1;
