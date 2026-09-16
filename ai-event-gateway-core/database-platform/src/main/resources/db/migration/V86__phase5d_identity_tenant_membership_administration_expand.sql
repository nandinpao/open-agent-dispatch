-- Phase 5D expand: Platform User administration and complete Tenant Membership lifecycle.

-- Publish the Platform User / Membership administration permissions as a new migration-owned Catalog revision.

-- Flyway runs each migration in its own transaction. Set an explicit INSTANCE
-- authority context before accessing FORCE-RLS protected global RBAC rows.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase5d-migration',true);

insert into permission_catalog_revisions(
  revision_id,revision_code,revision_number,status,content_hash,description,supersedes_revision_id,
  created_at,created_by,published_at,published_by,version)
select '00000000-0000-0000-0000-000000000003'::uuid,'PHASE5D-0.8.2',
       coalesce(max(revision_number),0)+1,'DRAFT','DRAFT:UNPUBLISHED',
       'Phase 5D Platform User and Tenant Membership administration permissions.',
       (select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE'),
       now(),'phase5d-migration',null,null,1
from permission_catalog_revisions
on conflict(revision_id) do nothing;

insert into permission_catalog_revision_entries(
  revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,
  lifecycle,allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,
  retired_at,updated_at,updated_by,version)
select '00000000-0000-0000-0000-000000000003'::uuid,e.permission_code,e.owner_module,e.resource_type,
       e.action_code,e.description,e.risk_level,e.risk_lane,e.lifecycle,e.allowed_scope_types,e.system_managed,
       e.replacement_permission_code,e.introduced_at,e.deprecated_at,e.retired_at,now(),'phase5d-migration',1
from permission_catalog_revision_entries e
join permission_catalog_active_revision a on a.singleton_id='ACTIVE' and a.revision_id=e.revision_id
on conflict(revision_id,permission_code) do nothing;

-- Preserve every alias owned by the previously active immutable revision.
insert into permission_catalog_revision_aliases(
  revision_id,alias_code,canonical_permission_code,alias_type,valid_from,valid_until,reason,created_by,created_at,version)
select '00000000-0000-0000-0000-000000000003'::uuid,a.alias_code,a.canonical_permission_code,
       a.alias_type,a.valid_from,a.valid_until,a.reason,'phase5d-migration',now(),1
from permission_catalog_revision_aliases a
join permission_catalog_active_revision active
  on active.singleton_id='ACTIVE' and active.revision_id=a.revision_id
on conflict(revision_id,alias_code) do nothing;

insert into permission_catalog_revision_entries(
  revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,
  lifecycle,allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,
  retired_at,updated_at,updated_by,version)
values
 ('00000000-0000-0000-0000-000000000003','identity.platform_user.read','identity-core','USER','READ',
  'Read global Human User identities and their Tenant membership directory.','HIGH','READ','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase5d-migration',1),
 ('00000000-0000-0000-0000-000000000003','identity.platform_user.create','identity-core','USER','CREATE',
  'Create a global Human User without implicitly granting Tenant access.','CRITICAL','ADMIN','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase5d-migration',1),
 ('00000000-0000-0000-0000-000000000003','identity.platform_user.update','identity-core','USER','UPDATE',
  'Update a global Human User profile and account lifecycle.','CRITICAL','ADMIN','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase5d-migration',1),
 ('00000000-0000-0000-0000-000000000003','identity.platform_user.security','authentication','USER','SECURITY',
  'Require password replacement and revoke every Tenant session for a global Human User.','CRITICAL','CRITICAL','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase5d-migration',1),
 ('00000000-0000-0000-0000-000000000003','identity.platform_admin.manage','rbac','ROLE_BINDING','MANAGE',
  'Manage named Platform Administrator bindings while protecting the final active administrator.','CRITICAL','CRITICAL','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase5d-migration',1),
 ('00000000-0000-0000-0000-000000000003','identity.tenant_membership.read','organization-access','MEMBERSHIP','READ',
  'Read Tenant Membership lifecycle and expiry details.','HIGH','READ','ACTIVE',array['TENANT'],true,null,now(),null,null,now(),'phase5d-migration',1),
 ('00000000-0000-0000-0000-000000000003','identity.tenant_membership.manage','organization-access','MEMBERSHIP','MANAGE',
  'Invite, activate, suspend, expire, reactivate or remove a Tenant Membership.','CRITICAL','CRITICAL','ACTIVE',array['TENANT'],true,null,now(),null,null,now(),'phase5d-migration',1)
on conflict(revision_id,permission_code) do update set
  owner_module=excluded.owner_module,resource_type=excluded.resource_type,action_code=excluded.action_code,
  description=excluded.description,risk_level=excluded.risk_level,risk_lane=excluded.risk_lane,
  lifecycle='ACTIVE',allowed_scope_types=excluded.allowed_scope_types,updated_at=excluded.updated_at,
  updated_by=excluded.updated_by,version=permission_catalog_revision_entries.version+1;

-- Tenant Administrators now control membership lifecycle only. Global account lifecycle is Platform-only.
update permission_catalog_revision_entries
set lifecycle='RETIRED',
    replacement_permission_code=case permission_code
      when 'identity.user.disable' then 'identity.platform_user.update'
      when 'identity.user.unlock' then 'identity.platform_user.security'
    end,
    retired_at=coalesce(retired_at,now()),
    updated_at=now(),updated_by='phase5d-migration',version=version+1
where revision_id='00000000-0000-0000-0000-000000000003'::uuid
  and permission_code in('identity.user.disable','identity.user.unlock');

-- The active projection is protected by the Phase 5B publication-context trigger.
select set_config(
  'app.permission_catalog_publish_revision_id',
  '00000000-0000-0000-0000-000000000003',
  true
);

insert into permission_definitions(
  permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,
  active,version,owner_module,risk_lane,lifecycle,catalog_revision_id,introduced_at,updated_at,updated_by)
select permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,
       (lifecycle<>'RETIRED'),1,owner_module,risk_lane,lifecycle,revision_id,introduced_at,updated_at,updated_by
from permission_catalog_revision_entries
where revision_id='00000000-0000-0000-0000-000000000003'::uuid
on conflict(permission_code) do update set
  resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,
  risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,system_managed=excluded.system_managed,
  active=excluded.active,owner_module=excluded.owner_module,risk_lane=excluded.risk_lane,lifecycle=excluded.lifecycle,
  catalog_revision_id=excluded.catalog_revision_id,updated_at=excluded.updated_at,updated_by=excluded.updated_by,
  version=permission_definitions.version+1;

update permission_catalog_revisions
set status='SUPERSEDED',version=version+1
where revision_id=(select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE')
  and revision_id<>'00000000-0000-0000-0000-000000000003'::uuid
  and status='PUBLISHED';

update permission_catalog_revisions
set status='PUBLISHED',
    content_hash=(
      with catalog_lines as (
        select 'P|'||permission_code||'|'||owner_module||'|'||resource_type||'|'||action_code||'|'||description||'|'||
               risk_level||'|'||risk_lane||'|'||lifecycle||'|'||
               coalesce(array_to_string(allowed_scope_types,','),'')||'|'||system_managed::text||'|'||
               coalesce(replacement_permission_code,'') as line
        from permission_catalog_revision_entries
        where revision_id='00000000-0000-0000-0000-000000000003'::uuid
        union all
        select 'A|'||alias_code||'|'||canonical_permission_code||'|'||alias_type||'|'||
               coalesce(to_char(valid_from at time zone 'UTC','YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"'),'')||'|'||
               coalesce(to_char(valid_until at time zone 'UTC','YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"'),'')||'|'||reason
        from permission_catalog_revision_aliases
        where revision_id='00000000-0000-0000-0000-000000000003'::uuid
      )
      select 'sha256:'||encode(sha256(convert_to(coalesce(string_agg(line,E'\n' order by line),''),'UTF8')),'hex')
      from catalog_lines
    ),
    published_at=now(),published_by='phase5d-migration',version=version+1
where revision_id='00000000-0000-0000-0000-000000000003'::uuid and status='DRAFT';

update permission_catalog_active_revision
set revision_id='00000000-0000-0000-0000-000000000003'::uuid,
    activated_at=now(),activated_by='phase5d-migration',version=version+1
where singleton_id='ACTIVE';

insert into permission_catalog_publication_events(
  publication_id,revision_id,previous_revision_id,content_hash,entry_count,alias_count,
  actor_id,audit_reason,correlation_id,published_at)
select '00000000-0000-0000-0000-000000005003'::uuid,r.revision_id,r.supersedes_revision_id,r.content_hash,
       (select count(*)::integer from permission_catalog_revision_entries e where e.revision_id=r.revision_id),
       (select count(*)::integer from permission_catalog_revision_aliases a where a.revision_id=r.revision_id),
       'phase5d-migration','Phase 5D migration-owned Catalog publication','phase5d-migration',coalesce(r.published_at,now())
from permission_catalog_revisions r
where r.revision_id='00000000-0000-0000-0000-000000000003'::uuid
on conflict(publication_id) do nothing;

-- Remove obsolete grants in every RLS scope. INSTANCE sees global templates only;
-- Tenant-owned custom roles are processed under their own transaction-local context.
delete from rbac_role_permissions
where tenant_id is null
  and permission_point in('identity.user.disable','identity.user.unlock');
do $$
declare tenant_value varchar(64);
begin
  for tenant_value in select tenant_id from tenants order by tenant_id loop
    perform set_config('app.current_tenant_id',tenant_value,true);
    delete from rbac_role_permissions
    where tenant_id=tenant_value
      and permission_point in('identity.user.disable','identity.user.unlock');
  end loop;
  perform set_config('app.current_tenant_id','INSTANCE',true);
end $$;

insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
values
 ('grant-system-admin-platform-user-read',null,'role-system-admin','identity.platform_user.read',now(),'phase5d-migration',1),
 ('grant-system-admin-platform-user-create',null,'role-system-admin','identity.platform_user.create',now(),'phase5d-migration',1),
 ('grant-system-admin-platform-user-update',null,'role-system-admin','identity.platform_user.update',now(),'phase5d-migration',1),
 ('grant-system-admin-platform-user-security',null,'role-system-admin','identity.platform_user.security',now(),'phase5d-migration',1),
 ('grant-system-admin-platform-admin-manage',null,'role-system-admin','identity.platform_admin.manage',now(),'phase5d-migration',1),
 ('grant-tenant-admin-membership-read',null,'role-tenant-admin','identity.tenant_membership.read',now(),'phase5d-migration',1),
 ('grant-tenant-admin-membership-manage',null,'role-tenant-admin','identity.tenant_membership.manage',now(),'phase5d-migration',1)
on conflict(role_id,permission_point) do nothing;

-- Expand the existing Tenant Membership authority instead of introducing another membership table.
alter table org_tenant_memberships
  add column if not exists updated_at timestamptz,
  add column if not exists updated_by varchar(128),
  add column if not exists status_reason varchar(500),
  add column if not exists default_tenant boolean,
  add column if not exists membership_source varchar(32),
  add column if not exists invited_at timestamptz,
  add column if not exists activated_at timestamptz,
  add column if not exists suspended_at timestamptz,
  add column if not exists expired_at timestamptz,
  add column if not exists removed_at timestamptz;

-- FORCE RLS intentionally prevents the migration owner from bypassing Tenant isolation.
-- Backfill each Tenant under its own explicit context instead of disabling RLS.
do $$
declare tenant_value varchar(64);
begin
  for tenant_value in select tenant_id from tenants order by tenant_id loop
    perform set_config('app.current_tenant_id',tenant_value,true);
    update org_tenant_memberships
    set updated_at=coalesce(updated_at,joined_at),
        updated_by=coalesce(nullif(updated_by,''),created_by),
        status_reason=coalesce(status_reason,''),
        default_tenant=coalesce(default_tenant,false),
        membership_source=coalesce(nullif(membership_source,''),'ADMIN_CREATED'),
        invited_at=case when status='INVITED' then coalesce(invited_at,joined_at) else invited_at end,
        activated_at=case when status='ACTIVE' then coalesce(activated_at,joined_at) else activated_at end,
        suspended_at=case when status='SUSPENDED' then coalesce(suspended_at,joined_at) else suspended_at end,
        expired_at=case when status='EXPIRED' then coalesce(expired_at,joined_at) else expired_at end,
        removed_at=case when status='REMOVED' then coalesce(removed_at,joined_at) else removed_at end
    where tenant_id=tenant_value;
  end loop;
  perform set_config('app.current_tenant_id','INSTANCE',true);
end $$;

create unique index if not exists uq_org_tenant_default_membership
  on org_tenant_memberships(user_id)
  where default_tenant=true and status='ACTIVE';
create index if not exists idx_org_tenant_membership_admin
  on org_tenant_memberships(tenant_id,status,updated_at desc,membership_id);

-- Cross-Tenant directory is the only Platform projection of Tenant membership; it contains no credentials.
alter table iam_user_tenant_directory
  add column if not exists default_tenant boolean not null default false,
  add column if not exists membership_source varchar(32) not null default 'ADMIN_CREATED',
  add column if not exists membership_version bigint not null default 1;

create table if not exists iam_tenant_membership_events (
  event_id varchar(128) primary key,
  tenant_id varchar(64) not null references tenants(tenant_id),
  membership_id varchar(128) not null,
  user_id varchar(128) not null references iam_users(user_id),
  previous_status varchar(32),
  current_status varchar(32) not null,
  reason varchar(500) not null,
  actor_id varchar(128) not null,
  correlation_id varchar(128),
  membership_version bigint not null,
  occurred_at timestamptz not null,
  foreign key(tenant_id,membership_id) references org_tenant_memberships(tenant_id,membership_id)
);
create index if not exists idx_iam_membership_events_tenant_user
  on iam_tenant_membership_events(tenant_id,user_id,occurred_at desc,event_id);

insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template)
values
 ('IDENTITY_PLATFORM_USER_NOT_FOUND',404,'IDENTITY',false,'The Platform User does not exist.'),
 ('IDENTITY_PLATFORM_USER_VERSION_CONFLICT',409,'CONCURRENCY',true,'The Platform User version changed.'),
 ('IDENTITY_TENANT_MEMBERSHIP_NOT_FOUND',404,'IDENTITY',false,'The Tenant Membership does not exist.'),
 ('IDENTITY_TENANT_MEMBERSHIP_ALREADY_EXISTS',409,'IDENTITY',false,'A Tenant Membership already exists; use the lifecycle transition API.'),
 ('IDENTITY_TENANT_MEMBERSHIP_VERSION_CONFLICT',409,'CONCURRENCY',true,'The Tenant Membership version changed.'),
 ('IDENTITY_TENANT_MEMBERSHIP_TRANSITION_INVALID',409,'IDENTITY',false,'The Tenant Membership status transition is invalid.'),
 ('IDENTITY_LAST_PLATFORM_ADMIN_PROTECTED',409,'AUTHORIZATION',false,'The final active Platform Administrator cannot be removed, disabled or suspended.'),
 ('IDENTITY_DEFAULT_TENANT_REQUIRES_ACTIVE_MEMBERSHIP',409,'IDENTITY',false,'Only an active Tenant Membership can be the default Tenant.'),
 ('IDENTITY_PLATFORM_USER_TENANT_SCOPE_FORBIDDEN',403,'AUTHORIZATION',false,'Platform User administration requires Instance scope.')
on conflict(reason_code) do nothing;
