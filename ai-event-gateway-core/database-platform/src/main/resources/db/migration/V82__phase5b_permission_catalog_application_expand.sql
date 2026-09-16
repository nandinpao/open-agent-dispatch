-- Phase 5B expand: revision-owned Catalog drafts, aliases and Platform administration permissions.

-- Phase 1A-4 allowed Tenant mutations only. Phase 5 introduces global Platform Role
-- grants, so INSTANCE context must be able to mutate tenant_id IS NULL rows while Tenant
-- requests keep read-only access to global templates. V89 reasserts the same invariant.

-- Flyway runs each migration in its own transaction. Set an explicit INSTANCE
-- authority context before accessing FORCE-RLS protected global RBAC rows.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase5b-migration',true);

do $$
declare table_name text;
begin
  foreach table_name in array array['rbac_roles','rbac_role_permissions'] loop
    execute format('drop policy if exists rbac_select_scope on %I',table_name);
    execute format('drop policy if exists rbac_insert_tenant on %I',table_name);
    execute format('drop policy if exists rbac_update_tenant on %I',table_name);
    execute format('drop policy if exists rbac_delete_tenant on %I',table_name);
    execute format('create policy rbac_select_scope on %I for select using (tenant_id is null or tenant_id=iam_current_tenant_id())',table_name);
    execute format('create policy rbac_insert_tenant on %I for insert with check ((tenant_id is null and iam_current_tenant_id()=''INSTANCE'') or tenant_id=iam_current_tenant_id())',table_name);
    execute format('create policy rbac_update_tenant on %I for update using ((tenant_id is null and iam_current_tenant_id()=''INSTANCE'') or tenant_id=iam_current_tenant_id()) with check ((tenant_id is null and iam_current_tenant_id()=''INSTANCE'') or tenant_id=iam_current_tenant_id())',table_name);
    execute format('create policy rbac_delete_tenant on %I for delete using ((tenant_id is null and iam_current_tenant_id()=''INSTANCE'') or tenant_id=iam_current_tenant_id())',table_name);
  end loop;
end $$;

-- Platform-only permissions used by the Catalog Administration API.
insert into permission_definitions(
  permission_code,resource_type,action_code,description,risk_level,
  allowed_scope_types,system_managed,active,version,owner_module,risk_lane,lifecycle,
  catalog_revision_id,introduced_at,updated_at,updated_by)
values
 ('permission.catalog.read','PERMISSION_CATALOG','READ','Read Permission Catalog revisions, definitions, aliases, validation and diffs.','HIGH',array['INSTANCE']::varchar[],true,true,1,'rbac','READ','ACTIVE','00000000-0000-0000-0000-000000000001',now(),now(),'phase5b-migration'),
 ('permission.catalog.manage','PERMISSION_CATALOG','MANAGE','Create Draft revisions and manage Draft Permission definitions and aliases.','CRITICAL',array['INSTANCE']::varchar[],true,true,1,'rbac','ADMIN','ACTIVE','00000000-0000-0000-0000-000000000001',now(),now(),'phase5b-migration'),
 ('permission.catalog.publish','PERMISSION_CATALOG','PUBLISH','Validate and publish a Permission Catalog revision as the active runtime authority.','CRITICAL',array['INSTANCE']::varchar[],true,true,1,'rbac','CRITICAL','ACTIVE','00000000-0000-0000-0000-000000000001',now(),now(),'phase5b-migration')
on conflict(permission_code) do update set
  resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,
  risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,system_managed=true,
  active=true,owner_module=excluded.owner_module,risk_lane=excluded.risk_lane,lifecycle='ACTIVE',
  updated_at=excluded.updated_at,updated_by=excluded.updated_by,version=permission_definitions.version+1;

-- V80 renamed the authority table. Rebind the persisted RBAC validation function before
-- any new Role-Permission grant invokes its trigger.
create or replace function phase1a4_validate_role_permission()
returns trigger language plpgsql as $$
declare role_tenant varchar(64); role_kind varchar(40); allowed varchar(32)[];
begin
  select tenant_id,role_type into role_tenant,role_kind from rbac_roles where role_id=new.role_id for key share;
  if not found then raise exception 'ROLE_NOT_FOUND' using errcode='23503'; end if;
  if role_tenant is distinct from new.tenant_id then raise exception 'ROLE_TENANT_MISMATCH' using errcode='23514'; end if;
  select allowed_scope_types into allowed from permission_definitions
   where permission_code=new.permission_point and active=true and lifecycle in('ACTIVE','DEPRECATED');
  if not found then raise exception 'AUTH_PERMISSION_UNKNOWN' using errcode='23503'; end if;
  if role_kind='CUSTOM_TENANT_ROLE' and 'INSTANCE'=any(allowed) then
    raise exception 'ROLE_INSTANCE_PERMISSION_FORBIDDEN' using errcode='23514';
  end if;
  return new;
