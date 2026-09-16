-- Phase 7E Integration: governed temporary access requests linked to canonical Scope Grant lifecycle.

alter table resource_scope_grants drop constraint if exists resource_scope_grants_grant_source_check;
alter table resource_scope_grants add constraint resource_scope_grants_grant_source_check
  check (grant_source in ('MANUAL','ACCESS_REQUEST','RESOURCE_SHARE','MIGRATION_COMPATIBILITY','BREAK_GLASS','SYSTEM_POLICY'));

create table if not exists resource_access_requests (
  tenant_id varchar(64) not null,
  access_request_id varchar(128) not null,
  scope_grant_id varchar(128) not null,
  ui_action_id varchar(160) not null,
  resource_type varchar(64) not null,
  resource_id varchar(128) not null,
  resource_version_at_request bigint not null,
  requested_visibility varchar(32) not null,
  requester_id varchar(128) not null,
  business_purpose text not null,
  valid_from timestamptz not null,
  valid_to timestamptz not null,
  request_state varchar(32) not null,
  approved_by varchar(128),
  idempotency_key varchar(256) not null,
  version bigint not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  primary key (tenant_id,access_request_id),
  unique (tenant_id,scope_grant_id),
  unique (tenant_id,idempotency_key),
  foreign key (tenant_id,scope_grant_id) references resource_scope_grants(tenant_id,scope_grant_id),
  foreign key (tenant_id,resource_type,resource_id) references resource_descriptors(tenant_id,resource_type,resource_id),
  check (resource_version_at_request > 0),
  check (requested_visibility in ('METADATA','SUMMARY','STANDARD','SENSITIVE','FULL','SECRET_METADATA')),
  check (request_state in ('DRAFT','PENDING_APPROVAL','ACTIVE','REJECTED','CANCELLED','EXPIRED')),
  check (valid_to > valid_from),
  check (version > 0),
  check (request_state <> 'ACTIVE' or nullif(approved_by,'') is not null),
  check (approved_by is null or approved_by <> requester_id)
);

create table if not exists resource_access_request_audits (
  tenant_id varchar(64) not null,
  audit_id varchar(160) not null,
  access_request_id varchar(128) not null,
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
  foreign key (tenant_id,access_request_id) references resource_access_requests(tenant_id,access_request_id),
  check (previous_state is null or previous_state in ('DRAFT','PENDING_APPROVAL','ACTIVE','REJECTED','CANCELLED','EXPIRED')),
  check (resulting_state in ('DRAFT','PENDING_APPROVAL','ACTIVE','REJECTED','CANCELLED','EXPIRED')),
  check (resulting_version > 0)
);

create index if not exists idx_resource_access_requests_requester
  on resource_access_requests(tenant_id,requester_id,request_state,updated_at desc);
create index if not exists idx_resource_access_requests_resource
  on resource_access_requests(tenant_id,resource_type,resource_id,request_state,updated_at desc);

alter table resource_access_request_audits enable row level security;
alter table resource_access_request_audits force row level security;
drop policy if exists tenant_isolation on resource_access_request_audits;
create policy tenant_isolation on resource_access_request_audits
  using (tenant_id = iam_current_tenant_id()) with check (tenant_id = iam_current_tenant_id());

alter table resource_access_requests enable row level security;
alter table resource_access_requests force row level security;
drop policy if exists tenant_isolation on resource_access_requests;
create policy tenant_isolation on resource_access_requests
  using (tenant_id = iam_current_tenant_id()) with check (tenant_id = iam_current_tenant_id());

drop trigger if exists trg_resource_access_request_audit_immutable on resource_access_request_audits;
create trigger trg_resource_access_request_audit_immutable before update or delete on resource_access_request_audits
for each row execute function p4ra_reject_append_only_mutation();

insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template)
values
 ('ACCESS_REQUEST_ACTION_NOT_REQUESTABLE',403,'RESOURCE_ACCESS',false,'The requested UI action does not support governed access requests.'),
 ('ACCESS_REQUEST_RESOURCE_NOT_FOUND',404,'RESOURCE_ACCESS',false,'The resource is unavailable.'),
 ('ACCESS_REQUEST_RESOURCE_VERSION_STALE',412,'RESOURCE_ACCESS',true,'The resource changed after the access request was prepared.'),
 ('ACCESS_REQUEST_VISIBILITY_NOT_ALLOWED',403,'RESOURCE_ACCESS',false,'The requested visibility exceeds the UI action profile.'),
 ('ACCESS_REQUEST_DURATION_NOT_ALLOWED',400,'RESOURCE_ACCESS',false,'The requested access duration is not allowed.'),
 ('ACCESS_REQUEST_NOT_FOUND',404,'RESOURCE_ACCESS',false,'The access request was not found.'),
 ('ACCESS_REQUEST_INVALID_STATE',409,'RESOURCE_ACCESS',false,'The access request lifecycle state does not allow this operation.'),
 ('ACCESS_REQUEST_VERSION_CONFLICT',409,'RESOURCE_ACCESS',true,'The access request version changed.'),
 ('ACCESS_REQUEST_SEPARATION_OF_DUTIES',409,'RESOURCE_ACCESS',false,'The requester cannot approve or reject the same access request.'),
 ('ACCESS_REQUEST_APPROVER_NOT_AUTHORIZED',403,'RESOURCE_ACCESS',false,'The approver is not authorized for this resource scope.'),
 ('ACCESS_REQUEST_GRANT_DRIFT',409,'RESOURCE_ACCESS',false,'The access request and canonical Scope Grant no longer match.')
on conflict(reason_code) do update set
 http_status=excluded.http_status,category=excluded.category,retryable=excluded.retryable,
 message_template=excluded.message_template,active=true,version=reason_code_catalog.version+1;

comment on table resource_access_requests is 'Phase 7E request metadata. Canonical active access remains resource_scope_grants.';
comment on table resource_access_request_audits is 'Immutable governed access-request lifecycle evidence.';
