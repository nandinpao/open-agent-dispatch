-- P4RA-J: runtime fencing, late-result quarantine, materialized scope snapshots and department revision cutover.

create table if not exists resource_scope_materialized_snapshots (
  tenant_id varchar(64) not null,
  snapshot_id varchar(128) not null,
  snapshot_kind varchar(32) not null,
  principal_type varchar(32) not null,
  principal_id varchar(128) not null,
  permission_code varchar(160) not null,
  resource_type varchar(64) not null,
  strategy varchar(64) not null,
  exact_department_ids varchar(128)[] not null default '{}',
  subtree_department_root_ids varchar(128)[] not null default '{}',
  group_ids varchar(128)[] not null default '{}',
  explicit_resource_ids varchar(160)[] not null default '{}',
  excluded_resource_ids varchar(160)[] not null default '{}',
  denied_department_ids varchar(128)[] not null default '{}',
  denied_subtree_department_root_ids varchar(128)[] not null default '{}',
  denied_group_ids varchar(128)[] not null default '{}',
  maximum_visibility varchar(32) not null,
  policy_catalog_version bigint not null,
  policy_revision bigint not null,
  policy_content_hash varchar(128) not null,
  global_epoch bigint not null,
  tenant_epoch bigint not null,
  principal_epoch bigint not null,
  resource_epoch bigint not null,
  epoch_policy_catalog_version bigint not null,
  department_revision bigint not null,
  plan_hash varchar(64) not null,
  snapshot_status varchar(24) not null default 'PREPARED',
  prepared_at timestamptz not null,
  activated_at timestamptz,
  retired_at timestamptz,
  version bigint not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(tenant_id,snapshot_id),
  foreign key(tenant_id) references tenants(tenant_id),
  check(snapshot_kind in ('TASK_LIST','INTEGRATION_LIST')),
  check(snapshot_status in ('PREPARED','ACTIVE','RETIRED','INVALIDATED')),
  check(maximum_visibility in ('NONE','METADATA','SUMMARY','STANDARD','SENSITIVE','FULL','SECRET_METADATA')),
  check(policy_catalog_version>=0 and policy_revision>=0),
  check(global_epoch>=0 and tenant_epoch>=0 and principal_epoch>=0 and resource_epoch>=0
        and epoch_policy_catalog_version>=0 and department_revision>=0),
  check(plan_hash~'^[0-9a-f]{64}$'),
  check((snapshot_status='ACTIVE' and activated_at is not null) or snapshot_status<>'ACTIVE')
);
create unique index if not exists uq_p4ra_j_prepared_scope_snapshot
 on resource_scope_materialized_snapshots(
  tenant_id,snapshot_kind,principal_type,principal_id,permission_code,resource_type,
  policy_catalog_version,policy_revision,policy_content_hash,global_epoch,tenant_epoch,
  principal_epoch,resource_epoch,epoch_policy_catalog_version,department_revision)
 where snapshot_status='PREPARED';

create unique index if not exists uq_p4ra_j_active_scope_snapshot
 on resource_scope_materialized_snapshots(
  tenant_id,snapshot_kind,principal_type,principal_id,permission_code,resource_type,
  policy_catalog_version,policy_revision,policy_content_hash,global_epoch,tenant_epoch,
  principal_epoch,resource_epoch,epoch_policy_catalog_version,department_revision)
 where snapshot_status='ACTIVE';
create index if not exists idx_p4ra_j_scope_snapshot_lookup
 on resource_scope_materialized_snapshots(tenant_id,snapshot_kind,principal_type,principal_id,permission_code,resource_type,department_revision,snapshot_status);

