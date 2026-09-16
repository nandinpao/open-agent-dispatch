-- Phase 1A-2 Expand: IAM persistence and organization schema evolution.
-- Existing tenants/departments/organization_groups remain the single authority.

create table if not exists iam_root_identities (
  root_identity_id varchar(64) primary key,
  identity_type varchar(32) not null default 'INSTANCE_ROOT',
  status varchar(64) not null,
  reason varchar(500),
  created_at timestamptz not null,
  updated_at timestamptz not null,
  updated_by varchar(128) not null,
  version bigint not null default 1
);

create table if not exists iam_users (
  user_id varchar(128) primary key,
  identity_type varchar(32) not null default 'HUMAN_USER',
  username varchar(128) not null,
  normalized_username varchar(128) not null,
  email varchar(320),
  normalized_email varchar(320),
  display_name varchar(200) not null,
  status varchar(64) not null,
  creation_mode varchar(32) not null,
  status_reason varchar(500),
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by varchar(128) not null,
  updated_by varchar(128) not null,
  version bigint not null default 1
);
create unique index if not exists uq_iam_users_normalized_username on iam_users(normalized_username);
create unique index if not exists uq_iam_users_normalized_email on iam_users(normalized_email) where normalized_email is not null;
create index if not exists idx_iam_users_status_updated on iam_users(status, updated_at desc, user_id);

alter table tenants add column if not exists tenant_code varchar(64);
alter table tenants add column if not exists legal_name varchar(300);
alter table tenants add column if not exists default_timezone varchar(64);
alter table tenants add column if not exists default_locale varchar(64);
alter table tenants add column if not exists data_region varchar(64);
alter table tenants add column if not exists updated_by varchar(128);
alter table tenants add column if not exists version bigint;
update tenants set tenant_code = coalesce(nullif(tenant_code, ''), tenant_id),
                   default_timezone = coalesce(nullif(default_timezone, ''), 'UTC'),
                   default_locale = coalesce(nullif(default_locale, ''), 'en'),
                   data_region = coalesce(nullif(data_region, ''), 'GLOBAL'),
                   updated_by = coalesce(nullif(updated_by, ''), 'phase1a2-migration'),
                   version = coalesce(version, 1);

alter table departments add column if not exists parent_department_id varchar(128);
alter table departments add column if not exists manager_user_id varchar(128);
alter table departments add column if not exists display_order integer;
alter table departments add column if not exists updated_by varchar(128);
alter table departments add column if not exists version bigint;
update departments set display_order = coalesce(display_order, 0),
                       updated_by = coalesce(nullif(updated_by, ''), 'phase1a2-migration'),
                       version = coalesce(version, 1);

alter table organization_groups add column if not exists parent_group_id varchar(128);
alter table organization_groups add column if not exists owner_department_id varchar(128);
alter table organization_groups add column if not exists updated_by varchar(128);
alter table organization_groups add column if not exists version bigint;
alter table organization_groups alter column group_type set default 'GENERAL';
update organization_groups set group_type = case when group_type = 'COLLABORATION' then 'GENERAL' else group_type end,
                               updated_by = coalesce(nullif(updated_by, ''), 'phase1a2-migration'),
                               version = coalesce(version, 1);

create table if not exists org_tenant_memberships (
  tenant_id varchar(64) not null,
  membership_id varchar(128) not null,
  user_id varchar(128) not null,
  status varchar(32) not null,
  employee_id varchar(128),
  joined_at timestamptz not null,
  expires_at timestamptz,
  created_by varchar(128) not null,
  version bigint not null default 1,
  primary key (tenant_id, membership_id),
  unique (tenant_id, user_id),
  foreign key (tenant_id) references tenants(tenant_id),
  foreign key (user_id) references iam_users(user_id)
);

create table if not exists org_department_closure (
  tenant_id varchar(64) not null,
  ancestor_department_id varchar(128) not null,
  descendant_department_id varchar(128) not null,
  depth integer not null,
  primary key (tenant_id, ancestor_department_id, descendant_department_id),
  foreign key (tenant_id, ancestor_department_id) references departments(tenant_id, department_id),
  foreign key (tenant_id, descendant_department_id) references departments(tenant_id, department_id)
);

insert into org_department_closure(tenant_id, ancestor_department_id, descendant_department_id, depth)
select tenant_id, department_id, department_id, 0 from departments
on conflict do nothing;

with recursive hierarchy as (
  select d.tenant_id, d.parent_department_id as ancestor_id, d.department_id as descendant_id, 1 as depth
  from departments d where d.parent_department_id is not null
  union all
  select h.tenant_id, p.parent_department_id, h.descendant_id, h.depth + 1
  from hierarchy h
  join departments p on p.tenant_id = h.tenant_id and p.department_id = h.ancestor_id
  where p.parent_department_id is not null and h.depth < 10
)
insert into org_department_closure(tenant_id, ancestor_department_id, descendant_department_id, depth)
select tenant_id, ancestor_id, descendant_id, depth from hierarchy where ancestor_id is not null
on conflict do nothing;

