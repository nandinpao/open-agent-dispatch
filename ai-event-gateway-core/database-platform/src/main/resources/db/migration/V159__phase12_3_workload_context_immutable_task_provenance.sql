-- Phase 12.3 — Workload Context & Immutable Task Provenance
-- Creation-time attribution is historical evidence. Current authorization remains RBAC/resource authority.

alter table tasks add column if not exists origin_principal_type varchar(32);
alter table tasks add column if not exists origin_principal_id varchar(128);
alter table tasks add column if not exists actor_principal_type varchar(32);
alter table tasks add column if not exists actor_principal_id varchar(128);
alter table tasks add column if not exists origin_credential_id varchar(128);
alter table tasks add column if not exists origin_oauth_client_id varchar(96);
alter table tasks add column if not exists origin_event_id varchar(128);
alter table tasks add column if not exists origin_workload_source_system varchar(160);
alter table tasks add column if not exists origin_correlation_id varchar(128);
alter table tasks add column if not exists initial_priority varchar(32);
alter table tasks add column if not exists initial_severity varchar(32);
alter table tasks add column if not exists origin_department_id varchar(128);
alter table tasks add column if not exists origin_group_id varchar(128);
alter table tasks add column if not exists origin_organization_source varchar(32);
alter table tasks add column if not exists origin_organization_status varchar(32);
alter table tasks add column if not exists origin_source_ip varchar(128);
alter table tasks add column if not exists origin_api_resource varchar(256);
alter table tasks add column if not exists authorization_decision_id varchar(128);
alter table tasks add column if not exists security_global_epoch bigint;
alter table tasks add column if not exists security_tenant_epoch bigint;
alter table tasks add column if not exists security_principal_epoch bigint;
alter table tasks add column if not exists request_id varchar(128);
alter table tasks add column if not exists trace_id varchar(64);
alter table tasks add column if not exists authentication_method varchar(64);
alter table tasks add column if not exists provenance_captured_at timestamptz;

-- Existing rows keep their best known historical attribution without inventing a Credential or OAuth client.
update tasks set
  origin_principal_type = coalesce(origin_principal_type, case upper(coalesce(created_by_type,'SYSTEM'))
      when 'USER' then 'USER' when 'AGENT' then 'AGENT' when 'INTEGRATION' then 'INTEGRATION' else 'SYSTEM' end),
  origin_principal_id = coalesce(nullif(origin_principal_id,''), nullif(created_by_id,''), 'CORE'),
  actor_principal_type = coalesce(actor_principal_type, case upper(coalesce(created_by_type,'SYSTEM'))
      when 'USER' then 'USER' when 'AGENT' then 'AGENT' when 'INTEGRATION' then 'INTEGRATION' else 'SYSTEM' end),
  actor_principal_id = coalesce(nullif(actor_principal_id,''), nullif(created_by_id,''), 'CORE'),
  origin_event_id = coalesce(origin_event_id, source_event_id),
  origin_workload_source_system = coalesce(nullif(origin_workload_source_system,''), nullif(origin_source_system,''), nullif(source_system,'')),
  origin_correlation_id = coalesce(origin_correlation_id, correlation_id),
  initial_priority = coalesce(nullif(initial_priority,''), nullif(priority,''), 'MEDIUM'),
  initial_severity = coalesce(nullif(initial_severity,''), nullif(severity,''), 'MEDIUM'),
  origin_department_id = coalesce(nullif(origin_department_id,''), nullif(owner_department_id,''), 'UNASSIGNED'),
  origin_group_id = coalesce(origin_group_id, owner_group_id),
  origin_organization_source = coalesce(nullif(origin_organization_source,''), 'HISTORICAL_TASK_SCOPE'),
  origin_organization_status = coalesce(nullif(origin_organization_status,''), nullif(origin_scope_status,''), 'UNRESOLVED'),
  security_global_epoch = coalesce(security_global_epoch,0),
  security_tenant_epoch = coalesce(security_tenant_epoch,0),
  security_principal_epoch = coalesce(security_principal_epoch,0),
  request_id = coalesce(request_id, correlation_id),
  authentication_method = coalesce(nullif(authentication_method,''), 'HISTORICAL'),
  provenance_captured_at = coalesce(provenance_captured_at, created_at, now())
where origin_principal_type is null or origin_principal_id is null or actor_principal_type is null or actor_principal_id is null
   or initial_priority is null or initial_severity is null or origin_department_id is null or origin_organization_source is null or origin_organization_status is null
   or security_global_epoch is null or security_tenant_epoch is null or security_principal_epoch is null
   or authentication_method is null or provenance_captured_at is null;

