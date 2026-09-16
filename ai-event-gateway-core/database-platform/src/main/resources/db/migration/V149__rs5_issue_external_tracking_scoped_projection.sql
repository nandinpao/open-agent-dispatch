-- RS5: Issues & External Issue Tracking scoped projection.
-- Canonical Issue scope is snapshotted from Task origin ownership. External provider ACL never becomes OpenDispatch authority.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','rs5-issue-external-tracking-scope',true);

alter table task_issue_links add column if not exists owner_department_id varchar(128);
alter table task_issue_links add column if not exists owner_group_id varchar(128);
alter table task_issue_links add column if not exists requester_department_id varchar(128);
alter table task_issue_links add column if not exists requester_group_id varchar(128);
alter table task_issue_links add column if not exists executor_department_id varchar(128);
alter table task_issue_links add column if not exists executor_group_id varchar(128);
alter table task_issue_links add column if not exists scope_status varchar(32) not null default 'UNRESOLVED';
alter table task_issue_links add column if not exists scope_source_type varchar(32) not null default 'TASK';
alter table task_issue_links add column if not exists scope_source_id varchar(128);
alter table task_issue_links add column if not exists scope_source_version bigint;
alter table task_issue_links add column if not exists scope_inherited_at timestamptz;

-- Normalize historical placeholders before FK validation. A missing organizational owner is unresolved, never Tenant-wide.
update task_issue_links set owner_department_id=null where owner_department_id='UNASSIGNED';
update task_issue_links set requester_department_id=null where requester_department_id='UNASSIGNED';
update task_issue_links set executor_department_id=null where executor_department_id='UNASSIGNED';

-- Deterministic historical backfill from the canonical Task. Primary ownership is frozen at this point.
update task_issue_links l set
 owner_department_id=nullif(t.owner_department_id,'UNASSIGNED'),
 owner_group_id=t.owner_group_id,
 requester_department_id=nullif(coalesce(t.requester_department_id,t.owner_department_id),'UNASSIGNED'),
 requester_group_id=coalesce(t.requester_group_id,t.owner_group_id),
 executor_department_id=nullif(t.executor_department_id,'UNASSIGNED'),
 executor_group_id=t.executor_group_id,
 scope_status=case
   when t.origin_scope_status='RESOLVED'
    and (nullif(t.owner_department_id,'UNASSIGNED') is not null or t.owner_group_id is not null)
   then 'RESOLVED' else 'UNRESOLVED' end,
 scope_source_type='TASK',
 scope_source_id=t.task_id,
 scope_source_version=t.version,
 scope_inherited_at=coalesce(t.origin_scope_inherited_at,t.created_at,l.created_at)
from tasks t
where t.tenant_id=l.tenant_id and t.task_id=l.task_id
  and (l.scope_status='UNRESOLVED' or l.scope_source_id is null);

-- Any row that still cannot be resolved remains hidden until governed remediation. Never widen it to TENANT.
update task_issue_links set scope_status='UNRESOLVED'
where scope_status<>'RESOLVED' or (owner_department_id is null and owner_group_id is null);

alter table task_issue_links drop constraint if exists ck_task_issue_links_rs5_scope_status;
alter table task_issue_links add constraint ck_task_issue_links_rs5_scope_status
 check(scope_status in ('RESOLVED','UNRESOLVED')) not valid;
alter table task_issue_links validate constraint ck_task_issue_links_rs5_scope_status;

create index if not exists idx_task_issue_links_rs5_owner_department
 on task_issue_links(tenant_id,scope_status,owner_department_id,updated_at desc);
create index if not exists idx_task_issue_links_rs5_owner_group
 on task_issue_links(tenant_id,scope_status,owner_group_id,updated_at desc);
create index if not exists idx_task_issue_links_rs5_requester_department
 on task_issue_links(tenant_id,requester_department_id,updated_at desc);
create index if not exists idx_task_issue_links_rs5_executor_department
 on task_issue_links(tenant_id,executor_department_id,updated_at desc);
create index if not exists idx_task_issue_links_rs5_external_project
 on task_issue_links(tenant_id,connection_id,external_project_id,scope_status,updated_at desc);