create table if not exists org_department_revisions (
  tenant_id varchar(64) not null,
  department_id varchar(128) not null,
  revision bigint not null,
  department_code varchar(128) not null,
  department_name varchar(255) not null,
  parent_department_id varchar(128),
  ancestor_path_ids varchar(128)[] not null default '{}',
  ancestor_path_codes varchar(128)[] not null default '{}',
  ancestor_path_names varchar(255)[] not null default '{}',
  valid_from timestamptz not null,
  valid_to timestamptz,
  change_type varchar(32) not null,
  changed_by varchar(128) not null,
  change_reason varchar(500) not null,
  created_at timestamptz not null,
  primary key (tenant_id, department_id, revision),
  foreign key (tenant_id, department_id) references departments(tenant_id, department_id)
);
create unique index if not exists uq_org_department_current_revision
  on org_department_revisions(tenant_id, department_id) where valid_to is null;

insert into org_department_revisions(
  tenant_id, department_id, revision, department_code, department_name, parent_department_id,
  ancestor_path_ids, ancestor_path_codes, ancestor_path_names, valid_from, valid_to,
  change_type, changed_by, change_reason, created_at)
select d.tenant_id, d.department_id, 1, d.department_code, d.department_name, d.parent_department_id,
       coalesce((select array_agg(a.department_id order by c.depth desc)
                 from org_department_closure c join departments a
                   on a.tenant_id=c.tenant_id and a.department_id=c.ancestor_department_id
                 where c.tenant_id=d.tenant_id and c.descendant_department_id=d.department_id), '{}'),
       coalesce((select array_agg(a.department_code order by c.depth desc)
                 from org_department_closure c join departments a
                   on a.tenant_id=c.tenant_id and a.department_id=c.ancestor_department_id
                 where c.tenant_id=d.tenant_id and c.descendant_department_id=d.department_id), '{}'),
       coalesce((select array_agg(a.department_name order by c.depth desc)
                 from org_department_closure c join departments a
                   on a.tenant_id=c.tenant_id and a.department_id=c.ancestor_department_id
                 where c.tenant_id=d.tenant_id and c.descendant_department_id=d.department_id), '{}'),
       d.created_at, null, 'CREATED', coalesce(d.updated_by, 'phase1a2-migration'),
       'Initial Phase 1A-2 revision backfill', d.created_at
from departments d
where not exists (select 1 from org_department_revisions r where r.tenant_id=d.tenant_id and r.department_id=d.department_id)
on conflict do nothing;

create table if not exists org_organization_snapshots (
  snapshot_id varchar(128) not null,
  tenant_id varchar(64) not null,
  department_id varchar(128) not null,
  department_revision bigint not null,
  department_code varchar(128) not null,
  department_name varchar(255) not null,
  ancestor_path_ids varchar(128)[] not null default '{}',
  ancestor_path_codes varchar(128)[] not null default '{}',
  ancestor_path_names varchar(255)[] not null default '{}',
  group_ids varchar(128)[] not null default '{}',
  captured_at timestamptz not null,
  content_hash varchar(128) not null,
  primary key (tenant_id, snapshot_id),
  unique (tenant_id, content_hash),
  foreign key (tenant_id, department_id, department_revision)
    references org_department_revisions(tenant_id, department_id, revision)
);

create table if not exists org_department_memberships (
  tenant_id varchar(64) not null,
  membership_id varchar(128) not null,
  user_id varchar(128) not null,
  department_id varchar(128) not null,
  membership_type varchar(32) not null,
  is_primary boolean not null default false,
  effective_at timestamptz not null,
  expires_at timestamptz,
  status varchar(32) not null,
  version bigint not null default 1,
  primary key (tenant_id, membership_id),
  unique (tenant_id, user_id, department_id, membership_type),
  foreign key (tenant_id, user_id) references org_tenant_memberships(tenant_id, user_id),
  foreign key (tenant_id, department_id) references departments(tenant_id, department_id)
);
create unique index if not exists uq_org_department_membership_primary
  on org_department_memberships(tenant_id, user_id) where is_primary and status='ACTIVE';

create table if not exists org_group_memberships (
  tenant_id varchar(64) not null,
  membership_id varchar(128) not null,
  user_id varchar(128) not null,
  group_id varchar(128) not null,
  membership_role varchar(32) not null,
  effective_at timestamptz not null,
  expires_at timestamptz,
  status varchar(32) not null,
  version bigint not null default 1,
  primary key (tenant_id, membership_id),
  unique (tenant_id, user_id, group_id),
  foreign key (tenant_id, user_id) references org_tenant_memberships(tenant_id, user_id),
  foreign key (tenant_id, group_id) references organization_groups(tenant_id, group_id)
);

create table if not exists iam_tenant_security_epochs (
  tenant_id varchar(64) primary key,
  security_epoch bigint not null default 0,
  updated_at timestamptz not null default now(),
  updated_by varchar(128) not null default 'system',
  foreign key (tenant_id) references tenants(tenant_id)
);
insert into iam_tenant_security_epochs(tenant_id) select tenant_id from tenants on conflict do nothing;

create index if not exists idx_org_tenant_memberships_user_status on org_tenant_memberships(tenant_id, user_id, status);
create index if not exists idx_org_department_closure_descendant on org_department_closure(tenant_id, descendant_department_id, depth);
create index if not exists idx_org_department_revisions_validity on org_department_revisions(tenant_id, department_id, valid_from desc);
create index if not exists idx_org_department_memberships_user on org_department_memberships(tenant_id, user_id, status, effective_at);
create index if not exists idx_org_group_memberships_user on org_group_memberships(tenant_id, user_id, status, effective_at);
