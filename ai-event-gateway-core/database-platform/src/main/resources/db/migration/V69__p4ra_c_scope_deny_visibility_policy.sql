-- P4RA-C: scope grants, explicit denies, visibility policy, principal clearance and security-state audit.
-- These tables are governance authorities only. Business authorization remains disabled until P4RA-D.

create table if not exists resource_scope_grants (
  tenant_id varchar(64) not null,
  scope_grant_id varchar(128) not null,
  principal_type varchar(32) not null,
  principal_id varchar(128) not null,
  permission_code varchar(160) not null,
  resource_type varchar(64) not null,
  scope_type varchar(32) not null,
  scope_ref_id varchar(128),
  visibility_level varchar(32) not null,
  valid_from timestamptz not null,
  valid_to timestamptz,
  grant_source varchar(48) not null,
  grant_reason text not null,
  approved_by varchar(128),
  grant_state varchar(32) not null,
  idempotency_key varchar(256) not null,
  version bigint not null default 1,
  created_at timestamptz not null,
  created_by varchar(128) not null,
  updated_at timestamptz not null,
  updated_by varchar(128) not null,
  primary key (tenant_id,scope_grant_id),
  unique (tenant_id,idempotency_key),
  foreign key (tenant_id) references tenants(tenant_id),
  foreign key (resource_type) references resource_catalog(resource_type),
  foreign key (permission_code) references permission_point_catalog(permission_point),
  check (principal_type in ('USER','DEPARTMENT','GROUP','SERVICE_ACCOUNT')),
  check (scope_type in ('TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP','RESOURCE','RESOURCE_TREE','TASK_CHAIN','PARTICIPANT','OWNER','CREATED_BY_ME','ASSIGNED_TO_ME','AUDIT_WINDOW','EXPLICIT_SET')),
  check (visibility_level in ('NONE','METADATA','SUMMARY','STANDARD','SENSITIVE','FULL','SECRET_METADATA')),
  check (grant_source in ('MANUAL','RESOURCE_SHARE','MIGRATION_COMPATIBILITY','BREAK_GLASS','SYSTEM_POLICY')),
  check (grant_state in ('DRAFT','PENDING_APPROVAL','ACTIVE','SUSPENDED','EXPIRED','REVOKED')),
  check (version > 0),
  check (valid_to is null or valid_to > valid_from),
  check (scope_type <> 'TENANT' or scope_ref_id = tenant_id),
  check (scope_type in ('OWNER','PARTICIPANT','CREATED_BY_ME','ASSIGNED_TO_ME') or nullif(scope_ref_id,'') is not null),
  check (grant_state <> 'ACTIVE' or nullif(approved_by,'') is not null),
  check (approved_by is null or approved_by <> created_by)
);

create table if not exists resource_scope_grant_audits (
  tenant_id varchar(64) not null,
  audit_id varchar(128) not null,
  scope_grant_id varchar(128) not null,
  previous_state varchar(32),
  resulting_state varchar(32) not null,
  actor_id varchar(128) not null,
  reason text not null,
  correlation_id varchar(128) not null,
  idempotency_key varchar(256) not null,
  resulting_version bigint not null,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id,audit_id),
  unique (tenant_id,idempotency_key),
  foreign key (tenant_id,scope_grant_id) references resource_scope_grants(tenant_id,scope_grant_id),
  check (previous_state is null or previous_state in ('DRAFT','PENDING_APPROVAL','ACTIVE','SUSPENDED','EXPIRED','REVOKED')),
  check (resulting_state in ('DRAFT','PENDING_APPROVAL','ACTIVE','SUSPENDED','EXPIRED','REVOKED')),
  check (resulting_version > 0)
);