alter table tasks alter column origin_principal_type set default 'SYSTEM';
alter table tasks alter column origin_principal_type set not null;
alter table tasks alter column origin_principal_id set default 'CORE';
alter table tasks alter column origin_principal_id set not null;
alter table tasks alter column actor_principal_type set default 'SYSTEM';
alter table tasks alter column actor_principal_type set not null;
alter table tasks alter column actor_principal_id set default 'CORE';
alter table tasks alter column actor_principal_id set not null;
alter table tasks alter column initial_priority set default 'MEDIUM';
alter table tasks alter column initial_priority set not null;
alter table tasks alter column initial_severity set default 'MEDIUM';
alter table tasks alter column initial_severity set not null;
alter table tasks alter column origin_department_id set default 'UNASSIGNED';
alter table tasks alter column origin_department_id set not null;
alter table tasks alter column origin_organization_source set default 'UNRESOLVED';
alter table tasks alter column origin_organization_source set not null;
alter table tasks alter column origin_organization_status set default 'UNRESOLVED';
alter table tasks alter column origin_organization_status set not null;
alter table tasks alter column security_global_epoch set default 0;
alter table tasks alter column security_global_epoch set not null;
alter table tasks alter column security_tenant_epoch set default 0;
alter table tasks alter column security_tenant_epoch set not null;
alter table tasks alter column security_principal_epoch set default 0;
alter table tasks alter column security_principal_epoch set not null;
alter table tasks alter column authentication_method set default 'UNKNOWN';
alter table tasks alter column authentication_method set not null;
alter table tasks alter column provenance_captured_at set default now();
alter table tasks alter column provenance_captured_at set not null;

alter table tasks drop constraint if exists ck_tasks_origin_principal_type;
alter table tasks add constraint ck_tasks_origin_principal_type check (origin_principal_type in ('USER','SERVICE_ACCOUNT','AGENT','SYSTEM','INTEGRATION','A2A_AGENT'));
alter table tasks drop constraint if exists ck_tasks_actor_principal_type;
alter table tasks add constraint ck_tasks_actor_principal_type check (actor_principal_type in ('USER','SERVICE_ACCOUNT','AGENT','SYSTEM','INTEGRATION','A2A_AGENT'));
alter table tasks drop constraint if exists ck_tasks_initial_priority;
alter table tasks add constraint ck_tasks_initial_priority check (initial_priority in ('LOW','MEDIUM','HIGH','CRITICAL'));
alter table tasks drop constraint if exists ck_tasks_initial_severity;
alter table tasks add constraint ck_tasks_initial_severity check (initial_severity in ('LOW','MEDIUM','HIGH','CRITICAL'));
alter table tasks drop constraint if exists ck_tasks_origin_organization_status;
alter table tasks add constraint ck_tasks_origin_organization_status check (origin_organization_status in ('RESOLVED','UNRESOLVED','HISTORICAL','REVIEW_REQUIRED'));
alter table tasks drop constraint if exists ck_tasks_provenance_security_epochs;
alter table tasks add constraint ck_tasks_provenance_security_epochs check (security_global_epoch >= 0 and security_tenant_epoch >= 0 and security_principal_epoch >= 0);

create index if not exists idx_tasks_provenance_principal
  on tasks(tenant_id,origin_principal_type,origin_principal_id,created_at desc,task_id);
create index if not exists idx_tasks_provenance_org
  on tasks(tenant_id,origin_department_id,origin_group_id,created_at desc,task_id);
create index if not exists idx_tasks_provenance_credential
  on tasks(tenant_id,origin_credential_id,created_at desc,task_id) where origin_credential_id is not null;
create index if not exists idx_tasks_provenance_correlation
  on tasks(tenant_id,correlation_id,created_at desc,task_id) where correlation_id is not null;
create index if not exists idx_tasks_provenance_authz_decision
  on tasks(tenant_id,authorization_decision_id,created_at desc,task_id) where authorization_decision_id is not null;

-- Guard creation-time evidence against later ownership transfers, assignment changes, retries or generic task upserts.
create or replace function phase12_3_reject_task_provenance_mutation()
returns trigger language plpgsql as $$
begin
  if old.origin_principal_type is distinct from new.origin_principal_type
     or old.origin_principal_id is distinct from new.origin_principal_id
     or old.actor_principal_type is distinct from new.actor_principal_type
     or old.actor_principal_id is distinct from new.actor_principal_id
     or old.origin_credential_id is distinct from new.origin_credential_id
     or old.origin_oauth_client_id is distinct from new.origin_oauth_client_id
     or old.origin_event_id is distinct from new.origin_event_id
     or old.origin_workload_source_system is distinct from new.origin_workload_source_system
     or old.origin_correlation_id is distinct from new.origin_correlation_id
     or old.initial_priority is distinct from new.initial_priority
     or old.initial_severity is distinct from new.initial_severity
     or old.origin_department_id is distinct from new.origin_department_id
     or old.origin_group_id is distinct from new.origin_group_id
     or old.origin_organization_source is distinct from new.origin_organization_source
     or old.origin_organization_status is distinct from new.origin_organization_status
     or old.origin_source_ip is distinct from new.origin_source_ip
     or old.origin_api_resource is distinct from new.origin_api_resource
     or old.authorization_decision_id is distinct from new.authorization_decision_id
     or old.security_global_epoch is distinct from new.security_global_epoch
     or old.security_tenant_epoch is distinct from new.security_tenant_epoch
     or old.security_principal_epoch is distinct from new.security_principal_epoch
     or old.request_id is distinct from new.request_id
     or old.trace_id is distinct from new.trace_id
     or old.authentication_method is distinct from new.authentication_method
     or old.provenance_captured_at is distinct from new.provenance_captured_at then
    raise exception 'TASK_PROVENANCE_IMMUTABLE' using errcode='55000';
  end if;
  return new;
