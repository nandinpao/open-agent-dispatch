-- Phase 0B Expand: Tenant and organizational boundary foundation.
--
-- This migration only expands the schema. Existing rows are not rejected here.
-- V9 performs deterministic backfill and records ambiguity; V10 validates and
-- enforces tenant-aware constraints.

create table if not exists tenants (
  tenant_id varchar(64) not null,
  display_name varchar(255) not null,
  status varchar(32) not null default 'ACTIVE',
  metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id)
);

create table if not exists departments (
  tenant_id varchar(64) not null,
  department_id varchar(128) not null,
  department_code varchar(128) not null,
  department_name varchar(255) not null,
  description text,
  status varchar(32) not null default 'ACTIVE',
  metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, department_id),
  unique (tenant_id, department_code),
  constraint fk_departments_tenant
    foreign key (tenant_id) references tenants(tenant_id)
);

-- "groups" is avoided as a physical identifier because GROUP/GROUPS are SQL
-- grammar terms. The canonical product term remains Group.
create table if not exists organization_groups (
  tenant_id varchar(64) not null,
  group_id varchar(128) not null,
  group_code varchar(128) not null,
  group_name varchar(255) not null,
  group_type varchar(32) not null default 'COLLABORATION',
  description text,
  status varchar(32) not null default 'ACTIVE',
  metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, group_id),
  unique (tenant_id, group_code),
  constraint fk_organization_groups_tenant
    foreign key (tenant_id) references tenants(tenant_id)
);

create table if not exists service_domains (
  tenant_id varchar(64) not null,
  service_domain_id varchar(128) not null,
  domain_code varchar(128) not null,
  domain_name varchar(255) not null,
  description text,
  status varchar(32) not null default 'ACTIVE',
  metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, service_domain_id),
  unique (tenant_id, domain_code),
  constraint fk_service_domains_tenant
    foreign key (tenant_id) references tenants(tenant_id)
);

create table if not exists trust_zones (
  tenant_id varchar(64) not null,
  trust_zone_id varchar(128) not null,
  trust_zone_code varchar(128) not null,
  trust_zone_name varchar(255) not null,
  isolation_mode varchar(32) not null default 'PER_TRUST_ZONE',
  sensitivity_ceiling varchar(32) not null default 'INTERNAL',
  description text,
  status varchar(32) not null default 'ACTIVE',
  metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, trust_zone_id),
  unique (tenant_id, trust_zone_code),
  constraint fk_trust_zones_tenant
    foreign key (tenant_id) references tenants(tenant_id)
);

create table if not exists department_group_bindings (
  tenant_id varchar(64) not null,
  department_id varchar(128) not null,
  group_id varchar(128) not null,
  binding_role varchar(32) not null default 'MEMBER',
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, department_id, group_id),
  constraint fk_department_group_bindings_department
    foreign key (tenant_id, department_id)
    references departments(tenant_id, department_id),
  constraint fk_department_group_bindings_group
    foreign key (tenant_id, group_id)
    references organization_groups(tenant_id, group_id)
);

create table if not exists department_service_domain_bindings (
  tenant_id varchar(64) not null,
  department_id varchar(128) not null,
  service_domain_id varchar(128) not null,
  ownership_role varchar(32) not null default 'OWNER',
  is_primary boolean not null default false,
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, department_id, service_domain_id),
  constraint fk_department_domain_bindings_department
    foreign key (tenant_id, department_id)
    references departments(tenant_id, department_id),
  constraint fk_department_domain_bindings_domain
    foreign key (tenant_id, service_domain_id)
    references service_domains(tenant_id, service_domain_id)
);

create table if not exists group_service_domain_bindings (
  tenant_id varchar(64) not null,
  group_id varchar(128) not null,
  service_domain_id varchar(128) not null,
  participation_role varchar(32) not null default 'SUPPORTER',
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, group_id, service_domain_id),
  constraint fk_group_domain_bindings_group
    foreign key (tenant_id, group_id)
    references organization_groups(tenant_id, group_id),
  constraint fk_group_domain_bindings_domain
    foreign key (tenant_id, service_domain_id)
    references service_domains(tenant_id, service_domain_id)
);

