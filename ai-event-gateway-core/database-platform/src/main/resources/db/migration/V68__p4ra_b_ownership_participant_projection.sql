-- P4RA-B: ownership / participant projection, reconciliation, orphan repair and transfer audit baseline.

alter table resource_descriptors add column if not exists visibility_policy_version bigint not null default 0;
alter table resource_descriptors add column if not exists source_resolved_at timestamptz;
alter table resource_descriptors add column if not exists last_projected_at timestamptz;
alter table resource_descriptors add column if not exists last_reconciled_at timestamptz;
alter table resource_descriptors add column if not exists projection_status varchar(32) not null default 'CURRENT';
alter table resource_descriptors drop constraint if exists ck_resource_descriptors_projection_status;
alter table resource_descriptors add constraint ck_resource_descriptors_projection_status
  check (projection_status in ('CURRENT','STALE','MISSING','ORPHANED','ERROR'));

create table if not exists resource_participants (
  tenant_id varchar(64) not null,
  participant_id varchar(128) not null,
  resource_type varchar(64) not null,
  resource_id varchar(128) not null,
  participant_type varchar(32) not null,
  participant_ref_id varchar(128) not null,
  participant_role varchar(32) not null,
  visibility_level varchar(32) not null,
  allowed_permission_codes text[] not null default '{}',
  valid_from timestamptz not null,
  valid_to timestamptz,
  source_authority varchar(64) not null,
  source_version bigint not null,
  participant_status varchar(32) not null,
  projected_at timestamptz not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, participant_id),
  foreign key (tenant_id, resource_type, resource_id) references resource_descriptors(tenant_id, resource_type, resource_id) on delete cascade,
  unique (tenant_id, resource_type, resource_id, participant_type, participant_ref_id, participant_role),
  check (participant_type in ('USER','DEPARTMENT','GROUP','SERVICE_ACCOUNT','AGENT_ASSIGNMENT')),
  check (participant_role in ('OWNER','STEWARD','CUSTODIAN','REQUESTER','EXECUTOR','SUPPORTER','OBSERVER','AUDITOR','APPROVER','CREATOR')),
  check (visibility_level in ('NONE','METADATA','SUMMARY','STANDARD','SENSITIVE','FULL','SECRET_METADATA')),
  check (participant_status in ('ACTIVE','EXPIRED','REVOKED','DISABLED')),
  check (source_version >= 0),
  check (valid_to is null or valid_to > valid_from)
);

create table if not exists resource_projection_outbox_events (
  tenant_id varchar(64) not null,
  event_id varchar(128) not null,
  aggregate_type varchar(64) not null default 'RESOURCE_DESCRIPTOR',
  resource_type varchar(64) not null,
  resource_id varchar(128) not null,
  event_type varchar(96) not null,
  source_event_id varchar(128),
  payload_hash varchar(128) not null,
  event_status varchar(32) not null default 'PENDING',
  attempt_count integer not null default 0,
  available_at timestamptz not null,
  published_at timestamptz,
  last_error text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, event_id),
  foreign key (tenant_id, resource_type, resource_id) references resource_descriptors(tenant_id, resource_type, resource_id) on delete cascade,
  check (event_status in ('PENDING','CLAIMED','PUBLISHED','FAILED','DEAD_LETTER')),
  check (attempt_count >= 0)
);

create table if not exists resource_descriptor_reconciliations (
  tenant_id varchar(64) not null,
  reconciliation_id varchar(128) not null,
  resource_type varchar(64) not null,
  resource_id varchar(128) not null,
  reconciliation_status varchar(32) not null,
  projected_hash varchar(128),
  authority_hash varchar(128),
  reason_code varchar(96),
  auto_repaired boolean not null default false,
  reconciled_at timestamptz not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id, reconciliation_id),
  check (reconciliation_status in ('MATCHED','MISSING_PROJECTION','STALE_PROJECTION','AUTHORITY_MISMATCH','ORPHANED','REPAIRED','FAILED'))
);

