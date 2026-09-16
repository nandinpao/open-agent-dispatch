-- P2.3B: canonical Tenant / Department / Group scoped business-data authorization.
-- This migration does not grant access. It adds ownership metadata and publishes the scope types
-- that Role Bindings may explicitly choose for Source System / Dispatch / A2A policy administration.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','p23b-scoped-resource-authorization',true);

-- ---------------------------------------------------------------------------
-- Business resource ownership. Existing rows remain Tenant-owned (null/null).
-- Dispatch Flow and Agent Pool inherit the Source System owner when one exists.
-- ---------------------------------------------------------------------------
alter table source_systems add column if not exists owner_department_id varchar(128);
alter table source_systems add column if not exists owner_group_id varchar(128);
alter table dispatch_flows add column if not exists owner_department_id varchar(128);
alter table dispatch_flows add column if not exists owner_group_id varchar(128);
alter table agent_pools add column if not exists owner_department_id varchar(128);
alter table agent_pools add column if not exists owner_group_id varchar(128);

update dispatch_flows f
set owner_department_id=s.owner_department_id, owner_group_id=s.owner_group_id
from source_systems s
where s.tenant_id=f.tenant_id and s.source_system_id=f.source_system
  and (f.owner_department_id is null and f.owner_group_id is null)
  and (s.owner_department_id is not null or s.owner_group_id is not null);

update agent_pools p
set owner_department_id=s.owner_department_id, owner_group_id=s.owner_group_id
from source_systems s
where s.tenant_id=p.tenant_id and s.source_system_id=p.source_system
  and (p.owner_department_id is null and p.owner_group_id is null)
  and (s.owner_department_id is not null or s.owner_group_id is not null);

create index if not exists idx_source_systems_owner_department on source_systems(tenant_id,owner_department_id,status,updated_at desc);
create index if not exists idx_source_systems_owner_group on source_systems(tenant_id,owner_group_id,status,updated_at desc);
create index if not exists idx_dispatch_flows_owner_department on dispatch_flows(tenant_id,owner_department_id,status,updated_at desc);
create index if not exists idx_dispatch_flows_owner_group on dispatch_flows(tenant_id,owner_group_id,status,updated_at desc);
create index if not exists idx_agent_pools_owner_department on agent_pools(tenant_id,owner_department_id,status,updated_at desc);
create index if not exists idx_agent_pools_owner_group on agent_pools(tenant_id,owner_group_id,status,updated_at desc);

