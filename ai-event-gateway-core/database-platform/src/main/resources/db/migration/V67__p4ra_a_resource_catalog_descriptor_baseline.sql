-- P4RA-A source baseline. These tables are Resource Access-owned projections and policy metadata.
-- Existing Task, A2A, IAM, Organization and Issue tables remain their canonical authorities.

create table if not exists resource_catalog (
  resource_type varchar(64) primary key,
  category varchar(32) not null,
  descriptor_authority varchar(64) not null,
  ownership_supported boolean not null,
  participants_supported boolean not null,
  field_visibility_supported boolean not null,
  runtime_lease_supported boolean not null,
  default_sensitivity varchar(32) not null,
  description varchar(512) not null,
  status varchar(16) not null default 'ACTIVE',
  catalog_version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (status in ('ACTIVE','DEPRECATED')),
  check (catalog_version > 0),
  check (default_sensitivity in ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED','SECRET'))
);

create table if not exists resource_descriptors (
  tenant_id varchar(64) not null,
  resource_type varchar(64) not null,
  resource_id varchar(128) not null,
  resource_key varchar(256),
  owner_department_id varchar(128),
  owner_group_id varchar(128),
  steward_user_id varchar(128),
  custodian_service_id varchar(128),
  requester_department_id varchar(128),
  executor_department_id varchar(128),
  parent_resource_type varchar(64),
  parent_resource_id varchar(128),
  root_resource_type varchar(64),
  root_resource_id varchar(128),
  sensitivity_level varchar(32) not null,
  maximum_visibility varchar(32) not null,
  visibility_policy_id varchar(128),
  security_state varchar(32) not null,
  ownership_version bigint not null,
  participant_version bigint not null,
  resource_version bigint not null,
  descriptor_authority varchar(64) not null,
  descriptor_hash varchar(128) not null,
  source_updated_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, resource_type, resource_id),
  foreign key (tenant_id) references tenants(tenant_id),
  foreign key (resource_type) references resource_catalog(resource_type),
  check (ownership_version >= 0 and participant_version >= 0 and resource_version >= 0),
  check (sensitivity_level in ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED','SECRET')),
  check (maximum_visibility in ('NONE','METADATA','SUMMARY','STANDARD','SENSITIVE','FULL','SECRET_METADATA')),
  check (security_state in ('NORMAL','RESTRICTED','QUARANTINED','LEGAL_HOLD','INVESTIGATION','ARCHIVED','DELETED','ORPHANED')),
  check ((parent_resource_type is null) = (parent_resource_id is null)),
  check ((root_resource_type is null) = (root_resource_id is null))
);

create table if not exists resource_ownership_snapshots (
  tenant_id varchar(64) not null,
  snapshot_id varchar(128) not null,
  resource_type varchar(64) not null,
  resource_id varchar(128) not null,
  owner_department_id varchar(128),
  owner_group_id varchar(128),
  steward_user_id varchar(128),
  custodian_service_id varchar(128),
  requester_department_id varchar(128),
  executor_department_id varchar(128),
  ownership_version bigint not null,
  source_authority varchar(64) not null,
  source_event_id varchar(128),
  valid_from timestamptz not null,
  valid_to timestamptz,
  content_hash varchar(128) not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id, snapshot_id),
  foreign key (tenant_id, resource_type, resource_id) references resource_descriptors(tenant_id, resource_type, resource_id),
  check (ownership_version >= 0),
  check (valid_to is null or valid_to > valid_from)
);

create table if not exists resource_access_policy_revisions (
  tenant_id varchar(64) not null,
  policy_revision bigint not null,
  catalog_version bigint not null,
  content_hash varchar(128) not null,
  status varchar(32) not null,
  activated_at timestamptz,
  created_at timestamptz not null default now(),
  created_by varchar(128) not null,
  primary key (tenant_id, policy_revision),
  foreign key (tenant_id) references tenants(tenant_id),
  check (policy_revision > 0 and catalog_version > 0),
  check (status in ('DRAFT','ACTIVE','RETIRED')),
  check (status <> 'ACTIVE' or activated_at is not null),
  check (status <> 'DRAFT' or activated_at is null)
);