create table if not exists resource_scope_denies (
  tenant_id varchar(64) not null,
  scope_deny_id varchar(128) not null,
  principal_type varchar(32) not null,
  principal_id varchar(128) not null,
  permission_code varchar(160),
  resource_type varchar(64) not null,
  scope_type varchar(32) not null,
  scope_ref_id varchar(128),
  deny_reason text not null,
  severity varchar(24) not null,
  valid_from timestamptz not null,
  valid_to timestamptz,
  created_by varchar(128) not null,
  approved_by varchar(128),
  deny_state varchar(32) not null,
  idempotency_key varchar(256) not null,
  version bigint not null default 1,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  updated_by varchar(128) not null,
  primary key (tenant_id,scope_deny_id),
  unique (tenant_id,idempotency_key),
  foreign key (tenant_id) references tenants(tenant_id),
  foreign key (resource_type) references resource_catalog(resource_type),
  foreign key (permission_code) references permission_point_catalog(permission_point),
  check (principal_type in ('USER','DEPARTMENT','GROUP','SERVICE_ACCOUNT')),
  check (scope_type in ('TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP','RESOURCE','RESOURCE_TREE','TASK_CHAIN','PARTICIPANT','OWNER','CREATED_BY_ME','ASSIGNED_TO_ME','AUDIT_WINDOW','EXPLICIT_SET')),
  check (severity in ('LOW','MEDIUM','HIGH','CRITICAL')),
  check (deny_state in ('DRAFT','PENDING_APPROVAL','ACTIVE','EXPIRED','REVOKED')),
  check (version > 0),
  check (valid_to is null or valid_to > valid_from),
  check (scope_type <> 'TENANT' or scope_ref_id = tenant_id),
  check (scope_type in ('OWNER','PARTICIPANT','CREATED_BY_ME','ASSIGNED_TO_ME') or nullif(scope_ref_id,'') is not null),
  check (deny_state <> 'ACTIVE' or nullif(approved_by,'') is not null),
  check (approved_by is null or approved_by <> created_by)
);

create table if not exists resource_scope_deny_audits (
  tenant_id varchar(64) not null,
  audit_id varchar(128) not null,
  scope_deny_id varchar(128) not null,
  previous_state varchar(32),
  resulting_state varchar(32) not null,
  actor_id varchar(128) not null,
  reason text not null,
  correlation_id varchar(128) not null,
  idempotency_key varchar(256) not null,
  resulting_version bigint not null,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id,audit_id),
  unique (tenant_id,idempotency_key),
  foreign key (tenant_id,scope_deny_id) references resource_scope_denies(tenant_id,scope_deny_id),
  check (previous_state is null or previous_state in ('DRAFT','PENDING_APPROVAL','ACTIVE','EXPIRED','REVOKED')),
  check (resulting_state in ('DRAFT','PENDING_APPROVAL','ACTIVE','EXPIRED','REVOKED')),
  check (resulting_version > 0)
);

create table if not exists resource_visibility_policies (
  tenant_id varchar(64) not null,
  visibility_policy_id varchar(128) not null,
  resource_type varchar(64) not null,
  policy_name varchar(200) not null,
  maximum_visibility varchar(32) not null,
  maximum_sensitivity varchar(32) not null,
  policy_state varchar(32) not null,
  idempotency_key varchar(256) not null,
  version bigint not null default 1,
  created_at timestamptz not null,
  created_by varchar(128) not null,
  updated_at timestamptz not null,
  updated_by varchar(128) not null,
  primary key (tenant_id,visibility_policy_id),
  unique (tenant_id,idempotency_key),
  foreign key (tenant_id) references tenants(tenant_id),
  foreign key (resource_type) references resource_catalog(resource_type),
  check (maximum_visibility in ('NONE','METADATA','SUMMARY','STANDARD','SENSITIVE','FULL','SECRET_METADATA')),
  check (maximum_sensitivity in ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED','SECRET')),
  check (policy_state in ('DRAFT','ACTIVE','RETIRED')),
  check (version > 0)
);
create unique index if not exists uq_resource_visibility_policy_active
  on resource_visibility_policies(tenant_id,resource_type) where policy_state='ACTIVE';

create table if not exists resource_visibility_fields (
  tenant_id varchar(64) not null,
  visibility_policy_id varchar(128) not null,
  field_rule_id varchar(128) not null,
  field_path varchar(512) not null,
  minimum_visibility_level varchar(32) not null,
  sensitivity_level varchar(32) not null,
  masking_method varchar(32) not null,
  export_allowed boolean not null default false,
  download_allowed boolean not null default false,
  priority integer not null default 100,
  version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,visibility_policy_id,field_rule_id),
  unique (tenant_id,visibility_policy_id,field_path),
  foreign key (tenant_id,visibility_policy_id) references resource_visibility_policies(tenant_id,visibility_policy_id) on delete cascade,
  check (minimum_visibility_level in ('NONE','METADATA','SUMMARY','STANDARD','SENSITIVE','FULL','SECRET_METADATA')),
  check (sensitivity_level in ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED','SECRET')),
  check (masking_method in ('NONE','REDACT','PARTIAL','HASH','TOKENIZE','NULL_VALUE')),
  check (priority >= 0 and version > 0)
);

create table if not exists resource_visibility_policy_audits (
  tenant_id varchar(64) not null,
  audit_id varchar(128) not null,
  visibility_policy_id varchar(128) not null,
  previous_state varchar(32),
  resulting_state varchar(32) not null,
  actor_id varchar(128) not null,
  reason text not null,
  correlation_id varchar(128) not null,
  idempotency_key varchar(256) not null,
  resulting_version bigint not null,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id,audit_id),
  unique (tenant_id,idempotency_key),
  foreign key (tenant_id,visibility_policy_id) references resource_visibility_policies(tenant_id,visibility_policy_id),
  check (previous_state is null or previous_state in ('DRAFT','ACTIVE','RETIRED')),
  check (resulting_state in ('DRAFT','ACTIVE','RETIRED')),
  check (resulting_version > 0)
);