do $$
begin
 if not exists(select 1 from pg_constraint where conname='fk_task_issue_links_rs5_owner_department') then
  alter table task_issue_links add constraint fk_task_issue_links_rs5_owner_department
   foreign key(tenant_id,owner_department_id) references departments(tenant_id,department_id) not valid;
 end if;
 if not exists(select 1 from pg_constraint where conname='fk_task_issue_links_rs5_owner_group') then
  alter table task_issue_links add constraint fk_task_issue_links_rs5_owner_group
   foreign key(tenant_id,owner_group_id) references organization_groups(tenant_id,group_id) not valid;
 end if;
 if not exists(select 1 from pg_constraint where conname='fk_task_issue_links_rs5_requester_department') then
  alter table task_issue_links add constraint fk_task_issue_links_rs5_requester_department
   foreign key(tenant_id,requester_department_id) references departments(tenant_id,department_id) not valid;
 end if;
 if not exists(select 1 from pg_constraint where conname='fk_task_issue_links_rs5_requester_group') then
  alter table task_issue_links add constraint fk_task_issue_links_rs5_requester_group
   foreign key(tenant_id,requester_group_id) references organization_groups(tenant_id,group_id) not valid;
 end if;
 if not exists(select 1 from pg_constraint where conname='fk_task_issue_links_rs5_executor_department') then
  alter table task_issue_links add constraint fk_task_issue_links_rs5_executor_department
   foreign key(tenant_id,executor_department_id) references departments(tenant_id,department_id) not valid;
 end if;
 if not exists(select 1 from pg_constraint where conname='fk_task_issue_links_rs5_executor_group') then
  alter table task_issue_links add constraint fk_task_issue_links_rs5_executor_group
   foreign key(tenant_id,executor_group_id) references organization_groups(tenant_id,group_id) not valid;
 end if;
end $$;
alter table task_issue_links validate constraint fk_task_issue_links_rs5_owner_department;
alter table task_issue_links validate constraint fk_task_issue_links_rs5_owner_group;
alter table task_issue_links validate constraint fk_task_issue_links_rs5_requester_department;
alter table task_issue_links validate constraint fk_task_issue_links_rs5_requester_group;
alter table task_issue_links validate constraint fk_task_issue_links_rs5_executor_department;
alter table task_issue_links validate constraint fk_task_issue_links_rs5_executor_group;

-- Once resolved, Issue primary scope/provenance is immutable. One-time UNRESOLVED -> RESOLVED remediation is permitted.
create or replace function rs5_guard_issue_primary_scope_immutable()
returns trigger language plpgsql as $$
begin
 if old.scope_status='RESOLVED' and (
    old.owner_department_id is distinct from new.owner_department_id
    or old.owner_group_id is distinct from new.owner_group_id
    or old.scope_status is distinct from new.scope_status
    or old.scope_source_type is distinct from new.scope_source_type
    or old.scope_source_id is distinct from new.scope_source_id
    or old.scope_source_version is distinct from new.scope_source_version
    or old.scope_inherited_at is distinct from new.scope_inherited_at) then
  raise exception 'RS5_ISSUE_PRIMARY_SCOPE_IMMUTABLE: external/provider updates cannot rewrite OpenDispatch Issue ownership' using errcode='23514';
 end if;
 if old.scope_status='UNRESOLVED' and new.scope_status='RESOLVED' then
  if new.scope_source_type<>'TASK' or new.scope_source_id is distinct from new.task_id
     or (new.owner_department_id is null and new.owner_group_id is null) then
   raise exception 'RS5_ISSUE_SCOPE_REMEDIATION_INVALID: remediation must resolve from the canonical Task owner' using errcode='23514';
  end if;
 end if;
 return new;
end $$;
drop trigger if exists trg_rs5_issue_primary_scope_immutable on task_issue_links;
create trigger trg_rs5_issue_primary_scope_immutable
 before update of owner_department_id,owner_group_id,scope_status,scope_source_type,scope_source_id,scope_source_version,scope_inherited_at
 on task_issue_links for each row execute function rs5_guard_issue_primary_scope_immutable();

