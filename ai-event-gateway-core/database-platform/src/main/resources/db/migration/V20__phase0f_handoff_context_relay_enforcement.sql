-- Phase 0F: Handoff Context and Cross-Project Relay enforcement.

-- dispatch_request_id remains the global primary key, but Tenant-aware evidence
-- references require an explicit composite candidate key. The extra UNIQUE is
-- safe for existing data because dispatch_request_id is already globally unique.
do $$ begin
  if not exists(select 1 from pg_constraint where conname='uq_dispatch_requests_tenant_request') then
    alter table dispatch_requests add constraint uq_dispatch_requests_tenant_request
      unique(tenant_id,dispatch_request_id);
  end if;
end $$;

do $$ begin
  if not exists(select 1 from pg_constraint where conname='fk_handoff_policy_tenant') then
    alter table handoff_context_policies add constraint fk_handoff_policy_tenant foreign key(tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_handoff_snapshot_root_task') then
    alter table handoff_context_snapshots add constraint fk_handoff_snapshot_root_task foreign key(tenant_id,root_task_id) references tasks(tenant_id,task_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_handoff_snapshot_source_task') then
    alter table handoff_context_snapshots add constraint fk_handoff_snapshot_source_task foreign key(tenant_id,source_task_id) references tasks(tenant_id,task_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_handoff_snapshot_target_task') then
    alter table handoff_context_snapshots add constraint fk_handoff_snapshot_target_task foreign key(tenant_id,target_task_id) references tasks(tenant_id,task_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_handoff_snapshot_policy') then
    alter table handoff_context_snapshots add constraint fk_handoff_snapshot_policy foreign key(tenant_id,context_policy_id) references handoff_context_policies(tenant_id,policy_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_handoff_snapshot_supersedes') then
    alter table handoff_context_snapshots add constraint fk_handoff_snapshot_supersedes foreign key(tenant_id,supersedes_snapshot_id) references handoff_context_snapshots(tenant_id,snapshot_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_handoff_field_snapshot') then
    alter table handoff_context_field_decisions add constraint fk_handoff_field_snapshot foreign key(tenant_id,snapshot_id) references handoff_context_snapshots(tenant_id,snapshot_id) on delete cascade not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_handoff_approval_snapshot') then
    alter table handoff_context_approvals add constraint fk_handoff_approval_snapshot foreign key(tenant_id,snapshot_id) references handoff_context_snapshots(tenant_id,snapshot_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_result_snapshot_root_task') then
    alter table result_context_snapshots add constraint fk_result_snapshot_root_task foreign key(tenant_id,root_task_id) references tasks(tenant_id,task_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_result_snapshot_source_task') then
    alter table result_context_snapshots add constraint fk_result_snapshot_source_task foreign key(tenant_id,source_task_id) references tasks(tenant_id,task_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_result_snapshot_target_task') then
    alter table result_context_snapshots add constraint fk_result_snapshot_target_task foreign key(tenant_id,target_task_id) references tasks(tenant_id,task_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_result_snapshot_policy') then
    alter table result_context_snapshots add constraint fk_result_snapshot_policy foreign key(tenant_id,policy_id) references handoff_context_policies(tenant_id,policy_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_issue_relay_source_task') then
    alter table cross_project_issue_relays add constraint fk_issue_relay_source_task foreign key(tenant_id,source_task_id) references tasks(tenant_id,task_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_issue_relay_target_task') then
    alter table cross_project_issue_relays add constraint fk_issue_relay_target_task foreign key(tenant_id,target_task_id) references tasks(tenant_id,task_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_issue_relay_source_mapping') then
    alter table cross_project_issue_relays add constraint fk_issue_relay_source_mapping foreign key(tenant_id,source_mapping_id) references integration_project_mappings(tenant_id,mapping_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_issue_relay_target_mapping') then
    alter table cross_project_issue_relays add constraint fk_issue_relay_target_mapping foreign key(tenant_id,target_mapping_id) references integration_project_mappings(tenant_id,mapping_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_issue_relay_snapshot') then
    alter table cross_project_issue_relays add constraint fk_issue_relay_snapshot foreign key(tenant_id,source_snapshot_id) references handoff_context_snapshots(tenant_id,snapshot_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_issue_relay_result_snapshot') then
    alter table cross_project_issue_relays add constraint fk_issue_relay_result_snapshot foreign key(tenant_id,result_snapshot_id) references result_context_snapshots(tenant_id,result_snapshot_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_issue_relay_attempt_relay') then
    alter table issue_relay_attempts add constraint fk_issue_relay_attempt_relay foreign key(tenant_id,relay_id) references cross_project_issue_relays(tenant_id,relay_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_agent_context_task') then
    alter table agent_context_access_events add constraint fk_agent_context_task foreign key(tenant_id,task_id) references tasks(tenant_id,task_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_agent_context_assignment') then
    alter table agent_context_access_events add constraint fk_agent_context_assignment foreign key(tenant_id,assignment_id) references task_assignments(tenant_id,assignment_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_agent_context_dispatch') then
    alter table agent_context_access_events add constraint fk_agent_context_dispatch foreign key(tenant_id,dispatch_request_id) references dispatch_requests(tenant_id,dispatch_request_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_agent_context_snapshot') then
    alter table agent_context_access_events add constraint fk_agent_context_snapshot foreign key(tenant_id,snapshot_id) references handoff_context_snapshots(tenant_id,snapshot_id) not valid;
  end if;
end $$;


do $$ begin
  if not exists(select 1 from pg_constraint where conname='fk_issue_relay_root_task') then
    alter table cross_project_issue_relays add constraint fk_issue_relay_root_task foreign key(tenant_id,root_task_id) references tasks(tenant_id,task_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_issue_relay_a2a_request') then
    alter table cross_project_issue_relays add constraint fk_issue_relay_a2a_request foreign key(tenant_id,a2a_request_id) references a2a_requests(tenant_id,a2a_request_id) not valid;
  end if;
end $$;

create or replace function phase0f_seed_tenant_handoff_policy() returns trigger language plpgsql as $$
begin
 insert into handoff_context_policies(tenant_id,policy_id,policy_name,policy_type,context_requirement,default_field_decision,attachment_policy,approval_mode,result_sharing_policy,enabled)
 values(new.tenant_id,'DEFAULT_HANDOFF','Default Handoff Context','SUMMARY_ONLY','OPTIONAL','OMIT','ATTACHMENT_METADATA_ONLY','NONE','SUMMARY_ONLY',true)
 on conflict (tenant_id,policy_id) do nothing;
 return new;
end $$;
drop trigger if exists trg_phase0f_seed_tenant_handoff_policy on tenants;
create trigger trg_phase0f_seed_tenant_handoff_policy after insert on tenants for each row execute function phase0f_seed_tenant_handoff_policy();


do $$ begin
  if not exists(select 1 from pg_constraint where conname='fk_a2a_policy_handoff_context') then
    alter table a2a_policies add constraint fk_a2a_policy_handoff_context foreign key(tenant_id,handoff_context_policy_id) references handoff_context_policies(tenant_id,policy_id) not valid;
  end if;
end $$;
alter table a2a_policies add constraint ck_a2a_handoff_context_requirement check(handoff_context_requirement in('NONE','OPTIONAL','REQUIRED_BEFORE_DISPATCH','REQUIRED_BEFORE_COMPLETION'));

alter table handoff_context_policies add constraint ck_handoff_policy_type check(policy_type in('NONE','SUMMARY_ONLY','SELECTED_FIELDS','SELECTED_COMMENTS','METADATA_ONLY','NO_ATTACHMENTS','ATTACHMENT_METADATA_ONLY','FULL_APPROVED_SNAPSHOT','CUSTOM_TEMPLATE'));
alter table handoff_context_policies add constraint ck_handoff_context_requirement check(context_requirement in('NONE','OPTIONAL','REQUIRED_BEFORE_DISPATCH','REQUIRED_BEFORE_COMPLETION'));
alter table handoff_context_policies add constraint ck_handoff_default_field_decision check(default_field_decision in('ALLOW','MASK','OMIT','REQUIRE_APPROVAL'));
alter table handoff_context_policies add constraint ck_handoff_attachment_policy check(attachment_policy in('NO_ATTACHMENTS','ATTACHMENT_METADATA_ONLY','FULL_APPROVED_SNAPSHOT'));
alter table handoff_context_policies add constraint ck_handoff_approval_mode check(approval_mode in('NONE','OPERATOR_APPROVAL','SECURITY_APPROVAL','DUAL_APPROVAL'));
alter table handoff_context_field_decisions add constraint ck_handoff_field_share_decision check(share_decision in('ALLOW','MASK','OMIT','REQUIRE_APPROVAL'));
alter table handoff_context_snapshots add constraint ck_handoff_snapshot_status check(status in('DRAFT','PENDING_APPROVAL','APPROVED','SUPERSEDED','EXPIRED','REJECTED'));
alter table handoff_context_snapshots add constraint ck_handoff_snapshot_version check(snapshot_version > 0);
alter table handoff_context_approvals add constraint ck_handoff_approval_decision check(decision in('APPROVE','REJECT'));
alter table cross_project_issue_relays add constraint ck_issue_relay_strategy check(strategy in('NATIVE_RELATION','DUAL_BACKLINK_COMMENT','OPEN_DISPATCH_ONLY','SHARED_SCOPED_PRINCIPAL'));
alter table cross_project_issue_relays add constraint ck_issue_relay_state check(relay_state in('PENDING_SOURCE_CONTEXT','SOURCE_CONTEXT_READY','TARGET_ISSUE_PENDING','TARGET_ISSUE_CREATED','BACKLINK_PENDING','LINKED','PARTIALLY_LINKED','FAILED_RETRYABLE','FAILED_PERMANENT'));
alter table issue_relay_attempts add constraint ck_issue_relay_attempt_side check(operation_side in('SOURCE','TARGET','INTERNAL'));
alter table issue_relay_attempts add constraint ck_issue_relay_attempt_status check(status in('STARTED','SUCCEEDED','FAILED_RETRYABLE','FAILED_PERMANENT','DEGRADED'));
alter table agent_context_access_events add constraint ck_agent_context_decision check(access_decision in('ALLOWED','DENIED'));

create or replace function phase0f_protect_immutable_snapshot() returns trigger language plpgsql as $$
begin
  if tg_op='DELETE' then raise exception 'HANDOFF_CONTEXT_SNAPSHOT_IMMUTABLE'; end if;
  if new.tenant_id is distinct from old.tenant_id
     or new.snapshot_id is distinct from old.snapshot_id
     or new.root_task_id is distinct from old.root_task_id
     or new.source_task_id is distinct from old.source_task_id
     or new.target_task_id is distinct from old.target_task_id
     or new.context_policy_id is distinct from old.context_policy_id
     or new.snapshot_version is distinct from old.snapshot_version
     or new.summary is distinct from old.summary
     or new.structured_context_json is distinct from old.structured_context_json
     or new.allowed_comment_refs_json is distinct from old.allowed_comment_refs_json
     or new.attachment_metadata_json is distinct from old.attachment_metadata_json
     or new.redacted_field_paths_json is distinct from old.redacted_field_paths_json
     or new.omitted_content_reasons_json is distinct from old.omitted_content_reasons_json
     or new.content_hash is distinct from old.content_hash
     or new.created_at is distinct from old.created_at
     or new.created_by_type is distinct from old.created_by_type
     or new.created_by_id is distinct from old.created_by_id then
    raise exception 'HANDOFF_CONTEXT_SNAPSHOT_CONTENT_IMMUTABLE';
  end if;
  if old.status='DRAFT' and new.status in('PENDING_APPROVAL','APPROVED','REJECTED') then return new; end if;
  if old.status='PENDING_APPROVAL' and new.status in('APPROVED','REJECTED') then return new; end if;
  if old.status='APPROVED' and new.status in('SUPERSEDED','EXPIRED') then return new; end if;
  raise exception 'INVALID_HANDOFF_SNAPSHOT_STATUS_TRANSITION';
end $$;
drop trigger if exists trg_phase0f_snapshot_immutable on handoff_context_snapshots;
create trigger trg_phase0f_snapshot_immutable before update or delete on handoff_context_snapshots for each row execute function phase0f_protect_immutable_snapshot();

create or replace function phase0f_protect_snapshot_children() returns trigger language plpgsql as $$
begin raise exception 'HANDOFF_CONTEXT_EVIDENCE_IMMUTABLE'; end $$;
drop trigger if exists trg_phase0f_field_decision_immutable on handoff_context_field_decisions;
create trigger trg_phase0f_field_decision_immutable before update or delete on handoff_context_field_decisions for each row execute function phase0f_protect_snapshot_children();
drop trigger if exists trg_phase0f_approval_immutable on handoff_context_approvals;
create trigger trg_phase0f_approval_immutable before update or delete on handoff_context_approvals for each row execute function phase0f_protect_snapshot_children();
drop trigger if exists trg_phase0f_result_snapshot_immutable on result_context_snapshots;
create trigger trg_phase0f_result_snapshot_immutable before update or delete on result_context_snapshots for each row execute function phase0f_protect_snapshot_children();
drop trigger if exists trg_phase0f_relay_attempt_immutable on issue_relay_attempts;
create trigger trg_phase0f_relay_attempt_immutable before update or delete on issue_relay_attempts for each row execute function phase0f_protect_snapshot_children();
drop trigger if exists trg_phase0f_agent_context_access_immutable on agent_context_access_events;
create trigger trg_phase0f_agent_context_access_immutable before update or delete on agent_context_access_events for each row execute function phase0f_protect_snapshot_children();

create or replace function phase0f_guard_relay_state() returns trigger language plpgsql as $$
begin
  if tg_op='DELETE' then raise exception 'ISSUE_RELAY_EVIDENCE_CANNOT_BE_DELETED'; end if;
  if new.tenant_id is distinct from old.tenant_id or new.relay_id is distinct from old.relay_id
     or new.root_task_id is distinct from old.root_task_id or new.source_task_id is distinct from old.source_task_id
     or new.target_task_id is distinct from old.target_task_id or new.source_mapping_id is distinct from old.source_mapping_id
     or new.target_mapping_id is distinct from old.target_mapping_id or new.strategy is distinct from old.strategy
     or new.idempotency_key is distinct from old.idempotency_key or new.correlation_id is distinct from old.correlation_id
     or new.created_at is distinct from old.created_at then
    raise exception 'ISSUE_RELAY_IDENTITY_FACTS_IMMUTABLE';
  end if;
  if old.relay_state='PENDING_SOURCE_CONTEXT' and new.relay_state in('SOURCE_CONTEXT_READY','FAILED_RETRYABLE','FAILED_PERMANENT') then return new; end if;
  if old.relay_state='SOURCE_CONTEXT_READY' and new.relay_state in('TARGET_ISSUE_PENDING','FAILED_RETRYABLE','FAILED_PERMANENT') then return new; end if;
  if old.relay_state='TARGET_ISSUE_PENDING' and new.relay_state in('TARGET_ISSUE_CREATED','FAILED_RETRYABLE','FAILED_PERMANENT') then return new; end if;
  if old.relay_state='TARGET_ISSUE_CREATED' and new.relay_state in('BACKLINK_PENDING','LINKED','PARTIALLY_LINKED','FAILED_RETRYABLE') then return new; end if;
  if old.relay_state='BACKLINK_PENDING' and new.relay_state in('LINKED','PARTIALLY_LINKED','FAILED_RETRYABLE','FAILED_PERMANENT') then return new; end if;
  if old.relay_state='FAILED_RETRYABLE' and new.relay_state in('SOURCE_CONTEXT_READY','TARGET_ISSUE_PENDING','TARGET_ISSUE_CREATED','BACKLINK_PENDING','LINKED','PARTIALLY_LINKED','FAILED_PERMANENT') then return new; end if;
  if old.relay_state='PARTIALLY_LINKED' and new.relay_state in('BACKLINK_PENDING','LINKED','FAILED_RETRYABLE','FAILED_PERMANENT') then return new; end if;
  raise exception 'INVALID_ISSUE_RELAY_STATE_TRANSITION';
end $$;
drop trigger if exists trg_phase0f_relay_state on cross_project_issue_relays;
create trigger trg_phase0f_relay_state before update or delete on cross_project_issue_relays for each row execute function phase0f_guard_relay_state();

-- Cross-tenant and arbitrary provider reads are blocked structurally: snapshots and relays can only
-- reference same-tenant Tasks, Mappings and Assignments. Provider content must be acquired through a
-- linked Issue workflow, never through a generic issue-key proxy.

alter table handoff_context_policies validate constraint fk_handoff_policy_tenant;
alter table a2a_policies validate constraint fk_a2a_policy_handoff_context;
alter table handoff_context_snapshots validate constraint fk_handoff_snapshot_root_task;
alter table handoff_context_snapshots validate constraint fk_handoff_snapshot_source_task;
alter table handoff_context_snapshots validate constraint fk_handoff_snapshot_target_task;
alter table handoff_context_snapshots validate constraint fk_handoff_snapshot_policy;
alter table handoff_context_snapshots validate constraint fk_handoff_snapshot_supersedes;
alter table handoff_context_field_decisions validate constraint fk_handoff_field_snapshot;
alter table handoff_context_approvals validate constraint fk_handoff_approval_snapshot;
alter table result_context_snapshots validate constraint fk_result_snapshot_root_task;
alter table result_context_snapshots validate constraint fk_result_snapshot_source_task;
alter table result_context_snapshots validate constraint fk_result_snapshot_target_task;
alter table result_context_snapshots validate constraint fk_result_snapshot_policy;
alter table cross_project_issue_relays validate constraint fk_issue_relay_root_task;
alter table cross_project_issue_relays validate constraint fk_issue_relay_a2a_request;
alter table cross_project_issue_relays validate constraint fk_issue_relay_source_task;
alter table cross_project_issue_relays validate constraint fk_issue_relay_target_task;
alter table cross_project_issue_relays validate constraint fk_issue_relay_source_mapping;
alter table cross_project_issue_relays validate constraint fk_issue_relay_target_mapping;
alter table cross_project_issue_relays validate constraint fk_issue_relay_snapshot;
alter table cross_project_issue_relays validate constraint fk_issue_relay_result_snapshot;
alter table issue_relay_attempts validate constraint fk_issue_relay_attempt_relay;
alter table agent_context_access_events validate constraint fk_agent_context_task;
alter table agent_context_access_events validate constraint fk_agent_context_assignment;
alter table agent_context_access_events validate constraint fk_agent_context_dispatch;
alter table agent_context_access_events validate constraint fk_agent_context_snapshot;
