-- RS6: A2A Governance scoped authorization.
-- A2A Policy is the canonical governed relationship; participant/capability data never becomes a parallel ACL.

-- Flyway runs outside a Human Tenant transaction. Establish an explicit Instance migration context
-- before touching FORCE-RLS IAM tables. Tenant-specific legacy Role grants are projected later
-- one Tenant at a time without disabling RLS.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','rs6-a2a-governance-scope',true);

alter table a2a_policies
  add column if not exists governance_owner_department_id varchar(128),
  add column if not exists governance_owner_group_id varchar(128),
  add column if not exists governance_scope_class varchar(32) not null default 'TENANT',
  add column if not exists governance_scope_status varchar(24) not null default 'RESOLVED';

create or replace function rs6_classify_a2a_policy_scope() returns trigger language plpgsql as $$
declare
 sd varchar(128); sg varchar(128); td varchar(128); tg varchar(128);
begin
 sd:=nullif(nullif(btrim(coalesce(new.source_department_id,'')),''),'UNASSIGNED');
 sg:=nullif(nullif(btrim(coalesce(new.source_group_id,'')),''),'UNASSIGNED');
 td:=nullif(nullif(btrim(coalesce(new.target_department_id,'')),''),'UNASSIGNED');
 tg:=nullif(nullif(btrim(coalesce(new.target_group_id,'')),''),'UNASSIGNED');
 new.governance_owner_department_id:=coalesce(sd,td);
 new.governance_owner_group_id:=coalesce(sg,tg);
 if sd is null and sg is null and td is null and tg is null then
   new.governance_scope_class:='TENANT';
 elsif (sd is not null or sg is not null) and (td is not null or tg is not null)
       and sd is not distinct from td and sg is not distinct from tg then
   new.governance_scope_class:='SAME_SCOPE';
 else
   new.governance_scope_class:='CROSS_SCOPE';
 end if;
 new.governance_scope_status:='RESOLVED';
 return new;
end $$;

drop trigger if exists trg_rs6_classify_a2a_policy_scope on a2a_policies;
create trigger trg_rs6_classify_a2a_policy_scope
 before insert or update of source_department_id,source_group_id,target_department_id,target_group_id
 on a2a_policies for each row execute function rs6_classify_a2a_policy_scope();

-- Deterministic backfill for existing policies.
update a2a_policies set
 governance_owner_department_id=coalesce(nullif(source_department_id,'UNASSIGNED'),nullif(target_department_id,'UNASSIGNED')),
 governance_owner_group_id=coalesce(nullif(source_group_id,'UNASSIGNED'),nullif(target_group_id,'UNASSIGNED')),
 governance_scope_class=case
   when nullif(source_department_id,'UNASSIGNED') is null and source_group_id is null
    and nullif(target_department_id,'UNASSIGNED') is null and target_group_id is null then 'TENANT'
   when (nullif(source_department_id,'UNASSIGNED') is not null or source_group_id is not null)
    and (nullif(target_department_id,'UNASSIGNED') is not null or target_group_id is not null)
    and nullif(source_department_id,'UNASSIGNED') is not distinct from nullif(target_department_id,'UNASSIGNED')
    and source_group_id is not distinct from target_group_id then 'SAME_SCOPE'
   else 'CROSS_SCOPE' end,
 governance_scope_status='RESOLVED';

alter table a2a_policies drop constraint if exists ck_rs6_a2a_governance_scope_class;
alter table a2a_policies add constraint ck_rs6_a2a_governance_scope_class
 check(governance_scope_class in('TENANT','SAME_SCOPE','CROSS_SCOPE'));
alter table a2a_policies drop constraint if exists ck_rs6_a2a_governance_scope_status;
alter table a2a_policies add constraint ck_rs6_a2a_governance_scope_status
 check(governance_scope_status in('RESOLVED','UNRESOLVED'));

create index if not exists idx_a2a_policy_governance_owner_department
 on a2a_policies(tenant_id,governance_owner_department_id,enabled,policy_id);
create index if not exists idx_a2a_policy_governance_owner_group
 on a2a_policies(tenant_id,governance_owner_group_id,enabled,policy_id);
create index if not exists idx_a2a_policy_governance_scope_class
 on a2a_policies(tenant_id,governance_scope_class,enabled,policy_id);

-- Canonical A2A governance permissions. Cross-scope and fleet recovery remain Tenant-only.
insert into permission_catalog_revisions(
 revision_id,revision_code,revision_number,status,content_hash,description,supersedes_revision_id,
 created_at,created_by,published_at,published_by,version)