create table if not exists resource_department_revision_cutovers (
  tenant_id varchar(64) not null,
  cutover_id varchar(128) not null,
  department_revision bigint not null,
  cutover_status varchar(24) not null,
  prepared_snapshot_count bigint not null,
  prepared_by varchar(128) not null,
  activated_by varchar(128) not null default '',
  correlation_id varchar(128) not null,
  prepared_at timestamptz not null,
  activated_at timestamptz,
  retired_at timestamptz,
  failure_reason_code varchar(160) not null default '',
  version bigint not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(tenant_id,cutover_id),
  foreign key(tenant_id) references tenants(tenant_id),
  check(department_revision>=0 and prepared_snapshot_count>=0),
  check(cutover_status in ('PREPARING','ACTIVE','RETIRED','FAILED')),
  check((cutover_status='ACTIVE' and activated_at is not null and activated_by<>'') or cutover_status<>'ACTIVE')
);
create unique index if not exists uq_p4ra_j_active_department_cutover
 on resource_department_revision_cutovers(tenant_id) where cutover_status='ACTIVE';

create table if not exists resource_department_revision_cutover_events (
  tenant_id varchar(64) not null,
  event_id varchar(128) not null,
  cutover_id varchar(128) not null,
  department_revision bigint not null,
  event_type varchar(32) not null,
  actor_id varchar(128) not null,
  reason_code varchar(160) not null,
  correlation_id varchar(128) not null,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now(),
  primary key(tenant_id,event_id),
  foreign key(tenant_id,cutover_id) references resource_department_revision_cutovers(tenant_id,cutover_id),
  check(event_type in ('PREPARED','ACTIVATED','RETIRED','FAILED'))
);

create table if not exists resource_runtime_late_result_quarantines (
  tenant_id varchar(64) not null,
  quarantine_id varchar(128) not null,
  submission_id varchar(128) not null,
  lease_id varchar(128) not null,
  presented_fencing_version bigint not null,
  resource_type varchar(64) not null,
  resource_id varchar(160) not null,
  assignment_id varchar(128) not null default '',
  attempt_no integer,
  payload_hash varchar(128) not null,
  lease_status varchar(32) not null,
  reason_code varchar(160) not null,
  quarantine_status varchar(32) not null default 'OPEN',
  correlation_id varchar(128) not null,
  observed_at timestamptz not null,
  quarantined_at timestamptz not null,
  resolved_by varchar(128) not null default '',
  resolution_reason text not null default '',
  resolved_at timestamptz,
  version bigint not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(tenant_id,quarantine_id),
  unique(tenant_id,submission_id),
  foreign key(tenant_id,lease_id) references resource_runtime_authorization_leases(tenant_id,lease_id),
  check(presented_fencing_version>=0),
  check(payload_hash~'^[0-9a-f]{64}$'),
  check(attempt_no is null or attempt_no>=0),
  check(lease_status in ('ACTIVE','RECHECK_REQUIRED','REVOKED','EXPIRED','FENCED','REVOCATION_PENDING','COMPLETED')),
  check(quarantine_status in ('OPEN','ACCEPTED_AS_EVIDENCE','DISCARDED')),
  check((quarantine_status='OPEN' and resolved_at is null) or
        (quarantine_status<>'OPEN' and resolved_at is not null and resolved_by<>''))
);
create index if not exists idx_p4ra_j_late_result_open
 on resource_runtime_late_result_quarantines(tenant_id,quarantine_status,quarantined_at desc);

create table if not exists resource_runtime_late_result_quarantine_events (
  tenant_id varchar(64) not null,
  event_id varchar(128) not null,
  quarantine_id varchar(128) not null,
  event_type varchar(32) not null,
  actor_id varchar(128) not null,
  reason_code varchar(160) not null,
  correlation_id varchar(128) not null,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now(),
  primary key(tenant_id,event_id),
  foreign key(tenant_id,quarantine_id) references resource_runtime_late_result_quarantines(tenant_id,quarantine_id),
  check(event_type in ('QUARANTINED','ACCEPTED_AS_EVIDENCE','DISCARDED'))
);

alter table resource_scope_materialized_snapshots enable row level security;
alter table resource_scope_materialized_snapshots force row level security;
alter table resource_department_revision_cutovers enable row level security;
alter table resource_department_revision_cutovers force row level security;
alter table resource_department_revision_cutover_events enable row level security;
alter table resource_department_revision_cutover_events force row level security;
alter table resource_runtime_late_result_quarantines enable row level security;
alter table resource_runtime_late_result_quarantines force row level security;
alter table resource_runtime_late_result_quarantine_events enable row level security;
alter table resource_runtime_late_result_quarantine_events force row level security;