end $$;

insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
values
 ('grant-system-admin-permission-catalog-read',null,'role-system-admin','permission.catalog.read',now(),'phase5b-migration',1),
 ('grant-system-admin-permission-catalog-manage',null,'role-system-admin','permission.catalog.manage',now(),'phase5b-migration',1),
 ('grant-system-admin-permission-catalog-publish',null,'role-system-admin','permission.catalog.publish',now(),'phase5b-migration',1)
on conflict(role_id,permission_point) do nothing;

-- Every revision owns a complete immutable snapshot after publication.
create table if not exists permission_catalog_revision_entries (
  revision_id uuid not null references permission_catalog_revisions(revision_id),
  permission_code varchar(160) not null,
  owner_module varchar(128) not null,
  resource_type varchar(96) not null,
  action_code varchar(96) not null,
  description text not null,
  risk_level varchar(24) not null,
  risk_lane varchar(24) not null,
  lifecycle varchar(24) not null,
  allowed_scope_types varchar(32)[] not null,
  system_managed boolean not null default true,
  replacement_permission_code varchar(160),
  introduced_at timestamptz not null,
  deprecated_at timestamptz,
  retired_at timestamptz,
  updated_at timestamptz not null,
  updated_by varchar(128) not null,
  version bigint not null default 1,
  primary key(revision_id,permission_code)
);
create index if not exists idx_permission_catalog_revision_entries_owner
  on permission_catalog_revision_entries(revision_id,owner_module,lifecycle,permission_code);
create index if not exists idx_permission_catalog_revision_entries_risk
  on permission_catalog_revision_entries(revision_id,risk_lane,lifecycle,permission_code);

-- Publication may introduce a replacement Permission in the same transaction.
alter table permission_definitions drop constraint if exists fk_permission_definition_replacement;
alter table permission_definitions
  add constraint fk_permission_definition_replacement
  foreign key(replacement_permission_code) references permission_definitions(permission_code)
  deferrable initially deferred;

insert into permission_catalog_revision_entries(
  revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,
  lifecycle,allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,
  retired_at,updated_at,updated_by,version)
select catalog_revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,
  risk_lane,lifecycle,allowed_scope_types,system_managed,replacement_permission_code,introduced_at,
  deprecated_at,retired_at,updated_at,updated_by,version
from permission_definitions
on conflict(revision_id,permission_code) do nothing;

-- The Phase 5B application permissions change the active Catalog, so publish a migration-owned
-- revision rather than silently mutating the Phase 5A baseline evidence.
insert into permission_catalog_revisions(
  revision_id,revision_code,revision_number,status,content_hash,description,supersedes_revision_id,
  created_at,created_by,published_at,published_by,version)
select '00000000-0000-0000-0000-000000000002'::uuid,'PHASE5B-0.8.2',
       coalesce(max(revision_number),0)+1,'DRAFT','DRAFT:UNPUBLISHED',
       'Phase 5B PostgreSQL Catalog Foundation application permissions and revision-owned snapshots.',
       '00000000-0000-0000-0000-000000000001'::uuid,now(),'phase5b-migration',null,null,1
from permission_catalog_revisions
on conflict(revision_id) do nothing;

insert into permission_catalog_revision_entries(
  revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,
  lifecycle,allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,
  retired_at,updated_at,updated_by,version)
select '00000000-0000-0000-0000-000000000002'::uuid,permission_code,owner_module,resource_type,
  action_code,description,risk_level,risk_lane,lifecycle,allowed_scope_types,system_managed,
  replacement_permission_code,introduced_at,deprecated_at,retired_at,now(),'phase5b-migration',1
from permission_catalog_revision_entries
where revision_id='00000000-0000-0000-0000-000000000001'::uuid
on conflict(revision_id,permission_code) do nothing;

-- The three Platform Catalog permissions belong to Revision 2, not the historical Phase 5A baseline.
delete from permission_catalog_revision_entries
where revision_id='00000000-0000-0000-0000-000000000001'::uuid
  and permission_code in('permission.catalog.read','permission.catalog.manage','permission.catalog.publish');

update permission_definitions
set catalog_revision_id='00000000-0000-0000-0000-000000000002'::uuid,
    updated_at=now(),updated_by='phase5b-migration',version=version+1;

update permission_catalog_revisions
set status='PUBLISHED',
    content_hash=(select 'md5:'||md5(string_agg(
      permission_code||'|'||owner_module||'|'||resource_type||'|'||action_code||'|'||description||'|'||
      risk_level||'|'||risk_lane||'|'||lifecycle||'|'||coalesce(array_to_string(allowed_scope_types,','),'')||'|'||
      system_managed::text||'|'||coalesce(replacement_permission_code,''),E'\n' order by permission_code))
      from permission_catalog_revision_entries
      where revision_id='00000000-0000-0000-0000-000000000002'::uuid),
    published_at=now(),published_by='phase5b-migration',version=version+1