create table if not exists resource_principal_clearances (
  tenant_id varchar(64) not null,
  clearance_id varchar(128) not null,
  principal_type varchar(32) not null,
  principal_id varchar(128) not null,
  clearance_level varchar(32) not null,
  valid_from timestamptz not null,
  valid_to timestamptz,
  clearance_status varchar(32) not null,
  created_by varchar(128) not null,
  approved_by varchar(128),
  reason text not null,
  idempotency_key varchar(256) not null,
  version bigint not null default 1,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  updated_by varchar(128) not null,
  primary key (tenant_id,clearance_id),
  unique (tenant_id,idempotency_key),
  foreign key (tenant_id) references tenants(tenant_id),
  check (principal_type in ('USER','DEPARTMENT','GROUP','SERVICE_ACCOUNT')),
  check (clearance_level in ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED','SECRET')),
  check (clearance_status in ('DRAFT','PENDING_APPROVAL','ACTIVE','SUSPENDED','EXPIRED','REVOKED')),
  check (valid_to is null or valid_to > valid_from),
  check (clearance_status <> 'ACTIVE' or nullif(approved_by,'') is not null),
  check (approved_by is null or approved_by <> created_by),
  check (version > 0)
);
create unique index if not exists uq_resource_principal_clearance_active
  on resource_principal_clearances(tenant_id,principal_type,principal_id)
  where clearance_status='ACTIVE';

create table if not exists resource_clearance_audits (
  tenant_id varchar(64) not null,
  audit_id varchar(128) not null,
  clearance_id varchar(128) not null,
  previous_status varchar(32),
  resulting_status varchar(32) not null,
  actor_id varchar(128) not null,
  reason text not null,
  correlation_id varchar(128) not null,
  idempotency_key varchar(256) not null,
  resulting_version bigint not null,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id,audit_id),
  unique (tenant_id,idempotency_key),
  foreign key (tenant_id,clearance_id) references resource_principal_clearances(tenant_id,clearance_id),
  check (previous_status is null or previous_status in ('DRAFT','PENDING_APPROVAL','ACTIVE','SUSPENDED','EXPIRED','REVOKED')),
  check (resulting_status in ('DRAFT','PENDING_APPROVAL','ACTIVE','SUSPENDED','EXPIRED','REVOKED')),
  check (resulting_version > 0)
);

create table if not exists resource_security_state_mutation_audits (
  tenant_id varchar(64) not null,
  mutation_id varchar(128) not null,
  resource_type varchar(64) not null,
  resource_id varchar(128) not null,
  previous_state varchar(32) not null,
  resulting_state varchar(32) not null,
  expected_resource_version bigint not null,
  resulting_resource_version bigint not null,
  resulting_security_epoch bigint not null,
  actor_id varchar(128) not null,
  reason text not null,
  incident_id varchar(128),
  correlation_id varchar(128) not null,
  idempotency_key varchar(256) not null,
  changed_at timestamptz not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id,mutation_id),
  unique (tenant_id,idempotency_key),
  foreign key (tenant_id,resource_type,resource_id) references resource_descriptors(tenant_id,resource_type,resource_id),
  check (previous_state in ('NORMAL','RESTRICTED','QUARANTINED','LEGAL_HOLD','INVESTIGATION','ARCHIVED','DELETED','ORPHANED')),
  check (resulting_state in ('NORMAL','RESTRICTED','QUARANTINED','LEGAL_HOLD','INVESTIGATION','ARCHIVED','DELETED')),
  check (resulting_state <> 'ORPHANED'),
  check (expected_resource_version > 0 and resulting_resource_version = expected_resource_version + 1),
  check (resulting_security_epoch > 0)
);

create index if not exists idx_resource_scope_grants_principal
  on resource_scope_grants(tenant_id,principal_type,principal_id,permission_code,resource_type,grant_state,valid_from,valid_to);
create index if not exists idx_resource_scope_grants_scope
  on resource_scope_grants(tenant_id,resource_type,scope_type,scope_ref_id,grant_state);