create unique index if not exists uq_resource_access_policy_active
  on resource_access_policy_revisions(tenant_id) where status='ACTIVE';

create table if not exists resource_security_epochs (
  tenant_id varchar(64) not null,
  resource_type varchar(64) not null,
  resource_id varchar(128) not null,
  resource_security_epoch bigint not null default 0,
  updated_at timestamptz not null default now(),
  updated_by varchar(128) not null,
  primary key (tenant_id, resource_type, resource_id),
  foreign key (tenant_id, resource_type, resource_id) references resource_descriptors(tenant_id, resource_type, resource_id),
  check (resource_security_epoch >= 0)
);

create index if not exists idx_resource_descriptors_owner_department
  on resource_descriptors(tenant_id, owner_department_id, resource_type, updated_at desc);
create index if not exists idx_resource_descriptors_owner_group
  on resource_descriptors(tenant_id, owner_group_id, resource_type, updated_at desc);
create index if not exists idx_resource_descriptors_root
  on resource_descriptors(tenant_id, root_resource_type, root_resource_id, resource_type);
create index if not exists idx_resource_ownership_snapshots_resource
  on resource_ownership_snapshots(tenant_id, resource_type, resource_id, ownership_version desc);

create or replace function p4ra_reject_ownership_snapshot_mutation()
returns trigger language plpgsql as $$
begin
  raise exception 'RESOURCE_OWNERSHIP_SNAPSHOT_IMMUTABLE' using errcode='55000';
end $$;

drop trigger if exists trg_resource_ownership_snapshot_immutable on resource_ownership_snapshots;
create trigger trg_resource_ownership_snapshot_immutable before update or delete on resource_ownership_snapshots
for each row execute function p4ra_reject_ownership_snapshot_mutation();

-- Tenant hard boundary: runtime role has no BYPASSRLS and every tenant-owned baseline table fails closed without context.
do $$
declare table_name text;
begin
  foreach table_name in array array[
    'resource_descriptors','resource_ownership_snapshots','resource_access_policy_revisions','resource_security_epochs'
  ] loop
    execute format('alter table %I enable row level security', table_name);
    execute format('alter table %I force row level security', table_name);
    execute format('drop policy if exists tenant_isolation on %I', table_name);
    execute format('create policy tenant_isolation on %I using (tenant_id = iam_current_tenant_id()) with check (tenant_id = iam_current_tenant_id())', table_name);
  end loop;
end $$;


-- The global catalog is migration-owned reference data. Runtime receives read-only access even though
-- the shared bootstrap grants DML on newly created public tables by default.
do $$
begin
  if exists (select 1 from pg_roles where rolname='opendispatch_runtime') then
    revoke insert, update, delete on resource_catalog from opendispatch_runtime;
    grant select on resource_catalog to opendispatch_runtime;
  end if;
end $$;

comment on table resource_catalog is 'P4RA stable ResourceType catalog. Metadata only; grants no access.';
comment on table resource_descriptors is 'Server-resolved Resource Access projection. Domain modules remain ownership authorities.';
comment on table resource_ownership_snapshots is 'Append-only ownership history projection for explain and audit.';
comment on table resource_access_policy_revisions is 'Tenant-bound Resource Access policy version namespace.';
comment on table resource_security_epochs is 'Per-resource monotonic epoch used to reject stale allow decisions and leases.';

