-- RS4: Dispatch + Task cross-scope authorization.
-- Task primary ownership is an origin snapshot; assignment adds executor operational scope without changing ownership.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','rs4-dispatch-task-scope',true);

alter table tasks add column if not exists origin_scope_status varchar(32) not null default 'UNRESOLVED';
alter table tasks add column if not exists origin_scope_source_version bigint;
alter table tasks add column if not exists origin_scope_inherited_at timestamptz;

-- Prefer RS2 Incident scope evidence for existing Tasks. If older Task ownership is already explicit,
-- preserve it as a deterministic legacy origin snapshot instead of widening to Tenant.
update tasks t set
  owner_department_id=coalesce(nullif(i.owner_department_id,''),t.owner_department_id,'UNASSIGNED'),
  owner_group_id=coalesce(i.owner_group_id,t.owner_group_id),
  requester_department_id=coalesce(nullif(i.owner_department_id,''),t.requester_department_id,t.owner_department_id,'UNASSIGNED'),
  requester_group_id=coalesce(i.owner_group_id,t.requester_group_id,t.owner_group_id),
  origin_scope_status=case when i.scope_status='RESOLVED' then 'RESOLVED' else t.origin_scope_status end,
  origin_scope_source_version=coalesce(i.scope_source_version,t.origin_scope_source_version),
  origin_scope_inherited_at=coalesce(i.scope_inherited_at,t.origin_scope_inherited_at,t.created_at)
from incidents i where i.tenant_id=t.tenant_id and i.incident_id=t.incident_id;

update tasks set
 origin_scope_status=case when coalesce(owner_department_id,'UNASSIGNED')<>'UNASSIGNED' or owner_group_id is not null then 'RESOLVED' else 'UNRESOLVED' end,
 origin_scope_inherited_at=coalesce(origin_scope_inherited_at,created_at),
 visibility_policy=case when coalesce(owner_department_id,'UNASSIGNED')='UNASSIGNED' and owner_group_id is null then 'RESTRICTED' else visibility_policy end
where origin_scope_status is null or origin_scope_status='UNRESOLVED';

-- Backfill the canonical execution scope from the latest active assignment. This is an operational
-- collaboration scope only: it never rewrites the immutable Task origin owner.
with current_assignment as (
  select distinct on (a.tenant_id,a.task_id)
         a.tenant_id,a.task_id,a.agent_id,a.created_at,a.assignment_id,
         nullif(p.owner_department_id,'') owner_department_id,p.owner_group_id
    from task_assignments a
    left join agent_profiles p on p.tenant_id=a.tenant_id and p.agent_id=a.agent_id
   where a.status in ('ASSIGNED','AWAITING_REVIEW')
   order by a.tenant_id,a.task_id,a.created_at desc,a.assignment_id desc
)
update tasks t set
 executor_department_id=coalesce(c.owner_department_id,t.executor_department_id),
 executor_group_id=coalesce(c.owner_group_id,t.executor_group_id)
from current_assignment c
where c.tenant_id=t.tenant_id and c.task_id=t.task_id
  and (c.owner_department_id is not null or c.owner_group_id is not null);

-- Domain participant evidence is the authority for Resource Access projection. Historical active
-- assignments receive the same AGENT/EXECUTOR evidence that new RS4 assignments create in Java.
with current_assignment as (
  select distinct on (a.tenant_id,a.task_id)
         a.tenant_id,a.task_id,a.agent_id,a.created_at,a.assignment_id,
         nullif(p.owner_department_id,'') owner_department_id,p.owner_group_id
    from task_assignments a
    left join agent_profiles p on p.tenant_id=a.tenant_id and p.agent_id=a.agent_id
   where a.status in ('ASSIGNED','AWAITING_REVIEW')
   order by a.tenant_id,a.task_id,a.created_at desc,a.assignment_id desc
)
insert into task_participants(
 tenant_id,participant_id,task_id,participant_type,participant_ref_id,participant_role,
 visibility_level,operation_level,created_at,created_by,version)
select c.tenant_id,'rs4-agent-'||substr(md5(c.task_id||':'||c.agent_id),1,32),c.task_id,'AGENT',c.agent_id,'EXECUTOR',
       'FULL','OPERATE',coalesce(c.created_at,now()),'RS4_MIGRATION_BACKFILL',1