create table if not exists trust_zone_department_bindings (
  tenant_id varchar(64) not null,
  trust_zone_id varchar(128) not null,
  department_id varchar(128) not null,
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, trust_zone_id, department_id),
  constraint fk_trust_zone_department_bindings_zone
    foreign key (tenant_id, trust_zone_id)
    references trust_zones(tenant_id, trust_zone_id),
  constraint fk_trust_zone_department_bindings_department
    foreign key (tenant_id, department_id)
    references departments(tenant_id, department_id)
);

create table if not exists tenant_backfill_conflicts (
  conflict_id varchar(128) not null,
  table_name varchar(128) not null,
  record_key varchar(512) not null,
  conflict_type varchar(64) not null,
  candidate_tenant_ids jsonb not null default '[]'::jsonb,
  details text,
  resolution_status varchar(32) not null default 'OPEN',
  resolved_tenant_id varchar(64),
  resolved_by varchar(128),
  resolved_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (conflict_id),
  unique (table_name, record_key, conflict_type)
);

-- Tenant expansion for Agent authority and runtime evidence.
alter table agents add column if not exists tenant_id varchar(64);
alter table agent_credentials add column if not exists tenant_id varchar(64);
alter table agent_capabilities add column if not exists tenant_id varchar(64);
alter table agent_runtime_capability_profiles add column if not exists tenant_id varchar(64);
alter table agent_runtime_capability_items add column if not exists tenant_id varchar(64);
alter table agent_runtime_load_snapshots add column if not exists tenant_id varchar(64);
alter table agent_runtime_descriptors add column if not exists tenant_id varchar(64);

-- Tenant expansion for Task lifecycle and evidence.
alter table task_dispatch_attempts add column if not exists tenant_id varchar(64);
alter table task_execution_attempts add column if not exists tenant_id varchar(64);
alter table task_callbacks add column if not exists tenant_id varchar(64);
alter table dispatch_attempt_history add column if not exists tenant_id varchar(64);
alter table task_issue_links add column if not exists tenant_id varchar(64);

-- Organization ownership fields. Group is optional; Department and Service
-- Domain become mandatory only after deterministic backfill in V10.
alter table agent_profiles add column if not exists owner_department_id varchar(128);
alter table agent_profiles add column if not exists owner_group_id varchar(128);
alter table agent_profiles add column if not exists service_domain_id varchar(128);
alter table agent_profiles add column if not exists trust_zone_id varchar(128);

alter table tasks add column if not exists owner_department_id varchar(128);
alter table tasks add column if not exists owner_group_id varchar(128);
alter table tasks add column if not exists requester_department_id varchar(128);
alter table tasks add column if not exists requester_group_id varchar(128);
alter table tasks add column if not exists requester_domain_id varchar(128);
alter table tasks add column if not exists executor_department_id varchar(128);
alter table tasks add column if not exists executor_group_id varchar(128);
alter table tasks add column if not exists executor_domain_id varchar(128);
alter table tasks add column if not exists visibility_policy varchar(32);
alter table tasks add column if not exists sensitivity_level varchar(32);

create index if not exists idx_departments_status
  on departments(tenant_id, status, department_name);
create index if not exists idx_organization_groups_status
  on organization_groups(tenant_id, status, group_name);
create index if not exists idx_service_domains_status
  on service_domains(tenant_id, status, domain_name);
create index if not exists idx_trust_zones_status
  on trust_zones(tenant_id, status, trust_zone_name);
create index if not exists idx_tenant_backfill_conflicts_open
  on tenant_backfill_conflicts(resolution_status, table_name, created_at);
create index if not exists idx_agents_tenant_status
  on agents(tenant_id, status, last_heartbeat_at desc);
create index if not exists idx_agent_credentials_tenant_agent
  on agent_credentials(tenant_id, agent_id, revoked_at, expires_at);
create index if not exists idx_task_issue_links_tenant_task
  on task_issue_links(tenant_id, task_id, updated_at desc);
