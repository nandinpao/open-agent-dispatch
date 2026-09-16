create table if not exists iam_legacy_identity_migrations (
  migration_id varchar(96) primary key,
  legacy_user_id varchar(128) not null,
  legacy_username varchar(256) not null,
  target_user_id varchar(128) not null,
  target_tenant_id varchar(128) not null,
  legacy_roles text[] not null default '{}',
  target_role_codes text[] not null default '{}',
  migration_status varchar(32) not null,
  password_material_migrated boolean not null default false,
  reset_required boolean not null default true,
  error_code varchar(128),
  correlation_id varchar(128) not null,
  created_at timestamptz not null default now(),
  completed_at timestamptz,
  version bigint not null default 1,
  unique(legacy_user_id,target_tenant_id)
);
create table if not exists iam_auth_cutover_events (
  cutover_event_id varchar(96) primary key,
  from_mode varchar(32) not null,
  to_mode varchar(32) not null,
  outcome varchar(32) not null,
  runtime_exit_gate_evidence varchar(512) not null,
  migration_report_ref varchar(512),
  rollback_deadline timestamptz,
  executed_by varchar(128) not null,
  reason varchar(1000) not null,
  correlation_id varchar(128) not null,
  occurred_at timestamptz not null default now()
);