drop policy if exists tenant_isolation on resource_scope_materialized_snapshots;
create policy tenant_isolation on resource_scope_materialized_snapshots using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
drop policy if exists tenant_isolation on resource_department_revision_cutovers;
create policy tenant_isolation on resource_department_revision_cutovers using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
drop policy if exists tenant_isolation on resource_department_revision_cutover_events;
create policy tenant_isolation on resource_department_revision_cutover_events using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
drop policy if exists tenant_isolation on resource_runtime_late_result_quarantines;
create policy tenant_isolation on resource_runtime_late_result_quarantines using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
drop policy if exists tenant_isolation on resource_runtime_late_result_quarantine_events;
create policy tenant_isolation on resource_runtime_late_result_quarantine_events using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

drop trigger if exists trg_p4ra_j_cutover_event_immutable on resource_department_revision_cutover_events;
create trigger trg_p4ra_j_cutover_event_immutable before update or delete on resource_department_revision_cutover_events
 for each row execute function p4ra_reject_append_only_mutation();
drop trigger if exists trg_p4ra_j_late_result_event_immutable on resource_runtime_late_result_quarantine_events;
create trigger trg_p4ra_j_late_result_event_immutable before update or delete on resource_runtime_late_result_quarantine_events
 for each row execute function p4ra_reject_append_only_mutation();


insert into permission_point_catalog(permission_point,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed)
values
 ('resource.runtime.quarantine.read','TENANT','READ','Read secret-free runtime late-result quarantine evidence.','HIGH',array['TENANT'],true),
 ('resource.runtime.quarantine.manage','TENANT','MANAGE','Resolve runtime late-result quarantine evidence.','CRITICAL',array['TENANT'],true),
 ('resource.scope.cutover.read','TENANT','READ','Read active department-revision scope cutover evidence.','HIGH',array['TENANT'],true),
 ('resource.scope.cutover.manage','TENANT','MANAGE','Prepare and atomically activate materialized scope snapshots.','CRITICAL',array['TENANT'],true),
 ('resource.cache.metrics.read','TENANT','READ','Read Resource Authorization cache coordination metrics.','MEDIUM',array['TENANT'],true)
on conflict(permission_point) do update set resource_type=excluded.resource_type,action_code=excluded.action_code,
 description=excluded.description,risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,
 system_managed=true,active=true,version=permission_point_catalog.version+1;

insert into reason_code_catalog(reason_code,version,category,http_status,retryable,message_template,active)
values
 ('RUNTIME_RESULT_FENCING_VERSION_MISMATCH',1,'RUNTIME',409,false,'The result fencing version no longer owns the resource.',true),
 ('RUNTIME_RESULT_LEASE_NOT_ACTIVE',1,'RUNTIME',409,false,'The runtime authorization lease is not active.',true),
 ('RUNTIME_RESULT_BINDING_MISMATCH',1,'RUNTIME',409,false,'The result assignment, attempt or resource binding does not match the lease.',true),
 ('RUNTIME_LATE_RESULT_QUARANTINED',1,'RUNTIME',202,false,'The late or stale result was quarantined as immutable evidence.',true),
 ('SCOPE_SNAPSHOT_REVISION_NOT_ACTIVE',1,'RESOURCE_ACCESS',409,true,'The materialized scope revision is not active.',true),
 ('DEPARTMENT_REVISION_CUTOVER_CONFLICT',1,'RESOURCE_ACCESS',409,true,'The department revision cutover changed concurrently.',true)
on conflict(reason_code) do update set http_status=excluded.http_status,category=excluded.category,retryable=excluded.retryable,message_template=excluded.message_template,active=true,version=reason_code_catalog.version+1;

comment on table resource_scope_materialized_snapshots is 'P4RA-J immutable SQL-ready scope plan for an exact policy/security/department revision namespace.';
comment on table resource_runtime_late_result_quarantines is 'Secret-free late/stale runtime result quarantine; payload content is never persisted.';