select '00000000-0000-0000-0000-000000000023'::uuid,'RS6-A2A-GOVERNANCE-SCOPE-0.8.2',coalesce(max(revision_number),0)+1,
 'DRAFT','DRAFT:UNPUBLISHED','RS6 A2A Governance scoped authorization.',
 (select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE'),now(),'rs6-a2a-governance-scope',null,null,1
from permission_catalog_revisions on conflict(revision_id) do nothing;

insert into permission_catalog_revision_entries(
 revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,
 allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
select '00000000-0000-0000-0000-000000000023'::uuid,e.permission_code,e.owner_module,e.resource_type,e.action_code,e.description,
 e.risk_level,e.risk_lane,e.lifecycle,e.allowed_scope_types,e.system_managed,e.replacement_permission_code,e.introduced_at,e.deprecated_at,
 e.retired_at,now(),'rs6-a2a-governance-scope',1
from permission_catalog_revision_entries e join permission_catalog_active_revision a on a.singleton_id='ACTIVE' and a.revision_id=e.revision_id
on conflict(revision_id,permission_code) do nothing;

insert into permission_catalog_revision_aliases(
 revision_id,alias_code,canonical_permission_code,alias_type,valid_from,valid_until,reason,created_by,created_at,version)
select '00000000-0000-0000-0000-000000000023'::uuid,a.alias_code,a.canonical_permission_code,a.alias_type,a.valid_from,a.valid_until,a.reason,
 'rs6-a2a-governance-scope',now(),1
from permission_catalog_revision_aliases a join permission_catalog_active_revision active on active.singleton_id='ACTIVE' and active.revision_id=a.revision_id
on conflict(revision_id,alias_code) do nothing;

insert into permission_catalog_revision_entries(
 revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,
 allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
values
 ('00000000-0000-0000-0000-000000000023','a2a.policy.read','a2a-api','A2A_POLICY','READ','Read governed A2A policies within Resource Scope.','MEDIUM','READ','ACTIVE',array['TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP']::varchar[],true,null,now(),null,null,now(),'rs6-a2a-governance-scope',1),
 ('00000000-0000-0000-0000-000000000023','a2a.policy.manage','a2a-api','A2A_POLICY','MANAGE','Manage governed A2A policies within Resource Scope.','HIGH','WRITE','ACTIVE',array['TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP']::varchar[],true,null,now(),null,null,now(),'rs6-a2a-governance-scope',1),
 ('00000000-0000-0000-0000-000000000023','a2a.cross_scope.manage','a2a-api','A2A_POLICY','MANAGE','Create or re-scope Tenant-wide or cross-organization A2A relationships.','CRITICAL','CRITICAL','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'rs6-a2a-governance-scope',1),
 ('00000000-0000-0000-0000-000000000023','a2a.reconciliation.read','a2a-api','A2A_REQUEST','READ','Read Tenant-wide A2A reconciliation cases.','HIGH','READ','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'rs6-a2a-governance-scope',1),
 ('00000000-0000-0000-0000-000000000023','a2a.reconciliation.manage','a2a-api','A2A_REQUEST','MANAGE','Resolve Tenant-wide A2A reconciliation cases.','CRITICAL','CRITICAL','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'rs6-a2a-governance-scope',1),
 ('00000000-0000-0000-0000-000000000023','a2a.quarantine.read','a2a-api','A2A_REQUEST','READ','Read Tenant-wide A2A late-result quarantine.','HIGH','READ','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'rs6-a2a-governance-scope',1),
 ('00000000-0000-0000-0000-000000000023','a2a.quarantine.manage','a2a-api','A2A_REQUEST','MANAGE','Resolve Tenant-wide A2A late-result quarantine.','CRITICAL','CRITICAL','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'rs6-a2a-governance-scope',1)
on conflict(revision_id,permission_code) do update set allowed_scope_types=excluded.allowed_scope_types,description=excluded.description,
 risk_level=excluded.risk_level,risk_lane=excluded.risk_lane,lifecycle='ACTIVE',updated_at=now(),updated_by='rs6-a2a-governance-scope',version=permission_catalog_revision_entries.version+1;

select set_config('app.permission_catalog_publish_revision_id','00000000-0000-0000-0000-000000000023',true);

insert into permission_definitions(
 permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,active,version,owner_module,risk_lane,lifecycle,
 catalog_revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by)
select permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,lifecycle<>'RETIRED',1,owner_module,risk_lane,lifecycle,
 revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by
from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000023'::uuid
on conflict(permission_code) do update set resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,
 risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,system_managed=excluded.system_managed,active=excluded.active,
 owner_module=excluded.owner_module,risk_lane=excluded.risk_lane,lifecycle=excluded.lifecycle,catalog_revision_id=excluded.catalog_revision_id,
 replacement_permission_code=excluded.replacement_permission_code,deprecated_at=excluded.deprecated_at,retired_at=excluded.retired_at,
 updated_at=excluded.updated_at,updated_by=excluded.updated_by,version=permission_definitions.version+1;

update permission_catalog_revisions set status='SUPERSEDED',version=version+1
where revision_id=(select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE')
 and revision_id<>'00000000-0000-0000-0000-000000000023'::uuid and status='PUBLISHED';

update permission_catalog_revisions set status='PUBLISHED',content_hash=(
 with catalog_lines as (
   select 'P|'||permission_code||'|'||owner_module||'|'||resource_type||'|'||action_code||'|'||description||'|'||risk_level||'|'||risk_lane||'|'||lifecycle||'|'||coalesce(array_to_string(allowed_scope_types,','),'')||'|'||system_managed::text||'|'||coalesce(replacement_permission_code,'') line
   from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000023'::uuid
   union all
   select 'A|'||alias_code||'|'||canonical_permission_code||'|'||alias_type||'|'||coalesce(to_char(valid_from at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||coalesce(to_char(valid_until at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||reason
   from permission_catalog_revision_aliases where revision_id='00000000-0000-0000-0000-000000000023'::uuid)
 select 'sha256:'||encode(sha256(convert_to(coalesce(string_agg(line,E'\n' order by line),''),'UTF8')),'hex') from catalog_lines),
 published_at=now(),published_by='rs6-a2a-governance-scope',version=version+1
where revision_id='00000000-0000-0000-0000-000000000023'::uuid and status='DRAFT';

update permission_catalog_active_revision set revision_id='00000000-0000-0000-0000-000000000023'::uuid,
 activated_at=now(),activated_by='rs6-a2a-governance-scope',version=version+1 where singleton_id='ACTIVE';

insert into permission_catalog_publication_events(publication_id,revision_id,previous_revision_id,content_hash,entry_count,alias_count,actor_id,audit_reason,correlation_id,published_at)
select '00000000-0000-0000-0000-000000005023'::uuid,r.revision_id,r.supersedes_revision_id,r.content_hash,
 (select count(*)::integer from permission_catalog_revision_entries e where e.revision_id=r.revision_id),
 (select count(*)::integer from permission_catalog_revision_aliases a where a.revision_id=r.revision_id),
 'rs6-a2a-governance-scope','RS6 A2A Governance scope publication','rs6-a2a-governance-scope',coalesce(r.published_at,now())
from permission_catalog_revisions r where r.revision_id='00000000-0000-0000-0000-000000000023'::uuid
on conflict(publication_id) do nothing;

-- Preserve custom-role compatibility by projecting legacy A2A grants into canonical RS6 permissions.
-- rbac_role_permissions is FORCE-RLS. Global grants are copied under INSTANCE; Tenant grants are
-- copied inside each Tenant context so this migration never disables or bypasses Tenant isolation.
do $$
declare
  tenant_value varchar(64);
begin
  perform set_config('app.current_tenant_id','INSTANCE',true);
  perform set_config('app.current_actor_id','rs6-a2a-governance-scope',true);

  with legacy_map(legacy_permission,canonical_permission) as (values
    ('api.a2.agovernance.policies','a2a.policy.read'),
    ('api.a2.agovernance.policy','a2a.policy.read'),
    ('api.a2.agovernance.upsert.policy','a2a.policy.read'),
    ('api.a2.agovernance.upsert.policy','a2a.policy.manage'),
    ('a2a.manage_policy','a2a.policy.manage'),
    ('api.a2.agovernance.reconciliation.cases','a2a.reconciliation.read'),
    ('api.a2.agovernance.resolve.reconciliation.case','a2a.reconciliation.manage'),
    ('api.a2.agovernance.result.quarantine','a2a.quarantine.read'),
    ('api.a2.agovernance.resolve.result.quarantine','a2a.quarantine.manage')
  )
  insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
  select 'rs6-'||substr(md5(rp.role_id||':'||m.canonical_permission),1,32),
         null,rp.role_id,m.canonical_permission,now(),'rs6-a2a-governance-scope',1
    from rbac_role_permissions rp
    join legacy_map m on m.legacy_permission=rp.permission_point
   where rp.tenant_id is null
  on conflict(role_id,permission_point) do nothing;

  for tenant_value in select tenant_id from tenants order by tenant_id loop
    perform set_config('app.current_tenant_id',tenant_value,true);
    perform set_config('app.current_actor_id','rs6-a2a-governance-scope',true);

    with legacy_map(legacy_permission,canonical_permission) as (values
      ('api.a2.agovernance.policies','a2a.policy.read'),
      ('api.a2.agovernance.policy','a2a.policy.read'),
      ('api.a2.agovernance.upsert.policy','a2a.policy.read'),
      ('api.a2.agovernance.upsert.policy','a2a.policy.manage'),
      ('a2a.manage_policy','a2a.policy.manage'),
      ('api.a2.agovernance.reconciliation.cases','a2a.reconciliation.read'),
      ('api.a2.agovernance.resolve.reconciliation.case','a2a.reconciliation.manage'),
      ('api.a2.agovernance.result.quarantine','a2a.quarantine.read'),
      ('api.a2.agovernance.resolve.result.quarantine','a2a.quarantine.manage')
    )
    insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
    select 'rs6-'||substr(md5(rp.role_id||':'||m.canonical_permission),1,32),
           tenant_value,rp.role_id,m.canonical_permission,now(),'rs6-a2a-governance-scope',1
      from rbac_role_permissions rp
      join legacy_map m on m.legacy_permission=rp.permission_point
     where rp.tenant_id=tenant_value
    on conflict(role_id,permission_point) do nothing;
  end loop;

  perform set_config('app.current_tenant_id','INSTANCE',true);
  perform set_config('app.current_actor_id','rs6-a2a-governance-scope',true);
exception when others then
  perform set_config('app.current_tenant_id','INSTANCE',true);
  perform set_config('app.current_actor_id','rs6-a2a-governance-scope',true);
  raise;
end $$;

-- Built-in Dispatch/Tenant responsibilities receive policy governance; cross-scope/recovery permissions remain usable only at TENANT Binding Scope.
insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'rs6-'||substr(md5(r.role_id||':'||p.permission_code),1,32),null,r.role_id,p.permission_code,now(),'rs6-a2a-governance-scope',1
from rbac_roles r join permission_definitions p on p.permission_code in(
 'a2a.policy.read','a2a.policy.manage','a2a.cross_scope.manage','a2a.reconciliation.read','a2a.reconciliation.manage','a2a.quarantine.read','a2a.quarantine.manage') and p.active=true
where r.tenant_id is null and r.role_code in('DISPATCH_ADMIN','TENANT_ADMIN') and r.status='ACTIVE'
on conflict(role_id,permission_point) do nothing;

insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template) values
 ('A2A_POLICY_SCOPE_ASSIGNMENT_DENIED',403,'AUTHORIZATION',false,'The selected A2A Policy organization scope is outside the active Responsibility scope.'),
 ('A2A_POLICY_WILDCARD_SCOPE_REQUIRES_TENANT_AUTHORITY',403,'AUTHORIZATION',false,'Tenant-wide A2A Policy scope requires Tenant authority.'),
 ('A2A_TENANT_WIDE_AUTHORITY_REQUIRED',403,'AUTHORIZATION',false,'This A2A governance operation requires Tenant-wide authority.'),
 ('A2A_TARGET_SCOPE_NOT_ALLOWED',403,'AUTHORIZATION',false,'Requested target scope is not permitted by the selected governed A2A Policy.'),
 ('A2A_AGENT_RUNTIME_AUTHORIZATION_DENIED',403,'AUTHORIZATION',false,'Agent Service Principal is not authorized for the source Task.')
on conflict(reason_code) do update set http_status=excluded.http_status,category=excluded.category,retryable=excluded.retryable,message_template=excluded.message_template;

-- R3 entry-point evidence for the changed A2A Governance controller.
update permission_entry_point_inventory
set source_hash='589619e1c36896c7733b2747d65fa4afda9f52ea87e8a4e207ae77c064f7ad30',manifest_revision='rs6-a2a-governance-entrypoints-2026-08-12',last_verified_at=now(),updated_at=now(),updated_by='rs6-a2a-governance-scope',version=version+1
where source_ref like 'ai-event-gateway-core/a2a-api/src/main/java/com/opensocket/aievent/core/a2a/api/A2AGovernanceController.java#%';