create index if not exists idx_resource_scope_denies_principal
  on resource_scope_denies(tenant_id,principal_type,principal_id,resource_type,deny_state,valid_from,valid_to);
create index if not exists idx_resource_scope_denies_scope
  on resource_scope_denies(tenant_id,resource_type,scope_type,scope_ref_id,deny_state,severity);
create index if not exists idx_resource_visibility_fields_policy
  on resource_visibility_fields(tenant_id,visibility_policy_id,priority,field_path);
create index if not exists idx_resource_clearance_principal
  on resource_principal_clearances(tenant_id,principal_type,principal_id,clearance_status,valid_from,valid_to);
create index if not exists idx_resource_security_state_audit_resource
  on resource_security_state_mutation_audits(tenant_id,resource_type,resource_id,changed_at desc);

-- Policy and security evidence are append-only. Mutable current-state rows retain optimistic versioning.
drop trigger if exists trg_resource_scope_grant_audit_immutable on resource_scope_grant_audits;
create trigger trg_resource_scope_grant_audit_immutable before update or delete on resource_scope_grant_audits
for each row execute function p4ra_reject_append_only_mutation();
drop trigger if exists trg_resource_scope_deny_audit_immutable on resource_scope_deny_audits;
create trigger trg_resource_scope_deny_audit_immutable before update or delete on resource_scope_deny_audits
for each row execute function p4ra_reject_append_only_mutation();
drop trigger if exists trg_resource_visibility_policy_audit_immutable on resource_visibility_policy_audits;
create trigger trg_resource_visibility_policy_audit_immutable before update or delete on resource_visibility_policy_audits
for each row execute function p4ra_reject_append_only_mutation();
drop trigger if exists trg_resource_clearance_audit_immutable on resource_clearance_audits;
create trigger trg_resource_clearance_audit_immutable before update or delete on resource_clearance_audits
for each row execute function p4ra_reject_append_only_mutation();
drop trigger if exists trg_resource_security_state_audit_immutable on resource_security_state_mutation_audits;
create trigger trg_resource_security_state_audit_immutable before update or delete on resource_security_state_mutation_audits
for each row execute function p4ra_reject_append_only_mutation();

-- Tenant hard boundary. No policy table can be read or mutated without the active database tenant context.
do $$
declare table_name text;
begin
  foreach table_name in array array[
    'resource_scope_grants','resource_scope_grant_audits','resource_scope_denies','resource_scope_deny_audits',
    'resource_visibility_policies','resource_visibility_fields','resource_visibility_policy_audits',
    'resource_principal_clearances','resource_clearance_audits','resource_security_state_mutation_audits'
  ] loop
    execute format('alter table %I enable row level security',table_name);
    execute format('alter table %I force row level security',table_name);
    execute format('drop policy if exists tenant_isolation on %I',table_name);
    execute format('create policy tenant_isolation on %I using (tenant_id = iam_current_tenant_id()) with check (tenant_id = iam_current_tenant_id())',table_name);
  end loop;
end $$;

insert into permission_point_catalog(permission_point,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed)
values
 ('resource.scope.read','RESOURCE_SCOPE','READ','Read Resource Access scope grants.','HIGH',array['TENANT'],true),
 ('resource.scope.grant','RESOURCE_SCOPE','CREATE','Create a draft Resource Access scope grant.','CRITICAL',array['TENANT'],true),
 ('resource.scope.approve','RESOURCE_SCOPE','APPROVE','Approve a pending Resource Access scope grant.','CRITICAL',array['TENANT'],true),
 ('resource.scope.suspend','RESOURCE_SCOPE','SUSPEND','Suspend an active Resource Access scope grant.','CRITICAL',array['TENANT'],true),
 ('resource.scope.revoke','RESOURCE_SCOPE','REVOKE','Revoke a Resource Access scope grant.','CRITICAL',array['TENANT'],true),
 ('resource.deny.read','RESOURCE_DENY','READ','Read explicit Resource Access denies.','HIGH',array['TENANT'],true),
 ('resource.deny.create','RESOURCE_DENY','CREATE','Create an explicit Resource Access deny.','CRITICAL',array['TENANT'],true),
 ('resource.deny.approve','RESOURCE_DENY','APPROVE','Approve a pending explicit Resource Access deny.','CRITICAL',array['TENANT'],true),
 ('resource.deny.revoke','RESOURCE_DENY','REVOKE','Revoke an explicit Resource Access deny.','CRITICAL',array['TENANT'],true),
 ('resource.visibility.read','RESOURCE_VISIBILITY','READ','Read Resource visibility policies.','HIGH',array['TENANT'],true),
 ('resource.visibility.manage','RESOURCE_VISIBILITY','MANAGE','Create, activate, or retire Resource visibility policies.','CRITICAL',array['TENANT'],true),
 ('resource.clearance.read','RESOURCE_CLEARANCE','READ','Read principal sensitivity clearance metadata.','CRITICAL',array['TENANT'],true),
 ('resource.clearance.manage','RESOURCE_CLEARANCE','MANAGE','Grant, suspend, expire, or revoke principal clearance.','CRITICAL',array['TENANT'],true),
 ('resource.clearance.approve','RESOURCE_CLEARANCE','APPROVE','Approve a pending principal sensitivity clearance.','CRITICAL',array['TENANT'],true),
 ('resource.security-state.manage','RESOURCE_SECURITY_STATE','MANAGE','Restrict, quarantine, investigate, archive, or delete a Resource projection.','CRITICAL',array['TENANT'],true)
