-- Phase 5E expand: Platform/Tenant role administration, permission matrices,
-- scoped principal bindings, separation-of-duties and access-review hooks.

-- Flyway runs each migration in its own transaction. Set an explicit INSTANCE
-- authority context before accessing FORCE-RLS protected global RBAC rows.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase5e-migration',true);

insert into permission_catalog_revisions(
  revision_id,revision_code,revision_number,status,content_hash,description,supersedes_revision_id,
  created_at,created_by,published_at,published_by,version)
select '00000000-0000-0000-0000-000000000004'::uuid,'PHASE5E-0.8.2',
       coalesce(max(revision_number),0)+1,'DRAFT','DRAFT:UNPUBLISHED',
       'Phase 5E Platform and Tenant role, permission matrix, binding and access-review permissions.',
       (select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE'),
       now(),'phase5e-migration',null,null,1
from permission_catalog_revisions
on conflict(revision_id) do nothing;

insert into permission_catalog_revision_entries(
  revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,
  lifecycle,allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,
  retired_at,updated_at,updated_by,version)
select '00000000-0000-0000-0000-000000000004'::uuid,e.permission_code,e.owner_module,e.resource_type,
       e.action_code,e.description,e.risk_level,e.risk_lane,e.lifecycle,e.allowed_scope_types,e.system_managed,
       e.replacement_permission_code,e.introduced_at,e.deprecated_at,e.retired_at,now(),'phase5e-migration',1
from permission_catalog_revision_entries e
join permission_catalog_active_revision a on a.singleton_id='ACTIVE' and a.revision_id=e.revision_id
on conflict(revision_id,permission_code) do nothing;

insert into permission_catalog_revision_aliases(
  revision_id,alias_code,canonical_permission_code,alias_type,valid_from,valid_until,reason,created_by,created_at,version)
select '00000000-0000-0000-0000-000000000004'::uuid,a.alias_code,a.canonical_permission_code,
       a.alias_type,a.valid_from,a.valid_until,a.reason,'phase5e-migration',now(),1
from permission_catalog_revision_aliases a
join permission_catalog_active_revision active
  on active.singleton_id='ACTIVE' and active.revision_id=a.revision_id
on conflict(revision_id,alias_code) do nothing;

insert into permission_catalog_revision_entries(
  revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,
  lifecycle,allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,
  retired_at,updated_at,updated_by,version)
values
 ('00000000-0000-0000-0000-000000000004','identity.platform_role.read','rbac','ROLE','READ',
  'Read Platform system and custom Platform roles.','HIGH','READ','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase5e-migration',1),
 ('00000000-0000-0000-0000-000000000004','identity.platform_role.manage','rbac','ROLE','MANAGE',
  'Create, update, enable or disable custom Platform roles.','CRITICAL','CRITICAL','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase5e-migration',1),
 ('00000000-0000-0000-0000-000000000004','identity.tenant_role.read','rbac','ROLE','READ',
  'Read Tenant role templates and custom Tenant roles.','HIGH','READ','ACTIVE',array['TENANT'],true,null,now(),null,null,now(),'phase5e-migration',1),
 ('00000000-0000-0000-0000-000000000004','identity.tenant_role.manage','rbac','ROLE','MANAGE',
  'Create, update, enable or disable custom Tenant roles.','CRITICAL','CRITICAL','ACTIVE',array['TENANT'],true,null,now(),null,null,now(),'phase5e-migration',1),
 ('00000000-0000-0000-0000-000000000004','identity.role_permission.manage','rbac','ROLE_PERMISSION','MANAGE',
  'Replace the complete permission matrix of a mutable Platform or Tenant role.','CRITICAL','CRITICAL','ACTIVE',array['INSTANCE','TENANT'],true,null,now(),null,null,now(),'phase5e-migration',1),
 ('00000000-0000-0000-0000-000000000004','identity.role_binding.read','rbac','ROLE_BINDING','READ',
  'Read effective User, Group and Service Account role bindings.','HIGH','READ','ACTIVE',array['INSTANCE','TENANT'],true,null,now(),null,null,now(),'phase5e-migration',1),
 ('00000000-0000-0000-0000-000000000004','identity.role_binding.manage','rbac','ROLE_BINDING','MANAGE',
  'Create and revoke scoped User, Group and Service Account role bindings.','CRITICAL','CRITICAL','ACTIVE',array['INSTANCE','TENANT'],true,null,now(),null,null,now(),'phase5e-migration',1),
 ('00000000-0000-0000-0000-000000000004','identity.role_access_review.manage','rbac','ACCESS_REVIEW','MANAGE',
  'Manage role-binding review schedules and remediation evidence.','CRITICAL','ADMIN','ACTIVE',array['INSTANCE','TENANT'],true,null,now(),null,null,now(),'phase5e-migration',1)
on conflict(revision_id,permission_code) do update set
  owner_module=excluded.owner_module,resource_type=excluded.resource_type,action_code=excluded.action_code,
  description=excluded.description,risk_level=excluded.risk_level,risk_lane=excluded.risk_lane,
  lifecycle='ACTIVE',allowed_scope_types=excluded.allowed_scope_types,updated_at=excluded.updated_at,
  updated_by=excluded.updated_by,version=permission_catalog_revision_entries.version+1;

-- The Phase 1A role read/manage vocabulary is replaced rather than kept as a parallel API authority.
update permission_catalog_revision_entries
set lifecycle='RETIRED',
    replacement_permission_code=case permission_code
      when 'identity.role.read' then 'identity.tenant_role.read'
      when 'identity.role.manage' then 'identity.tenant_role.manage'
    end,
    retired_at=coalesce(retired_at,now()),updated_at=now(),updated_by='phase5e-migration',version=version+1
where revision_id='00000000-0000-0000-0000-000000000004'::uuid
  and permission_code in('identity.role.read','identity.role.manage');

select set_config('app.permission_catalog_publish_revision_id','00000000-0000-0000-0000-000000000004',true);

insert into permission_definitions(
  permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,
  active,version,owner_module,risk_lane,lifecycle,catalog_revision_id,replacement_permission_code,
  introduced_at,deprecated_at,retired_at,updated_at,updated_by)
select permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,
       lifecycle<>'RETIRED',1,owner_module,risk_lane,lifecycle,revision_id,replacement_permission_code,
       introduced_at,deprecated_at,retired_at,updated_at,updated_by
from permission_catalog_revision_entries
where revision_id='00000000-0000-0000-0000-000000000004'::uuid
on conflict(permission_code) do update set
  resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,
  risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,system_managed=excluded.system_managed,
  active=excluded.active,owner_module=excluded.owner_module,risk_lane=excluded.risk_lane,lifecycle=excluded.lifecycle,
  catalog_revision_id=excluded.catalog_revision_id,replacement_permission_code=excluded.replacement_permission_code,
  deprecated_at=excluded.deprecated_at,retired_at=excluded.retired_at,updated_at=excluded.updated_at,
  updated_by=excluded.updated_by,version=permission_definitions.version+1;

update permission_catalog_revisions
set status='SUPERSEDED',version=version+1
where revision_id=(select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE')
  and revision_id<>'00000000-0000-0000-0000-000000000004'::uuid and status='PUBLISHED';

update permission_catalog_revisions
set status='PUBLISHED',
    content_hash=(
      with catalog_lines as (
        select 'P|'||permission_code||'|'||owner_module||'|'||resource_type||'|'||action_code||'|'||description||'|'||
               risk_level||'|'||risk_lane||'|'||lifecycle||'|'||coalesce(array_to_string(allowed_scope_types,','),'')||'|'||
               system_managed::text||'|'||coalesce(replacement_permission_code,'') as line
        from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000004'::uuid
        union all
        select 'A|'||alias_code||'|'||canonical_permission_code||'|'||alias_type||'|'||
               coalesce(to_char(valid_from at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||
               coalesce(to_char(valid_until at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||reason
        from permission_catalog_revision_aliases where revision_id='00000000-0000-0000-0000-000000000004'::uuid
      )
      select 'sha256:'||encode(sha256(convert_to(coalesce(string_agg(line,E'\n' order by line),''),'UTF8')),'hex') from catalog_lines
    ),published_at=now(),published_by='phase5e-migration',version=version+1
where revision_id='00000000-0000-0000-0000-000000000004'::uuid and status='DRAFT';

update permission_catalog_active_revision
set revision_id='00000000-0000-0000-0000-000000000004'::uuid,activated_at=now(),activated_by='phase5e-migration',version=version+1
where singleton_id='ACTIVE';

insert into permission_catalog_publication_events(
  publication_id,revision_id,previous_revision_id,content_hash,entry_count,alias_count,actor_id,audit_reason,correlation_id,published_at)
select '00000000-0000-0000-0000-000000005004'::uuid,r.revision_id,r.supersedes_revision_id,r.content_hash,
       (select count(*)::integer from permission_catalog_revision_entries e where e.revision_id=r.revision_id),
       (select count(*)::integer from permission_catalog_revision_aliases a where a.revision_id=r.revision_id),
       'phase5e-migration','Phase 5E migration-owned Catalog publication','phase5e-migration',coalesce(r.published_at,now())
from permission_catalog_revisions r where r.revision_id='00000000-0000-0000-0000-000000000004'::uuid
on conflict(publication_id) do nothing;

-- Remove obsolete grants and assign the replacement permissions to the canonical templates.
delete from rbac_role_permissions
where tenant_id is null and permission_point in('identity.role.read','identity.role.manage');
do $$
declare tenant_value varchar(64);
begin
  for tenant_value in select tenant_id from tenants order by tenant_id loop
    perform set_config('app.current_tenant_id',tenant_value,true);
    delete from rbac_role_permissions
    where tenant_id=tenant_value and permission_point in('identity.role.read','identity.role.manage');
  end loop;
  perform set_config('app.current_tenant_id','INSTANCE',true);
end $$;

insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
values
 ('grant-system-admin-platform-role-read',null,'role-system-admin','identity.platform_role.read',now(),'phase5e-migration',1),
 ('grant-system-admin-platform-role-manage',null,'role-system-admin','identity.platform_role.manage',now(),'phase5e-migration',1),
 ('grant-system-admin-role-permission-manage',null,'role-system-admin','identity.role_permission.manage',now(),'phase5e-migration',1),
 ('grant-system-admin-role-binding-read',null,'role-system-admin','identity.role_binding.read',now(),'phase5e-migration',1),
 ('grant-system-admin-role-binding-manage',null,'role-system-admin','identity.role_binding.manage',now(),'phase5e-migration',1),
 ('grant-system-admin-role-review-manage',null,'role-system-admin','identity.role_access_review.manage',now(),'phase5e-migration',1),
 ('grant-tenant-admin-tenant-role-read',null,'role-tenant-admin','identity.tenant_role.read',now(),'phase5e-migration',1),
 ('grant-tenant-admin-tenant-role-manage',null,'role-tenant-admin','identity.tenant_role.manage',now(),'phase5e-migration',1),
 ('grant-tenant-admin-role-permission-manage',null,'role-tenant-admin','identity.role_permission.manage',now(),'phase5e-migration',1),
 ('grant-tenant-admin-role-binding-read',null,'role-tenant-admin','identity.role_binding.read',now(),'phase5e-migration',1),
 ('grant-tenant-admin-role-binding-manage',null,'role-tenant-admin','identity.role_binding.manage',now(),'phase5e-migration',1),
 ('grant-tenant-admin-role-review-manage',null,'role-tenant-admin','identity.role_access_review.manage',now(),'phase5e-migration',1),
 ('grant-user-admin-tenant-role-read',null,'role-user-admin','identity.tenant_role.read',now(),'phase5e-migration',1),
 ('grant-user-admin-role-binding-read',null,'role-user-admin','identity.role_binding.read',now(),'phase5e-migration',1),
 ('grant-user-admin-role-binding-manage',null,'role-user-admin','identity.role_binding.manage',now(),'phase5e-migration',1),
 ('grant-auditor-tenant-role-read',null,'role-auditor','identity.tenant_role.read',now(),'phase5e-migration',1),
 ('grant-auditor-role-binding-read',null,'role-auditor','identity.role_binding.read',now(),'phase5e-migration',1),
 ('grant-viewer-tenant-role-read',null,'role-identity-viewer','identity.tenant_role.read',now(),'phase5e-migration',1),
 ('grant-viewer-role-binding-read',null,'role-identity-viewer','identity.role_binding.read',now(),'phase5e-migration',1)
on conflict(role_id,permission_point) do nothing;

alter table rbac_roles
  add column if not exists risk_level varchar(16) not null default 'MEDIUM',
  add column if not exists review_required boolean not null default false,
  add column if not exists next_review_at timestamptz,
  add column if not exists last_reviewed_at timestamptz;

update rbac_roles set risk_level=case
      when role_code in('SYSTEM_ADMIN','TENANT_ADMIN','SECURITY_ADMIN') then 'CRITICAL'
      when role_code in('USER_ADMIN','LEGACY_ADMIN') then 'HIGH'
      else risk_level end,
      review_required=role_code in('SYSTEM_ADMIN','TENANT_ADMIN','SECURITY_ADMIN','USER_ADMIN','LEGACY_ADMIN'),
      next_review_at=case when role_code in('SYSTEM_ADMIN','TENANT_ADMIN','SECURITY_ADMIN','USER_ADMIN','LEGACY_ADMIN')
        then coalesce(next_review_at,now()+interval '90 days') else next_review_at end
where tenant_id is null;
do $$
declare tenant_value varchar(64);
begin
  for tenant_value in select tenant_id from tenants order by tenant_id loop
    perform set_config('app.current_tenant_id',tenant_value,true);
    update rbac_roles set risk_level=case
      when role_code in('SYSTEM_ADMIN','TENANT_ADMIN','SECURITY_ADMIN') then 'CRITICAL'
      when role_code in('USER_ADMIN','LEGACY_ADMIN') then 'HIGH'
      else risk_level end,
      review_required=role_code in('SYSTEM_ADMIN','TENANT_ADMIN','SECURITY_ADMIN','USER_ADMIN','LEGACY_ADMIN'),
      next_review_at=case when role_code in('SYSTEM_ADMIN','TENANT_ADMIN','SECURITY_ADMIN','USER_ADMIN','LEGACY_ADMIN')
        then coalesce(next_review_at,now()+interval '90 days') else next_review_at end
    where tenant_id=tenant_value;
  end loop;
  perform set_config('app.current_tenant_id','INSTANCE',true);
end $$;

alter table rbac_principal_role_bindings
  add column if not exists review_required boolean not null default false,
  add column if not exists next_review_at timestamptz,
  add column if not exists last_reviewed_at timestamptz,
  add column if not exists last_reviewed_by varchar(128);

create table if not exists rbac_separation_of_duties_rules (
  rule_id varchar(128) primary key,
  tenant_id varchar(64),
  left_role_id varchar(128) not null references rbac_roles(role_id),
  right_role_id varchar(128) not null references rbac_roles(role_id),
  scope_overlap_required boolean not null default true,
  status varchar(32) not null default 'ACTIVE',
  description varchar(1000) not null default '',
  created_at timestamptz not null,
  created_by varchar(128) not null,
  updated_at timestamptz not null,
  updated_by varchar(128) not null,
  version bigint not null default 1,
  foreign key(tenant_id) references tenants(tenant_id),
  check(left_role_id<>right_role_id),
  unique(tenant_id,left_role_id,right_role_id)
);

insert into rbac_separation_of_duties_rules(rule_id,tenant_id,left_role_id,right_role_id,scope_overlap_required,status,description,created_at,created_by,updated_at,updated_by,version)
values
 ('sod-security-admin-auditor',null,'role-security-admin','role-auditor',true,'ACTIVE','Security mutation and independent audit roles must not overlap for the same principal and scope.',now(),'phase5e-migration',now(),'phase5e-migration',1),
 ('sod-user-admin-auditor',null,'role-user-admin','role-auditor',true,'ACTIVE','Identity administration and independent audit roles must not overlap for the same principal and scope.',now(),'phase5e-migration',now(),'phase5e-migration',1)
on conflict(rule_id) do nothing;

create table if not exists rbac_administration_events (
  event_id varchar(128) primary key,
  tenant_id varchar(64),
  event_type varchar(64) not null,
  aggregate_type varchar(40) not null,
  aggregate_id varchar(128) not null,
  principal_type varchar(40),
  principal_id varchar(128),
  role_id varchar(128),
  permission_code varchar(160),
  scope_type varchar(32),
  scope_id varchar(128),
  previous_json jsonb not null default '{}'::jsonb,
  current_json jsonb not null default '{}'::jsonb,
  audit_reason varchar(500) not null,
  actor_id varchar(128) not null,
  correlation_id varchar(128),
  occurred_at timestamptz not null,
  foreign key(tenant_id) references tenants(tenant_id)
);
create index if not exists idx_rbac_admin_events_scope on rbac_administration_events(tenant_id,occurred_at desc,event_id);
create index if not exists idx_rbac_admin_events_role on rbac_administration_events(role_id,occurred_at desc);

create or replace view rbac_access_review_candidates as
select b.tenant_id,b.binding_id,b.principal_type,b.principal_id,b.role_id,r.role_code,r.role_name,
       b.scope_type,b.scope_id,b.effective_at,b.expires_at,b.review_required,
       coalesce(b.next_review_at,r.next_review_at) as next_review_at,b.last_reviewed_at,
       r.risk_level,
       case when b.expires_at is not null and b.expires_at<=now()+interval '30 days' then 'EXPIRING'
            when coalesce(b.next_review_at,r.next_review_at)<=now() then 'REVIEW_DUE'
            when r.risk_level='CRITICAL' then 'CRITICAL_ROLE'
            else 'SCHEDULED' end as review_reason
from rbac_principal_role_bindings b join rbac_roles r on r.role_id=b.role_id
where b.status='ACTIVE' and (b.review_required or r.review_required or r.risk_level='CRITICAL'
      or (b.expires_at is not null and b.expires_at<=now()+interval '30 days'));

insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template)
values
 ('ROLE_PERMISSION_NOT_FOUND',404,'AUTHORIZATION',false,'The Role Permission mapping does not exist.'),
 ('ROLE_STATUS_TRANSITION_INVALID',409,'AUTHORIZATION',false,'The Role status transition is invalid.'),
 ('ROLE_SEPARATION_OF_DUTIES_CONFLICT',409,'AUTHORIZATION',false,'The Role Binding conflicts with an active separation-of-duties rule.'),
 ('ROLE_PLATFORM_SCOPE_REQUIRED',403,'AUTHORIZATION',false,'Platform Role administration requires Instance scope.'),
 ('ROLE_TENANT_SCOPE_REQUIRED',403,'AUTHORIZATION',false,'Tenant Role administration requires Tenant scope.')
on conflict(reason_code) do update set http_status=excluded.http_status,category=excluded.category,retryable=excluded.retryable,message_template=excluded.message_template,active=true;

insert into domain_event_catalog(event_type,payload_version,aggregate_type,description,exportable)
values
 ('ROLE_UPDATED','1','RBAC_ROLE','A mutable Role was updated.',false),
 ('ROLE_STATUS_CHANGED','1','RBAC_ROLE','A mutable Role status changed.',false),
 ('ROLE_PERMISSION_MATRIX_REPLACED','1','RBAC_ROLE','A mutable Role permission matrix was replaced.',false),
 ('ROLE_ACCESS_REVIEW_DUE','1','RBAC_BINDING','A Role Binding requires access review.',false)
on conflict(event_type,payload_version) do nothing;