from current_assignment c
where (c.owner_department_id is not null or c.owner_group_id is not null)
  and not exists (
    select 1 from task_participants tp
     where tp.tenant_id=c.tenant_id and tp.task_id=c.task_id and tp.participant_type='AGENT'
       and tp.participant_ref_id=c.agent_id and tp.participant_role='EXECUTOR'
  );

create index if not exists idx_tasks_rs4_origin_scope on tasks(tenant_id,origin_scope_status,owner_department_id,owner_group_id,created_at desc);
create index if not exists idx_dispatch_requests_rs4_task_scope on dispatch_requests(tenant_id,task_id,status,updated_at desc);

create or replace function rs4_guard_task_origin_scope_immutable()
returns trigger language plpgsql as $$
begin
  if old.origin_scope_status is distinct from new.origin_scope_status
     or old.origin_scope_source_version is distinct from new.origin_scope_source_version
     or old.origin_scope_inherited_at is distinct from new.origin_scope_inherited_at then
    raise exception 'RS4_TASK_ORIGIN_SCOPE_IMMUTABLE: create governed ownership transfer evidence instead of rewriting origin provenance' using errcode='23514';
  end if;
  return new;
end $$;
drop trigger if exists trg_rs4_task_origin_scope_immutable on tasks;
create trigger trg_rs4_task_origin_scope_immutable before update of origin_scope_status,origin_scope_source_version,origin_scope_inherited_at on tasks
for each row execute function rs4_guard_task_origin_scope_immutable();

-- Assignment/reassignment changes executor scope and must invalidate cached Task decisions immediately.
create or replace function rs4_touch_task_execution_scope_epoch()
returns trigger language plpgsql as $$
declare actor varchar;
begin
  if old.executor_department_id is not distinct from new.executor_department_id
     and old.executor_group_id is not distinct from new.executor_group_id
     and old.executor_domain_id is not distinct from new.executor_domain_id then return new; end if;
  actor=coalesce(nullif(current_setting('app.current_actor_id',true),''),'rs4-task-executor-scope');
  insert into resource_security_epochs(tenant_id,resource_type,resource_id,resource_security_epoch,updated_at,updated_by)
  select new.tenant_id,'TASK',new.task_id,1,now(),actor
  where exists (
    select 1 from resource_descriptors d
    where d.tenant_id=new.tenant_id and d.resource_type='TASK' and d.resource_id=new.task_id
  )
  on conflict(tenant_id,resource_type,resource_id) do update
    set resource_security_epoch=resource_security_epochs.resource_security_epoch+1,updated_at=now(),updated_by=actor;
  return new;
end $$;
drop trigger if exists trg_rs4_task_execution_scope_epoch on tasks;
create trigger trg_rs4_task_execution_scope_epoch after update of executor_department_id,executor_group_id,executor_domain_id on tasks
for each row execute function rs4_touch_task_execution_scope_epoch();

-- Permission Catalog revision 021.
insert into permission_catalog_revisions(
 revision_id,revision_code,revision_number,status,content_hash,description,supersedes_revision_id,
 created_at,created_by,published_at,published_by,version)