create table if not exists resource_orphan_repairs (
  tenant_id varchar(64) not null,
  repair_id varchar(128) not null,
  resource_type varchar(64) not null,
  resource_id varchar(128) not null,
  repair_status varchar(32) not null,
  reason_code varchar(96) not null,
  proposed_owner_department_id varchar(128),
  proposed_owner_group_id varchar(128),
  proposed_steward_user_id varchar(128),
  assigned_to varchar(128),
  detected_at timestamptz not null,
  resolved_at timestamptz,
  resolved_by varchar(128),
  resource_version bigint not null,
  version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, repair_id),
  foreign key (tenant_id, resource_type, resource_id) references resource_descriptors(tenant_id, resource_type, resource_id) on delete cascade,
  check (repair_status in ('OPEN','ASSIGNED','RESOLVED','WAIVED')),
  check (resource_version >= 0 and version > 0)
);
create unique index if not exists uq_resource_orphan_repair_open
  on resource_orphan_repairs(tenant_id,resource_type,resource_id)
  where repair_status in ('OPEN','ASSIGNED');

create table if not exists resource_ownership_transfer_audits (
  tenant_id varchar(64) not null,
  transfer_id varchar(128) not null,
  resource_type varchar(64) not null,
  resource_id varchar(128) not null,
  expected_resource_version bigint not null,
  resulting_resource_version bigint not null,
  previous_owner_department_id varchar(128),
  previous_owner_group_id varchar(128),
  previous_steward_user_id varchar(128),
  new_owner_department_id varchar(128),
  new_owner_group_id varchar(128),
  new_steward_user_id varchar(128),
  actor_id varchar(128) not null,
  reason text not null,
  correlation_id varchar(128) not null,
  idempotency_key varchar(256) not null,
  source_event_id varchar(128) not null,
  transferred_at timestamptz not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id, transfer_id),
  unique (tenant_id, idempotency_key),
  check (expected_resource_version > 0 and resulting_resource_version > expected_resource_version)
);

create index if not exists idx_resource_participants_resource
  on resource_participants(tenant_id,resource_type,resource_id,participant_status);
create index if not exists idx_resource_participants_principal
  on resource_participants(tenant_id,participant_type,participant_ref_id,resource_type);
create index if not exists idx_resource_projection_outbox_pending
  on resource_projection_outbox_events(event_status,available_at,tenant_id);
create index if not exists idx_resource_descriptor_reconciliation_resource
  on resource_descriptor_reconciliations(tenant_id,resource_type,resource_id,reconciled_at desc);
create index if not exists idx_resource_orphan_repairs_queue
  on resource_orphan_repairs(tenant_id,repair_status,detected_at);

create or replace function p4ra_reject_append_only_mutation()
returns trigger language plpgsql as $$
begin
  raise exception 'P4RA_APPEND_ONLY_RECORD_IMMUTABLE' using errcode='55000';
end $$;

drop trigger if exists trg_resource_reconciliation_immutable on resource_descriptor_reconciliations;
create trigger trg_resource_reconciliation_immutable before update or delete on resource_descriptor_reconciliations
for each row execute function p4ra_reject_append_only_mutation();
drop trigger if exists trg_resource_ownership_transfer_audit_immutable on resource_ownership_transfer_audits;
create trigger trg_resource_ownership_transfer_audit_immutable before update or delete on resource_ownership_transfer_audits
for each row execute function p4ra_reject_append_only_mutation();

do $$
declare table_name text;
begin
  foreach table_name in array array[
    'resource_participants','resource_projection_outbox_events','resource_descriptor_reconciliations',
    'resource_orphan_repairs','resource_ownership_transfer_audits'
  ] loop
    execute format('alter table %I enable row level security',table_name);
    execute format('alter table %I force row level security',table_name);
    execute format('drop policy if exists tenant_isolation on %I',table_name);
    execute format('create policy tenant_isolation on %I using (tenant_id = iam_current_tenant_id()) with check (tenant_id = iam_current_tenant_id())',table_name);
  end loop;
end $$;

comment on table resource_participants is 'Versioned one-way participant projection; the originating Domain remains authority.';
comment on table resource_projection_outbox_events is 'Transactional Resource Access projection events for cache invalidation and reconciliation.';
comment on table resource_descriptor_reconciliations is 'Append-only comparison evidence between Domain authority and Resource Access projection.';
comment on table resource_orphan_repairs is 'Fail-closed repair queue for resources whose required owner is absent.';
comment on table resource_ownership_transfer_audits is 'Append-only evidence of ownership mutations performed through a canonical Domain authority.';