-- Requester/executor collaboration may change as Task assignment changes; invalidate cached Issue decisions when it does.
create or replace function rs5_touch_issue_operational_scope_epoch()
returns trigger language plpgsql as $$
declare actor varchar;
begin
 if old.requester_department_id is not distinct from new.requester_department_id
    and old.requester_group_id is not distinct from new.requester_group_id
    and old.executor_department_id is not distinct from new.executor_department_id
    and old.executor_group_id is not distinct from new.executor_group_id then return new; end if;
 actor=coalesce(nullif(current_setting('app.current_actor_id',true),''),'rs5-issue-operational-scope');
 insert into resource_security_epochs(tenant_id,resource_type,resource_id,resource_security_epoch,updated_at,updated_by)
 select new.tenant_id,'TASK_ISSUE_LINK',new.link_id,1,now(),actor
 where exists(select 1 from resource_descriptors d where d.tenant_id=new.tenant_id and d.resource_type='TASK_ISSUE_LINK' and d.resource_id=new.link_id)
 on conflict(tenant_id,resource_type,resource_id) do update
  set resource_security_epoch=resource_security_epochs.resource_security_epoch+1,updated_at=now(),updated_by=actor;
 return new;
end $$;
drop trigger if exists trg_rs5_issue_operational_scope_epoch on task_issue_links;
create trigger trg_rs5_issue_operational_scope_epoch
 after update of requester_department_id,requester_group_id,executor_department_id,executor_group_id
 on task_issue_links for each row execute function rs5_touch_issue_operational_scope_epoch();

-- Task collaboration remains operational rather than ownership authority. Keep Issue SQL-list evidence aligned
-- with Task requester/executor changes so List and Detail authorization cannot diverge after reassignment.
create or replace function rs5_propagate_task_collaboration_to_issues()
returns trigger language plpgsql as $$
begin
 if old.requester_department_id is not distinct from new.requester_department_id
    and old.requester_group_id is not distinct from new.requester_group_id
    and old.executor_department_id is not distinct from new.executor_department_id
    and old.executor_group_id is not distinct from new.executor_group_id then return new; end if;
 update task_issue_links set
  requester_department_id=nullif(new.requester_department_id,'UNASSIGNED'),
  requester_group_id=new.requester_group_id,
  executor_department_id=nullif(new.executor_department_id,'UNASSIGNED'),
  executor_group_id=new.executor_group_id,
  updated_at=now()
 where tenant_id=new.tenant_id and task_id=new.task_id and scope_status='RESOLVED';
 return new;
end $$;
drop trigger if exists trg_rs5_task_collaboration_to_issues on tasks;
create trigger trg_rs5_task_collaboration_to_issues
 after update of requester_department_id,requester_group_id,executor_department_id,executor_group_id
 on tasks for each row execute function rs5_propagate_task_collaboration_to_issues();

-- Existing Issue permissions are canonical. V80 retired permission_point_catalog in favor of the
-- revision-owned Permission Catalog plus its permission_definitions runtime projection. Do not mutate
-- a retired physical relation (or permission_definitions directly) before publication; revision 022 below
-- expands the allowed scope vocabulary and then projects the published revision into permission_definitions.

-- Permission Catalog revision 022 keeps the canonical catalog hash/version aligned with the RS5 scope vocabulary.
insert into permission_catalog_revisions(
 revision_id,revision_code,revision_number,status,content_hash,description,supersedes_revision_id,
 created_at,created_by,published_at,published_by,version)
