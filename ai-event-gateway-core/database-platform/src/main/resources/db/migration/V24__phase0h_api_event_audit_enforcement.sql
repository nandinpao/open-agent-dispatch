-- Phase 0H enforcement: tenant-aware references, immutable evidence,
-- governed mutation receipt lifecycle and a database-level Task completion guard.

do $$
begin
  if not exists(select 1 from pg_constraint where conname='fk_auth_decision_tenant') then
    alter table authorization_decisions add constraint fk_auth_decision_tenant
      foreign key(tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_audit_evidence_tenant') then
    alter table audit_evidence add constraint fk_audit_evidence_tenant
      foreign key(tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_audit_authorization_decision') then
    alter table audit_evidence add constraint fk_audit_authorization_decision
      foreign key(tenant_id,authorization_decision_id)
      references authorization_decisions(tenant_id,decision_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_mutation_receipt_tenant') then
    alter table api_mutation_receipts add constraint fk_mutation_receipt_tenant
      foreign key(tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_mutation_receipt_decision') then
    alter table api_mutation_receipts add constraint fk_mutation_receipt_decision
      foreign key(tenant_id,authorization_decision_id)
      references authorization_decisions(tenant_id,decision_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='ck_authorization_decision_value') then
    alter table authorization_decisions add constraint ck_authorization_decision_value
      check(decision in('ALLOW','DENY','WOULD_DENY')) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='ck_audit_evidence_outcome') then
    alter table audit_evidence add constraint ck_audit_evidence_outcome
      check(outcome in('SUCCESS','FAILURE','DENIED','CONFLICT','WARNING')) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='ck_api_mutation_receipt_status') then
    alter table api_mutation_receipts add constraint ck_api_mutation_receipt_status
      check(status in('RECEIVED','IN_PROGRESS','COMPLETED','FAILED','CONFLICT','REPLAYED')) not valid;
  end if;
end $$;

alter table authorization_decisions validate constraint fk_auth_decision_tenant;
alter table audit_evidence validate constraint fk_audit_evidence_tenant;
alter table audit_evidence validate constraint fk_audit_authorization_decision;
alter table api_mutation_receipts validate constraint fk_mutation_receipt_tenant;
alter table api_mutation_receipts validate constraint fk_mutation_receipt_decision;
alter table authorization_decisions validate constraint ck_authorization_decision_value;
alter table audit_evidence validate constraint ck_audit_evidence_outcome;
alter table api_mutation_receipts validate constraint ck_api_mutation_receipt_status;

create index if not exists idx_authorization_decision_actor
  on authorization_decisions(tenant_id,actor_type,actor_id,decided_at desc);
create index if not exists idx_audit_evidence_aggregate
  on audit_evidence(tenant_id,aggregate_type,aggregate_id,occurred_at desc);
create index if not exists idx_audit_evidence_correlation
  on audit_evidence(tenant_id,correlation_id,occurred_at desc);
create index if not exists idx_mutation_receipt_created
  on api_mutation_receipts(tenant_id,created_at desc);

create or replace function phase0h_immutable_evidence_guard()
returns trigger language plpgsql as $$
begin
  raise exception 'Phase 0H evidence is immutable: %',tg_table_name using errcode='55000';
end $$;

drop trigger if exists trg_phase0h_authorization_immutable on authorization_decisions;
create trigger trg_phase0h_authorization_immutable
before update or delete on authorization_decisions
for each row execute function phase0h_immutable_evidence_guard();

drop trigger if exists trg_phase0h_audit_immutable on audit_evidence;
create trigger trg_phase0h_audit_immutable
before update or delete on audit_evidence
for each row execute function phase0h_immutable_evidence_guard();

create or replace function phase0h_mutation_receipt_guard()
returns trigger language plpgsql as $$
begin
  if new.tenant_id is distinct from old.tenant_id
     or new.receipt_id is distinct from old.receipt_id
     or new.request_method is distinct from old.request_method
     or new.request_path is distinct from old.request_path
     or new.idempotency_key is distinct from old.idempotency_key
     or new.request_hash is distinct from old.request_hash
     or new.expected_version is distinct from old.expected_version
     or new.actor_type is distinct from old.actor_type
     or new.actor_id is distinct from old.actor_id
     or new.audit_reason is distinct from old.audit_reason
     or new.correlation_id is distinct from old.correlation_id
     or new.authorization_decision_id is distinct from old.authorization_decision_id
     or new.permission_point is distinct from old.permission_point
     or new.created_at is distinct from old.created_at then
    raise exception 'Mutation receipt identity is immutable' using errcode='55000';
  end if;

  if old.status in('COMPLETED','FAILED','CONFLICT','REPLAYED') and new is distinct from old then
    raise exception 'Terminal mutation receipt is immutable' using errcode='55000';
  end if;

  if new.status is distinct from old.status then
    if old.status='RECEIVED' and new.status in('IN_PROGRESS','FAILED','CONFLICT') then return new; end if;
    if old.status='IN_PROGRESS' and new.status in('COMPLETED','FAILED','CONFLICT','REPLAYED') then return new; end if;
    raise exception 'Invalid mutation receipt transition: % -> %',old.status,new.status using errcode='23514';
  end if;
  return new;
end $$;

drop trigger if exists trg_phase0h_mutation_receipt_guard on api_mutation_receipts;
create trigger trg_phase0h_mutation_receipt_guard
before update on api_mutation_receipts
for each row execute function phase0h_mutation_receipt_guard();

drop trigger if exists trg_phase0h_mutation_receipt_delete on api_mutation_receipts;
create trigger trg_phase0h_mutation_receipt_delete
before delete on api_mutation_receipts
for each row execute function phase0h_immutable_evidence_guard();

-- Database defense-in-depth: every completion path, including legacy direct
-- repository updates, must honor REQUIRED_BEFORE_COMPLETION.
create or replace function phase0h_task_completion_guard()
returns trigger language plpgsql as $$
begin
  if new.status in('COMPLETED','SUCCEEDED')
     and new.status is distinct from old.status
     and new.a2a_policy_id is not null
     and exists (
       select 1 from a2a_policies p
       where p.tenant_id=new.tenant_id
         and p.policy_id=new.a2a_policy_id
         and p.handoff_context_requirement='REQUIRED_BEFORE_COMPLETION'
     )
     and not exists (
       select 1 from handoff_context_snapshots s
       where s.tenant_id=new.tenant_id
         and s.target_task_id=new.task_id
         and s.status='APPROVED'
         and (s.expires_at is null or s.expires_at>now())
     ) then
    raise exception 'HANDOFF_CONTEXT_REQUIRED_BEFORE_COMPLETION' using errcode='23514';
  end if;
  return new;
end $$;

drop trigger if exists trg_phase0h_task_completion_guard on tasks;
create trigger trg_phase0h_task_completion_guard
before update of status on tasks
for each row execute function phase0h_task_completion_guard();

insert into permission_point_catalog(permission_point,resource_type,action_code,description,risk_level)
values
 ('source_system.manage','SOURCE_SYSTEM','MANAGE','Manage Source System configuration.','HIGH'),
 ('dispatch.manage','DISPATCH_CONFIGURATION','MANAGE','Manage dispatch flows, pools, and policies.','HIGH'),
 ('handoff.manage','HANDOFF_CONTEXT','MANAGE','Create or approve Handoff Context Snapshots.','HIGH'),
 ('integration.webhook.receive','INTEGRATION_WEBHOOK','RECEIVE','Receive a signed Provider webhook.','HIGH'),
 ('system.manage','SYSTEM_RESOURCE','MANAGE','Perform a governed administrative mutation.','HIGH')
on conflict(permission_point) do nothing;

insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template)
values
 ('TENANT_CONTEXT_REQUIRED',400,'TENANT',false,'Trusted Tenant Context is required.'),
 ('API_CORRELATION_ID_REQUIRED',400,'API_CONTRACT',false,'Correlation ID is required for this mutation.'),
 ('API_EXPECTED_VERSION_INVALID',400,'API_CONTRACT',false,'If-Match must contain a numeric resource version.'),
 ('API_IDEMPOTENCY_REPLAY',200,'API_CONTRACT',false,'The mutation was already processed and was not executed again.'),
 ('API_IDEMPOTENCY_IN_PROGRESS',409,'API_CONTRACT',true,'A mutation with this Idempotency-Key is still in progress.'),
 ('EVENT_ENVELOPE_REQUIRED_FIELD_MISSING',500,'EVENT',false,'A required canonical event envelope field is missing.'),
 ('EVENT_SCHEMA_VERSION_UNSUPPORTED',400,'EVENT',false,'The event envelope schema version is unsupported.')
on conflict(reason_code) do nothing;

insert into domain_event_catalog(event_type,payload_version,aggregate_type,description,exportable)
values
 ('API_MUTATION_COMPLETED','1','API_MUTATION','A governed API mutation completed.',false),
 ('API_MUTATION_FAILED','1','API_MUTATION','A governed API mutation failed.',false),
 ('API_MUTATION_REPLAYED','1','API_MUTATION','An idempotent API replay was blocked from re-execution.',false),
 ('incident.escalated.v1','1','INCIDENT','Compatibility event exported through the canonical envelope.',true),
 ('task.terminal.v1','1','TASK','Compatibility event exported through the canonical envelope.',true),
 ('adapter-action.requested.v1','1','ADAPTER_ACTION','Compatibility event exported through the canonical envelope.',true),
 ('dispatch.dead-lettered.v1','1','DISPATCH_REQUEST','Compatibility event exported through the canonical envelope.',true)
on conflict(event_type,payload_version) do nothing;