insert into resource_catalog(resource_type,category,descriptor_authority,ownership_supported,participants_supported,field_visibility_supported,runtime_lease_supported,default_sensitivity,description)
values
('TASK','TASK','TASK_DOMAIN',true,true,true,true,'CONFIDENTIAL','Dispatch task'),
('TASK_CHAIN','TASK','TASK_DOMAIN',true,true,true,false,'CONFIDENTIAL','Task relationship graph'),
('A2A_REQUEST','A2A','A2A_DOMAIN',true,true,true,true,'CONFIDENTIAL','A2A handoff request'),
('A2A_APPROVAL','A2A','A2A_DOMAIN',true,true,true,false,'RESTRICTED','A2A approval package'),
('TASK_CONTEXT_SNAPSHOT','TASK','TASK_DOMAIN',true,true,true,true,'RESTRICTED','Assignment-bound task context'),
('TASK_RESULT','TASK','TASK_DOMAIN',true,true,true,true,'CONFIDENTIAL','Task result'),
('TASK_ATTACHMENT','TASK','TASK_DOMAIN',true,true,true,true,'RESTRICTED','Task attachment'),
('AGENT','AGENT','AGENT_CONTROL',true,false,true,true,'RESTRICTED','Registered agent'),
('AGENT_POOL','AGENT','AGENT_CONTROL',true,true,true,false,'INTERNAL','Agent pool'),
('AGENT_SERVICE_SCOPE','AGENT','AGENT_CONTROL',true,false,true,false,'RESTRICTED','Agent service scope'),
('AGENT_CREDENTIAL_METADATA','SECURITY','AGENT_CONTROL',true,false,true,true,'SECRET','Agent credential metadata'),
('ISSUE_CONNECTION','INTEGRATION','ISSUE_TRACKING',true,true,true,false,'RESTRICTED','Issue provider connection'),
('ISSUE_PRINCIPAL','INTEGRATION','ISSUE_TRACKING',true,true,true,false,'RESTRICTED','Issue principal metadata'),
('ISSUE_CREDENTIAL_METADATA','SECURITY','ISSUE_TRACKING',true,false,true,false,'SECRET','Issue credential metadata'),
('ISSUE_PROJECT_MAPPING','INTEGRATION','ISSUE_TRACKING',true,true,true,false,'CONFIDENTIAL','Provider project mapping'),
('TASK_ISSUE_LINK','ISSUE','ISSUE_TRACKING',true,true,true,false,'CONFIDENTIAL','Task issue link'),
('ISSUE_CONTEXT_SNAPSHOT','ISSUE','ISSUE_TRACKING',true,true,true,false,'CONFIDENTIAL','Task-bound issue snapshot'),
('ISSUE_ATTACHMENT','ISSUE','ISSUE_TRACKING',true,true,true,false,'RESTRICTED','Issue attachment'),
('ISSUE_CONFLICT','ISSUE','ISSUE_TRACKING',true,true,true,false,'RESTRICTED','Issue conflict'),
('ISSUE_DEAD_LETTER','INTEGRATION','ISSUE_TRACKING',true,true,true,false,'RESTRICTED','Issue dead letter'),
('ISSUE_TOPOLOGY','ISSUE','ISSUE_TRACKING',true,true,true,false,'CONFIDENTIAL','Issue topology'),
('TENANT','IDENTITY','IAM',true,false,true,false,'RESTRICTED','Tenant'),
('DEPARTMENT','ORGANIZATION','ORGANIZATION_ACCESS',true,true,true,false,'INTERNAL','Department'),
('GROUP','ORGANIZATION','ORGANIZATION_ACCESS',true,true,true,false,'INTERNAL','Group'),
('USER','IDENTITY','IAM',true,false,true,false,'RESTRICTED','User metadata'),
('ROLE','IDENTITY','IAM',true,false,true,false,'RESTRICTED','Role metadata'),
('SERVICE_ACCOUNT','SECURITY','IAM',true,false,true,true,'SECRET','Service account metadata'),
('ACCESS_TOKEN_METADATA','SECURITY','IAM',true,false,true,true,'SECRET','Access token metadata')
on conflict(resource_type) do update set
 category=excluded.category, descriptor_authority=excluded.descriptor_authority,
 ownership_supported=excluded.ownership_supported, participants_supported=excluded.participants_supported,
 field_visibility_supported=excluded.field_visibility_supported, runtime_lease_supported=excluded.runtime_lease_supported,
 default_sensitivity=excluded.default_sensitivity, description=excluded.description, updated_at=now();