where revision_id='00000000-0000-0000-0000-000000000002'::uuid and status='DRAFT';

update permission_catalog_active_revision
set revision_id='00000000-0000-0000-0000-000000000002'::uuid,
    activated_at=now(),activated_by='phase5b-migration',version=version+1
where singleton_id='ACTIVE';

-- Aliases are revision-owned. The former active-only table is migrated then removed.
create table if not exists permission_catalog_revision_aliases (
  revision_id uuid not null references permission_catalog_revisions(revision_id),
  alias_code varchar(160) not null,
  canonical_permission_code varchar(160) not null,
  alias_type varchar(24) not null,
  valid_from timestamptz not null,
  valid_until timestamptz,
  reason varchar(500) not null,
  created_at timestamptz not null,
  created_by varchar(128) not null,
  version bigint not null default 1,
  primary key(revision_id,alias_code),
  foreign key(revision_id,canonical_permission_code)
    references permission_catalog_revision_entries(revision_id,permission_code)
    deferrable initially deferred
);

insert into permission_catalog_revision_aliases(
  revision_id,alias_code,canonical_permission_code,alias_type,valid_from,valid_until,reason,created_at,created_by,version)
select active.revision_id,alias.alias_code,alias.canonical_permission_code,alias.alias_type,
       alias.valid_from,alias.valid_until,alias.reason,alias.created_at,alias.created_by,alias.version
from permission_catalog_aliases alias
cross join permission_catalog_active_revision active
where active.singleton_id='ACTIVE'
on conflict(revision_id,alias_code) do nothing;

drop table permission_catalog_aliases;

-- Publication evidence retained independently from the revision row.
create table if not exists permission_catalog_publication_events (
  publication_id uuid primary key,
  revision_id uuid not null references permission_catalog_revisions(revision_id),
  previous_revision_id uuid references permission_catalog_revisions(revision_id),
  content_hash varchar(128) not null,
  entry_count integer not null,
  alias_count integer not null,
  actor_id varchar(128) not null,
  audit_reason varchar(500) not null,
  correlation_id varchar(128),
  published_at timestamptz not null
);
create index if not exists idx_permission_catalog_publication_events_revision
  on permission_catalog_publication_events(revision_id,published_at desc);

insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template)
values
 ('PERMISSION_CATALOG_REVISION_NOT_FOUND',404,'AUTHORIZATION',false,'The Permission Catalog revision does not exist.'),
 ('PERMISSION_CATALOG_REVISION_NOT_DRAFT',409,'AUTHORIZATION',false,'Only a Draft Permission Catalog revision can be changed.'),
 ('PERMISSION_CATALOG_ACTIVE_DRAFT_EXISTS',409,'AUTHORIZATION',false,'A Draft Permission Catalog revision already exists.'),
 ('PERMISSION_CATALOG_VERSION_CONFLICT',409,'CONCURRENCY',true,'The Permission Catalog resource version changed.'),
 ('PERMISSION_CATALOG_VALIDATION_FAILED',409,'AUTHORIZATION',false,'The Permission Catalog revision failed publication validation.'),
 ('PERMISSION_CATALOG_PERMISSION_NOT_FOUND',404,'AUTHORIZATION',false,'The Permission definition does not exist in the revision.'),
 ('PERMISSION_CATALOG_ALIAS_NOT_FOUND',404,'AUTHORIZATION',false,'The Permission alias does not exist in the revision.'),
 ('PERMISSION_CATALOG_EXISTING_PERMISSION_MUST_BE_RETIRED',409,'AUTHORIZATION',false,'An existing Permission must be retired instead of removed.'),
 ('PERMISSION_CATALOG_LIFECYCLE_TRANSITION_INVALID',409,'AUTHORIZATION',false,'The requested Permission lifecycle transition is invalid.'),
 ('PERMISSION_CATALOG_ALIAS_CONFLICT',409,'AUTHORIZATION',false,'The alias conflicts with a canonical Permission code.'),
 ('PERMISSION_CATALOG_ACTIVE_PROJECTION_FAILED',500,'INTERNAL',false,'The active Permission projection could not be materialized.'),
 ('PERMISSION_CATALOG_CHANGE_EVENT_FAILED',500,'INTERNAL',false,'The Permission Catalog change event could not be appended.'),
 ('PERMISSION_CATALOG_PUBLICATION_EVENT_FAILED',500,'INTERNAL',false,'The Permission Catalog publication evidence could not be appended.'),
 ('PERMISSION_CATALOG_PUBLISHED',200,'AUTHORIZATION',false,'The Permission Catalog revision was published.')
on conflict(reason_code) do nothing;