do $$
begin
  if not exists(select 1 from pg_constraint where conname='fk_source_system_owner_department') then
    alter table source_systems add constraint fk_source_system_owner_department
      foreign key(tenant_id,owner_department_id) references departments(tenant_id,department_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_source_system_owner_group') then
    alter table source_systems add constraint fk_source_system_owner_group
      foreign key(tenant_id,owner_group_id) references organization_groups(tenant_id,group_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_dispatch_flow_owner_department') then
    alter table dispatch_flows add constraint fk_dispatch_flow_owner_department
      foreign key(tenant_id,owner_department_id) references departments(tenant_id,department_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_dispatch_flow_owner_group') then
    alter table dispatch_flows add constraint fk_dispatch_flow_owner_group
      foreign key(tenant_id,owner_group_id) references organization_groups(tenant_id,group_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_agent_pool_owner_department') then
    alter table agent_pools add constraint fk_agent_pool_owner_department
      foreign key(tenant_id,owner_department_id) references departments(tenant_id,department_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_agent_pool_owner_group') then
    alter table agent_pools add constraint fk_agent_pool_owner_group
      foreign key(tenant_id,owner_group_id) references organization_groups(tenant_id,group_id) not valid;
  end if;
end $$;

alter table source_systems validate constraint fk_source_system_owner_department;
alter table source_systems validate constraint fk_source_system_owner_group;
alter table dispatch_flows validate constraint fk_dispatch_flow_owner_department;
alter table dispatch_flows validate constraint fk_dispatch_flow_owner_group;
alter table agent_pools validate constraint fk_agent_pool_owner_department;
alter table agent_pools validate constraint fk_agent_pool_owner_group;

comment on column source_systems.owner_department_id is 'P2.3B canonical Department data owner; null with owner_group_id null means Tenant-owned.';
comment on column source_systems.owner_group_id is 'P2.3B canonical Group data owner.';
comment on column dispatch_flows.owner_department_id is 'Inherited Source System Department owner used for SQL/object authorization.';
comment on column dispatch_flows.owner_group_id is 'Inherited Source System Group owner used for SQL/object authorization.';
comment on column agent_pools.owner_department_id is 'Inherited Source System Department owner used for SQL/object authorization.';
comment on column agent_pools.owner_group_id is 'Inherited Source System Group owner used for SQL/object authorization.';

-- Organization lifecycle cannot leave active business resources bound to disabled/deleted scopes.
create or replace function p23b_guard_department_resource_scope_status()
returns trigger language plpgsql security invoker as $$
begin
  if new.status in ('DISABLED','DELETED') and old.status is distinct from new.status then
    if exists (
      select 1 from source_systems s
      where s.tenant_id=new.tenant_id and s.owner_department_id=new.department_id and s.status<>'RETIRED'
    ) or exists (
      select 1 from dispatch_flows f
      where f.tenant_id=new.tenant_id and f.owner_department_id=new.department_id and f.status<>'RETIRED'
    ) or exists (
      select 1 from agent_pools p
      where p.tenant_id=new.tenant_id and p.owner_department_id=new.department_id and p.status<>'RETIRED'
    ) then
      if new.status='DELETED' then
        raise exception 'DEPARTMENT_DELETE_BLOCKED: re-scope active Source Systems, Dispatch Flows, and Agent Pools first' using errcode='23514';
      else
        raise exception 'DEPARTMENT_DISABLE_BLOCKED_BY_RESOURCES' using errcode='23514';
      end if;
    end if;
    if exists (
      select 1 from a2a_policies a
      where a.tenant_id=new.tenant_id
        and (a.source_department_id=new.department_id or a.target_department_id=new.department_id)
    ) then
      if new.status='DELETED' then
        raise exception 'DEPARTMENT_DELETE_BLOCKED: re-scope A2A Policies first' using errcode='23514';
      else
        raise exception 'DEPARTMENT_DISABLE_BLOCKED_BY_A2A_POLICIES' using errcode='23514';
      end if;
    end if;
  end if;
  return new;
end $$;

drop trigger if exists trg_p23b_guard_department_resource_scope_status on departments;
create trigger trg_p23b_guard_department_resource_scope_status
before update of status on departments
for each row execute function p23b_guard_department_resource_scope_status();

create or replace function p23b_guard_group_resource_scope_status()
returns trigger language plpgsql security invoker as $$
begin
  if new.status in ('DISABLED','DELETED') and old.status is distinct from new.status then
    if exists (
      select 1 from source_systems s
      where s.tenant_id=new.tenant_id and s.owner_group_id=new.group_id and s.status<>'RETIRED'
    ) or exists (
      select 1 from dispatch_flows f
      where f.tenant_id=new.tenant_id and f.owner_group_id=new.group_id and f.status<>'RETIRED'
    ) or exists (
      select 1 from agent_pools p
      where p.tenant_id=new.tenant_id and p.owner_group_id=new.group_id and p.status<>'RETIRED'
    ) then
      if new.status='DELETED' then
        raise exception 'GROUP_DELETE_BLOCKED: re-scope active Source Systems, Dispatch Flows, and Agent Pools first' using errcode='23514';
      else
        raise exception 'GROUP_DISABLE_BLOCKED_BY_RESOURCES' using errcode='23514';
      end if;
    end if;
    if exists (
      select 1 from a2a_policies a
      where a.tenant_id=new.tenant_id
        and (a.source_group_id=new.group_id or a.target_group_id=new.group_id)
    ) then
      if new.status='DELETED' then
        raise exception 'GROUP_DELETE_BLOCKED: re-scope A2A Policies first' using errcode='23514';
      else
        raise exception 'GROUP_DISABLE_BLOCKED_BY_A2A_POLICIES' using errcode='23514';
      end if;
    end if;
  end if;
  return new;
end $$;

drop trigger if exists trg_p23b_guard_group_resource_scope_status on organization_groups;
create trigger trg_p23b_guard_group_resource_scope_status
before update of status on organization_groups
for each row execute function p23b_guard_group_resource_scope_status();

-- ---------------------------------------------------------------------------
-- Resource Access catalog: domain authorities remain canonical; Resource Access only resolves them.
-- ---------------------------------------------------------------------------
insert into resource_catalog(resource_type,category,descriptor_authority,ownership_supported,participants_supported,
  field_visibility_supported,runtime_lease_supported,default_sensitivity,description)
values
 ('SOURCE_SYSTEM','CONFIGURATION','DISPATCH_CONFIGURATION',true,true,true,false,'INTERNAL','Source System business configuration'),
 ('DISPATCH_FLOW','CONFIGURATION','DISPATCH_CONFIGURATION',true,true,true,false,'INTERNAL','Source Flow / Dispatch Flow configuration'),
 ('A2A_POLICY','CONFIGURATION','A2A_DOMAIN',true,true,true,false,'CONFIDENTIAL','Directional A2A governance policy')
on conflict(resource_type) do update set
 category=excluded.category,descriptor_authority=excluded.descriptor_authority,
 ownership_supported=excluded.ownership_supported,participants_supported=excluded.participants_supported,
 field_visibility_supported=excluded.field_visibility_supported,runtime_lease_supported=excluded.runtime_lease_supported,
 default_sensitivity=excluded.default_sensitivity,description=excluded.description,status='ACTIVE',
 catalog_version=resource_catalog.catalog_version+1,updated_at=now();

-- ---------------------------------------------------------------------------
-- Permission Catalog revision 018: publish organizational scopes for business data.
-- RESOURCE remains a Resource Access grant/deny scope, not an IAM Role Binding scope.
-- ---------------------------------------------------------------------------
insert into permission_catalog_revisions(
  revision_id,revision_code,revision_number,status,content_hash,description,supersedes_revision_id,
  created_at,created_by,published_at,published_by,version)
select '00000000-0000-0000-0000-000000000018'::uuid,'P23B-SCOPED-DATA-0.8.2',coalesce(max(revision_number),0)+1,
  'DRAFT','DRAFT:UNPUBLISHED','P2.3B Source System, Dispatch and A2A policy organizational data scopes.',
  (select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE'),
  now(),'p23b-scoped-resource-authorization',null,null,1
from permission_catalog_revisions
on conflict(revision_id) do nothing;

insert into permission_catalog_revision_entries(
  revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,
  lifecycle,allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,
  retired_at,updated_at,updated_by,version)
select '00000000-0000-0000-0000-000000000018'::uuid,e.permission_code,e.owner_module,e.resource_type,
  e.action_code,e.description,e.risk_level,e.risk_lane,e.lifecycle,e.allowed_scope_types,e.system_managed,
  e.replacement_permission_code,e.introduced_at,e.deprecated_at,e.retired_at,now(),'p23b-scoped-resource-authorization',1
from permission_catalog_revision_entries e
join permission_catalog_active_revision a on a.singleton_id='ACTIVE' and a.revision_id=e.revision_id
on conflict(revision_id,permission_code) do nothing;

insert into permission_catalog_revision_aliases(
  revision_id,alias_code,canonical_permission_code,alias_type,valid_from,valid_until,reason,created_by,created_at,version)
select '00000000-0000-0000-0000-000000000018'::uuid,a.alias_code,a.canonical_permission_code,a.alias_type,
  a.valid_from,a.valid_until,a.reason,'p23b-scoped-resource-authorization',now(),1
from permission_catalog_revision_aliases a
join permission_catalog_active_revision active on active.singleton_id='ACTIVE' and active.revision_id=a.revision_id
on conflict(revision_id,alias_code) do nothing;

update permission_catalog_revision_entries
set allowed_scope_types=array['TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP']::varchar[],
    updated_at=now(),updated_by='p23b-scoped-resource-authorization',version=version+1
where revision_id='00000000-0000-0000-0000-000000000018'::uuid
  and (permission_code='source_system.manage'
       or permission_code like 'admin.source.system.%'
       or permission_code='a2a.manage_policy'
       or permission_code in ('api.a2.agovernance.policies','api.a2.agovernance.policy','api.a2.agovernance.upsert.policy')
       or permission_code like 'admin.dispatch.flow.%');

select set_config('app.permission_catalog_publish_revision_id','00000000-0000-0000-0000-000000000018',true);

insert into permission_definitions(
  permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,
  active,version,owner_module,risk_lane,lifecycle,catalog_revision_id,replacement_permission_code,
  introduced_at,deprecated_at,retired_at,updated_at,updated_by)
select permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,
  lifecycle<>'RETIRED',1,owner_module,risk_lane,lifecycle,revision_id,replacement_permission_code,
  introduced_at,deprecated_at,retired_at,updated_at,updated_by
from permission_catalog_revision_entries
where revision_id='00000000-0000-0000-0000-000000000018'::uuid
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
  and revision_id<>'00000000-0000-0000-0000-000000000018'::uuid and status='PUBLISHED';

update permission_catalog_revisions set status='PUBLISHED',content_hash=(
  with catalog_lines as (
    select 'P|'||permission_code||'|'||owner_module||'|'||resource_type||'|'||action_code||'|'||description||'|'||
      risk_level||'|'||risk_lane||'|'||lifecycle||'|'||coalesce(array_to_string(allowed_scope_types,','),'')||'|'||
      system_managed::text||'|'||coalesce(replacement_permission_code,'') as line
    from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000018'::uuid
    union all
    select 'A|'||alias_code||'|'||canonical_permission_code||'|'||alias_type||'|'||
      coalesce(to_char(valid_from at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||
      coalesce(to_char(valid_until at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||reason
    from permission_catalog_revision_aliases where revision_id='00000000-0000-0000-0000-000000000018'::uuid)
  select 'sha256:'||encode(sha256(convert_to(coalesce(string_agg(line,E'\n' order by line),''),'UTF8')),'hex') from catalog_lines),
  published_at=now(),published_by='p23b-scoped-resource-authorization',version=version+1
where revision_id='00000000-0000-0000-0000-000000000018'::uuid and status='DRAFT';

update permission_catalog_active_revision
set revision_id='00000000-0000-0000-0000-000000000018'::uuid,
    activated_at=now(),activated_by='p23b-scoped-resource-authorization',version=version+1
where singleton_id='ACTIVE';

insert into permission_catalog_publication_events(
  publication_id,revision_id,previous_revision_id,content_hash,entry_count,alias_count,
  actor_id,audit_reason,correlation_id,published_at)
select '00000000-0000-0000-0000-000000005018'::uuid,r.revision_id,r.supersedes_revision_id,r.content_hash,
  (select count(*)::integer from permission_catalog_revision_entries e where e.revision_id=r.revision_id),
  (select count(*)::integer from permission_catalog_revision_aliases a where a.revision_id=r.revision_id),
  'p23b-scoped-resource-authorization','P2.3B scoped business data authorization publication',
  'p23b-scoped-resource-authorization',coalesce(r.published_at,now())
from permission_catalog_revisions r
where r.revision_id='00000000-0000-0000-0000-000000000018'::uuid
on conflict(publication_id) do nothing;