on conflict(permission_point) do update set
 resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,
 risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,system_managed=true,active=true,
 version=permission_point_catalog.version+1;

insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template)
values
 ('SCOPE_GRANT_NOT_FOUND',404,'RESOURCE_ACCESS',false,'The scope grant was not found.'),
 ('SCOPE_GRANT_INVALID_TRANSITION',409,'RESOURCE_ACCESS',false,'The scope grant lifecycle transition is invalid.'),
 ('SCOPE_GRANT_ALREADY_EXPIRED',409,'RESOURCE_ACCESS',false,'The scope grant expired before approval.'),
 ('SCOPE_DENY_NOT_FOUND',404,'RESOURCE_ACCESS',false,'The explicit deny was not found.'),
 ('SCOPE_DENY_INVALID_TRANSITION',409,'RESOURCE_ACCESS',false,'The explicit deny lifecycle transition is invalid.'),
 ('SCOPE_DENY_ALREADY_EXPIRED',409,'RESOURCE_ACCESS',false,'The explicit deny expired before approval.'),
 ('VISIBILITY_POLICY_NOT_FOUND',404,'RESOURCE_ACCESS',false,'The visibility policy was not found.'),
 ('VISIBILITY_POLICY_INVALID_TRANSITION',409,'RESOURCE_ACCESS',false,'The visibility policy lifecycle transition is invalid.'),
 ('CLEARANCE_NOT_FOUND',404,'RESOURCE_ACCESS',false,'The principal clearance was not found.'),
 ('CLEARANCE_INVALID_TRANSITION',409,'RESOURCE_ACCESS',false,'The principal clearance lifecycle transition is invalid.'),
 ('CLEARANCE_ALREADY_EXPIRED',409,'RESOURCE_ACCESS',false,'The principal clearance expired before approval.'),
 ('CLEARANCE_CREATOR_EVIDENCE_MISSING',500,'RESOURCE_ACCESS',false,'The clearance creator evidence is missing.'),
 ('SEPARATION_OF_DUTIES_VIOLATION',409,'RESOURCE_ACCESS',false,'The requester cannot approve the same high-risk policy mutation.'),
 ('ORPHAN_STATE_OWNED_BY_PROJECTION',409,'RESOURCE_ACCESS',false,'ORPHANED state is controlled by descriptor projection and repair.'),
 ('INCIDENT_ID_REQUIRED',400,'RESOURCE_ACCESS',false,'An incident identifier is required for quarantine or investigation.'),
 ('EXPLICIT_DENY_MATCHED',403,'AUTHORIZATION',false,'An explicit deny matched the requested Resource operation.')
on conflict(reason_code) do update set http_status=excluded.http_status,category=excluded.category,retryable=excluded.retryable,message_template=excluded.message_template,active=true,version=reason_code_catalog.version+1;

comment on table resource_scope_grants is 'P4RA-C explicit grant authority. Not consumed by business authorization until P4RA-D.';
comment on table resource_scope_denies is 'P4RA-C explicit deny authority. Matching active deny has precedence over every allow source.';
comment on table resource_visibility_policies is 'Versioned Resource Type visibility policy authority.';
comment on table resource_visibility_fields is 'Field-level visibility, masking, export, and download policy rules.';
comment on table resource_visibility_policy_audits is 'Immutable visibility policy lifecycle evidence.';
comment on table resource_principal_clearances is 'Explicit sensitivity clearance; no implicit enum-ordinal authorization.';
comment on table resource_clearance_audits is 'Immutable principal-clearance lifecycle evidence.';
comment on table resource_security_state_mutation_audits is 'Immutable resource security-state and security-epoch transition evidence.';