end $$;

drop trigger if exists trg_tasks_provenance_immutable on tasks;
create trigger trg_tasks_provenance_immutable before update on tasks
for each row execute function phase12_3_reject_task_provenance_mutation();

-- Transactional analytics/search projection hand-off. This is not an authorization source of truth.
create table if not exists workload_provenance_outbox (
  tenant_id varchar(64) not null,
  event_id varchar(180) not null,
  aggregate_type varchar(32) not null default 'TASK',
  aggregate_id varchar(128) not null,
  event_type varchar(64) not null default 'TASK_PROVENANCE_CAPTURED',
  payload_json jsonb not null,
  payload_hash varchar(64) not null,
  event_status varchar(32) not null default 'PENDING',
  attempt_count integer not null default 0,
  available_at timestamptz not null default now(),
  published_at timestamptz,
  last_error text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(tenant_id,event_id),
  check(event_status in ('PENDING','CLAIMED','PUBLISHED','FAILED','DEAD_LETTER')),
  check(attempt_count >= 0)
);
create index if not exists idx_workload_provenance_outbox_pending
  on workload_provenance_outbox(event_status,available_at,tenant_id,event_id);

alter table workload_provenance_outbox enable row level security;
alter table workload_provenance_outbox force row level security;
drop policy if exists tenant_isolation on workload_provenance_outbox;
create policy tenant_isolation on workload_provenance_outbox
  using (tenant_id = iam_current_tenant_id()) with check (tenant_id = iam_current_tenant_id());

create or replace function phase12_3_emit_task_provenance_outbox()
returns trigger language plpgsql as $$
declare payload jsonb;
begin
  payload := jsonb_build_object(
    'tenantId',new.tenant_id,'taskId',new.task_id,'rootTaskId',new.root_task_id,'parentTaskId',new.parent_task_id,
    'sourceSystem',new.source_system,'originPrincipalType',new.origin_principal_type,'originPrincipalId',new.origin_principal_id,
    'actorPrincipalType',new.actor_principal_type,'actorPrincipalId',new.actor_principal_id,
    'credentialId',new.origin_credential_id,'oauthClientId',new.origin_oauth_client_id,
    'originEventId',new.origin_event_id,'originSourceSystem',new.origin_workload_source_system,'originCorrelationId',new.origin_correlation_id,
    'initialPriority',new.initial_priority,'initialSeverity',new.initial_severity,
    'departmentId',new.origin_department_id,'groupId',new.origin_group_id,
    'organizationSource',new.origin_organization_source,'organizationStatus',new.origin_organization_status,
    'sourceIp',new.origin_source_ip,'apiResource',new.origin_api_resource,'authorizationDecisionId',new.authorization_decision_id,
    'securityEpoch',jsonb_build_object('global',new.security_global_epoch,'tenant',new.security_tenant_epoch,'principal',new.security_principal_epoch),
    'requestId',new.request_id,'correlationId',new.correlation_id,'traceId',new.trace_id,
    'authenticationMethod',new.authentication_method,'capturedAt',new.provenance_captured_at,
    'priority',new.priority,'severity',new.severity,'createdAt',new.created_at
  );
  insert into workload_provenance_outbox(tenant_id,event_id,aggregate_id,payload_json,payload_hash)
  values(new.tenant_id,'task-provenance:'||new.task_id,new.task_id,payload,md5(payload::text))
  on conflict(tenant_id,event_id) do nothing;
  return new;
end $$;

drop trigger if exists trg_tasks_emit_provenance_outbox on tasks;
create trigger trg_tasks_emit_provenance_outbox after insert on tasks
for each row execute function phase12_3_emit_task_provenance_outbox();

comment on column tasks.origin_principal_type is 'Immutable creation-time workload origin. Not current authorization authority.';
comment on column tasks.origin_department_id is 'Immutable organization attribution captured when the Task was created.';
comment on column tasks.authorization_decision_id is 'Authorization decision evidence correlated to the workload creation boundary.';
comment on table workload_provenance_outbox is 'Transactional analytics/search hand-off for immutable workload provenance. Never an IAM/RBAC source of truth.';
