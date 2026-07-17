-- Phase 25 live hotfix: align runtime_resources uniqueness with AgentAssignmentDao upsert.
-- Safe to run on an existing local/dev database before rebuilding the Core image.

create table if not exists runtime_resources (
  tenant_id varchar(64) not null,
  runtime_id varchar(128) not null,
  metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, runtime_id)
);

alter table runtime_resources add column if not exists tenant_id varchar(64);
alter table runtime_resources add column if not exists runtime_id varchar(128);
update runtime_resources set tenant_id = 'tenant-a' where tenant_id is null;
update runtime_resources set runtime_id = coalesce(runtime_code, runtime_name, md5(random()::text || clock_timestamp()::text)) where runtime_id is null;
alter table runtime_resources alter column tenant_id set not null;
alter table runtime_resources alter column runtime_id set not null;

alter table runtime_resources add column if not exists runtime_code varchar(128);
update runtime_resources set runtime_code = runtime_id where runtime_code is null;
alter table runtime_resources add column if not exists runtime_name varchar(255);
alter table runtime_resources add column if not exists runtime_type varchar(64);
alter table runtime_resources add column if not exists connector_type varchar(128);
alter table runtime_resources add column if not exists execution_host varchar(255);
alter table runtime_resources add column if not exists environment varchar(64);
alter table runtime_resources add column if not exists region varchar(64);
alter table runtime_resources add column if not exists zone varchar(64);
alter table runtime_resources add column if not exists trust_status varchar(32);
alter table runtime_resources add column if not exists status varchar(32) not null default 'ACTIVE';
alter table runtime_resources add column if not exists capacity_limit int;
alter table runtime_resources add column if not exists metadata_json jsonb not null default '{}'::jsonb;
alter table runtime_resources add column if not exists created_at timestamptz not null default now();
alter table runtime_resources add column if not exists updated_at timestamptz not null default now();

create unique index if not exists ux_runtime_resources_tenant_runtime on runtime_resources(tenant_id, runtime_id);
create unique index if not exists ux_runtime_resources_code on runtime_resources(tenant_id, runtime_code) where runtime_code is not null;