select '00000000-0000-0000-0000-000000000022'::uuid,'RS5-ISSUE-EXTERNAL-TRACKING-SCOPE-0.8.2',coalesce(max(revision_number),0)+1,
 'DRAFT','DRAFT:UNPUBLISHED','RS5 Issue + External Issue Tracking scoped projection.',
 (select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE'),now(),'rs5-issue-external-tracking-scope',null,null,1
from permission_catalog_revisions on conflict(revision_id) do nothing;

insert into permission_catalog_revision_entries(
 revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,
 allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
select '00000000-0000-0000-0000-000000000022'::uuid,e.permission_code,e.owner_module,e.resource_type,e.action_code,e.description,
 e.risk_level,e.risk_lane,e.lifecycle,e.allowed_scope_types,e.system_managed,e.replacement_permission_code,e.introduced_at,e.deprecated_at,
 e.retired_at,now(),'rs5-issue-external-tracking-scope',1
from permission_catalog_revision_entries e join permission_catalog_active_revision a on a.singleton_id='ACTIVE' and a.revision_id=e.revision_id
on conflict(revision_id,permission_code) do nothing;

insert into permission_catalog_revision_aliases(
 revision_id,alias_code,canonical_permission_code,alias_type,valid_from,valid_until,reason,created_by,created_at,version)
select '00000000-0000-0000-0000-000000000022'::uuid,a.alias_code,a.canonical_permission_code,a.alias_type,a.valid_from,a.valid_until,a.reason,
 'rs5-issue-external-tracking-scope',now(),1
from permission_catalog_revision_aliases a join permission_catalog_active_revision active on active.singleton_id='ACTIVE' and active.revision_id=a.revision_id
on conflict(revision_id,alias_code) do nothing;

update permission_catalog_revision_entries set
 allowed_scope_types=array['TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP']::varchar[],
 updated_at=now(),updated_by='rs5-issue-external-tracking-scope',version=version+1
where revision_id='00000000-0000-0000-0000-000000000022'::uuid and permission_code in (
 'integration.issue.link.read','integration.issue.link.update','integration.issue.snapshot.read',
 'integration.issue.attachment.read','integration.issue.attachment.download','integration.issue.export',
 'integration.issue.mapping.read','integration.issue.mapping.update','integration.issue.conflict.read','integration.issue.conflict.resolve',
 'integration.issue.dead-letter.read','integration.issue.dead-letter.retry','integration.issue.topology.read','integration.issue.topology.update'
);

select set_config('app.permission_catalog_publish_revision_id','00000000-0000-0000-0000-000000000022',true);

insert into permission_definitions(
 permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,active,version,owner_module,risk_lane,lifecycle,
 catalog_revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by)
select permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,lifecycle<>'RETIRED',1,owner_module,risk_lane,lifecycle,
 revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by
from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000022'::uuid
on conflict(permission_code) do update set resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,
 risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,system_managed=excluded.system_managed,active=excluded.active,
 owner_module=excluded.owner_module,risk_lane=excluded.risk_lane,lifecycle=excluded.lifecycle,catalog_revision_id=excluded.catalog_revision_id,
 replacement_permission_code=excluded.replacement_permission_code,deprecated_at=excluded.deprecated_at,retired_at=excluded.retired_at,
 updated_at=excluded.updated_at,updated_by=excluded.updated_by,version=permission_definitions.version+1;

update permission_catalog_revisions set status='SUPERSEDED',version=version+1
where revision_id=(select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE')
 and revision_id<>'00000000-0000-0000-0000-000000000022'::uuid and status='PUBLISHED';

update permission_catalog_revisions set status='PUBLISHED',content_hash=(
 with catalog_lines as (
   select 'P|'||permission_code||'|'||owner_module||'|'||resource_type||'|'||action_code||'|'||description||'|'||risk_level||'|'||risk_lane||'|'||lifecycle||'|'||coalesce(array_to_string(allowed_scope_types,','),'')||'|'||system_managed::text||'|'||coalesce(replacement_permission_code,'') line
   from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000022'::uuid
   union all
   select 'A|'||alias_code||'|'||canonical_permission_code||'|'||alias_type||'|'||coalesce(to_char(valid_from at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||coalesce(to_char(valid_until at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||reason
   from permission_catalog_revision_aliases where revision_id='00000000-0000-0000-0000-000000000022'::uuid)
 select 'sha256:'||encode(sha256(convert_to(coalesce(string_agg(line,E'\n' order by line),''),'UTF8')),'hex') from catalog_lines),
 published_at=now(),published_by='rs5-issue-external-tracking-scope',version=version+1
where revision_id='00000000-0000-0000-0000-000000000022'::uuid and status='DRAFT';

update permission_catalog_active_revision set revision_id='00000000-0000-0000-0000-000000000022'::uuid,
 activated_at=now(),activated_by='rs5-issue-external-tracking-scope',version=version+1 where singleton_id='ACTIVE';

insert into permission_catalog_publication_events(publication_id,revision_id,previous_revision_id,content_hash,entry_count,alias_count,actor_id,audit_reason,correlation_id,published_at)
select '00000000-0000-0000-0000-000000005022'::uuid,r.revision_id,r.supersedes_revision_id,r.content_hash,
 (select count(*)::integer from permission_catalog_revision_entries e where e.revision_id=r.revision_id),
 (select count(*)::integer from permission_catalog_revision_aliases a where a.revision_id=r.revision_id),
 'rs5-issue-external-tracking-scope','RS5 Issue + External Issue Tracking scope publication','rs5-issue-external-tracking-scope',coalesce(r.published_at,now())
from permission_catalog_revisions r where r.revision_id='00000000-0000-0000-0000-000000000022'::uuid
on conflict(publication_id) do nothing;

-- Built-in Responsibility templates use the same permissions at different Binding Scopes; no Department-specific Roles are created.
insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'rs5-'||substr(md5(r.role_id||':'||p.permission_code),1,32),null,r.role_id,p.permission_code,now(),'rs5-issue-external-tracking-scope',1
from rbac_roles r join permission_definitions p on p.permission_code in('integration.issue.link.read','integration.issue.attachment.read','integration.issue.mapping.read') and p.active=true
where r.tenant_id is null and r.role_code in('VIEWER','AUDITOR','OPERATOR','DISPATCH_ADMIN','TENANT_ADMIN') and r.status='ACTIVE'
on conflict(role_id,permission_point) do nothing;

insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'rs5-'||substr(md5(r.role_id||':'||p.permission_code),1,32),null,r.role_id,p.permission_code,now(),'rs5-issue-external-tracking-scope',1
from rbac_roles r join permission_definitions p on p.permission_code in('integration.issue.link.update','integration.issue.attachment.download') and p.active=true
where r.tenant_id is null and r.role_code in('OPERATOR','DISPATCH_ADMIN','TENANT_ADMIN') and r.status='ACTIVE'
on conflict(role_id,permission_point) do nothing;

insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template) values
 ('RS5_ISSUE_SCOPE_UNRESOLVED',403,'AUTHORIZATION',false,'Issue scope cannot be resolved from its canonical Task and remains fail-closed.'),
 ('RS5_EXTERNAL_PROJECT_MAPPING_REQUIRED',403,'AUTHORIZATION',false,'The provider project is not mapped to an active OpenDispatch project mapping.'),
 ('RS5_EXTERNAL_PROJECT_MAPPING_AMBIGUOUS',409,'CONFIGURATION',false,'The provider project resolves to multiple active OpenDispatch project mappings.'),
 ('RS5_WEBHOOK_MAPPING_PRINCIPAL_MISMATCH',403,'AUTHORIZATION',false,'The authenticated webhook principal is not authorized for this project mapping.'),
 ('RS5_EXTERNAL_SCOPE_EXPANSION_BLOCKED',403,'AUTHORIZATION',false,'External provider state cannot expand the canonical OpenDispatch Issue scope.')
on conflict(reason_code) do update set http_status=excluded.http_status,category=excluded.category,retryable=excluded.retryable,message_template=excluded.message_template;

-- R3 entry-point evidence for changed/new RS5 controllers.
update permission_entry_point_inventory
set source_hash='216fc40d5af13456e4cdc41d09d915f61d12d0c8ab0249735c864c272c57e9b7',manifest_revision='rs5-issue-external-tracking-entrypoints-2026-08-12',last_verified_at=now(),updated_at=now(),updated_by='rs5-issue-external-tracking-scope',version=version+1
where source_ref like 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/IssueProjectionController.java#%';
update permission_entry_point_inventory
set source_hash='4de2bc7abced2b9feafb3b1f250e6a27f623addcaafb514c4934021c137ad4bd',manifest_revision='rs5-issue-external-tracking-entrypoints-2026-08-12',last_verified_at=now(),updated_at=now(),updated_by='rs5-issue-external-tracking-scope',version=version+1
where source_ref like 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/IssueProjectionStateController.java#%';
update permission_entry_point_inventory
set source_hash='5324df2df65eaad4f10aa9428bf64e125e38ff417884c9efd3aa0eae4e6ddc0f',manifest_revision='rs5-issue-external-tracking-entrypoints-2026-08-12',last_verified_at=now(),updated_at=now(),updated_by='rs5-issue-external-tracking-scope',version=version+1
where source_ref like 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/IssueRelayController.java#%';
update permission_entry_point_inventory
set source_hash='a9962572ede7862c2e7c4d9abc9dd3e619ae5849f5cd2fcf502d417b9a6a815a',manifest_revision='rs5-issue-external-tracking-entrypoints-2026-08-12',last_verified_at=now(),updated_at=now(),updated_by='rs5-issue-external-tracking-scope',version=version+1
where source_ref like 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProviderWebhookOperationsController.java#%';

insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,
 target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,
 migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values
('REST:GET:/api/issues','REST','control-plane-app','control-plane-app','IssueProjectionController.issues','/api/issues','GET','TARGET_ONLY','integration.issue.link.read',null,'[]'::jsonb,'TASK_ISSUE_LINK','R3_ROUTE_RESOURCE_RESOLVER',null,null,'rs5-issue-external-tracking-entrypoints-2026-08-12','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/IssueProjectionController.java#issues','216fc40d5af13456e4cdc41d09d915f61d12d0c8ab0249735c864c272c57e9b7',now(),'rs5-issue-external-tracking-scope','rs5-issue-external-tracking-scope'),
('REST:GET:/api/issues/{linkId}','REST','control-plane-app','control-plane-app','IssueProjectionController.issue','/api/issues/{linkId}','GET','TARGET_ONLY','integration.issue.link.read',null,'[]'::jsonb,'TASK_ISSUE_LINK','R3_PATH_RESOURCE_RESOLVER',null,null,'rs5-issue-external-tracking-entrypoints-2026-08-12','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/IssueProjectionController.java#issue','216fc40d5af13456e4cdc41d09d915f61d12d0c8ab0249735c864c272c57e9b7',now(),'rs5-issue-external-tracking-scope','rs5-issue-external-tracking-scope'),
('REST:GET:/api/issues/{linkId}/attachments','REST','control-plane-app','control-plane-app','IssueAttachmentController.list','/api/issues/{linkId}/attachments','GET','TARGET_ONLY','integration.issue.link.read',null,'[]'::jsonb,'TASK_ISSUE_LINK','R3_PATH_RESOURCE_RESOLVER',null,null,'rs5-issue-external-tracking-entrypoints-2026-08-12','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/IssueAttachmentController.java#list','1795d2f646815c58344cc25e889056981ec20a286c5771cd1fd376609f0bfd71',now(),'rs5-issue-external-tracking-scope','rs5-issue-external-tracking-scope'),
('REST:GET:/api/issues/{linkId}/attachments/{attachmentId}','REST','control-plane-app','control-plane-app','IssueAttachmentController.detail','/api/issues/{linkId}/attachments/{attachmentId}','GET','TARGET_ONLY','integration.issue.attachment.read',null,'[]'::jsonb,'ISSUE_ATTACHMENT','R3_PATH_RESOURCE_RESOLVER',null,null,'rs5-issue-external-tracking-entrypoints-2026-08-12','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/IssueAttachmentController.java#detail','1795d2f646815c58344cc25e889056981ec20a286c5771cd1fd376609f0bfd71',now(),'rs5-issue-external-tracking-scope','rs5-issue-external-tracking-scope'),
('REST:POST:/api/issues/{linkId}/attachments/{attachmentId}/download','REST','control-plane-app','control-plane-app','IssueAttachmentController.download','/api/issues/{linkId}/attachments/{attachmentId}/download','POST','TARGET_ONLY','integration.issue.attachment.download',null,'[]'::jsonb,'ISSUE_ATTACHMENT','R3_PATH_RESOURCE_RESOLVER',null,null,'rs5-issue-external-tracking-entrypoints-2026-08-12','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/IssueAttachmentController.java#download','1795d2f646815c58344cc25e889056981ec20a286c5771cd1fd376609f0bfd71',now(),'rs5-issue-external-tracking-scope','rs5-issue-external-tracking-scope')
on conflict(entry_point_id) do update set display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,
 authority_state=excluded.authority_state,target_permission_code=excluded.target_permission_code,legacy_authority_type=null,legacy_authorities='[]'::jsonb,
 resource_type=excluded.resource_type,resource_resolver_id=excluded.resource_resolver_id,exemption_reason=null,migration_deadline=null,
 manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,source_hash=excluded.source_hash,last_verified_at=now(),updated_at=now(),
 updated_by='rs5-issue-external-tracking-scope',version=permission_entry_point_inventory.version+1;