select '00000000-0000-0000-0000-000000000021'::uuid,'RS4-DISPATCH-TASK-CROSS-SCOPE-0.8.2',coalesce(max(revision_number),0)+1,
 'DRAFT','DRAFT:UNPUBLISHED','RS4 Dispatch + Task cross-scope authorization.',
 (select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE'),now(),'rs4-dispatch-task-scope',null,null,1
from permission_catalog_revisions on conflict(revision_id) do nothing;

insert into permission_catalog_revision_entries(
 revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,
 allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
select '00000000-0000-0000-0000-000000000021'::uuid,e.permission_code,e.owner_module,e.resource_type,e.action_code,e.description,
 e.risk_level,e.risk_lane,e.lifecycle,e.allowed_scope_types,e.system_managed,e.replacement_permission_code,e.introduced_at,e.deprecated_at,
 e.retired_at,now(),'rs4-dispatch-task-scope',1
from permission_catalog_revision_entries e join permission_catalog_active_revision a on a.singleton_id='ACTIVE' and a.revision_id=e.revision_id
on conflict(revision_id,permission_code) do nothing;

insert into permission_catalog_revision_aliases(
 revision_id,alias_code,canonical_permission_code,alias_type,valid_from,valid_until,reason,created_by,created_at,version)
select '00000000-0000-0000-0000-000000000021'::uuid,a.alias_code,a.canonical_permission_code,a.alias_type,a.valid_from,a.valid_until,a.reason,
 'rs4-dispatch-task-scope',now(),1
from permission_catalog_revision_aliases a join permission_catalog_active_revision active on active.singleton_id='ACTIVE' and active.revision_id=a.revision_id
on conflict(revision_id,alias_code) do nothing;

insert into permission_catalog_revision_entries(
 revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,
 allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
values
 ('00000000-0000-0000-0000-000000000021'::uuid,'task.read','task-orchestration','TASK','READ','Read Task metadata inside effective Resource Scope.','MEDIUM','READ','ACTIVE',array['TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP']::varchar[],true,null,now(),null,null,now(),'rs4-dispatch-task-scope',1),
 ('00000000-0000-0000-0000-000000000021'::uuid,'task.update','task-orchestration','TASK','UPDATE','Update a Task inside effective Resource Scope.','HIGH','WRITE','ACTIVE',array['TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP']::varchar[],true,null,now(),null,null,now(),'rs4-dispatch-task-scope',1),
 ('00000000-0000-0000-0000-000000000021'::uuid,'task.approve','execution-control','TASK','APPROVE','Approve or reject a Dispatch action linked to an authorized Task.','HIGH','WRITE','ACTIVE',array['TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP']::varchar[],true,null,now(),null,null,now(),'rs4-dispatch-task-scope',1),
 ('00000000-0000-0000-0000-000000000021'::uuid,'task.execute','execution-control','TASK','EXECUTE','Execute/retry a Dispatch action or Agent runtime operation for an authorized Task.','CRITICAL','CRITICAL','ACTIVE',array['TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP']::varchar[],true,null,now(),null,null,now(),'rs4-dispatch-task-scope',1),
 ('00000000-0000-0000-0000-000000000021'::uuid,'admin.dispatch.cross.scope.manage','control-plane-app','AGENT_POOL','CROSS_SCOPE_MANAGE','Create or change Dispatch targets whose governed Agent owner is outside the Source owner scope.','CRITICAL','CRITICAL','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'rs4-dispatch-task-scope',1)
on conflict(revision_id,permission_code) do update set description=excluded.description,risk_level=excluded.risk_level,risk_lane=excluded.risk_lane,
 allowed_scope_types=excluded.allowed_scope_types,updated_at=excluded.updated_at,updated_by=excluded.updated_by,version=permission_catalog_revision_entries.version+1;

select set_config('app.permission_catalog_publish_revision_id','00000000-0000-0000-0000-000000000021',true);

insert into permission_definitions(
 permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,active,version,owner_module,risk_lane,lifecycle,
 catalog_revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by)
select permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,lifecycle<>'RETIRED',1,owner_module,risk_lane,lifecycle,
 revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by
from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000021'::uuid
on conflict(permission_code) do update set resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,
 risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,system_managed=excluded.system_managed,active=excluded.active,
 owner_module=excluded.owner_module,risk_lane=excluded.risk_lane,lifecycle=excluded.lifecycle,catalog_revision_id=excluded.catalog_revision_id,
 replacement_permission_code=excluded.replacement_permission_code,deprecated_at=excluded.deprecated_at,retired_at=excluded.retired_at,
 updated_at=excluded.updated_at,updated_by=excluded.updated_by,version=permission_definitions.version+1;

update permission_catalog_revisions set status='SUPERSEDED',version=version+1
where revision_id=(select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE')
 and revision_id<>'00000000-0000-0000-0000-000000000021'::uuid and status='PUBLISHED';

update permission_catalog_revisions set status='PUBLISHED',content_hash=(
 with catalog_lines as (
   select 'P|'||permission_code||'|'||owner_module||'|'||resource_type||'|'||action_code||'|'||description||'|'||risk_level||'|'||risk_lane||'|'||lifecycle||'|'||coalesce(array_to_string(allowed_scope_types,','),'')||'|'||system_managed::text||'|'||coalesce(replacement_permission_code,'') line
   from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000021'::uuid
   union all
   select 'A|'||alias_code||'|'||canonical_permission_code||'|'||alias_type||'|'||coalesce(to_char(valid_from at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||coalesce(to_char(valid_until at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||reason
   from permission_catalog_revision_aliases where revision_id='00000000-0000-0000-0000-000000000021'::uuid)
 select 'sha256:'||encode(sha256(convert_to(coalesce(string_agg(line,E'\n' order by line),''),'UTF8')),'hex') from catalog_lines),
 published_at=now(),published_by='rs4-dispatch-task-scope',version=version+1
where revision_id='00000000-0000-0000-0000-000000000021'::uuid and status='DRAFT';

update permission_catalog_active_revision set revision_id='00000000-0000-0000-0000-000000000021'::uuid,
 activated_at=now(),activated_by='rs4-dispatch-task-scope',version=version+1 where singleton_id='ACTIVE';

insert into permission_catalog_publication_events(publication_id,revision_id,previous_revision_id,content_hash,entry_count,alias_count,actor_id,audit_reason,correlation_id,published_at)
select '00000000-0000-0000-0000-000000005021'::uuid,r.revision_id,r.supersedes_revision_id,r.content_hash,
 (select count(*)::integer from permission_catalog_revision_entries e where e.revision_id=r.revision_id),
 (select count(*)::integer from permission_catalog_revision_aliases a where a.revision_id=r.revision_id),
 'rs4-dispatch-task-scope','RS4 Dispatch + Task cross-scope authorization publication','rs4-dispatch-task-scope',coalesce(r.published_at,now())
from permission_catalog_revisions r where r.revision_id='00000000-0000-0000-0000-000000000021'::uuid
on conflict(publication_id) do nothing;

-- Responsibility templates: Viewer/Auditor read; Operator operates within binding scope; Dispatch Admin owns review/config.
insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'rs4-'||substr(md5(r.role_id||':'||p.permission_code),1,32),null,r.role_id,p.permission_code,now(),'rs4-dispatch-task-scope',1
from rbac_roles r join permission_definitions p on p.permission_code='task.read' and p.active=true
where r.tenant_id is null and r.role_code in('VIEWER','AUDITOR','OPERATOR','DISPATCH_ADMIN','TENANT_ADMIN') and r.status='ACTIVE'
on conflict(role_id,permission_point) do nothing;

insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'rs4-'||substr(md5(r.role_id||':'||p.permission_code),1,32),null,r.role_id,p.permission_code,now(),'rs4-dispatch-task-scope',1
from rbac_roles r join permission_definitions p on p.permission_code in('task.update','task.execute') and p.active=true
where r.tenant_id is null and r.role_code in('OPERATOR','DISPATCH_ADMIN','TENANT_ADMIN') and r.status='ACTIVE'
on conflict(role_id,permission_point) do nothing;

insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'rs4-'||substr(md5(r.role_id||':'||p.permission_code),1,32),null,r.role_id,p.permission_code,now(),'rs4-dispatch-task-scope',1
from rbac_roles r join permission_definitions p on p.permission_code in('task.approve','admin.dispatch.cross.scope.manage') and p.active=true
where r.tenant_id is null and r.role_code in('DISPATCH_ADMIN','TENANT_ADMIN') and r.status='ACTIVE'
on conflict(role_id,permission_point) do nothing;

insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template) values
 ('RS4_TASK_ORIGIN_SCOPE_UNRESOLVED',403,'AUTHORIZATION',false,'Task origin scope is unresolved and cannot be widened to Tenant.'),
 ('RS4_AGENT_SERVICE_TASK_SCOPE_NOT_MATCHED',403,'AUTHORIZATION',false,'Agent service principal is outside the governed Task execution scope.'),
 ('RS4_CROSS_SCOPE_DISPATCH_AUTHORITY_REQUIRED',403,'AUTHORIZATION',false,'Cross-scope Dispatch requires Tenant-wide cross-scope authority.')
on conflict(reason_code) do update set http_status=excluded.http_status,category=excluded.category,retryable=excluded.retryable,message_template=excluded.message_template;
